package com.emberr.domain.ai.chat

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Clock

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val toolCallSummary: String? = null,
    val updatedAt: Long = Clock.System.now().toEpochMilliseconds()
)
