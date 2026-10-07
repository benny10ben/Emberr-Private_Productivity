package com.emberr.domain.sample

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
import com.emberr.domain.canvas.CanvasRepository
import com.emberr.domain.canvas.CanvasViewPositionStore
import com.emberr.domain.database.databaseNoteId
import com.emberr.domain.model.NoteContent
import com.emberr.domain.repository.FavoriteNoteOrderStore
import com.emberr.domain.repository.NoteRepositoryImpl
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.template.DefaultTemplateSeeder
import com.emberr.domain.template.PREDEFINED_TEMPLATES
import com.emberr.domain.template.ProjectsTemplate
import com.emberr.domain.template.noteIdInSpace
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterContentSeedingTest {

    private val appDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val aiIndexDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { EmberrDatabase.Schema.create(it) }

    private var activeSpaceId = DEFAULT_SPACE_ID
    private var sampleNotesSeeded = false
    private var favoriteNoteOrderJson = ""
    private var seededTemplateNoteIds = emptySet<String>()

    @Suppress("UNCHECKED_CAST")
    private val settings: SettingsManager = stubOf { methodName, arguments ->
        when (methodName) {
            "getActiveSpaceId" -> activeSpaceId
            "getActiveSpaceIdFlow" -> flowOf(activeSpaceId)
            "isAiFeaturesDisabled" -> true
            "isSampleNotesSeeded" -> sampleNotesSeeded
            "saveSampleNotesSeeded" -> { sampleNotesSeeded = arguments[0] as Boolean }
            "getFavoriteNoteOrderJson" -> favoriteNoteOrderJson
            "getFavoriteNoteOrderJsonFlow" -> flowOf(favoriteNoteOrderJson)
            "saveFavoriteNoteOrderJson" -> { favoriteNoteOrderJson = arguments[0] as String }
            "saveCanvasViewPositionJson" -> Unit
            "getSeededTemplateNoteIds" -> seededTemplateNoteIds
            "saveSeededTemplateNoteIds" -> { seededTemplateNoteIds = arguments[0] as Set<String> }
            else -> error("These tests do not expect SettingsManager.$methodName to be called")
        }
    }
    private val aiSettingsThatAreNeverRead: AiSettingsRepository = stubOf { methodName, _ ->
        error("These tests do not expect AiSettingsRepository.$methodName to be called")
    }

    private val activeSpaceStore = ActiveSpaceStore(settings)
    private val repository = NoteRepositoryImpl(
        activeSpaceStore = activeSpaceStore,
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
    private val canvasRepository = CanvasRepository(appDatabase.canvasDao(), appDatabase.noteDao())
    private val favoriteNoteOrderStore = FavoriteNoteOrderStore(settings, activeSpaceStore)

    private val sampleNotesSeeder = SampleNotesSeeder(
        repository = repository,
        settingsManager = settings,
        canvasRepository = canvasRepository,
        canvasViewPositionStore = CanvasViewPositionStore(settings),
        favoriteNoteOrderStore = favoriteNoteOrderStore
    )
    private val templateSeeder = DefaultTemplateSeeder(repository, activeSpaceStore, settings)

    @AfterTest
    fun closeDatabases() {
        appDatabase.close()
        aiIndexDriver.close()
    }

    private inline fun <reified T : Any> stubOf(
        crossinline answer: (methodName: String, arguments: Array<out Any?>) -> Any?
    ): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { stub, method, arguments ->
            when (method.name) {
                "toString" -> "stub of ${T::class.simpleName}"
                "hashCode" -> System.identityHashCode(stub)
                "equals" -> stub === arguments?.firstOrNull()
                else -> answer(method.name, arguments ?: emptyArray())
            }
        } as T

    private suspend fun assertEverythingIsUntouchedStarterContent() {
        val notes = appDatabase.noteDao().getAllNotesForBackup()
        assertTrue(notes.isNotEmpty())
        assertTrue(notes.all { it.isUntouchedStarterContent() }, "notes: ${notes.map { it.noteId to it.updatedAt }}")
        for (note in notes) {
            assertTrue(appDatabase.blockDao().getAllBlocksForNoteIncludingDeleted(note.noteId).all { it.updatedAt == STARTER_CONTENT_UPDATED_AT })
            assertTrue(appDatabase.canvasDao().getAllNodesForNoteIncludingDeleted(note.noteId).all { it.updatedAt == STARTER_CONTENT_UPDATED_AT })
            assertTrue(appDatabase.canvasDao().getAllEdgesForNoteIncludingDeleted(note.noteId).all { it.updatedAt == STARTER_CONTENT_UPDATED_AT })
        }
    }

    private suspend fun assertNothingIsWaitingToSync() {
        assertTrue(appDatabase.noteDao().getNotesNeedingSelfHostSync().isEmpty())
        assertTrue(appDatabase.noteDao().getNotesModifiedSince(0L).isEmpty())
        assertTrue(appDatabase.folderDao().getFoldersModifiedSince(0L).isEmpty())
    }

    @Test
    fun sampleNotesFoldersDatabaseAndCanvasAreSeededAsUntouchedStarterContentWithNoDailyPage() = runTest {
        sampleNotesSeeder.seedIfNeeded()

        assertNotNull(appDatabase.noteDao().getNoteById("sample_note_welcome"))
        assertNotNull(appDatabase.noteDao().getNoteById(databaseNoteId(SampleNoteContent.DATABASE_ID)))
        assertEquals(
            SampleNoteContent.databaseRows.size,
            appDatabase.noteDao().getAllNotesForBackup().count { it.databaseId == SampleNoteContent.DATABASE_ID }
        )
        assertTrue(repository.getSavedDailyNoteDates().isEmpty())
        assertEverythingIsUntouchedStarterContent()
        val folders = appDatabase.folderDao().getFoldersModifiedSince(-1L)
        assertTrue(folders.isNotEmpty())
        assertTrue(folders.all { it.updatedAt == STARTER_CONTENT_UPDATED_AT })
        assertEquals(STARTER_CONTENT_UPDATED_AT, favoriteNoteOrderStore.getOrder().updatedAt)
        assertNothingIsWaitingToSync()
    }

    @Test
    fun defaultTemplatesAreSeededAsUntouchedStarterContent() = runTest {
        templateSeeder.seedIfMissing()

        for (template in PREDEFINED_TEMPLATES) {
            val templateNote = appDatabase.noteDao().getNoteById(template.noteIdInSpace(DEFAULT_SPACE_ID))
            assertNotNull(templateNote)
            assertTrue(templateNote.isTemplate)
            assertTrue(templateNote.isUntouchedStarterContent())
        }
        assertNothingIsWaitingToSync()
    }

    @Test
    fun aPermanentlyDeletedTemplateIsNotCreatedAgain() = runTest {
        templateSeeder.seedIfMissing()
        val projectsNoteId = ProjectsTemplate.noteIdInSpace(DEFAULT_SPACE_ID)

        repository.deleteNote(projectsNoteId, filePath = "")
        templateSeeder.seedIfMissing()

        assertNull(appDatabase.noteDao().getNoteById(projectsNoteId))
    }

    @Test
    fun aTemplateAlreadySyncedFromAnotherDeviceIsLeftAsItIs() = runTest {
        val projectsNoteId = ProjectsTemplate.noteIdInSpace(DEFAULT_SPACE_ID)
        repository.saveNote(
            NoteMetadataEntity(
                noteId = projectsNoteId,
                title = "Client projects",
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                filePath = "",
                isTemplate = true
            ),
            NoteContent(blocks = emptyList()),
            stampUpdatedAt = false
        )

        templateSeeder.seedIfMissing()

        val projects = appDatabase.noteDao().getNoteById(projectsNoteId)
        assertEquals("Client projects", projects?.title)
        assertFalse(projects!!.isUntouchedStarterContent())
    }

    @Test
    fun aNewSpaceStillGetsItsOwnTemplates() = runTest {
        templateSeeder.seedIfMissing()
        activeSpaceId = "work-space"

        templateSeeder.seedIfMissing()

        assertNotNull(appDatabase.noteDao().getNoteById(ProjectsTemplate.noteIdInSpace("work-space")))
    }
}
