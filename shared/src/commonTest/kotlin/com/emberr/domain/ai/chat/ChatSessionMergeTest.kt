package com.emberr.domain.ai.chat

import com.emberr.data.local.room.entity.ChatSessionEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatSessionMergeTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val messageListSerializer = ListSerializer(ChatMessage.serializer())
    private val messageIdSetSerializer = SetSerializer(String.serializer())

    private fun message(id: String, text: String = id, updatedAt: Long = 1L) =
        ChatMessage(id = id, text = text, isUser = id.startsWith("question"), updatedAt = updatedAt)

    private val sharedMessages = listOf(message("question-1"), message("answer-1"), message("question-2"), message("answer-2"))

    private fun session(
        messages: List<ChatMessage>,
        updatedAt: Long,
        removedMessageIds: Set<String> = emptySet(),
        title: String = "Trip planning",
        createdAt: Long = 1L
    ) = ChatSessionEntity(
        id = "trip-planning",
        title = title,
        messagesJson = json.encodeToString(messageListSerializer, messages),
        removedMessageIdsJson = json.encodeToString(messageIdSetSerializer, removedMessageIds),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ChatSessionEntity.messageIds(): List<String> =
        json.decodeFromString(messageListSerializer, messagesJson).map { it.id }

    private fun ChatSessionEntity.removedIds(): Set<String> =
        json.decodeFromString(messageIdSetSerializer, removedMessageIdsJson)

    @Test
    fun messagesAddedOnBothDevicesAreAllKept() {
        val phone = session(sharedMessages + message("question-hotels") + message("answer-hotels"), updatedAt = 10L)
        val desktop = session(sharedMessages + message("question-flights") + message("answer-flights"), updatedAt = 20L)

        val merged = ChatSessionMerge.merge(localSession = phone, remoteSession = desktop)

        assertEquals(
            listOf(
                "question-1", "answer-1", "question-2", "answer-2",
                "question-hotels", "answer-hotels", "question-flights", "answer-flights"
            ),
            merged.messageIds()
        )
    }

    @Test
    fun theResultIsTheSameOnBothDevices() {
        val phone = session(sharedMessages + message("question-hotels") + message("answer-hotels"), updatedAt = 10L)
        val desktop = session(sharedMessages + message("question-flights") + message("answer-flights"), updatedAt = 20L)

        val mergedOnPhone = ChatSessionMerge.merge(localSession = phone, remoteSession = desktop)
        val mergedOnDesktop = ChatSessionMerge.merge(localSession = desktop, remoteSession = phone)

        assertEquals(mergedOnPhone.messageIds(), mergedOnDesktop.messageIds())
    }

    @Test
    fun aMessageRemovedOnOneDeviceDoesNotComeBackFromTheOther() {
        val phoneAfterEditingQuestionTwo = session(
            sharedMessages.take(2) + message("question-2-edited") + message("answer-2-edited"),
            updatedAt = 20L,
            removedMessageIds = setOf("question-2", "answer-2")
        )
        val desktop = session(sharedMessages, updatedAt = 10L)

        val merged = ChatSessionMerge.merge(localSession = desktop, remoteSession = phoneAfterEditingQuestionTwo)

        assertEquals(listOf("question-1", "answer-1", "question-2-edited", "answer-2-edited"), merged.messageIds())
    }

    @Test
    fun aRemovalStillWinsWhenTheDeviceThatRemovedItHasTheOlderChat() {
        val phoneThatRemovedAnswerTwo = session(sharedMessages.dropLast(1), updatedAt = 10L, removedMessageIds = setOf("answer-2"))
        val desktopWithANewerMessage = session(sharedMessages + message("question-3"), updatedAt = 20L)

        val merged = ChatSessionMerge.merge(localSession = phoneThatRemovedAnswerTwo, remoteSession = desktopWithANewerMessage)

        assertEquals(listOf("question-1", "answer-1", "question-2", "question-3"), merged.messageIds())
    }

    @Test
    fun removedMessagesFromBothDevicesAreRemembered() {
        val phone = session(sharedMessages, updatedAt = 10L, removedMessageIds = setOf("old-placeholder-1"))
        val desktop = session(sharedMessages, updatedAt = 20L, removedMessageIds = setOf("old-placeholder-2"))

        val merged = ChatSessionMerge.merge(localSession = phone, remoteSession = desktop)

        assertEquals(setOf("old-placeholder-1", "old-placeholder-2"), merged.removedIds())
    }

    @Test
    fun theLatestVersionOfAMessageWinsEvenFromTheOlderChat() {
        val finishedAnswer = message("answer-3", text = "Rome has great food", updatedAt = 30L)
        val emptyPlaceholder = message("answer-3", text = "", updatedAt = 5L)
        val phoneThatFinishedTheAnswer = session(sharedMessages + message("question-3") + finishedAnswer, updatedAt = 30L)
        val desktopThatOnlySawThePlaceholder = session(
            sharedMessages + message("question-3") + emptyPlaceholder + message("question-4"),
            updatedAt = 40L
        )

        val merged = ChatSessionMerge.merge(localSession = phoneThatFinishedTheAnswer, remoteSession = desktopThatOnlySawThePlaceholder)

        val answer = json.decodeFromString(messageListSerializer, merged.messagesJson).single { it.id == "answer-3" }
        assertEquals("Rome has great food", answer.text)
    }

    @Test
    fun mergingAChatWithItselfChangesNothing() {
        val phone = session(sharedMessages, updatedAt = 10L)

        val merged = ChatSessionMerge.merge(localSession = phone, remoteSession = phone)

        assertEquals(phone.messageIds(), merged.messageIds())
        assertEquals(phone.updatedAt, merged.updatedAt)
    }

    @Test
    fun theNewerChatsTitleAndTimeAreKeptWithTheEarliestCreationTime() {
        val phone = session(sharedMessages, updatedAt = 10L, title = "Old title", createdAt = 1L)
        val desktop = session(sharedMessages, updatedAt = 20L, title = "New title", createdAt = 5L)

        val merged = ChatSessionMerge.merge(localSession = phone, remoteSession = desktop)

        assertEquals("New title", merged.title)
        assertEquals(20L, merged.updatedAt)
        assertEquals(1L, merged.createdAt)
    }

    @Test
    fun unreadableMessagesFromTheOtherDeviceDoNotEraseThisDevicesMessages() {
        val phone = session(sharedMessages, updatedAt = 10L)
        val brokenDesktopCopy = session(emptyList(), updatedAt = 20L).copy(messagesJson = "not json")

        val merged = ChatSessionMerge.merge(localSession = phone, remoteSession = brokenDesktopCopy)

        assertEquals(phone.messageIds(), merged.messageIds())
    }
}
