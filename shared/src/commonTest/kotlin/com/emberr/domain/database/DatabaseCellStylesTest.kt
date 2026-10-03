package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCellStyle
import com.emberr.domain.model.DatabaseColorRule
import com.emberr.domain.model.DatabaseColorRuleTarget
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseRelativeDate
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.TextAlignment
import kotlinx.datetime.LocalDate
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

    private val dueDateColumn = DatabaseColumnTarget.Property(PropertyType.DUE_DATE)
    private val today = LocalDate(2026, 10, 3)
    private val overdue = DatabaseFilter(
        id = "overdue",
        target = dueDateColumn,
        condition = DatabaseFilterCondition.IS_BEFORE,
        relativeDate = DatabaseRelativeDate.TODAY
    )

    private fun row(noteId: String, dueDate: LocalDate? = null) = DatabaseRow(
        noteId = noteId,
        title = "",
        createdAt = 0L,
        cellsByColumn = listOfNotNull(
            dueDate?.let { dueDateColumn to PropertyBlock(id = "due-$noteId", propertyType = PropertyType.DUE_DATE, date = it) }
        ).toMap()
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
            styled.effectiveStyleOf(row("row-a"), statusColumn, today)
        )
        assertEquals(
            DatabaseCellStyle(textColorName = "blue", backgroundColorName = "grey", alignment = TextAlignment.RIGHT),
            styled.effectiveStyleOf(row("row-b"), statusColumn, today)
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

    @Test
    fun aRowRuleColorsEveryCellOfTheRowsThatMatch() {
        val withRule = database.copy(
            columns = listOf(statusColumn, dueDateColumn),
            colorRules = listOf(DatabaseColorRule(id = "rule", condition = overdue, backgroundColorName = "red"))
        )

        assertEquals("red", withRule.effectiveStyleOf(row("late", dueDate = LocalDate(2026, 10, 1)), statusColumn, today).backgroundColorName)
        assertEquals(null, withRule.effectiveStyleOf(row("on-time", dueDate = LocalDate(2026, 10, 9)), statusColumn, today).backgroundColorName)
        assertEquals(null, withRule.effectiveStyleOf(row("no-date"), statusColumn, today).backgroundColorName)
    }

    @Test
    fun aCellRuleOnlyColorsTheCellOfItsOwnColumn() {
        val withRule = database.copy(
            columns = listOf(statusColumn, dueDateColumn),
            colorRules = listOf(DatabaseColorRule(id = "rule", condition = overdue, target = DatabaseColorRuleTarget.CELL, textColorName = "red"))
        )
        val lateRow = row("late", dueDate = LocalDate(2026, 10, 1))

        assertEquals("red", withRule.effectiveStyleOf(lateRow, dueDateColumn, today).textColorName)
        assertEquals(null, withRule.effectiveStyleOf(lateRow, statusColumn, today).textColorName)
    }

    @Test
    fun manualRowStylesWinOverRulesAndRulesWinOverColumnStylesAndEarlierRulesWin() {
        val styled = database.copy(
            columns = listOf(statusColumn, dueDateColumn),
            colorRules = listOf(
                DatabaseColorRule(id = "first", condition = overdue, backgroundColorName = "red", textColorName = "orange"),
                DatabaseColorRule(id = "second", condition = overdue, backgroundColorName = "blue")
            )
        )
            .withStyle(DatabaseStyleTarget.COLUMN, "late", statusColumn, DatabaseCellStyle(textColorName = "grey", backgroundColorName = "grey"))
            .withStyle(DatabaseStyleTarget.ROW, "late", statusColumn, DatabaseCellStyle(textColorName = "green"))

        assertEquals(
            DatabaseCellStyle(textColorName = "green", backgroundColorName = "red"),
            styled.effectiveStyleOf(row("late", dueDate = LocalDate(2026, 10, 1)), statusColumn, today)
        )
    }

    @Test
    fun aRuleWithoutAValueYetColorsNothing() {
        val unfinished = database.copy(
            columns = listOf(statusColumn, dueDateColumn),
            colorRules = listOf(DatabaseColorRule(id = "rule", condition = overdue.copy(relativeDate = null), backgroundColorName = "red"))
        )

        assertEquals(null, unfinished.effectiveStyleOf(row("late", dueDate = LocalDate(2026, 10, 1)), statusColumn, today).backgroundColorName)
    }

    @Test
    fun aRuleOnAColumnTheDatabaseNoLongerHasColorsNothing() {
        val withRuleButNoDueDateColumn = database.copy(
            columns = listOf(statusColumn),
            colorRules = listOf(DatabaseColorRule(id = "rule", condition = overdue, backgroundColorName = "red"))
        )

        val style = withRuleButNoDueDateColumn.effectiveStyleOf(row("late", dueDate = LocalDate(2026, 10, 1)), statusColumn, today)

        assertEquals(null, style.backgroundColorName)
    }

    @Test
    fun removingAColumnDropsTheRulesThatCheckIt() {
        val withRule = database.copy(
            columns = listOf(statusColumn, dueDateColumn),
            colorRules = listOf(DatabaseColorRule(id = "rule", condition = overdue, backgroundColorName = "red"))
        )

        assertEquals(emptyList(), withRule.withColumnRemoved(dueDateColumn).colorRules)
    }
}
