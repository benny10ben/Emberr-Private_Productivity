package com.emberr.data.local.room

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.emberr.data.local.room.entity.UnappliedSyncChangeEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnappliedSyncChangeDaoTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val unappliedSyncChangeDao = database.unappliedSyncChangeDao()

    private val maxFailedAttempts = 20

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private fun failedChange(
        entityId: String,
        appVersion: String,
        envelopeJson: String = "{}",
        waitsForAppUpdate: Boolean = true,
        failedAttempts: Int = 0
    ) =
        UnappliedSyncChangeEntity(
            entityType = "NOTE",
            entityId = entityId,
            envelopeJson = envelopeJson,
            failedOnAppVersion = appVersion,
            failedAt = 1L,
            waitsForAppUpdate = waitsForAppUpdate,
            failedAttempts = failedAttempts
        )

    @Test
    fun aChangeThatFailedOnThisVersionIsNotRetriedYet() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0"))

        assertTrue(unappliedSyncChangeDao.getChangesReadyToRetry("1.0", maxFailedAttempts).isEmpty())
    }

    @Test
    fun aChangeThatFailedOnAnOlderVersionIsRetriedAfterAnUpdate() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0"))

        val toRetry = unappliedSyncChangeDao.getChangesReadyToRetry("1.1", maxFailedAttempts)

        assertEquals(listOf("note-1"), toRetry.map { it.entityId })
    }

    @Test
    fun aNewerFailureForTheSameItemReplacesTheOlderOne() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0", envelopeJson = "older"))
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0", envelopeJson = "newer"))

        val toRetry = unappliedSyncChangeDao.getChangesReadyToRetry("1.1", maxFailedAttempts)

        assertEquals(listOf("newer"), toRetry.map { it.envelopeJson })
    }

    @Test
    fun aChangeThatWorkedOnRetryIsForgotten() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0"))

        unappliedSyncChangeDao.deleteChange(entityType = "NOTE", entityId = "note-1")

        assertTrue(unappliedSyncChangeDao.getChangesReadyToRetry("1.1", maxFailedAttempts).isEmpty())
    }

    @Test
    fun aChangeThatFailedForAnotherReasonIsRetriedOnTheNextSyncWithoutAnUpdate() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0", waitsForAppUpdate = false))

        val toRetry = unappliedSyncChangeDao.getChangesReadyToRetry("1.0", maxFailedAttempts)

        assertEquals(listOf("note-1"), toRetry.map { it.entityId })
    }

    @Test
    fun onlyChangesRetriedOnTheNextSyncAreCountedAsWaiting() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0", waitsForAppUpdate = true))
        unappliedSyncChangeDao.saveChange(failedChange("note-2", appVersion = "1.0", waitsForAppUpdate = false))
        unappliedSyncChangeDao.saveChange(failedChange("note-3", appVersion = "1.0", waitsForAppUpdate = false))

        assertEquals(2, unappliedSyncChangeDao.countChangesToRetryOnNextSync())
    }

    @Test
    fun aChangeThatFailsForAnotherReasonAfterWaitingForAnUpdateStopsWaitingForAnUpdate() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0", waitsForAppUpdate = true))
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.1", waitsForAppUpdate = false))

        assertEquals(1, unappliedSyncChangeDao.countChangesToRetryOnNextSync())
        assertEquals(listOf("note-1"), unappliedSyncChangeDao.getChangesReadyToRetry("1.1", maxFailedAttempts).map { it.entityId })
    }

    @Test
    fun aChangeThatFailedTooManyTimesIsNotRetriedOnThisVersion() = runTest {
        unappliedSyncChangeDao.saveChange(
            failedChange("note-1", appVersion = "1.0", waitsForAppUpdate = false, failedAttempts = maxFailedAttempts)
        )

        assertTrue(unappliedSyncChangeDao.getChangesReadyToRetry("1.0", maxFailedAttempts).isEmpty())
    }

    @Test
    fun aChangeThatFailedTooManyTimesIsRetriedAgainAfterAnUpdate() = runTest {
        unappliedSyncChangeDao.saveChange(
            failedChange("note-1", appVersion = "1.0", waitsForAppUpdate = false, failedAttempts = maxFailedAttempts)
        )

        val toRetry = unappliedSyncChangeDao.getChangesReadyToRetry("1.1", maxFailedAttempts)

        assertEquals(listOf("note-1"), toRetry.map { it.entityId })
    }

    @Test
    fun aChangeThatFailedTooManyTimesIsStillCountedAsNotSaved() = runTest {
        unappliedSyncChangeDao.saveChange(
            failedChange("note-1", appVersion = "1.0", waitsForAppUpdate = false, failedAttempts = maxFailedAttempts)
        )

        assertEquals(1, unappliedSyncChangeDao.countChangesToRetryOnNextSync())
    }

    @Test
    fun aChangeWithFailuresLeftIsStillRetried() = runTest {
        unappliedSyncChangeDao.saveChange(
            failedChange("note-1", appVersion = "1.0", waitsForAppUpdate = false, failedAttempts = maxFailedAttempts - 1)
        )

        val toRetry = unappliedSyncChangeDao.getChangesReadyToRetry("1.0", maxFailedAttempts)

        assertEquals(listOf("note-1"), toRetry.map { it.entityId })
    }
}
