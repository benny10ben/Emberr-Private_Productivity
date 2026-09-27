package com.emberr.domain.database

import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseRowChangeTest {

    private fun nameCell(text: String, isDeleted: Boolean = false, updatedAt: Long = 100L) = PropertyBlock(
        id = "name-row-1",
        propertyType = PropertyType.NAME,
        text = text,
        isDeleted = isDeleted,
        updatedAt = updatedAt
    )

    @Test
    fun undoingACellChangeWritesTheValueFromBefore() {
        val change = DatabaseRowChange.Cell("row-1", before = nameCell("Ada"), after = nameCell("Ada Lovelace", updatedAt = 200L))

        val written = change.cellToWrite(HistoryDirection.UNDO, currentCell = nameCell("Ada Lovelace", updatedAt = 200L), now = 900L)

        assertEquals(nameCell("Ada", updatedAt = 900L), written)
    }

    @Test
    fun redoingACellChangeWritesTheValueFromAfter() {
        val change = DatabaseRowChange.Cell("row-1", before = nameCell("Ada"), after = nameCell("Ada Lovelace", updatedAt = 200L))

        val written = change.cellToWrite(HistoryDirection.REDO, currentCell = nameCell("Ada", updatedAt = 900L), now = 950L)

        assertEquals(nameCell("Ada Lovelace", updatedAt = 950L), written)
    }

    @Test
    fun undoingACellThatDidNotExistBeforeTombstonesIt() {
        val change = DatabaseRowChange.Cell("row-1", before = null, after = nameCell(""))

        val written = change.cellToWrite(HistoryDirection.UNDO, currentCell = nameCell(""), now = 900L)

        assertEquals(nameCell("", isDeleted = true, updatedAt = 900L), written)
    }

    @Test
    fun redoingACellThatDidNotExistBeforeBringsItBack() {
        val change = DatabaseRowChange.Cell("row-1", before = null, after = nameCell(""))

        assertEquals(
            nameCell("", updatedAt = 950L),
            change.cellToWrite(HistoryDirection.REDO, currentCell = nameCell("", isDeleted = true, updatedAt = 900L), now = 950L)
        )
        assertEquals(
            nameCell("", updatedAt = 950L),
            change.cellToWrite(HistoryDirection.REDO, currentCell = null, now = 950L)
        )
    }

    @Test
    fun undoingARemovedColumnBringsBackItsValue() {
        val change = DatabaseRowChange.Cell(
            "row-1",
            before = nameCell("Ada"),
            after = nameCell("Ada", isDeleted = true, updatedAt = 200L)
        )

        val written = change.cellToWrite(HistoryDirection.UNDO, currentCell = nameCell("Ada", isDeleted = true, updatedAt = 200L), now = 900L)

        assertEquals(nameCell("Ada", updatedAt = 900L), written)
    }

    @Test
    fun undoSkipsACellThatWasChangedSince() {
        val change = DatabaseRowChange.Cell("row-1", before = nameCell("Ada"), after = nameCell("Ada Lovelace"))

        assertNull(change.cellToWrite(HistoryDirection.UNDO, currentCell = nameCell("Grace Hopper"), now = 900L))
        assertNull(change.cellToWrite(HistoryDirection.UNDO, currentCell = nameCell("Ada Lovelace", isDeleted = true), now = 900L))
    }

    @Test
    fun aDifferentSaveTimeAloneDoesNotCountAsAChange() {
        val change = DatabaseRowChange.Cell("row-1", before = nameCell("Ada"), after = nameCell("Ada Lovelace", updatedAt = 200L))

        val written = change.cellToWrite(HistoryDirection.UNDO, currentCell = nameCell("Ada Lovelace", updatedAt = 777L), now = 900L)

        assertEquals(nameCell("Ada", updatedAt = 900L), written)
    }

    @Test
    fun titleChangesUndoRedoAndSkipARenameMadeSince() {
        val change = DatabaseRowChange.Title("row-1", before = "Dune", after = "Dune Messiah")

        assertEquals("Dune", change.titleToWrite(HistoryDirection.UNDO, currentTitle = "Dune Messiah"))
        assertEquals("Dune Messiah", change.titleToWrite(HistoryDirection.REDO, currentTitle = "Dune"))
        assertNull(change.titleToWrite(HistoryDirection.UNDO, currentTitle = "Children of Dune"))
    }

    @Test
    fun undoingAnAddedRowTakesItOutOfTheTableAndRedoPutsItBack() {
        val change = DatabaseRowChange.RowPresence("row-1", wasInTable = false, isInTable = true)

        assertEquals(false, change.inTableStateToWrite(HistoryDirection.UNDO, isCurrentlyInTable = true))
        assertEquals(true, change.inTableStateToWrite(HistoryDirection.REDO, isCurrentlyInTable = false))
    }

    @Test
    fun oneTypingSessionInACellMergesIntoOneStep() {
        val firstSave = DatabaseRowChange.Cell("row-1", before = nameCell("Ada"), after = nameCell("Ada L", updatedAt = 200L))
        val secondSave = DatabaseRowChange.Cell("row-1", before = nameCell("Ada L", updatedAt = 200L), after = nameCell("Ada Lovelace", updatedAt = 300L))

        val merged = listOf<DatabaseRowChange>(firstSave).mergedWith(listOf(secondSave))

        assertEquals(
            listOf<DatabaseRowChange>(DatabaseRowChange.Cell("row-1", before = nameCell("Ada"), after = nameCell("Ada Lovelace", updatedAt = 300L))),
            merged
        )
    }

    @Test
    fun mergingKeepsChangesToDifferentThingsSeparate() {
        val nameChange = DatabaseRowChange.Cell("row-1", before = null, after = nameCell("Ada"))
        val titleChange = DatabaseRowChange.Title("row-1", before = "", after = "Dune")
        val otherRowTitle = DatabaseRowChange.Title("row-2", before = "", after = "Emma")
        val laterTitle = DatabaseRowChange.Title("row-1", before = "Dune", after = "Dune Messiah")

        val merged = listOf(nameChange, titleChange).mergedWith(listOf(otherRowTitle, laterTitle))

        assertEquals(
            listOf(nameChange, DatabaseRowChange.Title("row-1", before = "", after = "Dune Messiah"), otherRowTitle),
            merged
        )
    }

    @Test
    fun undoingADeletedRowSkipsARowThatWasAlreadyRestored() {
        val change = DatabaseRowChange.RowPresence("row-1", wasInTable = true, isInTable = false)

        assertEquals(true, change.inTableStateToWrite(HistoryDirection.UNDO, isCurrentlyInTable = false))
        assertNull(change.inTableStateToWrite(HistoryDirection.UNDO, isCurrentlyInTable = true))
    }
}
