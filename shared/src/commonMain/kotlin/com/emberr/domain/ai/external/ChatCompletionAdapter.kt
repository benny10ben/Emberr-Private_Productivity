package com.emberr.domain.ai.external

import com.emberr.domain.ai.chat.ChatTurn
import com.emberr.domain.ai.tools.NoteToolDefinition
import com.emberr.domain.ai.tools.NoteToolExecutor
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow

interface ChatCompletionAdapter {
    fun streamChatCompletion(
        httpClient: HttpClient,
        config: ExternalAiProviderConfig,
        providerDisplayName: String,
        systemPrompt: String,
        userQuestion: String,
        contextBlock: String,
        conversationHistory: List<ChatTurn>,
        maxOutputTokens: Int,
        toolDefinitions: List<NoteToolDefinition> = emptyList(),
        toolRunner: NoteToolExecutor? = null
    ): Flow<String>
}
