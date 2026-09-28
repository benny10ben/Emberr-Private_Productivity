package com.emberr.data.local.room

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.emberr.data.local.room.entity.DEFAULT_SPACE_ID
import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteKind
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.TextBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseRowQueriesTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val noteDao = database.noteDao()
    private val blockDao = database.blockDao()
    private val blockJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private fun note(
        noteId: String,
        databaseId: String? = null,
        isSubNote: Boolean = databaseId != null,
        trashedAt: Long? = null
    ) = NoteMetadataEntity(
        noteId = noteId,
        title = noteId,
        folderId = null,
        isDaily = false,
        dateString = null,
        createdAt = 1L,
        updatedAt = 1L,
        filePath = "",
        trashedAt = trashedAt,
        isSubNote = isSubNote,
        databaseId = databaseId
    )

    private fun stored(noteId: String, block: NoteBlock, displayOrder: Int = 0) = NoteBlockEntity(
        blockId = block.id,
        noteId = noteId,
        displayOrder = displayOrder,
        blockDataJson = blockJson.encodeToString(NoteBlock.serializer(), block),
        updatedAt = block.updatedAt,
        isDeleted = block.isDeleted
    )

    @Test
    fun onlyRowsOfThatDatabaseThatAreNotInTheTrashAreObserved() = runTest {
        noteDao.insertOrUpdateMetadata(note("row-live", databaseId = "books"))
        noteDao.insertOrUpdateMetadata(note("row-trashed", databaseId = "books", trashedAt = 5L))
        noteDao.insertOrUpdateMetadata(note("row-other-database", databaseId = "films"))
        noteDao.insertOrUpdateMetadata(note("plain-note"))

        assertEquals(listOf("row-live"), noteDao.observeRowNotesInTable("books").first().map { it.noteId })
        assertEquals(
            setOf("row-live", "row-trashed"),
            noteDao.getAllRowNotesIncludingTrashed("books").map { it.noteId }.toSet()
        )
    }

    @Test
    fun databaseTemplatesAndNoteTemplatesAreListedSeparately() = runTest {
        noteDao.insertOrUpdateMetadata(note("note-template").copy(isTemplate = true))
        noteDao.insertOrUpdateMetadata(note("row-template").copy(isTemplate = true, isDatabaseTemplate = true))
        noteDao.insertOrUpdateMetadata(note("deleted-row-template", trashedAt = 5L).copy(isTemplate = true, isDatabaseTemplate = true))
        noteDao.insertOrUpdateMetadata(note("plain-note"))

        assertEquals(listOf("note-template"), noteDao.getAllTemplates(DEFAULT_SPACE_ID).first().map { it.noteId })
        assertEquals(listOf("row-template"), noteDao.getAllDatabaseTemplates(DEFAULT_SPACE_ID).first().map { it.noteId })
    }

    @Test
    fun onlyLivePropertyBlocksOfRowsInTheTableAreObserved() = runTest {
        noteDao.insertOrUpdateMetadata(note("row-live", databaseId = "books"))
        noteDao.insertOrUpdateMetadata(note("row-trashed", databaseId = "books", trashedAt = 5L))
        noteDao.insertOrUpdateMetadata(note("row-other-database", databaseId = "films"))
        blockDao.insertOrUpdateBlocks(
            listOf(
                stored("row-live", PropertyBlock(id = "status-row-live", propertyType = PropertyType.STATUS, tags = listOf("Reading")), 0),
                stored("row-live", PropertyBlock(id = "name-row-live", propertyType = PropertyType.NAME, isDeleted = true), 1),
                stored("row-live", TextBlock(id = "body", text = "Loved it"), 2),
                stored("row-trashed", PropertyBlock(id = "status-row-trashed", propertyType = PropertyType.STATUS)),
                stored("row-other-database", PropertyBlock(id = "status-row-other-database", propertyType = PropertyType.STATUS))
            )
        )

        val observedBlockIds = blockDao.observeLivePropertyBlocksOfRowsInTable("books").first().map { it.blockId }

        assertEquals(listOf("status-row-live"), observedBlockIds)
    }

    @Test
    fun rowNotesAreLeftOutOfTheLinkPickerAndRecents() = runTest {
        noteDao.insertOrUpdateMetadata(note("plain-note"))
        noteDao.insertOrUpdateMetadata(note("linked-sub-note", isSubNote = true))
        noteDao.insertOrUpdateMetadata(note("row", databaseId = "books"))

        val linkableNoteIds = noteDao.getAllLinkableNotes(DEFAULT_SPACE_ID).first().map { it.noteId }.toSet()

        assertEquals(setOf("plain-note", "linked-sub-note"), linkableNoteIds)
    }

    @Test
    fun searchFindsRowsAndSubNotesButNotEmbeddedCanvases() = runTest {
        val notes = listOf(
            note("plain-note"),
            note("linked-sub-note", isSubNote = true),
            note("row", databaseId = "books"),
            note("embedded-canvas", isSubNote = true).copy(kind = NoteKind.CANVAS),
            note("trashed-row", databaseId = "books", trashedAt = 5L)
        )
        notes.forEach { noteDao.insertOrUpdateMetadata(it.copy(title = "Monday")) }
        val expectedIds = setOf("plain-note", "linked-sub-note", "row")

        val titleMatchIds = noteDao.searchNotesByTitleOrSnippet(DEFAULT_SPACE_ID, "Monday").map { it.noteId }.toSet()
        val searchableIds = noteDao.getSearchableNotesByIds(notes.map { it.noteId }).map { it.noteId }.toSet()

        assertEquals(expectedIds, titleMatchIds)
        assertEquals(expectedIds, searchableIds)
    }
}
