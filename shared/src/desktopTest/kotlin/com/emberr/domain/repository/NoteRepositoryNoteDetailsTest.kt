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
import com.emberr.domain.model.NoteContent
import com.emberr.domain.space.ActiveSpaceStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteRepositoryNoteDetailsTest {

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


    private fun note(
        title: String = "Groceries",
        titleUpdatedAt: Long = 0L,
        folderId: String? = "home",
        folderUpdatedAt: Long = 0L,
        icon: String? = null,
        iconUpdatedAt: Long = 0L,
        isFavorite: Boolean = false,
        favoriteUpdatedAt: Long = 0L,
        coverImagePath: String? = null,
        coverImageUpdatedAt: Long = 0L,
        showWordCount: Boolean = false,
        wordCountUpdatedAt: Long = 0L
    ) = NoteMetadataEntity(
        noteId = "note-1",
        title = title,
        folderId = folderId,
        isDaily = false,
        dateString = null,
        createdAt = 1L,
        updatedAt = 1L,
        filePath = "",
        icon = icon,
        isFavorite = isFavorite,
        titleUpdatedAt = titleUpdatedAt,
        folderUpdatedAt = folderUpdatedAt,
        iconUpdatedAt = iconUpdatedAt,
        favoriteUpdatedAt = favoriteUpdatedAt,
        coverImagePath = coverImagePath,
        coverImageUpdatedAt = coverImageUpdatedAt,
        showWordCount = showWordCount,
        wordCountUpdatedAt = wordCountUpdatedAt
    )

    private suspend fun save(metadata: NoteMetadataEntity) {
        repository.saveNote(metadata, NoteContent(blocks = emptyList()))
    }

    @Test
    fun anOpenEditorWithTheOldTitleCannotUndoARename() = runTest {
        val loadedByTheEditor = note()
        save(loadedByTheEditor)
        save(note(title = "Grocery list", titleUpdatedAt = 100L))

        save(loadedByTheEditor)

        assertEquals("Grocery list", repository.getNoteById("note-1")?.title)
    }

    @Test
    fun anOpenEditorWithTheOldFolderCannotUndoAMove() = runTest {
        val loadedByTheEditor = note()
        save(loadedByTheEditor)
        save(note(folderId = "errands", folderUpdatedAt = 100L))

        save(loadedByTheEditor)

        assertEquals("errands", repository.getNoteById("note-1")?.folderId)
    }

    @Test
    fun aNewRenameReplacesAnOlderOne() = runTest {
        save(note(title = "Grocery list", titleUpdatedAt = 100L))

        save(note(title = "Shopping", titleUpdatedAt = 200L))

        assertEquals("Shopping", repository.getNoteById("note-1")?.title)
    }

    @Test
    fun anOpenEditorWithTheOldIconAndFavoriteCannotUndoNewerChanges() = runTest {
        val loadedByTheEditor = note()
        save(loadedByTheEditor)
        save(note(icon = "🛒", iconUpdatedAt = 100L, isFavorite = true, favoriteUpdatedAt = 100L))

        save(loadedByTheEditor)

        val saved = repository.getNoteById("note-1")
        assertEquals("🛒", saved?.icon)
        assertEquals(true, saved?.isFavorite)
    }

    @Test
    fun favoritingFromTheListRecordsWhenItHappened() = runTest {
        save(note())

        repository.addNoteToFavorites("note-1")

        assertTrue((repository.getNoteById("note-1")?.favoriteUpdatedAt ?: 0L) > 0L)
    }

    @Test
    fun renamingADatabaseRowRecordsWhenItHappenedAndSurvivesAnOlderSave() = runTest {
        val loadedBeforeTheRename = note()
        save(loadedBeforeTheRename)

        repository.renameDatabaseRow("note-1", "Gym")
        save(loadedBeforeTheRename)

        val saved = repository.getNoteById("note-1")
        assertEquals("Gym", saved?.title)
        assertTrue((saved?.titleUpdatedAt ?: 0L) > 0L)
    }

    @Test
    fun anOpenEditorWithTheOldCoverAndWordCountCannotUndoNewerChanges() = runTest {
        val loadedByTheEditor = note()
        save(loadedByTheEditor)
        save(note(coverImagePath = "beach.jpg", coverImageUpdatedAt = 100L, showWordCount = true, wordCountUpdatedAt = 100L))

        save(loadedByTheEditor)

        val saved = repository.getNoteById("note-1")
        assertEquals("beach.jpg", saved?.coverImagePath)
        assertEquals(true, saved?.showWordCount)
    }
}
