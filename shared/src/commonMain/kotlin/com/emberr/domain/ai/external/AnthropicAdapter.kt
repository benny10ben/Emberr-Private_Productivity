package com.emberr.domain.ai.external

import com.emberr.domain.ai.chat.ChatTurn
import com.emberr.domain.ai.tools.NoteToolDefinition
import com.emberr.domain.ai.tools.NoteToolLimits
import com.emberr.domain.ai.tools.NoteToolExecutor
import com.emberr.domain.ai.tools.renderForModel
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: JsonElement
)

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
    val stream: Boolean = true,
    val tools: List<JsonObject>? = null
)

@Serializable
private data class AnthropicContentBlock(
    val type: String,
    val id: String? = null,
    val name: String? = null
)

@Serializable
private data class AnthropicDelta(
    val type: String? = null,
    val text: String? = null,
    @SerialName("partial_json") val partialJson: String? = null
)

@Serializable
private data class AnthropicStreamEvent(
    val type: String,
    val index: Int? = null,
    @SerialName("content_block") val contentBlock: AnthropicContentBlock? = null,
    val delta: AnthropicDelta? = null
)

private class ToolUseRequest(val id: String, val name: String, val input: JsonObject)

private class AnthropicRoundResult(val assistantContent: JsonArray, val toolUseBlocks: List<ToolUseRequest>)

class AnthropicAdapter : ChatCompletionAdapter {

    private val json = Json { ignoreUnknownKeys = true }

    override fun streamChatCompletion(
        httpClient: HttpClient,
        config: ExternalAiProviderConfig,
        providerDisplayName: String,
        systemPrompt: String,
        userQuestion: String,
        contextBlock: String,
        conversationHistory: List<ChatTurn>,
        maxOutputTokens: Int,
        toolDefinitions: List<NoteToolDefinition>,
        toolRunner: NoteToolExecutor?
    ): Flow<String> = flow {
        val messages = mutableListOf<AnthropicMessage>()
        conversationHistory.forEach { turn ->
            messages.add(AnthropicMessage(role = "user", content = JsonPrimitive(turn.userMessage)))
            messages.add(AnthropicMessage(role = "assistant", content = JsonPrimitive(turn.assistantMessage)))
        }
        messages.add(AnthropicMessage(role = "user", content = JsonPrimitive(userQuestion)))

        val tools = toolDefinitions.takeIf { it.isNotEmpty() && toolRunner != null }
            ?.map { it.toAnthropicToolJson() }

        var remainingToolRounds = NoteToolLimits.MAX_TOOL_ROUNDS_PER_TURN
        while (true) {
            val toolsForThisRound = tools.takeIf { remainingToolRounds > 0 }

            val round = streamOneRound(
                httpClient = httpClient,
                config = config,
                providerDisplayName = providerDisplayName,
                systemPrompt = systemPrompt,
                contextBlock = contextBlock,
                messages = messages,
                tools = toolsForThisRound,
                maxOutputTokens = maxOutputTokens
            )

            if (round.toolUseBlocks.isEmpty()) break

            remainingToolRounds--
            messages.add(AnthropicMessage(role = "assistant", content = round.assistantContent))

            val toolResults = buildJsonArray {
                round.toolUseBlocks.forEach { toolUse ->
                    val result = toolRunner!!.run(toolUse.name, toolUse.input.toNoteToolArguments())
                    addJsonObject {
                        put("type", "tool_result")
                        put("tool_use_id", toolUse.id)
                        put("content", result.renderForModel())
                    }
                }
            }
            messages.add(AnthropicMessage(role = "user", content = toolResults))
        }
    }

