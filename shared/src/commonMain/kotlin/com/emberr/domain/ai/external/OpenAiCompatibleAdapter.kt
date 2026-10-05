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
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessagePayload>,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = true,
    val tools: List<JsonObject>? = null
)

@Serializable
private data class ChatMessagePayload(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallPayload>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null
)

@Serializable
private data class ToolCallPayload(
    val id: String,
    val type: String = "function",
    val function: FunctionCallPayload
)

@Serializable
private data class FunctionCallPayload(
    val name: String,
    val arguments: String
)

@Serializable
private data class ChatCompletionChunk(
    val choices: List<ChunkChoice> = emptyList()
)

@Serializable
private data class ChunkChoice(
    val delta: ChunkDelta = ChunkDelta()
)

@Serializable
private data class ChunkDelta(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ChunkToolCall>? = null
)

@Serializable
private data class ChunkToolCall(
    val index: Int,
    val id: String? = null,
    val function: ChunkFunctionCall? = null
)

@Serializable
private data class ChunkFunctionCall(
    val name: String? = null,
    val arguments: String? = null
)

private class AccumulatedToolCall(val id: String, val name: String, val argumentsJson: String)

private class OpenAiRoundResult(val assistantText: String, val toolCalls: List<AccumulatedToolCall>)

class OpenAiCompatibleAdapter : ChatCompletionAdapter {

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
        val messages = mutableListOf<ChatMessagePayload>()
        messages.add(ChatMessagePayload(role = "system", content = "$systemPrompt\n\n$contextBlock"))
        conversationHistory.forEach { turn ->
            messages.add(ChatMessagePayload(role = "user", content = turn.userMessage))
            messages.add(ChatMessagePayload(role = "assistant", content = turn.assistantMessage))
        }
        messages.add(ChatMessagePayload(role = "user", content = userQuestion))

        val tools = toolDefinitions.takeIf { it.isNotEmpty() && toolRunner != null }
            ?.map { it.toOpenAiToolJson() }

        var remainingToolRounds = NoteToolLimits.MAX_TOOL_ROUNDS_PER_TURN
        while (true) {
            val toolsForThisRound = tools.takeIf { remainingToolRounds > 0 }

            val round = streamOneRound(
                httpClient = httpClient,
                config = config,
                providerDisplayName = providerDisplayName,
                messages = messages,
                tools = toolsForThisRound,
                maxOutputTokens = maxOutputTokens
            )

            if (round.toolCalls.isEmpty()) break

            remainingToolRounds--
            messages.add(
                ChatMessagePayload(
                    role = "assistant",
                    content = round.assistantText.takeIf { it.isNotEmpty() },
                    toolCalls = round.toolCalls.map { toolCall ->
                        ToolCallPayload(
                            id = toolCall.id,
                            function = FunctionCallPayload(name = toolCall.name, arguments = toolCall.argumentsJson)
                        )
                    }
                )
            )

            round.toolCalls.forEach { toolCall ->
                val result = toolRunner!!.run(toolCall.name, parseArguments(toolCall.argumentsJson))
                messages.add(ChatMessagePayload(role = "tool", content = result.renderForModel(), toolCallId = toolCall.id))
            }
        }
    }

    private suspend fun FlowCollector<String>.streamOneRound(
        httpClient: HttpClient,
        config: ExternalAiProviderConfig,
        providerDisplayName: String,
        messages: List<ChatMessagePayload>,
        tools: List<JsonObject>?,
        maxOutputTokens: Int
    ): OpenAiRoundResult {
        val requestBody = json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(model = config.model, messages = messages, maxTokens = maxOutputTokens, tools = tools)
        )

        val assistantText = StringBuilder()
        val toolCallIdsByIndex = mutableMapOf<Int, String>()
        val toolCallNamesByIndex = mutableMapOf<Int, String>()
        val toolCallArgumentsByIndex = mutableMapOf<Int, StringBuilder>()
        val orderedToolCallIndices = mutableListOf<Int>()

        httpClient.preparePost(resolveEndpoint(config)) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
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
                if (payload == "[DONE]") break

                val chunk = try {
                    json.decodeFromString(ChatCompletionChunk.serializer(), payload)
                } catch (cause: SerializationException) {
                    continue
                }

                val delta = chunk.choices.firstOrNull()?.delta ?: continue

                val text = delta.content
                if (!text.isNullOrEmpty()) {
                    assistantText.append(text)
                    emit(text)
                }

                delta.toolCalls?.forEach { toolCall ->
                    val index = toolCall.index
                    if (!orderedToolCallIndices.contains(index)) orderedToolCallIndices.add(index)
                    toolCall.id?.let { toolCallIdsByIndex[index] = it }
                    toolCall.function?.name?.let { toolCallNamesByIndex[index] = it }
                    toolCall.function?.arguments?.let { fragment ->
                        toolCallArgumentsByIndex.getOrPut(index) { StringBuilder() }.append(fragment)
                    }
                }
            }
        }

        val toolCalls = orderedToolCallIndices.map { index ->
            AccumulatedToolCall(
                id = toolCallIdsByIndex[index].orEmpty(),
                name = toolCallNamesByIndex[index].orEmpty(),
                argumentsJson = toolCallArgumentsByIndex[index]?.toString().orEmpty()
            )
        }

        return OpenAiRoundResult(assistantText.toString(), toolCalls)
    }

    private fun parseArguments(argumentsJson: String): Map<String, String> {
        if (argumentsJson.isBlank()) return emptyMap()
        val parsed = try {
            json.parseToJsonElement(argumentsJson)
        } catch (cause: SerializationException) {
            return emptyMap()
        }
        return (parsed as? JsonObject)?.toNoteToolArguments().orEmpty()
    }

    private fun NoteToolDefinition.toOpenAiToolJson(): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            putJsonObject("parameters") {
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
    }

    private fun resolveEndpoint(config: ExternalAiProviderConfig): String {
        val base = config.baseUrl?.trimEnd('/') ?: DEFAULT_OPENAI_BASE_URL
        return "$base/chat/completions"
    }

    private companion object {
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
    }
}
