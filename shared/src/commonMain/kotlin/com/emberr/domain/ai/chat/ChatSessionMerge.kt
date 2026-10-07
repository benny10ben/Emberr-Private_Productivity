package com.emberr.domain.ai.chat

import com.emberr.data.local.room.entity.ChatSessionEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object ChatSessionMerge {

    private val json = Json { ignoreUnknownKeys = true }
    private val messageListSerializer = ListSerializer(ChatMessage.serializer())
    private val messageIdSetSerializer = SetSerializer(String.serializer())

    fun merge(localSession: ChatSessionEntity, remoteSession: ChatSessionEntity): ChatSessionEntity {
        val remoteIsNewer = remoteSession.updatedAt > localSession.updatedAt
        val newerSession = if (remoteIsNewer) remoteSession else localSession
        val olderSession = if (remoteIsNewer) localSession else remoteSession

        val removedMessageIds = readRemovedMessageIds(newerSession) + readRemovedMessageIds(olderSession)
        val mergedMessages = mergeMessages(
            newerSessionMessages = readMessages(newerSession),
            olderSessionMessages = readMessages(olderSession),
            removedMessageIds = removedMessageIds
        )

        return newerSession.copy(
            messagesJson = json.encodeToString(messageListSerializer, mergedMessages),
            removedMessageIdsJson = json.encodeToString(messageIdSetSerializer, removedMessageIds),
            createdAt = minOf(localSession.createdAt, remoteSession.createdAt)
        )
    }

    fun mergeWithServerCopy(localSession: ChatSessionEntity, serverSession: ChatSessionEntity): ChatSessionEntity {
        val mergedSession = merge(localSession, serverSession)
        if (hasSameContent(mergedSession, serverSession)) return serverSession
        return mergedSession.copy(updatedAt = maxOf(mergedSession.updatedAt, serverSession.updatedAt + 1))
    }

    private fun hasSameContent(firstSession: ChatSessionEntity, secondSession: ChatSessionEntity): Boolean =
        firstSession.title == secondSession.title &&
            firstSession.spaceId == secondSession.spaceId &&
            firstSession.createdAt == secondSession.createdAt &&
            readMessages(firstSession) == readMessages(secondSession) &&
            readRemovedMessageIds(firstSession) == readRemovedMessageIds(secondSession)

    internal fun mergeMessages(
        newerSessionMessages: List<ChatMessage>,
        olderSessionMessages: List<ChatMessage>,
        removedMessageIds: Set<String>
    ): List<ChatMessage> {
        val olderCopiesById = olderSessionMessages.associateBy { it.id }
        val merged = newerSessionMessages.map { newerCopy ->
            val olderCopy = olderCopiesById[newerCopy.id]
            if (olderCopy != null && olderCopy.updatedAt > newerCopy.updatedAt) olderCopy else newerCopy
        }.toMutableList()

        val idsInNewerSession = newerSessionMessages.mapTo(HashSet()) { it.id }
        olderSessionMessages.forEachIndexed { index, olderOnlyMessage ->
            if (olderOnlyMessage.id in idsInNewerSession) return@forEachIndexed
            val precedingMessageId = olderSessionMessages.subList(0, index)
                .lastOrNull { previous -> merged.any { it.id == previous.id } }
                ?.id
            val insertAt = if (precedingMessageId == null) 0 else merged.indexOfFirst { it.id == precedingMessageId } + 1
            merged.add(insertAt, olderOnlyMessage)
        }

        return merged.filter { it.id !in removedMessageIds }
    }

    private fun readMessages(session: ChatSessionEntity): List<ChatMessage> =
        try {
            json.decodeFromString(messageListSerializer, session.messagesJson)
        } catch (cause: SerializationException) {
            emptyList()
        }

    private fun readRemovedMessageIds(session: ChatSessionEntity): Set<String> =
        try {
            json.decodeFromString(messageIdSetSerializer, session.removedMessageIdsJson)
        } catch (cause: SerializationException) {
            emptySet()
        }
}
