package com.emberr.domain.ai.external

import com.emberr.domain.ai.AiGenerationEngine
import com.emberr.domain.ai.chat.ChatTurn
import com.emberr.domain.ai.tools.NoteToolExecutor
import com.emberr.domain.ai.tools.NoteTools
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ExternalAiEngine(
    private val aiSettingsRepository: AiSettingsRepository,
    private val noteToolExecutor: NoteToolExecutor
) : AiGenerationEngine {

    private val httpClient = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
    }

    private val openAiCompatibleAdapter = OpenAiCompatibleAdapter()
    private val anthropicAdapter = AnthropicAdapter()
    private val geminiAdapter = GeminiAdapter()

    override suspend fun isModelAvailable(): Boolean {
        val provider = aiSettingsRepository.selectedExternalAiProvider.first()
        val config = aiSettingsRepository.getProviderConfig(provider)
        return !config?.apiKey.isNullOrBlank()
    }

    override fun generateResponseStream(
        systemPrompt: String,
        userQuestion: String,
        contextBlock: String,
        conversationHistory: List<ChatTurn>
    ): Flow<String> = flow {
        val provider = aiSettingsRepository.selectedExternalAiProvider.first()
        val config = aiSettingsRepository.getProviderConfig(provider)

        if (config == null || config.apiKey.isBlank()) {
            throw ExternalAiException.NotConfigured(provider.displayName)
        }

        val maxOutputTokens = aiSettingsRepository.maxOutputTokens.first()

        try {
            adapterFor(provider).streamChatCompletion(
                httpClient = httpClient,
                config = config,
                providerDisplayName = provider.displayName,
                systemPrompt = systemPrompt,
                userQuestion = userQuestion,
                contextBlock = contextBlock,
                conversationHistory = conversationHistory,
                maxOutputTokens = maxOutputTokens,
                toolDefinitions = NoteTools.all,
                toolRunner = noteToolExecutor
            ).collect { token -> emit(token) }
        } catch (cause: ExternalAiException) {
            throw cause
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: HttpRequestTimeoutException) {
            throw ExternalAiException.TimedOut(provider.displayName)
        } catch (cause: Exception) {
            throw ExternalAiException.NoConnection(provider.displayName)
        }
    }.flowOn(Dispatchers.IO)

    private fun adapterFor(provider: ExternalAiProvider): ChatCompletionAdapter = when (provider) {
        ExternalAiProvider.OPENAI, ExternalAiProvider.CUSTOM -> openAiCompatibleAdapter
        ExternalAiProvider.ANTHROPIC -> anthropicAdapter
        ExternalAiProvider.GEMINI -> geminiAdapter
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val REQUEST_TIMEOUT_MS = 60_000L
        const val SOCKET_TIMEOUT_MS = 60_000L
    }
}
