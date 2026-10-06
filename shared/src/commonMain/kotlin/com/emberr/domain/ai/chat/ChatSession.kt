package com.emberr.domain.ai.chat

data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val removedMessageIds: Set<String> = emptySet(),
    val createdAt: Long,
    val updatedAt: Long
)
