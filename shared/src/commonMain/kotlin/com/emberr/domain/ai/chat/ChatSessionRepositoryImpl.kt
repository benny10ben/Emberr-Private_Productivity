package com.emberr.domain.ai.chat

import com.emberr.data.local.room.dao.ChatSessionDao
import com.emberr.data.local.room.entity.ChatSessionEntity
import com.emberr.domain.sync.AutoSyncTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatSessionRepositoryImpl(
    private val chatSessionDao: ChatSessionDao,
    private val activeSpaceStore: com.emberr.domain.space.ActiveSpaceStore
) : ChatSessionRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val messageListSerializer = ListSerializer(ChatMessage.serializer())

    override fun getAllSessions(): Flow<List<ChatSession>> =
        activeSpaceStore.activeSpaceId.flatMapLatest { spaceId ->
            chatSessionDao.getAllSessions(spaceId).map { entities -> entities.map { it.toDomain() } }
        }

    override suspend fun getSession(sessionId: String): ChatSession? =
        withContext(Dispatchers.IO) { chatSessionDao.getSession(sessionId)?.toDomain() }

    override suspend fun saveSession(session: ChatSession) {
        withContext(Dispatchers.IO) {
            val existingSpaceId = chatSessionDao.getSession(session.id)?.spaceId
            chatSessionDao.upsertSession(
                ChatSessionEntity(
                    id = session.id,
                    title = session.title,
                    messagesJson = json.encodeToString(messageListSerializer, session.messages),
                    createdAt = session.createdAt,
                    updatedAt = session.updatedAt,
                    spaceId = existingSpaceId ?: activeSpaceStore.currentActiveSpaceId()
                )
            )
        }
        AutoSyncTrigger.requestSync()
    }

    override suspend fun deleteSession(sessionId: String) {
        withContext(Dispatchers.IO) { chatSessionDao.softDeleteSession(sessionId, System.currentTimeMillis()) }
        AutoSyncTrigger.requestSync()
    }

    override suspend fun deleteSessionsInSpace(spaceId: String) {
        withContext(Dispatchers.IO) {
            val deletedAt = System.currentTimeMillis()
            chatSessionDao.getAllSessionsIncludingDeleted()
                .filter { it.spaceId == spaceId && !it.isDeleted }
                .forEach { session -> chatSessionDao.softDeleteSession(session.id, deletedAt) }
        }
        AutoSyncTrigger.requestSync()
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        withContext(Dispatchers.IO) { chatSessionDao.renameSession(sessionId, title, System.currentTimeMillis()) }
        AutoSyncTrigger.requestSync()
    }

    private fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
        id = id,
        title = title,
        messages = try {
            json.decodeFromString(messageListSerializer, messagesJson)
        } catch (cause: SerializationException) {
            emptyList()
        },
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
