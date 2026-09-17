package com.emberr.data.local.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions WHERE spaceId = :spaceId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllSessions(spaceId: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Upsert
    suspend fun upsertSession(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET isDeleted = 1, updatedAt = :deletedAt WHERE id = :sessionId")
    suspend fun softDeleteSession(sessionId: String, deletedAt: Long)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun renameSession(sessionId: String, title: String, updatedAt: Long)

    @Query("SELECT * FROM chat_sessions WHERE isDeleted = 1 AND updatedAt > :timestamp")
    suspend fun getTombstonesModifiedSince(timestamp: Long): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE updatedAt > :timestamp")
    suspend fun getSessionsModifiedSince(timestamp: Long): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions")
    suspend fun getAllSessionsIncludingDeleted(): List<ChatSessionEntity>
}
