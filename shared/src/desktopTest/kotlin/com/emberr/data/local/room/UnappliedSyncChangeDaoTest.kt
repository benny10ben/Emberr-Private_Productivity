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

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private fun failedChange(entityId: String, appVersion: String, envelopeJson: String = "{}") =
        UnappliedSyncChangeEntity(
            entityType = "NOTE",
            entityId = entityId,
            envelopeJson = envelopeJson,
            failedOnAppVersion = appVersion,
            failedAt = 1L
        )

    @Test
    fun aChangeThatFailedOnThisVersionIsNotRetriedYet() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0"))

        assertTrue(unappliedSyncChangeDao.getChangesThatFailedOnAnotherAppVersion("1.0").isEmpty())
    }

    @Test
    fun aChangeThatFailedOnAnOlderVersionIsRetriedAfterAnUpdate() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0"))

        val toRetry = unappliedSyncChangeDao.getChangesThatFailedOnAnotherAppVersion("1.1")

        assertEquals(listOf("note-1"), toRetry.map { it.entityId })
    }

    @Test
    fun aNewerFailureForTheSameItemReplacesTheOlderOne() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0", envelopeJson = "older"))
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0", envelopeJson = "newer"))

        val toRetry = unappliedSyncChangeDao.getChangesThatFailedOnAnotherAppVersion("1.1")

        assertEquals(listOf("newer"), toRetry.map { it.envelopeJson })
    }

    @Test
    fun aChangeThatWorkedOnRetryIsForgotten() = runTest {
        unappliedSyncChangeDao.saveChange(failedChange("note-1", appVersion = "1.0"))

        unappliedSyncChangeDao.deleteChange(entityType = "NOTE", entityId = "note-1")

        assertTrue(unappliedSyncChangeDao.getChangesThatFailedOnAnotherAppVersion("1.1").isEmpty())
    }
}
