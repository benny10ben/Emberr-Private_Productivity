package com.emberr.domain.ai.chat

import kotlinx.coroutines.flow.Flow

interface ChatSessionRepository {
    fun getAllSessions(): Flow<List<ChatSession>>
    suspend fun getSession(sessionId: String): ChatSession?
    suspend fun saveSession(session: ChatSession)
    suspend fun deleteSession(sessionId: String)
    suspend fun deleteSessionsInSpace(spaceId: String)
    suspend fun renameSession(sessionId: String, title: String)
}
