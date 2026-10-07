package com.emberr.domain.backup.manual

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.emberr.data.local.room.AppDatabase
import com.emberr.data.local.room.entity.ImageBlockEntity
import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TaskSource
import com.emberr.data.local.room.entity.UnappliedSyncChangeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupRestoreTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val noteDao = database.noteDao()
    private val blockDao = database.blockDao()
    private val imageBlockDao = database.imageBlockDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private fun note(noteId: String, selfHostSyncedAt: Long = 0L) = NoteMetadataEntity(
        noteId = noteId,
        title = noteId,
        folderId = null,
        isDaily = false,
        dateString = null,
        createdAt = 1L,
        updatedAt = 1L,
        filePath = "",
        selfHostSyncedAt = selfHostSyncedAt
    )

    private fun block(blockId: String, noteId: String) = NoteBlockEntity(
        blockId = blockId,
        noteId = noteId,
        displayOrder = 0,
        blockDataJson = """{"type":"text","id":"$blockId","text":"hello"}""",
        updatedAt = 1L,
        isDeleted = false
    )

    private fun backupWith(vararg notes: NoteMetadataEntity, blocks: List<NoteBlockEntity> = emptyList()) =
        EmberrBackupData(exportTimestamp = 1L, notes = notes.toList(), blocks = blocks)

    @Test
    fun notesThatAreNotInTheBackupAreRemoved() = runTest {
        noteDao.insertOrUpdateMetadata(note("created-after-the-backup"))

        database.replaceAllTablesWith(backupWith(note("in-the-backup"))) {}

        assertNull(noteDao.getNoteById("created-after-the-backup"))
    }

    @Test
    fun everythingInTheBackupComesBack() = runTest {
        database.replaceAllTablesWith(
            backupWith(note("in-the-backup"), blocks = listOf(block("block-1", "in-the-backup")))
        ) {}

        assertNotNull(noteDao.getNoteById("in-the-backup"))
        assertEquals(listOf("block-1"), blockDao.getAllBlocksForNoteIncludingDeleted("in-the-backup").map { it.blockId })
    }

    @Test
    fun restoredNotesAreComparedWithTheSelfHostServerAgain() = runTest {
        database.replaceAllTablesWith(backupWith(note("in-the-backup", selfHostSyncedAt = 500L))) {}

        assertEquals(0L, noteDao.getNoteById("in-the-backup")?.selfHostSyncedAt)
    }

    @Test
    fun derivedListsAreEmptiedSoTheyCanBeRebuiltFromTheBackup() = runTest {
        imageBlockDao.upsertImages(
            listOf(ImageBlockEntity("old-image", "old-note", "old.png", noteCreatedAt = 1L, sourceType = TaskSource.NOTE))
        )

        database.replaceAllTablesWith(backupWith(note("in-the-backup"))) {}

        assertTrue(imageBlockDao.getAllImagesAcrossSpacesFlow().first().isEmpty())
    }

    @Test
    fun syncChangesWaitingToBeRetriedAreForgotten() = runTest {
        database.unappliedSyncChangeDao().saveChange(
            UnappliedSyncChangeEntity(
                entityType = "NOTE",
                entityId = "changed-before-the-restore",
                envelopeJson = "{}",
                failedOnAppVersion = "1.0",
                failedAt = 1L,
                waitsForAppUpdate = false,
                failedAttempts = 1
            )
        )

        database.replaceAllTablesWith(backupWith(note("in-the-backup"))) {}

        assertEquals(0, database.unappliedSyncChangeDao().countChangesToRetryOnNextSync())
    }

    @Test
    fun aRestoreThatFailsHalfwayLeavesTheOldDataUntouched() = runTest {
        noteDao.insertOrUpdateMetadata(note("current-note"))

        assertFailsWith<IllegalStateException> {
            database.replaceAllTablesWith(backupWith(note("in-the-backup"))) { error("rebuilding failed") }
        }

        assertNotNull(noteDao.getNoteById("current-note"))
        assertNull(noteDao.getNoteById("in-the-backup"))
    }
}
