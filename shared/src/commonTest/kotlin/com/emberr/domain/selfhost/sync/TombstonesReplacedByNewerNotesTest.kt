package com.emberr.domain.selfhost.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TombstonesReplacedByNewerNotesTest {

    private fun note(entryId: String, updatedAt: Long) =
        SelfHostManifestEntry(entryId = entryId, entryType = SelfHostEntryType.NOTE, updatedAt = updatedAt)

    private fun tombstone(entryId: String, deletedAt: Long) = note(entryId, deletedAt).copy(isDeleted = true)

    private fun replacedIds(
        tombstones: List<SelfHostManifestEntry>,
        serverEntries: List<SelfHostManifestEntry> = emptyList(),
        localEntries: List<SelfHostManifestEntry> = emptyList(),
        entryIdsStillWaitingToUpload: Set<String> = emptySet()
    ): Set<String> =
        tombstoneIdsReplacedByNewerNotes(tombstones, serverEntries, localEntries, entryIdsStillWaitingToUpload)

    @Test
    fun aNoteEditedOnThisDeviceAfterItWasDeletedElsewhereReplacesTheTombstone() {
        val groceriesTombstone = tombstone("groceries", deletedAt = 1000)

        val replaced = replacedIds(
            tombstones = listOf(groceriesTombstone),
            serverEntries = listOf(groceriesTombstone),
            localEntries = listOf(note("groceries", updatedAt = 1005))
        )

        assertEquals(setOf("groceries"), replaced)
    }

    @Test
    fun aNoteEditedBeforeItWasDeletedKeepsTheTombstone() {
        val replaced = replacedIds(
            tombstones = listOf(tombstone("groceries", deletedAt = 1000)),
            localEntries = listOf(note("groceries", updatedAt = 995))
        )

        assertTrue(replaced.isEmpty())
    }

    @Test
    fun anEditAtTheSameMomentAsTheDeleteKeepsTheTombstone() {
        val replaced = replacedIds(
            tombstones = listOf(tombstone("groceries", deletedAt = 1000)),
            localEntries = listOf(note("groceries", updatedAt = 1000))
        )

        assertTrue(replaced.isEmpty())
    }

    @Test
    fun aNewerEditThatHasNotBeenUploadedYetKeepsTheTombstone() {
        val replaced = replacedIds(
            tombstones = listOf(tombstone("groceries", deletedAt = 1000)),
            localEntries = listOf(note("groceries", updatedAt = 1005)),
            entryIdsStillWaitingToUpload = setOf("groceries")
        )

        assertTrue(replaced.isEmpty())
    }

    @Test
    fun aNewerEditAnotherDeviceAlreadyUploadedReplacesThisDevicesOwnTombstone() {
        val replaced = replacedIds(
            tombstones = listOf(tombstone("groceries", deletedAt = 1000)),
            serverEntries = listOf(note("groceries", updatedAt = 1005)),
            localEntries = emptyList()
        )

        assertEquals(setOf("groceries"), replaced)
    }

    @Test
    fun anOlderServerCopyDoesNotReplaceThisDevicesOwnTombstone() {
        val replaced = replacedIds(
            tombstones = listOf(tombstone("groceries", deletedAt = 1000)),
            serverEntries = listOf(note("groceries", updatedAt = 900))
        )

        assertTrue(replaced.isEmpty())
    }

    @Test
    fun aTombstoneForANoteNoDeviceStillHasIsKept() {
        val replaced = replacedIds(
            tombstones = listOf(tombstone("groceries", deletedAt = 1000)),
            serverEntries = listOf(note("other-note", updatedAt = 2000)),
            localEntries = listOf(note("another-note", updatedAt = 2000))
        )

        assertTrue(replaced.isEmpty())
    }

    @Test
    fun aDailyNoteEditedAfterItWasDeletedReplacesTheTombstone() {
        val dailyEntryId = "daily_space-1_2026-10-07"
        val dailyNote = SelfHostManifestEntry(
            entryId = dailyEntryId,
            entryType = SelfHostEntryType.DAILY,
            spaceId = "space-1",
            updatedAt = 1005,
            dateString = "2026-10-07"
        )

        val replaced = replacedIds(
            tombstones = listOf(dailyNote.copy(updatedAt = 1000, isDeleted = true)),
            localEntries = listOf(dailyNote)
        )

        assertEquals(setOf(dailyEntryId), replaced)
    }

    @Test
    fun aReplacedTombstoneLetsTheNoteBackIntoTheManifest() {
        val groceriesTombstone = tombstone("groceries", deletedAt = 1000)
        val editedGroceries = note("groceries", updatedAt = 1005)
        val replaced = replacedIds(
            tombstones = listOf(groceriesTombstone),
            serverEntries = listOf(groceriesTombstone),
            localEntries = listOf(editedGroceries)
        )

        val merged = mergeNoteManifestEntries(
            serverEntries = listOf(groceriesTombstone),
            localEntries = listOf(editedGroceries),
            entryIdsStillWaitingToUpload = emptySet(),
            tombstoneIds = setOf("groceries") - replaced
        )

        assertEquals(listOf(editedGroceries), merged)
    }
}
