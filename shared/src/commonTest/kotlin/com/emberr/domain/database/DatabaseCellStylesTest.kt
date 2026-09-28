package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCellStyle
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.TextAlignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseCellStylesTest {

    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val tagsColumn = DatabaseColumnTarget.Property(PropertyType.TAGS)

    private val database = DatabaseBlock(
        id = "block-1",
        databaseId = "database-1",
        columns = listOf(statusColumn, tagsColumn),
        updatedAt = 100L
    )

    private fun DatabaseBlock.editedAt(time: Long, change: (DatabaseBlock) -> DatabaseBlock): DatabaseBlock =
        change(this).copy(updatedAt = time).withSettingTimesStamped(before = this, now = time)

    @Test
    fun aCellStyleIsSavedForThatCellOnly() {
        val red = DatabaseCellStyle(textColorName = "red")

        val styled = database.withStyle(DatabaseStyleTarget.CELL, "row-a", statusColumn, red)

        assertEquals(red, styled.styleOf(DatabaseStyleTarget.CELL, "row-a", statusColumn))
        assertEquals(DatabaseCellStyle(), styled.styleOf(DatabaseStyleTarget.CELL, "row-a", tagsColumn))
        assertEquals(DatabaseCellStyle(), styled.styleOf(DatabaseStyleTarget.CELL, "row-b", statusColumn))
    }

    @Test
    fun clearingEveryPartOfAStyleRemovesIt() {
        val styled = database.withStyle(DatabaseStyleTarget.ROW, "row-a", statusColumn, DatabaseCellStyle(alignment = TextAlignment.CENTER))

        val cleared = styled.withStyle(DatabaseStyleTarget.ROW, "row-a", statusColumn, DatabaseCellStyle())

        assertTrue(cleared.rowStyles.isEmpty())
    }

    @Test
    fun theCellStyleWinsOverTheRowStyleWhichWinsOverTheColumnStyle() {
        val styled = database
            .withStyle(DatabaseStyleTarget.COLUMN, "row-a", statusColumn, DatabaseCellStyle(textColorName = "blue", backgroundColorName = "grey", alignment = TextAlignment.RIGHT))
            .withStyle(DatabaseStyleTarget.ROW, "row-a", statusColumn, DatabaseCellStyle(textColorName = "green", backgroundColorName = "pink"))
            .withStyle(DatabaseStyleTarget.CELL, "row-a", statusColumn, DatabaseCellStyle(textColorName = "red"))

        assertEquals(
            DatabaseCellStyle(textColorName = "red", backgroundColorName = "pink", alignment = TextAlignment.RIGHT),
            styled.effectiveStyleOf("row-a", statusColumn)
        )
        assertEquals(
            DatabaseCellStyle(textColorName = "blue", backgroundColorName = "grey", alignment = TextAlignment.RIGHT),
            styled.effectiveStyleOf("row-b", statusColumn)
        )
    }

    @Test
    fun removingAColumnDropsItsColumnAndCellStyles() {
        val styled = database
            .withStyle(DatabaseStyleTarget.COLUMN, "row-a", statusColumn, DatabaseCellStyle(textColorName = "blue"))
            .withStyle(DatabaseStyleTarget.CELL, "row-a", statusColumn, DatabaseCellStyle(textColorName = "red"))
            .withStyle(DatabaseStyleTarget.CELL, "row-a", tagsColumn, DatabaseCellStyle(textColorName = "green"))

        val result = styled.withColumnRemoved(statusColumn)

        assertTrue(result.columnStyles.isEmpty())
        assertEquals(DatabaseCellStyle(textColorName = "green"), result.styleOf(DatabaseStyleTarget.CELL, "row-a", tagsColumn))
        assertEquals(1, result.cellStyles.size)
    }

    @Test
    fun stylesMadeOnTwoDevicesAreBothKeptWhenMerged() {
        val firstDevice = database.editedAt(200L) {
            it.withStyle(DatabaseStyleTarget.CELL, "row-a", statusColumn, DatabaseCellStyle(textColorName = "red"))
        }
        val secondDevice = database.editedAt(300L) {
            it.withStyle(DatabaseStyleTarget.ROW, "row-b", statusColumn, DatabaseCellStyle(backgroundColorName = "blue"))
        }

        val merged = mergeDatabaseBlocks(firstDevice, secondDevice)

        assertEquals(DatabaseCellStyle(textColorName = "red"), merged.styleOf(DatabaseStyleTarget.CELL, "row-a", statusColumn))
        assertEquals(DatabaseCellStyle(backgroundColorName = "blue"), merged.styleOf(DatabaseStyleTarget.ROW, "row-b", statusColumn))
    }
}