    private suspend fun FlowCollector<String>.streamOneRound(
        httpClient: HttpClient,
        config: ExternalAiProviderConfig,
        providerDisplayName: String,
        systemPrompt: String,
        contextBlock: String,
        messages: List<AnthropicMessage>,
        tools: List<JsonObject>?,
        maxOutputTokens: Int
    ): AnthropicRoundResult {
        val requestBody = json.encodeToString(
            AnthropicRequest.serializer(),
            AnthropicRequest(
                model = config.model,
                maxTokens = maxOutputTokens,
                system = "$systemPrompt\n\n$contextBlock",
                messages = messages,
                tools = tools
            )
        )

        val blockTypesByIndex = mutableMapOf<Int, String>()
        val textByIndex = mutableMapOf<Int, StringBuilder>()
        val partialJsonByIndex = mutableMapOf<Int, StringBuilder>()
        val toolIdsByIndex = mutableMapOf<Int, String>()
        val toolNamesByIndex = mutableMapOf<Int, String>()
        val orderedIndices = mutableListOf<Int>()

        httpClient.preparePost(API_ENDPOINT) {
            header("x-api-key", config.apiKey)
            header("anthropic-version", ANTHROPIC_VERSION)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.execute { response ->
            if (response.status.value !in 200..299) {
                throwForFailedResponse(response, providerDisplayName)
            }

            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                if (!line.startsWith("data:")) continue

                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue

                val event = try {
                    json.decodeFromString(AnthropicStreamEvent.serializer(), payload)
                } catch (cause: SerializationException) {
                    continue
                }

                when (event.type) {
                    "content_block_start" -> {
                        val index = event.index
                        val blockType = event.contentBlock?.type
                        if (index != null && blockType != null) {
                            blockTypesByIndex[index] = blockType
                            orderedIndices.add(index)
                            if (blockType == "tool_use") {
                                toolIdsByIndex[index] = event.contentBlock.id.orEmpty()
                                toolNamesByIndex[index] = event.contentBlock.name.orEmpty()
                                partialJsonByIndex[index] = StringBuilder()
                            } else {
                                textByIndex[index] = StringBuilder()
                            }
                        }
                    }

                    "content_block_delta" -> {
                        val index = event.index
                        if (index != null) {
                            when (event.delta?.type) {
                                "text_delta" -> {
                                    val text = event.delta.text.orEmpty()
                                    if (text.isNotEmpty()) {
                                        textByIndex[index]?.append(text)
                                        emit(text)
                                    }
                                }

                                "input_json_delta" -> {
                                    partialJsonByIndex[index]?.append(event.delta.partialJson.orEmpty())
                                }
                            }
                        }
                    }

                    "message_stop" -> break
                }
            }
        }

        val assistantContent = buildJsonArray {
            orderedIndices.forEach { index ->
                when (blockTypesByIndex[index]) {
                    "text" -> addJsonObject {
                        put("type", "text")
                        put("text", textByIndex[index]?.toString().orEmpty())
                    }

                    "tool_use" -> addJsonObject {
                        put("type", "tool_use")
                        put("id", toolIdsByIndex.getValue(index))
                        put("name", toolNamesByIndex.getValue(index))
                        put("input", parseToolInput(partialJsonByIndex[index]?.toString()))
                    }
                }
            }
        }

        val toolUseBlocks = orderedIndices
            .filter { blockTypesByIndex[it] == "tool_use" }
            .map { index ->
                ToolUseRequest(
                    id = toolIdsByIndex.getValue(index),
                    name = toolNamesByIndex.getValue(index),
                    input = parseToolInput(partialJsonByIndex[index]?.toString())
                )
            }

        return AnthropicRoundResult(assistantContent, toolUseBlocks)
    }

    private fun parseToolInput(jsonString: String?): JsonObject {
        if (jsonString.isNullOrBlank()) return JsonObject(emptyMap())
        val parsed = try {
            json.parseToJsonElement(jsonString)
        } catch (cause: SerializationException) {
            return JsonObject(emptyMap())
        }
        return parsed as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun NoteToolDefinition.toAnthropicToolJson(): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                parameters.forEach { parameter ->
                    putJsonObject(parameter.name) {
                        put("type", "string")
                        put("description", parameter.description)
                    }
                }
            }
            putJsonArray("required") {
                parameters.filter { it.isRequired }.forEach { add(it.name) }
            }
        }
    }

    private companion object {
        const val API_ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
