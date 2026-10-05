package com.emberr.domain.ai.tools

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.AppDatabase
import com.emberr.data.local.room.entity.DEFAULT_SPACE_ID
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.database.EmberrDatabase
import com.emberr.domain.ai.LocalAiEngine
import com.emberr.domain.ai.NoteIndexer
import com.emberr.domain.ai.external.AiSettingsRepository
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.TextBlock
import com.emberr.domain.repository.NoteRepositoryImpl
import com.emberr.domain.space.ActiveSpaceStore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NoteToolExecutorTest {

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

    private val toolCallEvents = NoteToolCallEvents()
    private val executor = NoteToolExecutor(repository, activeSpaceStore, toolCallEvents)

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

    private fun note(noteId: String, title: String, folderId: String? = null, trashedAt: Long? = null) =
        NoteMetadataEntity(
            noteId = noteId,
            title = title,
            folderId = folderId,
            isDaily = false,
            dateString = null,
            createdAt = 1L,
            updatedAt = 1L,
            filePath = "",
            trashedAt = trashedAt
        )

    private fun folder(folderId: String, name: String, parentFolderId: String? = null) =
        FolderEntity(folderId = folderId, name = name, parentFolderId = parentFolderId, createdAt = 1L)

    private suspend fun saveNote(metadata: NoteMetadataEntity, vararg blocks: NoteBlock) {
        repository.saveNote(metadata, NoteContent(blocks = blocks.toList()))
    }

    private suspend fun saveWorkProjectsAndAnInboxNote() {
        repository.insertFolder(folder("work", "Work"))
        repository.insertFolder(folder("projects", "Projects", parentFolderId = "work"))
        repository.insertFolder(folder("home", "Home"))
        saveNote(note("launch-plan", "Launch plan", folderId = "projects"))
        saveNote(note("garden", "Garden", folderId = "home"))
        saveNote(note("inbox", "Inbox"))
    }

    @Test
    fun listingEveryNoteShowsEachNoteWithItsFullFolderPath() = runTest {
        saveWorkProjectsAndAnInboxNote()

        val result = assertIs<NoteToolResult.Notes>(executor.run("list_notes", emptyMap()))

        assertEquals(
            setOf(
                FoundNote("launch-plan", "Launch plan", "Work/Projects"),
                FoundNote("garden", "Garden", "Home"),
                FoundNote("inbox", "Inbox", "")
            ),
            result.notes.toSet()
        )
        assertTrue("- Launch plan (note_id: launch-plan, folder: Work/Projects)" in result.renderForModel())
        assertTrue("- Inbox (note_id: inbox)" in result.renderForModel())
    }

    @Test
    fun listingAFolderIncludesItsSubfoldersAndIgnoresLetterCase() = runTest {
        saveWorkProjectsAndAnInboxNote()

        val result = assertIs<NoteToolResult.Notes>(executor.run("list_notes", mapOf("folder_path" to "work")))

        assertEquals(listOf("launch-plan"), result.notes.map { it.noteId })
    }

    @Test
    fun listingAFolderThatDoesNotExistFails() = runTest {
        saveWorkProjectsAndAnInboxNote()

        assertIs<NoteToolResult.Failure>(executor.run("list_notes", mapOf("folder_path" to "Recipes")))
    }

    @Test
    fun readingANoteReturnsItsTitleAndBlocksAsMarkdown() = runTest {
        saveNote(
            note("groceries", "Groceries"),
            TextBlock(id = "first", text = "Buy milk", updatedAt = 1L),
            TextBlock(id = "second", text = "Buy bread", updatedAt = 1L)
        )

        val result = assertIs<NoteToolResult.FullNote>(executor.run("read_note", mapOf("note_id" to "groceries")))

        assertEquals("# Groceries\n\nBuy milk\n\nBuy bread", result.markdown)
    }

    @Test
    fun aTrashedNoteCannotBeRead() = runTest {
        saveNote(note("old", "Old idea", trashedAt = 5L), TextBlock(id = "text", text = "secret", updatedAt = 1L))

        assertIs<NoteToolResult.Failure>(executor.run("read_note", mapOf("note_id" to "old")))
    }

    @Test
    fun aNoteInAnotherSpaceCannotBeRead() = runTest {
        repository.saveNoteInSpace(
            spaceId = "other-space",
            metadata = note("elsewhere", "Elsewhere"),
            content = NoteContent(blocks = listOf(TextBlock(id = "text", text = "private", updatedAt = 1L)))
        )

        assertIs<NoteToolResult.Failure>(executor.run("read_note", mapOf("note_id" to "elsewhere")))
    }

    @Test
    fun aMadeUpNoteIdFails() = runTest {
        assertIs<NoteToolResult.Failure>(executor.run("read_note", mapOf("note_id" to "does-not-exist")))
    }

    @Test
    fun searchingFindsANoteByWordsInsideItsContent() = runTest {
        saveNote(note("week", "This week"), TextBlock(id = "text", text = "Dentist on Friday", updatedAt = 1L))
        saveNote(note("other", "Something else"), TextBlock(id = "text-2", text = "Nothing here", updatedAt = 1L))

        val result = assertIs<NoteToolResult.Notes>(executor.run("search_notes", mapOf("query" to "dentist")))

        assertEquals(listOf("week"), result.notes.map { it.noteId })
    }

    @Test
    fun aBlankSearchFails() = runTest {
        assertIs<NoteToolResult.Failure>(executor.run("search_notes", mapOf("query" to "  ")))
    }

    @Test
    fun anUnknownToolFails() = runTest {
        assertIs<NoteToolResult.Failure>(executor.run("delete_note", mapOf("note_id" to "anything")))
    }

    @Test
    fun everyToolCallIsAnnouncedSoTheChatCanShowIt() = runTest {
        saveNote(note("groceries", "Groceries"), TextBlock(id = "text", text = "Buy milk", updatedAt = 1L))
        val announcedCalls = mutableListOf<NoteToolCallSummary>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            toolCallEvents.calls.toList(announcedCalls)
        }

        executor.run("read_note", mapOf("note_id" to "groceries"))
        executor.run("search_notes", mapOf("query" to "milk"))

        assertEquals(
            listOf("Read \"Groceries\"", "Searched notes for \"milk\""),
            announcedCalls.map { it.description }
        )
    }
}
