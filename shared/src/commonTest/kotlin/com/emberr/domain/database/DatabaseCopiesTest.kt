package com.emberr.domain.database

import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DatabaseCopiesTest {

    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val clientColumn = DatabaseColumnTarget.CustomProperty("client-id")

    private val statusCell = PropertyBlock(
        id = databaseCellBlockId(statusColumn, "source-row"),
        propertyType = PropertyType.STATUS,
        text = "Done",
        updatedAt = 100L
    )
    private val clientCell = PropertyBlock(
        id = databaseCellBlockId(clientColumn, "source-row"),
        customPropertyId = "client-id",
        customLabel = "Client",
        customValueType = PropertyValueType.TEXT,
        text = "Acme",
        updatedAt = 100L
    )

    @Test
    fun copiedCellsGetTheCellIdsOfTheNewRowAndKeepTheirValues() {
        val copiedBlocks = listOf(statusCell, clientCell).copiedForRow("source-row", "copy-row")

        assertEquals(
            listOf(
                statusCell.copy(id = databaseCellBlockId(statusColumn, "copy-row")),
                clientCell.copy(id = databaseCellBlockId(clientColumn, "copy-row"))
            ),
            copiedBlocks
        )
    }

    @Test
    fun copiedCellsShowUpInTheNewRow() {
        val copiedBlocks = listOf(statusCell, clientCell).copiedForRow("source-row", "copy-row")

        val copiedRow = buildDatabaseRow(noteId = "copy-row", title = "Task", createdAt = 1L, blocks = copiedBlocks)

        assertEquals("Done", copiedRow.cell(statusColumn)?.text)
        assertEquals("Acme", copiedRow.cell(clientColumn)?.text)
    }

    @Test
    fun otherBlocksInTheRowGetNewIdsAndKeepTheirContent() {
        val text = TextBlock(id = "text-1", text = "Meeting notes", updatedAt = 100L)
        val propertyThatIsNotACell = PropertyBlock(id = "loose-property", propertyType = PropertyType.PHONE, text = "555", updatedAt = 100L)

        val copiedBlocks = listOf(text, propertyThatIsNotACell).copiedForRow("source-row", "copy-row")

        val copiedText = copiedBlocks[0] as TextBlock
        val copiedProperty = copiedBlocks[1] as PropertyBlock
        assertNotEquals(text.id, copiedText.id)
        assertEquals(text.copy(id = copiedText.id), copiedText)
        assertNotEquals(propertyThatIsNotACell.id, copiedProperty.id)
        assertEquals(propertyThatIsNotACell.copy(id = copiedProperty.id), copiedProperty)
    }

    @Test
    fun deletedBlocksAreLeftOutOfTheCopy() {
        val deletedCell = statusCell.copy(isDeleted = true)
        val deletedText = TextBlock(id = "text-1", text = "Old", isDeleted = true, updatedAt = 100L)

        val copiedBlocks = listOf(deletedCell, deletedText, clientCell).copiedForRow("source-row", "copy-row")

        assertEquals(listOf(clientCell.copy(id = databaseCellBlockId(clientColumn, "copy-row"))), copiedBlocks)
    }
}
