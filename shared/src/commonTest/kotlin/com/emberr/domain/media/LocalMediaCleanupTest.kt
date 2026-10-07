package com.emberr.domain.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalMediaCleanupTest {

    private val now = 100L * 24 * 60 * 60 * 1000
    private val oneHour = 60L * 60 * 1000
    private val oneDay = 24 * oneHour

    @Test
    fun aFileANoteUsesIsNeverMarkedOrDeleted() {
        val plan = planLocalMediaCleanup(
            fileNamesOnDisk = setOf("photo.jpg"),
            usedFileNames = setOf("photo.jpg"),
            unusedSinceByFileName = emptyMap(),
            nowMs = now
        )

        assertTrue(plan.newlyUnusedFileNames.isEmpty())
        assertTrue(plan.fileNamesToDelete.isEmpty())
    }

    @Test
    fun aFileThatJustStoppedBeingUsedIsMarkedButKept() {
        val plan = planLocalMediaCleanup(
            fileNamesOnDisk = setOf("old-photo.jpg"),
            usedFileNames = emptySet(),
            unusedSinceByFileName = emptyMap(),
            nowMs = now
        )

        assertEquals(setOf("old-photo.jpg"), plan.newlyUnusedFileNames)
        assertTrue(plan.fileNamesToDelete.isEmpty())
    }

    @Test
    fun aFileUnusedForSixDaysIsKept() {
        val plan = planLocalMediaCleanup(
            fileNamesOnDisk = setOf("photo.jpg"),
            usedFileNames = emptySet(),
            unusedSinceByFileName = mapOf("photo.jpg" to now - 6 * oneDay),
            nowMs = now
        )

        assertTrue(plan.fileNamesToDelete.isEmpty())
        assertTrue(plan.newlyUnusedFileNames.isEmpty())
    }

    @Test
    fun aFileUnusedForMoreThanSevenDaysIsDeleted() {
        val plan = planLocalMediaCleanup(
            fileNamesOnDisk = setOf("photo.jpg"),
            usedFileNames = emptySet(),
            unusedSinceByFileName = mapOf("photo.jpg" to now - 7 * oneDay - oneHour),
            nowMs = now
        )

        assertEquals(setOf("photo.jpg"), plan.fileNamesToDelete)
    }

    @Test
    fun aFileANoteUsesAgainLosesItsMarkAndIsKept() {
        val plan = planLocalMediaCleanup(
            fileNamesOnDisk = setOf("trip.mp4"),
            usedFileNames = setOf("trip.mp4"),
            unusedSinceByFileName = mapOf("trip.mp4" to now - 10 * oneDay),
            nowMs = now
        )

        assertEquals(setOf("trip.mp4"), plan.fileNamesToForget)
        assertTrue(plan.fileNamesToDelete.isEmpty())
    }

    @Test
    fun aMarkForAFileNoLongerOnDiskIsForgotten() {
        val plan = planLocalMediaCleanup(
            fileNamesOnDisk = emptySet(),
            usedFileNames = emptySet(),
            unusedSinceByFileName = mapOf("gone.jpg" to now - oneDay),
            nowMs = now
        )

        assertEquals(setOf("gone.jpg"), plan.fileNamesToForget)
    }

    @Test
    fun cleanupWaitsAfterARestoreWhileLanSyncHasNotCaughtUp() {
        assertTrue(
            isMediaCleanupWaitingForSync(
                waitingForLanSync = true,
                isLanPaired = true,
                waitingForSelfHostSync = false,
                isSelfHostConnected = false
            )
        )
    }

    @Test
    fun cleanupWaitsAfterARestoreWhileSelfHostSyncHasNotCaughtUp() {
        assertTrue(
            isMediaCleanupWaitingForSync(
                waitingForLanSync = false,
                isLanPaired = true,
                waitingForSelfHostSync = true,
                isSelfHostConnected = true
            )
        )
    }

    @Test
    fun cleanupRunsOnceEverySyncHasCaughtUp() {
        assertFalse(
            isMediaCleanupWaitingForSync(
                waitingForLanSync = false,
                isLanPaired = true,
                waitingForSelfHostSync = false,
                isSelfHostConnected = true
            )
        )
    }

    @Test
    fun cleanupStopsWaitingForASyncThatWasTurnedOff() {
        assertFalse(
            isMediaCleanupWaitingForSync(
                waitingForLanSync = true,
                isLanPaired = false,
                waitingForSelfHostSync = true,
                isSelfHostConnected = false
            )
        )
    }
}
