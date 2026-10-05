package com.emberr.domain.ai.external

import com.emberr.domain.ai.chat.ChatTurn
import com.emberr.domain.ai.tools.NoteToolDefinition
import com.emberr.domain.ai.tools.NoteToolLimits
import com.emberr.domain.ai.tools.NoteToolExecutor
import com.emberr.domain.ai.tools.renderForModel
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Serializable
private data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null,
    val functionResponse: GeminiFunctionResponse? = null,
    val thoughtSignature: String? = null
)

@Serializable
private data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap())
)

@Serializable
private data class GeminiFunctionResponse(
    val name: String,
    val response: JsonObject
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
private data class GeminiGenerationConfig(
    val maxOutputTokens: Int
)

@Serializable
private data class GeminiRequest(
    @SerialName("system_instruction") val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
    val tools: List<JsonObject>? = null
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
private data class GeminiStreamChunk(
    val candidates: List<GeminiCandidate> = emptyList()
)

private class GeminiRoundResult(val functionCallParts: List<GeminiPart>)

class GeminiAdapter : ChatCompletionAdapter {

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
        val contents = mutableListOf<GeminiContent>()
        conversationHistory.forEach { turn ->
            contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = turn.userMessage))))
            contents.add(GeminiContent(role = "model", parts = listOf(GeminiPart(text = turn.assistantMessage))))
        }
        contents.add(GeminiContent(role = "user", parts = listOf(GeminiPart(text = userQuestion))))

        val tools = toolDefinitions.takeIf { it.isNotEmpty() && toolRunner != null }
            ?.let { listOf(it.toGeminiToolsJson()) }

        var remainingToolRounds = NoteToolLimits.MAX_TOOL_ROUNDS_PER_TURN
        while (true) {
            val toolsForThisRound = tools.takeIf { remainingToolRounds > 0 }

            val round = streamOneRound(
                httpClient = httpClient,
                config = config,
                providerDisplayName = providerDisplayName,
                systemPrompt = systemPrompt,
                contextBlock = contextBlock,
                contents = contents,
                tools = toolsForThisRound,
                maxOutputTokens = maxOutputTokens
            )

            if (round.functionCallParts.isEmpty()) break

            remainingToolRounds--
            contents.add(GeminiContent(role = "model", parts = round.functionCallParts))
            contents.add(
                GeminiContent(
                    role = "user",
                    parts = round.functionCallParts.map { part ->
                        val functionCall = part.functionCall!!
                        val result = toolRunner!!.run(functionCall.name, functionCall.args.toNoteToolArguments())
                        GeminiPart(
                            functionResponse = GeminiFunctionResponse(
                                name = functionCall.name,
                                response = buildJsonObject {
                                    put("name", functionCall.name)
                                    put("content", result.renderForModel())
                                }
                            )
                        )
                    }
                )
            )
        }
    }

    private suspend fun FlowCollector<String>.streamOneRound(
        httpClient: HttpClient,
        config: ExternalAiProviderConfig,
        providerDisplayName: String,
        systemPrompt: String,
        contextBlock: String,
        contents: List<GeminiContent>,
        tools: List<JsonObject>?,
        maxOutputTokens: Int
    ): GeminiRoundResult {
        val requestBody = json.encodeToString(
            GeminiRequest.serializer(),
            GeminiRequest(
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = "$systemPrompt\n\n$contextBlock"))),
                contents = contents,
                generationConfig = GeminiGenerationConfig(maxOutputTokens = maxOutputTokens),
                tools = tools
            )
        )

        val functionCallParts = mutableListOf<GeminiPart>()

        httpClient.preparePost("$API_BASE_URL/${config.model}:streamGenerateContent") {
            parameter("alt", "sse")
            parameter("key", config.apiKey)
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

                val chunk = try {
                    json.decodeFromString(GeminiStreamChunk.serializer(), payload)
                } catch (cause: SerializationException) {
                    continue
                }

                val parts = chunk.candidates.firstOrNull()?.content?.parts.orEmpty()
                parts.forEach { part ->
                    val text = part.text
                    if (!text.isNullOrEmpty()) emit(text)

                    if (part.functionCall != null) functionCallParts.add(part)
                }
            }
        }

        return GeminiRoundResult(functionCallParts)
    }

    private fun List<NoteToolDefinition>.toGeminiToolsJson(): JsonObject = buildJsonObject {
        putJsonArray("functionDeclarations") {
            forEach { definition ->
                addJsonObject {
                    put("name", definition.name)
                    put("description", definition.description)
                    putJsonObject("parameters") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            definition.parameters.forEach { parameter ->
                                putJsonObject(parameter.name) {
                                    put("type", "STRING")
                                    put("description", parameter.description)
                                }
                            }
                        }
                        putJsonArray("required") {
                            definition.parameters.filter { it.isRequired }.forEach { add(it.name) }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }
}
