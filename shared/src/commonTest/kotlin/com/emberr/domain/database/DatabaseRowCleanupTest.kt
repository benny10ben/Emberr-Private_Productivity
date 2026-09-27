package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseRowCleanupTest {

    private val now = 100L * 24 * 60 * 60 * 1000
    private val eightDaysAgo = now - 8L * 24 * 60 * 60 * 1000
    private val oneDayAgo = now - 1L * 24 * 60 * 60 * 1000

    private fun databaseBlock(id: String, databaseId: String, isDeleted: Boolean, updatedAt: Long) =
        DatabaseBlock(id = id, databaseId = databaseId, isDeleted = isDeleted, updatedAt = updatedAt)

    @Test
    fun rowsOfADatabaseWhoseOnlyBlockWasDeletedLongAgoAreDeletedAtLaunch() {
        val blocks = listOf(databaseBlock("b1", "books", isDeleted = true, updatedAt = eightDaysAgo))

        assertEquals(setOf("books"), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
    }

    @Test
    fun rowsOfADatabaseDeletedRecentlyAreKeptBecauseUndoOrAnotherDeviceMayBringItBack() {
        val blocks = listOf(databaseBlock("b1", "books", isDeleted = true, updatedAt = oneDayAgo))

        assertEquals(emptySet(), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
    }

    @Test
    fun rowsAreKeptWhileAnyBlockStillShowsTheDatabase() {
        val blocks = listOf(
            databaseBlock("b1", "books", isDeleted = true, updatedAt = eightDaysAgo),
            databaseBlock("b2", "books", isDeleted = false, updatedAt = eightDaysAgo)
        )

        assertEquals(emptySet(), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), blocks, now))
    }

    @Test
    fun rowsNoBlockMentionsAreKeptBecauseTheirNoteMayNotHaveSyncedYet() {
        assertEquals(emptySet(), DatabaseRowCleanup.databasesToDeleteAtLaunch(setOf("books"), emptyList(), now))
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
