package com.emberr.data.local.room

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.emberr.data.local.room.entity.NoteMetadataEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostSyncProgressDaoTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val noteDao = database.noteDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private fun noteAlreadySentToServer(noteId: String) =
        NoteMetadataEntity(
            noteId = noteId,
            title = noteId,
            folderId = null,
            isDaily = false,
            dateString = null,
            createdAt = 1L,
            updatedAt = 100L,
            filePath = "",
            selfHostSyncedAt = 100L
        )

    @Test
    fun notesAlreadySentToTheServerDoNotNeedSyncing() = runTest {
        noteDao.insertOrUpdateMetadata(noteAlreadySentToServer("note-1"))

        assertTrue(noteDao.getNotesNeedingSelfHostSync().isEmpty())
    }

    @Test
    fun forgettingSelfHostProgressMakesEveryNoteNeedSyncingAgain() = runTest {
        noteDao.insertOrUpdateMetadata(noteAlreadySentToServer("note-1"))
        noteDao.insertOrUpdateMetadata(noteAlreadySentToServer("note-2"))

        noteDao.forgetSelfHostSyncProgressForAllNotes()

        assertEquals(setOf("note-1", "note-2"), noteDao.getNotesNeedingSelfHostSync().map { it.noteId }.toSet())
    }
}
