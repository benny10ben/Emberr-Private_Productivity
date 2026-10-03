package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseRowCleanupTest {

    private val now = 100L * 24 * 60 * 60 * 1000
    private val longAgo = now - DatabaseRowCleanup.DELETED_BLOCK_GRACE_PERIOD_MILLIS
    private val oneDayAgo = now - 1L * 24 * 60 * 60 * 1000

    private fun databaseBlock(id: String, databaseId: String, isDeleted: Boolean, updatedAt: Long) =
        DatabaseBlock(id = id, databaseId = databaseId, isDeleted = isDeleted, updatedAt = updatedAt)

    @Test
    fun rowsOfADatabaseWhoseOnlyBlockWasDeletedLongAgoAreDeletedAtLaunch() {
        val blocks = listOf(databaseBlock("b1", "books", isDeleted = true, updatedAt = longAgo))

        assertEquals(setOf("books"), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
    }

    @Test
    fun rowsOfADatabaseDeletedRecentlyAreKeptBecauseUndoOrAnotherDeviceMayBringItBack() {
        val blocks = listOf(databaseBlock("b1", "books", isDeleted = true, updatedAt = oneDayAgo))

        assertEquals(emptySet(), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
    }

    @Test
    fun rowsOfADatabaseDeletedJustUnderTheGracePeriodAgoAreStillKept() {
        val blocks = listOf(databaseBlock("b1", "books", isDeleted = true, updatedAt = longAgo + 1))

        assertEquals(emptySet(), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
    }

    @Test
    fun rowsAreKeptWhileAnyBlockStillShowsTheDatabase() {
        val blocks = listOf(
            databaseBlock("b1", "books", isDeleted = true, updatedAt = longAgo),
            databaseBlock("b2", "books", isDeleted = false, updatedAt = longAgo)
        )

        assertEquals(emptySet(), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
    }

    @Test
    fun rowsNoBlockMentionsAreKeptBecauseTheirNoteMayNotHaveSyncedYet() {
        assertEquals(emptySet(), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), emptyList(), now))
    }

    @Test
    fun aViewAddedByLinkingKeepsTheRowsAfterTheFirstViewWasDeletedLongAgo() {
        val blocks = listOf(
            databaseBlock("b1", "books", isDeleted = true, updatedAt = longAgo),
            databaseBlock("b2", "books", isDeleted = false, updatedAt = oneDayAgo).copy(isLinkedDatabase = true)
        )

        assertEquals(emptySet(), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
        assertEquals(emptySet(), DatabaseRowCleanup.databasesNoLiveBlockUses(setOf("books"), blocks))
    }

    @Test
    fun theHiddenSettingsOfADatabaseDoNotCountAsShowingIt() {
        val settings = databaseBlock(databaseSettingsBlockId("books"), "books", isDeleted = false, updatedAt = oneDayAgo)
        val blocks = listOf(settings, databaseBlock("b1", "books", isDeleted = true, updatedAt = longAgo))

        assertEquals(setOf("books"), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
        assertEquals(setOf("books"), DatabaseRowCleanup.databasesNoLiveBlockUses(setOf("books"), listOf(settings)))
    }

    @Test
    fun aDeletedViewStillCountsAsMentioningItsDatabaseButTheHiddenSettingsDoNot() {
        val blocks = listOf(
            databaseBlock("b1", "books", isDeleted = false, updatedAt = oneDayAgo),
            databaseBlock("b2", "films", isDeleted = true, updatedAt = oneDayAgo),
            databaseBlock(databaseSettingsBlockId("music"), "music", isDeleted = false, updatedAt = oneDayAgo)
        )

        assertEquals(setOf("music"), DatabaseRowCleanup.databasesNoBlockMentions(setOf("books", "films", "music"), blocks))
    }

    @Test
    fun anUnmentionedDatabaseUntouchedForTheGracePeriodIsOldEnoughToDeleteWhenEmpty() {
        assertTrue(DatabaseRowCleanup.isUntouchedLongEnoughToDeleteWhenEmpty(lastEditedAt = longAgo, now = now))
    }

    @Test
    fun anUnmentionedDatabaseEditedJustUnderTheGracePeriodAgoIsNotOldEnoughToDelete() {
        assertFalse(DatabaseRowCleanup.isUntouchedLongEnoughToDeleteWhenEmpty(lastEditedAt = longAgo + 1, now = now))
    }

    @Test
    fun onlyDatabasesWithoutAnyLiveBlockAreReportedAsUnused() {
        val blocks = listOf(
            databaseBlock("b1", "books", isDeleted = false, updatedAt = oneDayAgo),
            databaseBlock("b2", "films", isDeleted = true, updatedAt = oneDayAgo)
        )

        assertEquals(
            setOf("films", "music"),
            DatabaseRowCleanup.databasesNoLiveBlockUses(setOf("books", "films", "music"), blocks)
        )
    }
}
