package com.emberr.domain.selfhost.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeNoteManifestEntriesTest {

    private fun note(entryId: String, updatedAt: Long) =
        SelfHostManifestEntry(entryId = entryId, entryType = SelfHostEntryType.NOTE, updatedAt = updatedAt)

    private fun merge(
        serverEntries: List<SelfHostManifestEntry>,
        localEntries: List<SelfHostManifestEntry>,
        entryIdsStillWaitingToUpload: Set<String> = emptySet(),
        tombstoneIds: Set<String> = emptySet()
    ): Map<String, SelfHostManifestEntry> =
        mergeNoteManifestEntries(serverEntries, localEntries, entryIdsStillWaitingToUpload, tombstoneIds)
            .associateBy { it.entryId }

    @Test
    fun aServerNoteThatIsNotOnThisDeviceYetStaysInTheList() {
        val merged = merge(
            serverEntries = listOf(note("a", 100), note("b", 100), note("c", 100)),
            localEntries = listOf(note("a", 100), note("b", 100))
        )

        assertEquals(setOf("a", "b", "c"), merged.keys)
        assertEquals(100, merged.getValue("c").updatedAt)
    }

    @Test
    fun aNoteAnotherDeviceAddedAndThisDeviceAddedAreBothListed() {
        val merged = merge(
            serverEntries = listOf(note("added-by-laptop", 300)),
            localEntries = listOf(note("added-by-phone", 300))
        )

        assertEquals(setOf("added-by-laptop", "added-by-phone"), merged.keys)
    }

    @Test
    fun aNewerEditFromAnotherDeviceIsNotHiddenByThisDevicesOlderCopy() {
        val merged = merge(
            serverEntries = listOf(note("m", updatedAt = 1005)),
            localEntries = listOf(note("m", updatedAt = 1000))
        )

        assertEquals(1005, merged.getValue("m").updatedAt)
    }

    @Test
    fun aNewerEditUploadedFromThisDeviceReplacesTheOlderServerEntry() {
        val merged = merge(
            serverEntries = listOf(note("m", updatedAt = 1000)),
            localEntries = listOf(note("m", updatedAt = 1005))
        )

        assertEquals(1005, merged.getValue("m").updatedAt)
    }

    @Test
    fun aNoteThisDeviceCouldNotUploadKeepsTheServerEntry() {
        val merged = merge(
            serverEntries = listOf(note("n", updatedAt = 1000)),
            localEntries = listOf(note("n", updatedAt = 1005)),
            entryIdsStillWaitingToUpload = setOf("n")
        )

        assertEquals(1000, merged.getValue("n").updatedAt)
    }

    @Test
    fun aNewNoteThatHasNotBeenUploadedYetIsLeftOut() {
        val merged = merge(
            serverEntries = emptyList(),
            localEntries = listOf(note("new-note", updatedAt = 1000)),
            entryIdsStillWaitingToUpload = setOf("new-note")
        )

        assertTrue(merged.isEmpty())
    }

    @Test
    fun aDeletedNoteIsLeftOutEvenIfThisDeviceStillHasACopy() {
        val merged = merge(
            serverEntries = listOf(note("d", updatedAt = 1000).copy(isDeleted = true)),
            localEntries = listOf(note("d", updatedAt = 900)),
            tombstoneIds = setOf("d")
        )

        assertTrue(merged.isEmpty())
    }

    @Test
    fun mediaAndChatEntriesAreLeftForTheirOwnCode() {
        val merged = merge(
            serverEntries = listOf(
                SelfHostManifestEntry(entryId = "photo.jpg", entryType = SelfHostEntryType.MEDIA, updatedAt = 100),
                SelfHostManifestEntry(entryId = "chat-1", entryType = SelfHostEntryType.CHAT_SESSION, updatedAt = 100)
            ),
            localEntries = emptyList()
        )

        assertTrue(merged.isEmpty())
    }

    @Test
    fun aNoteOnlyOnTheServerKeepsItsListOfFiles() {
        val merged = merge(
            serverEntries = listOf(note("receipt", 100).copy(mediaFileNames = setOf("receipt.jpg"))),
            localEntries = emptyList()
        )

        assertEquals(setOf("receipt.jpg"), merged.getValue("receipt").mediaFileNames)
    }

    @Test
    fun aNoteThisDeviceCouldNotUploadKeepsTheServerListOfFiles() {
        val merged = merge(
            serverEntries = listOf(note("receipt", 1000).copy(mediaFileNames = setOf("receipt.jpg"))),
            localEntries = listOf(note("receipt", 1005)),
            entryIdsStillWaitingToUpload = setOf("receipt")
        )

        assertEquals(setOf("receipt.jpg"), merged.getValue("receipt").mediaFileNames)
    }

    @Test
    fun aDailyNoteIsKeptTheSameWayAsANormalNote() {
        val dailyEntry = SelfHostManifestEntry(
            entryId = "daily_space-1_2026-10-07",
            entryType = SelfHostEntryType.DAILY,
            spaceId = "space-1",
            updatedAt = 100,
            dateString = "2026-10-07"
        )

        val merged = merge(serverEntries = listOf(dailyEntry), localEntries = emptyList())

        assertEquals(dailyEntry, merged.getValue("daily_space-1_2026-10-07"))
    }
}
