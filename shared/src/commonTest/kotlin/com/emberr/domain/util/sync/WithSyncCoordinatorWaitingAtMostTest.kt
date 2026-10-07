package com.emberr.domain.util.sync

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WithSyncCoordinatorWaitingAtMostTest {

    @Test
    fun aFreeLockIsTakenRightAway() = runTest {
        val result = withSyncCoordinatorWaitingAtMost(10.seconds) { "synced" }

        assertEquals("synced", result)
        assertFalse(SyncCoordinator.mutex.isLocked)
    }

    @Test
    fun syncWaitsForAShortSaveInsteadOfSkipping() = runTest {
        val save = launch { SyncCoordinator.mutex.withLock { delay(50.milliseconds) } }
        runCurrent()

        val result = withSyncCoordinatorWaitingAtMost(10.seconds) { "synced" }

        assertEquals("synced", result)
        save.join()
        assertFalse(SyncCoordinator.mutex.isLocked)
    }

    @Test
    fun syncGivesUpWhenTheLockStaysBusyTooLong() = runTest {
        val stuckHolder = launch { SyncCoordinator.mutex.withLock { awaitCancellation() } }
        runCurrent()
        var workRan = false

        try {
            val result = withSyncCoordinatorWaitingAtMost(10.seconds) { workRan = true }

            assertNull(result)
            assertFalse(workRan)
        } finally {
            stuckHolder.cancelAndJoin()
        }
        assertFalse(SyncCoordinator.mutex.isLocked)
    }

    @Test
    fun aZeroWaitOnlyTriesOnceLikeTheOldSkip() = runTest {
        val save = launch { SyncCoordinator.mutex.withLock { delay(50.milliseconds) } }
        runCurrent()

        val result = withSyncCoordinatorWaitingAtMost(Duration.ZERO) { "synced" }

        assertNull(result)
        save.join()
    }

    @Test
    fun theLockIsReleasedEvenWhenTheWorkFails() = runTest {
        assertFailsWith<IllegalStateException> {
            withSyncCoordinatorWaitingAtMost(10.seconds) { error("download failed") }
        }

        assertFalse(SyncCoordinator.mutex.isLocked)
    }
}
