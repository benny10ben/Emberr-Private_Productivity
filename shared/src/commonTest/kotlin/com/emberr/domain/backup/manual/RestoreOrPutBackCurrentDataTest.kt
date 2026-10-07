package com.emberr.domain.backup.manual

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestoreOrPutBackCurrentDataTest {

    @Test
    fun aSuccessfulRestoreDoesNotPutTheCurrentDataBack() = runTest {
        var currentDataWasPutBack = false

        restoreOrPutBackCurrentData(
            restoreBackup = {},
            putBackCurrentData = { currentDataWasPutBack = true }
        )

        assertFalse(currentDataWasPutBack)
    }

    @Test
    fun aFailedRestorePutsTheCurrentDataBackAndSaysNothingChanged() = runTest {
        var currentDataWasPutBack = false

        val error = assertFailsWith<IllegalStateException> {
            restoreOrPutBackCurrentData(
                restoreBackup = { error("reminders failed") },
                putBackCurrentData = { currentDataWasPutBack = true }
            )
        }

        assertTrue(currentDataWasPutBack)
        assertEquals("Your data was not changed. Reason: reminders failed", error.message)
    }

    @Test
    fun aFailedPutBackSaysTheCurrentDataCouldNotBePutBack() = runTest {
        val error = assertFailsWith<IllegalStateException> {
            restoreOrPutBackCurrentData(
                restoreBackup = { error("reminders failed") },
                putBackCurrentData = { error("database locked") }
            )
        }

        assertEquals("Your previous data could not be put back. Reason: database locked", error.message)
    }

    @Test
    fun aCancelledRestoreStillPutsTheCurrentDataBackFully() = runTest {
        var currentDataWasPutBack = false
        val restore = launch {
            restoreOrPutBackCurrentData(
                restoreBackup = { awaitCancellation() },
                putBackCurrentData = {
                    delay(1_000)
                    currentDataWasPutBack = true
                }
            )
        }
        runCurrent()

        restore.cancelAndJoin()

        assertTrue(currentDataWasPutBack)
        assertTrue(restore.isCancelled)
    }
}
