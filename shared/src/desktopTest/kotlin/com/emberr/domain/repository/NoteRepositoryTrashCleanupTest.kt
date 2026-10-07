package com.emberr.domain.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.AppDatabase
import com.emberr.data.local.room.entity.DEFAULT_SPACE_ID
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.database.EmberrDatabase
import com.emberr.domain.ai.LocalAiEngine
import com.emberr.domain.ai.NoteIndexer
import com.emberr.domain.ai.external.AiSettingsRepository
import com.emberr.domain.space.ActiveSpaceStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteRepositoryTrashCleanupTest {

    private val appDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val aiIndexDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EmberrDatabase.Schema.create(it) }

    private val settings: SettingsManager = stubOf { methodName ->
        when (methodName) {
            "getActiveSpaceId" -> DEFAULT_SPACE_ID
            "getActiveSpaceIdFlow" -> flowOf(DEFAULT_SPACE_ID)
            "isAiFeaturesDisabled" -> true
            else -> error("These tests do not expect SettingsManager.$methodName to be called")
        }
    }
    private val aiSettingsThatAreNeverRead: AiSettingsRepository = stubOf { methodName ->
        error("These tests do not expect AiSettingsRepository.$methodName to be called")
    }

    private val repository = NoteRepositoryImpl(
        activeSpaceStore = ActiveSpaceStore(settings),
        noteDao = appDatabase.noteDao(),
        folderDao = appDatabase.folderDao(),
        blockDao = appDatabase.blockDao(),
        noteIndexer = NoteIndexer(EmberrDatabase(aiIndexDriver), LocalAiEngine(aiSettingsThatAreNeverRead), settings),
        calendarTaskDao = appDatabase.calendarTaskDao(),
        calendarEventExceptionDao = appDatabase.calendarEventExceptionDao(),
        imageBlockDao = appDatabase.imageBlockDao(),
        documentBlockDao = appDatabase.documentBlockDao(),
        bookmarkBlockDao = appDatabase.bookmarkBlockDao(),
        categoryDao = appDatabase.categoryDao(),
        propertyTagDao = appDatabase.propertyTagDao(),
        customPropertyDao = appDatabase.customPropertyDao(),
        selfHostDeletedNoteDao = appDatabase.selfHostDeletedNoteDao(),
        mediaReferenceDao = appDatabase.mediaReferenceDao(),
        canvasDao = appDatabase.canvasDao()
    )

    @AfterTest
    fun closeDatabases() {
        appDatabase.close()
        aiIndexDriver.close()
    }

    private inline fun <reified T : Any> stubOf(crossinline answer: (methodName: String) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { stub, method, arguments ->
            when (method.name) {
                "toString" -> "stub of ${T::class.simpleName}"
                "hashCode" -> System.identityHashCode(stub)
                "equals" -> stub === arguments?.firstOrNull()
                else -> answer(method.name)
            }
        } as T

    private suspend fun insertTrashedNote(trashedAt: Long, updatedAt: Long) {
        appDatabase.noteDao().insertOrUpdateMetadata(
            NoteMetadataEntity(
                noteId = "note-1",
                title = "Groceries",
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = 1L,
                updatedAt = updatedAt,
                filePath = "",
                trashedAt = trashedAt
            )
        )
    }

    @Test
    fun emptyingOldTrashRecordsTheDeletionAtTheNotesLastChangeSoALaterRestoreElsewhereWins() = runTest {
        insertTrashedNote(trashedAt = 1_000L, updatedAt = 2_000L)

        repository.cleanupOldTrashedNotes()

        assertNull(repository.getNoteById("note-1"))
        assertEquals(2_000L, appDatabase.selfHostDeletedNoteDao().getTombstoneByNoteId("note-1")?.deletedAt)
    }

    @Test
    fun emptyingOldTrashUsesTheTrashTimeWhenItIsNewerThanTheLastChange() = runTest {
        insertTrashedNote(trashedAt = 3_000L, updatedAt = 2_000L)

        repository.cleanupOldTrashedNotes()

        assertEquals(3_000L, appDatabase.selfHostDeletedNoteDao().getTombstoneByNoteId("note-1")?.deletedAt)
    }

    @Test
    fun deletingForeverByHandRecordsTheDeletionAtTheCurrentTime() = runTest {
        insertTrashedNote(trashedAt = 1_000L, updatedAt = 2_000L)
        val timeBeforeDeleting = System.currentTimeMillis()

        repository.deleteNote("note-1", filePath = "")

        val deletedAt = appDatabase.selfHostDeletedNoteDao().getTombstoneByNoteId("note-1")?.deletedAt ?: 0L
        assertTrue(deletedAt >= timeBeforeDeleting)
    }
}
