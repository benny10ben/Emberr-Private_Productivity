package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DatabaseCellsTest {

    private val rowNoteId = "row-1"
    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val nameColumn = DatabaseColumnTarget.Property(PropertyType.NAME)
    private val tagsColumn = DatabaseColumnTarget.Property(PropertyType.TAGS)
    private val phoneColumn = DatabaseColumnTarget.Property(PropertyType.PHONE)
    private val clientColumn = DatabaseColumnTarget.CustomProperty("client-id")

    private val database = DatabaseBlock(
        id = "block-1",
        databaseId = "database-1",
        columns = listOf(statusColumn, clientColumn, nameColumn),
        customProperties = listOf(DatabaseCustomProperty(id = "client-id", name = "Client", valueType = PropertyValueType.TEXT))
    )

    private fun cell(column: DatabaseColumnTarget.Property, text: String = "", isDeleted: Boolean = false, updatedAt: Long = 100L) =
        PropertyBlock(
            id = databaseCellBlockId(column, rowNoteId),
            propertyType = column.propertyType,
            text = text,
            isDeleted = isDeleted,
            updatedAt = updatedAt
        )

    private fun clientCell(text: String = "", label: String = "Client", isDeleted: Boolean = false, updatedAt: Long = 100L) =
        PropertyBlock(
            id = "client-id-row-1",
            customPropertyId = "client-id",
            customLabel = label,
            customValueType = PropertyValueType.TEXT,
            text = text,
            isDeleted = isDeleted,
            updatedAt = updatedAt
        )

    @Test
    fun aCellBlockIdIsBuiltFromTheColumnAndTheRow() {
        assertEquals("status-row-1", databaseCellBlockId(statusColumn, rowNoteId))
        assertEquals("due_date-row-1", databaseCellBlockId(DatabaseColumnTarget.Property(PropertyType.DUE_DATE), rowNoteId))
        assertEquals("client-id-row-1", databaseCellBlockId(clientColumn, rowNoteId))
    }

    @Test
    fun aNewRowGetsAnEmptyCellForEveryColumnInColumnOrder() {
        val blocks = database.newRowBlocks(rowNoteId, now = 500L)

        assertEquals(
            listOf<NoteBlock>(
                PropertyBlock(id = "status-row-1", propertyType = PropertyType.STATUS, updatedAt = 500L),
                clientCell(updatedAt = 500L),
                PropertyBlock(id = "name-row-1", propertyType = PropertyType.NAME, updatedAt = 500L)
            ),
            blocks
        )
    }

    @Test
    fun aCustomColumnWhosePropertyIsMissingHasNoCell() {
        val orphan = DatabaseColumnTarget.CustomProperty("gone-id")

        assertNull(database.emptyCell(orphan, rowNoteId, now = 500L))
        assertNull(database.emptyCell(DatabaseColumnTarget.NotesTitle, rowNoteId, now = 500L))
    }

    @Test
    fun showingAColumnAddsAnEmptyCellAfterTheOtherProperties() {
        val body = TextBlock(id = "body", text = "Notes about the book", updatedAt = 100L)
        val blocks = listOf(cell(statusColumn), body)

        val result = blocks.withDatabaseColumnShown(database, tagsColumn, rowNoteId, now = 500L)

        assertEquals(
            listOf(cell(statusColumn), PropertyBlock(id = "tags-row-1", propertyType = PropertyType.TAGS, updatedAt = 500L), body),
            result
        )
    }

    @Test
    fun showingAColumnKeepsACellThatIsAlreadyThere() {
        val blocks = listOf<NoteBlock>(cell(nameColumn, text = "Ada"))

        assertSame(blocks, blocks.withDatabaseColumnShown(database, nameColumn, rowNoteId, now = 500L))
    }

    @Test
    fun showingAColumnAgainAfterItWasRemovedStartsEmptyInTheSamePlace() {
        val body = TextBlock(id = "body", updatedAt = 100L)
        val blocks = listOf(clientCell(text = "Acme", isDeleted = true), body)

        val result = blocks.withDatabaseColumnShown(database, clientColumn, rowNoteId, now = 500L)

        assertEquals(listOf(clientCell(updatedAt = 500L), body), result)
    }

    @Test
    fun removingAColumnTombstonesTheCellAndKeepsItsValue() {
        val blocks = listOf<NoteBlock>(cell(nameColumn, text = "Ada"))

        val result = blocks.withDatabaseColumnRemoved(nameColumn, rowNoteId, now = 500L)

        assertEquals(listOf<NoteBlock>(cell(nameColumn, text = "Ada", isDeleted = true, updatedAt = 500L)), result)
    }

    @Test
    fun removingAColumnTheRowDoesNotHaveChangesNothing() {
        val blocks = listOf<NoteBlock>(cell(nameColumn, isDeleted = true))

        assertSame(blocks, blocks.withDatabaseColumnRemoved(nameColumn, rowNoteId, now = 500L))
        assertSame(blocks, blocks.withDatabaseColumnRemoved(phoneColumn, rowNoteId, now = 500L))
    }

    @Test
    fun renamingACustomPropertyRelabelsOnlyItsLiveCell() {
        val blocks = listOf<NoteBlock>(clientCell(text = "Acme"), cell(nameColumn, text = "Ada"))

        val result = blocks.withDatabaseCellRenamed("client-id", rowNoteId, "Customer", now = 500L)

        assertEquals(listOf<NoteBlock>(clientCell(text = "Acme", label = "Customer", updatedAt = 500L), cell(nameColumn, text = "Ada")), result)
        assertSame(result, result.withDatabaseCellRenamed("client-id", rowNoteId, "Customer", now = 900L))
    }

    @Test
    fun placingACellReplacesTheOneWithTheSameIdWhereItIs() {
        val body = TextBlock(id = "body", updatedAt = 100L)
        val blocks = listOf(cell(statusColumn), cell(nameColumn), body)

        val result = blocks.withDatabaseCellPlaced(cell(statusColumn, text = "changed", updatedAt = 500L))

        assertEquals(listOf(cell(statusColumn, text = "changed", updatedAt = 500L), cell(nameColumn), body), result)
    }

    @Test
    fun editingStartsFromTheLiveCellOrFromAFreshEmptyOne() {
        val liveCell = cell(nameColumn, text = "Ada")
        val freshCell = PropertyBlock(id = "name-row-1", propertyType = PropertyType.NAME, updatedAt = 500L)

        assertEquals(liveCell, listOf<NoteBlock>(liveCell).databaseCellForEditing(database, nameColumn, rowNoteId, now = 500L))
        assertEquals(
            freshCell,
            listOf<NoteBlock>(cell(nameColumn, text = "Ada", isDeleted = true))
                .databaseCellForEditing(database, nameColumn, rowNoteId, now = 500L)
        )
        assertEquals(freshCell, emptyList<NoteBlock>().databaseCellForEditing(database, nameColumn, rowNoteId, now = 500L))
    }

    @Test
    fun aRowShowsOnlyItsLiveCellsIncludingCustomOnes() {
        val liveName = cell(nameColumn, text = "Ada")
        val liveClient = clientCell(text = "Acme")
        val blocks = listOf(
            liveName,
            liveClient,
            cell(phoneColumn, text = "555", isDeleted = true),
            PropertyBlock(id = "added-by-hand", propertyType = PropertyType.STATUS, tags = listOf("Done")),
            PropertyBlock(id = "global-custom", customPropertyId = "global-id", customLabel = "Budget", text = "10"),
            TextBlock(id = "body", text = "hello")
        )

        val row = buildDatabaseRow(noteId = rowNoteId, title = "Dune", createdAt = 42L, blocks = blocks)

        assertEquals(DatabaseRow(rowNoteId, "Dune", 42L, mapOf(nameColumn to liveName, clientColumn to liveClient)), row)
    }
}
