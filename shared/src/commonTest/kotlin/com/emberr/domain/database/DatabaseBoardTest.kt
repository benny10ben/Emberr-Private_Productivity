package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseDateGrouping
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseBoardTest {

    private val nameColumn = DatabaseColumnTarget.Property(PropertyType.NAME)
    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val tagsColumn = DatabaseColumnTarget.Property(PropertyType.TAGS)
    private val dueDateColumn = DatabaseColumnTarget.Property(PropertyType.DUE_DATE)
    private val checkboxColumn = DatabaseColumnTarget.Property(PropertyType.CHECKBOX)

    private val board = DatabaseView(id = "board", name = "Board", type = DatabaseViewType.BOARD)

    private fun row(
        noteId: String,
        status: String? = null,
        tags: List<String> = emptyList(),
        dueDate: LocalDate? = null,
        checked: Boolean? = null
    ): DatabaseRow {
        val cells = listOfNotNull(
            status?.let { statusColumn to PropertyBlock(id = "status-$noteId", propertyType = PropertyType.STATUS, tags = listOf(it)) },
            tags.takeIf { it.isNotEmpty() }?.let { tagsColumn to PropertyBlock(id = "tags-$noteId", propertyType = PropertyType.TAGS, tags = it) },
            dueDate?.let { dueDateColumn to PropertyBlock(id = "due_date-$noteId", propertyType = PropertyType.DUE_DATE, date = it) },
            checked?.let { checkboxColumn to PropertyBlock(id = "checkbox-$noteId", propertyType = PropertyType.CHECKBOX, isChecked = it) }
        )
        return DatabaseRow(noteId = noteId, title = noteId, createdAt = 0L, cellsByColumn = cells.toMap())
    }

    private fun List<DatabaseBoardGroup>.summary(): List<Pair<String, List<String>>> = map { group -> group.key to group.rows.map { it.noteId } }

    @Test
    fun statusGroupsFollowTheSavedOptionsThenOptionsOnlyRowsUseThenNoValue() {
        val rows = listOf(row("a", status = "Done"), row("b", status = "Blocked"), row("c"), row("d", status = "done"))

        val groups = boardGroups(rows, statusColumn, PropertyValueType.SINGLE_CHOICE, listOf("To do", "Done"), DatabaseDateGrouping.MONTH)

        assertEquals(
            listOf("to do" to emptyList(), "done" to listOf("a", "d"), "blocked" to listOf("b"), NO_VALUE_GROUP_KEY to listOf("c")),
            groups.summary()
        )
    }

    @Test
    fun aRowWithTwoTagsShowsInBothTagGroups() {
        val rows = listOf(row("a", tags = listOf("Design", "Urgent")), row("b", tags = listOf("Design")))

        val groups = boardGroups(rows, tagsColumn, PropertyValueType.TAGS, emptyList(), DatabaseDateGrouping.MONTH)

        assertEquals(
            listOf("design" to listOf("a", "b"), "urgent" to listOf("a"), NO_VALUE_GROUP_KEY to emptyList()),
            groups.summary()
        )
    }

    @Test
    fun checkboxGroupsAreUncheckedThenCheckedAndAMissingCellIsUnchecked() {
        val rows = listOf(row("a", checked = true), row("b", checked = false), row("c"))

        val groups = boardGroups(rows, checkboxColumn, PropertyValueType.CHECKBOX, emptyList(), DatabaseDateGrouping.MONTH)

        assertEquals(listOf("unchecked" to listOf("b", "c"), "checked" to listOf("a")), groups.summary())
    }

    @Test
    fun dateGroupsAreOnePerPeriodInDateOrderWithUndatedRowsLast() {
        val rows = listOf(
            row("october", dueDate = LocalDate(2026, 10, 5)),
            row("late-september", dueDate = LocalDate(2026, 9, 30)),
            row("early-september", dueDate = LocalDate(2026, 9, 1)),
            row("undated")
        )

        val groups = boardGroups(rows, dueDateColumn, PropertyValueType.DATE, emptyList(), DatabaseDateGrouping.MONTH)

        assertEquals(
            listOf(
                "2026-09-01" to listOf("late-september", "early-september"),
                "2026-10-01" to listOf("october"),
                NO_VALUE_GROUP_KEY to listOf("undated")
            ),
            groups.summary()
        )
    }

    @Test
    fun eachDateGroupingStartsAtTheBeginningOfItsPeriod() {
        val wednesday = LocalDate(2026, 9, 30)

        assertEquals(wednesday, periodStartOf(wednesday, DatabaseDateGrouping.DAY))
        assertEquals(LocalDate(2026, 9, 28), periodStartOf(wednesday, DatabaseDateGrouping.WEEK))
        assertEquals(LocalDate(2026, 9, 1), periodStartOf(wednesday, DatabaseDateGrouping.MONTH))
        assertEquals(LocalDate(2026, 1, 1), periodStartOf(wednesday, DatabaseDateGrouping.YEAR))
    }

    @Test
    fun hiddenGroupsAndEmptyGroupsCanBeLeftOffTheBoard() {
        val rows = listOf(row("a", status = "Done"))
        val groups = boardGroups(rows, statusColumn, PropertyValueType.SINGLE_CHOICE, listOf("To do", "Done"), DatabaseDateGrouping.MONTH)
        val view = board.copy(hiddenGroupKeys = listOf(NO_VALUE_GROUP_KEY), hidesEmptyGroups = true)

        assertEquals(listOf("done"), groups.shownIn(view).map { it.key })
        assertEquals(listOf(NO_VALUE_GROUP_KEY), groups.hiddenIn(view).map { it.key })
    }

    @Test
    fun movingAStatusCardReplacesItsStatusOrClearsIt() {
        val cell = PropertyBlock(id = "status-a", propertyType = PropertyType.STATUS, tags = listOf("To do"))

        val moved = cell.movedBetweenGroups(DatabaseBoardGroupValue.Option("To do"), DatabaseBoardGroupValue.Option("Done"))
        val cleared = cell.movedBetweenGroups(DatabaseBoardGroupValue.Option("To do"), DatabaseBoardGroupValue.NoValue)

        assertEquals(listOf("Done"), moved.tags)
        assertEquals(emptyList(), cleared.tags)
    }

    @Test
    fun movingATagsCardSwapsOnlyTheTagOfTheColumnItLeft() {
        val cell = PropertyBlock(id = "tags-a", propertyType = PropertyType.TAGS, tags = listOf("Design", "Urgent"))

        val moved = cell.movedBetweenGroups(DatabaseBoardGroupValue.Option("design"), DatabaseBoardGroupValue.Option("Later"))
        val removed = cell.movedBetweenGroups(DatabaseBoardGroupValue.Option("Urgent"), DatabaseBoardGroupValue.NoValue)
        val addedFromNoValue = PropertyBlock(id = "tags-b", propertyType = PropertyType.TAGS)
            .movedBetweenGroups(DatabaseBoardGroupValue.NoValue, DatabaseBoardGroupValue.Option("Design"))

        assertEquals(listOf("Urgent", "Later"), moved.tags)
        assertEquals(listOf("Design"), removed.tags)
        assertEquals(listOf("Design"), addedFromNoValue.tags)
    }

    @Test
    fun movingACheckboxCardChecksOrUnchecksIt() {
        val cell = PropertyBlock(id = "checkbox-a", propertyType = PropertyType.CHECKBOX)

        val checked = cell.movedBetweenGroups(DatabaseBoardGroupValue.Checkbox(false), DatabaseBoardGroupValue.Checkbox(true))

        assertEquals(true, checked.isChecked)
        assertEquals(false, checked.movedBetweenGroups(DatabaseBoardGroupValue.Checkbox(true), DatabaseBoardGroupValue.Checkbox(false)).isChecked)
    }

    @Test
    fun movingADateCardToAnotherPeriodSetsItsStartButKeepsADateAlreadyInThatPeriod() {
        val september = DatabaseBoardGroupValue.DatePeriod(LocalDate(2026, 9, 1), DatabaseDateGrouping.MONTH)
        val october = DatabaseBoardGroupValue.DatePeriod(LocalDate(2026, 10, 1), DatabaseDateGrouping.MONTH)
        val cell = PropertyBlock(id = "due_date-a", propertyType = PropertyType.DUE_DATE, date = LocalDate(2026, 9, 27))

        assertEquals(LocalDate(2026, 10, 1), cell.movedBetweenGroups(september, october).date)
        assertSame(cell, cell.movedBetweenGroups(DatabaseBoardGroupValue.NoValue, september))
        assertNull(cell.movedBetweenGroups(september, DatabaseBoardGroupValue.NoValue).date)
    }

    @Test
    fun aBoardGroupsByItsChosenColumnOrElseStatusOrElseTheFirstGroupableColumn() {
        val database = DatabaseBlock(id = "block", databaseId = "tasks", columns = listOf(nameColumn, tagsColumn, statusColumn))

        assertEquals(statusColumn, database.groupByColumn(board))
        assertEquals(tagsColumn, database.groupByColumn(board.copy(groupByColumnKey = PropertyType.TAGS.name)))
        assertEquals(tagsColumn, database.copy(columns = listOf(nameColumn, tagsColumn)).groupByColumn(board))
        assertNull(database.copy(columns = listOf(nameColumn)).groupByColumn(board))
    }

    @Test
    fun rowsInTheManualOrderComeFirstAndNewRowsKeepTheirPlaceAfterThem() {
        val rows = listOf(row("a"), row("b"), row("c"), row("d"))

        assertEquals(listOf("c", "a", "b", "d"), rows.inManualOrder(listOf("c", "gone", "a")).map { it.noteId })
        assertSame(rows, rows.inManualOrder(emptyList()))
    }

    @Test
    fun anItemMovesBeforeItsAnchorOrToTheEnd() {
        val names = listOf("To do", "Doing", "Done")

        assertEquals(listOf("Done", "To do", "Doing"), names.withItemMovedBefore("Done", "To do"))
        assertEquals(listOf("Doing", "Done", "To do"), names.withItemMovedBefore("To do", null))
        assertSame(names, names.withItemMovedBefore("Doing", "Doing"))
    }

    @Test
    fun droppingACardBeforeAnotherPlacesItThere() {
        val order = manualRowOrderAfterDrop(
            shownRowIds = listOf("a", "b", "c", "d"),
            previousManualOrder = emptyList(),
            draggedRowId = "d",
            beforeRowId = "b",
            lastOtherRowIdInTargetColumn = "c"
        )

        assertEquals(listOf("a", "d", "b", "c"), order)
    }

    @Test
    fun droppingACardAtTheEndOfAColumnPlacesItAfterThatColumnsLastCard() {
        val order = manualRowOrderAfterDrop(
            shownRowIds = listOf("a", "b", "c", "d"),
            previousManualOrder = emptyList(),
            draggedRowId = "a",
            beforeRowId = null,
            lastOtherRowIdInTargetColumn = "c"
        )

        assertEquals(listOf("b", "c", "a", "d"), order)
    }

    @Test
    fun droppingIntoAnEmptyColumnKeepsTheCardsPlace() {
        val order = manualRowOrderAfterDrop(
            shownRowIds = listOf("a", "b"),
            previousManualOrder = emptyList(),
            draggedRowId = "b",
            beforeRowId = null,
            lastOtherRowIdInTargetColumn = null
        )

        assertEquals(listOf("a", "b"), order)
    }

    @Test
    fun rowsHiddenByAFilterKeepTheirSavedPlaceAfterTheShownRows() {
        val order = manualRowOrderAfterDrop(
            shownRowIds = listOf("a", "b"),
            previousManualOrder = listOf("hidden", "a", "b"),
            draggedRowId = "b",
            beforeRowId = "a",
            lastOtherRowIdInTargetColumn = "a"
        )

        assertEquals(listOf("b", "a", "hidden"), order)
    }

    @Test
    fun groupingByAnotherColumnForgetsTheOldHiddenAndCollapsedGroups() {
        val database = DatabaseBlock(id = "block", databaseId = "tasks", columns = listOf(statusColumn, tagsColumn))
            .withViewAdded(board.copy(hiddenGroupKeys = listOf("done"), collapsedGroupKeys = listOf("to do")))

        val regrouped = database.withViewGroupedBy("board", PropertyType.TAGS.name).activeView()

        assertEquals(PropertyType.TAGS.name, regrouped.groupByColumnKey)
        assertEquals(emptyList(), regrouped.hiddenGroupKeys)
        assertEquals(emptyList(), regrouped.collapsedGroupKeys)
    }

    @Test
    fun placingANewRowAboveOrBelowAnotherPutsItNextToThatRow() {
        val shownRowIds = listOf("a", "b", "c")

        assertEquals(
            listOf("a", "new", "b", "c"),
            manualRowOrderWithRowPlaced(shownRowIds, emptyList(), rowId = "new", nextToRowId = "b", isAfter = false)
        )
        assertEquals(
            listOf("a", "b", "new", "c"),
            manualRowOrderWithRowPlaced(shownRowIds, emptyList(), rowId = "new", nextToRowId = "b", isAfter = true)
        )
    }

    @Test
    fun movingARowUpOrDownSwapsItWithItsNeighbour() {
        val shownRowIds = listOf("a", "b", "c")

        assertEquals(
            listOf("b", "a", "c"),
            manualRowOrderWithRowPlaced(shownRowIds, emptyList(), rowId = "b", nextToRowId = "a", isAfter = false)
        )
        assertEquals(
            listOf("a", "c", "b"),
            manualRowOrderWithRowPlaced(shownRowIds, emptyList(), rowId = "b", nextToRowId = "c", isAfter = true)
        )
    }

    @Test
    fun droppingACardNextToItselfOrBackInItsOwnGapDoesNotMoveIt() {
        val shownRowIds = listOf("a", "b", "c")

        assertFalse(dropMovesRow(shownRowIds, draggedRowId = "b", nextToRowId = "b", isAfter = false))
        assertFalse(dropMovesRow(shownRowIds, draggedRowId = "b", nextToRowId = "b", isAfter = true))
        assertFalse(dropMovesRow(shownRowIds, draggedRowId = "b", nextToRowId = "a", isAfter = true))
        assertFalse(dropMovesRow(shownRowIds, draggedRowId = "b", nextToRowId = "c", isAfter = false))
    }

    @Test
    fun droppingACardInAnotherGapMovesIt() {
        val shownRowIds = listOf("a", "b", "c")

        assertTrue(dropMovesRow(shownRowIds, draggedRowId = "b", nextToRowId = "a", isAfter = false))
        assertTrue(dropMovesRow(shownRowIds, draggedRowId = "b", nextToRowId = "c", isAfter = true))
        assertFalse(dropMovesRow(shownRowIds, draggedRowId = "b", nextToRowId = "missing", isAfter = true))
    }

    @Test
    fun eachCopyIsPlacedRightAfterItsOwnSourceRow() {
        val order = manualRowOrderWithCopiesPlaced(
            shownRowIds = listOf("a", "b", "c"),
            previousManualOrder = emptyList(),
            sourceAndCopyRowIds = listOf("a" to "a-copy", "c" to "c-copy", "hidden" to "hidden-copy")
        )

        assertEquals(listOf("a", "a-copy", "b", "c", "c-copy"), order)
    }

    @Test
    fun placingARowKeepsRowsHiddenByFiltersAfterTheShownOnes() {
        val order = manualRowOrderWithRowPlaced(
            shownRowIds = listOf("a", "c"),
            previousManualOrder = listOf("a", "hidden", "c"),
            rowId = "c",
            nextToRowId = "a",
            isAfter = false
        )

        assertEquals(listOf("c", "a", "hidden"), order)
    }

    @Test
    fun onlyChoiceTagsCheckboxAndDatePropertiesCanGroupABoard() {
        assertEquals(
            listOf(PropertyValueType.DATE, PropertyValueType.SINGLE_CHOICE, PropertyValueType.TAGS, PropertyValueType.CHECKBOX),
            PropertyValueType.entries.filter(::canGroupBy)
        )
    }
}
