package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DEFAULT_VIEW_ID
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseDateRange
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseRelativeDate
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseQueryTest {

    private val nameColumn = DatabaseColumnTarget.Property(PropertyType.NAME)
    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val tagsColumn = DatabaseColumnTarget.Property(PropertyType.TAGS)
    private val dueDateColumn = DatabaseColumnTarget.Property(PropertyType.DUE_DATE)
    private val checkboxColumn = DatabaseColumnTarget.Property(PropertyType.CHECKBOX)
    private val numberColumn = DatabaseColumnTarget.Property(PropertyType.NUMBER)

    private val clientColumn = DatabaseColumnTarget.CustomProperty("client-id")
    private val kickoffColumn = DatabaseColumnTarget.CustomProperty("kickoff-id")

    private fun row(
        noteId: String,
        title: String = "",
        createdAt: Long = 0L,
        name: String? = null,
        status: String? = null,
        tags: List<String> = emptyList(),
        dueDate: LocalDate? = null,
        checked: Boolean? = null,
        number: String? = null,
        client: String? = null,
        kickoff: LocalDate? = null
    ): DatabaseRow {
        val cells = listOfNotNull(
            name?.let { nameColumn to PropertyBlock(id = "name-$noteId", propertyType = PropertyType.NAME, text = it) },
            status?.let { statusColumn to PropertyBlock(id = "status-$noteId", propertyType = PropertyType.STATUS, tags = listOf(it)) },
            tags.takeIf { it.isNotEmpty() }?.let {
                tagsColumn to PropertyBlock(id = "tags-$noteId", propertyType = PropertyType.TAGS, tags = it)
            },
            dueDate?.let { dueDateColumn to PropertyBlock(id = "due_date-$noteId", propertyType = PropertyType.DUE_DATE, date = it) },
            checked?.let {
                checkboxColumn to PropertyBlock(id = "checkbox-$noteId", propertyType = PropertyType.CHECKBOX, isChecked = it)
            },
            number?.let { numberColumn to PropertyBlock(id = "number-$noteId", propertyType = PropertyType.NUMBER, text = it) },
            client?.let {
                clientColumn to PropertyBlock(
                    id = "client-id-$noteId",
                    customPropertyId = "client-id",
                    customLabel = "Client",
                    customValueType = PropertyValueType.SINGLE_CHOICE,
                    tags = listOf(it)
                )
            },
            kickoff?.let {
                kickoffColumn to PropertyBlock(
                    id = "kickoff-id-$noteId",
                    customPropertyId = "kickoff-id",
                    customLabel = "Kickoff",
                    customValueType = PropertyValueType.DATE,
                    date = it
                )
            }
        )
        return DatabaseRow(noteId = noteId, title = title, createdAt = createdAt, cellsByColumn = cells.toMap())
    }

    private val wednesday = LocalDate(2026, 9, 30)

    private fun databaseShowing(filters: List<DatabaseFilter>, sorts: List<DatabaseSort>) = DatabaseBlock(
        id = "block-1",
        databaseId = "database-1",
        columns = listOf(nameColumn, statusColumn, tagsColumn, dueDateColumn, checkboxColumn, numberColumn, clientColumn, kickoffColumn),
        customProperties = listOf(
            DatabaseCustomProperty(id = "client-id", name = "Client", valueType = PropertyValueType.SINGLE_CHOICE),
            DatabaseCustomProperty(id = "kickoff-id", name = "Kickoff", valueType = PropertyValueType.DATE)
        ),
        views = listOf(DatabaseView(id = DEFAULT_VIEW_ID, name = "Table", type = DatabaseViewType.TABLE, filters = filters, sorts = sorts))
    )

    private fun query(rows: List<DatabaseRow>, filters: List<DatabaseFilter>, sort: DatabaseSort?): List<DatabaseRow> =
        query(rows, filters, listOfNotNull(sort))

    private fun query(rows: List<DatabaseRow>, filters: List<DatabaseFilter>, sorts: List<DatabaseSort>): List<DatabaseRow> =
        applyFiltersAndSort(rows, databaseShowing(filters, sorts), today = wednesday)

    private fun filter(
        target: DatabaseColumnTarget,
        condition: DatabaseFilterCondition,
        text: String = "",
        date: LocalDate? = null,
        relativeDate: DatabaseRelativeDate? = null,
        dateRange: DatabaseDateRange? = null,
        tagName: String? = null
    ) = DatabaseFilter(
        id = "filter",
        target = target,
        condition = condition,
        text = text,
        date = date,
        relativeDate = relativeDate,
        dateRange = dateRange,
        tagName = tagName
    )

    private fun noteIdsOf(rows: List<DatabaseRow>) = rows.map { it.noteId }

    @Test
    fun withoutFiltersOrSortRowsKeepTheOrderTheyWereCreatedIn() {
        val rows = listOf(row("c", createdAt = 30L), row("a", createdAt = 10L), row("b", createdAt = 20L))

        assertEquals(listOf("a", "b", "c"), noteIdsOf(query(rows, emptyList(), null)))
    }

    @Test
    fun aTextFilterMatchesPartOfTheValueIgnoringCase() {
        val rows = listOf(row("ada", name = "Ada Lovelace"), row("alan", name = "Alan Turing"))

        val result = query(rows, listOf(filter(nameColumn, DatabaseFilterCondition.CONTAINS, text = "LOVE")), null)

        assertEquals(listOf("ada"), noteIdsOf(result))
    }

    @Test
    fun doesNotContainAlsoKeepsRowsWithNoValue() {
        val rows = listOf(row("ada", name = "Ada Lovelace"), row("alan", name = "Alan Turing"), row("empty"))

        val result = query(rows, listOf(filter(nameColumn, DatabaseFilterCondition.DOES_NOT_CONTAIN, text = "ada")), null)

        assertEquals(listOf("alan", "empty"), noteIdsOf(result))
    }

    @Test
    fun aNotesFilterLooksAtTheRowTitle() {
        val rows = listOf(row("dune", title = "Dune"), row("emma", title = "Emma"))

        val result = query(
            rows,
            listOf(filter(DatabaseColumnTarget.NotesTitle, DatabaseFilterCondition.CONTAINS, text = "dun")),
            null
        )

        assertEquals(listOf("dune"), noteIdsOf(result))
    }

    @Test
    fun isEmptyAndIsNotEmptyCheckWhetherACellHasAValue() {
        val rows = listOf(
            row("filled", name = "Ada"),
            row("blank", name = "   "),
            row("missing")
        )

        val emptyRows = query(rows, listOf(filter(nameColumn, DatabaseFilterCondition.IS_EMPTY)), null)
        val filledRows = query(rows, listOf(filter(nameColumn, DatabaseFilterCondition.IS_NOT_EMPTY)), null)

        assertEquals(listOf("blank", "missing"), noteIdsOf(emptyRows))
        assertEquals(listOf("filled"), noteIdsOf(filledRows))
    }

    @Test
    fun dateFiltersCompareWholeDaysAndSkipRowsWithoutADate() {
        val rows = listOf(
            row("early", dueDate = LocalDate(2026, 9, 1)),
            row("same", dueDate = LocalDate(2026, 9, 27)),
            row("late", dueDate = LocalDate(2026, 10, 5)),
            row("none")
        )
        val day = LocalDate(2026, 9, 27)

        fun matching(condition: DatabaseFilterCondition) =
            noteIdsOf(query(rows, listOf(filter(dueDateColumn, condition, date = day)), null))

        assertEquals(listOf("same"), matching(DatabaseFilterCondition.IS))
        assertEquals(listOf("early"), matching(DatabaseFilterCondition.IS_BEFORE))
        assertEquals(listOf("late"), matching(DatabaseFilterCondition.IS_AFTER))
        assertEquals(listOf("early", "same"), matching(DatabaseFilterCondition.IS_ON_OR_BEFORE))
        assertEquals(listOf("same", "late"), matching(DatabaseFilterCondition.IS_ON_OR_AFTER))
    }

    @Test
    fun aRelativeDateIsCountedFromToday() {
        val rows = listOf(
            row("yesterday", dueDate = LocalDate(2026, 9, 29)),
            row("today", dueDate = wednesday),
            row("tomorrow", dueDate = LocalDate(2026, 10, 1)),
            row("next-week", dueDate = LocalDate(2026, 10, 7)),
            row("last-month", dueDate = LocalDate(2026, 8, 30)),
            row("none")
        )

        fun matching(condition: DatabaseFilterCondition, relativeDate: DatabaseRelativeDate) =
            noteIdsOf(query(rows, listOf(filter(dueDateColumn, condition, relativeDate = relativeDate)), null))

        assertEquals(listOf("today"), matching(DatabaseFilterCondition.IS, DatabaseRelativeDate.TODAY))
        assertEquals(listOf("tomorrow"), matching(DatabaseFilterCondition.IS, DatabaseRelativeDate.TOMORROW))
        assertEquals(listOf("yesterday"), matching(DatabaseFilterCondition.IS, DatabaseRelativeDate.YESTERDAY))
        assertEquals(listOf("next-week"), matching(DatabaseFilterCondition.IS, DatabaseRelativeDate.ONE_WEEK_FROM_NOW))
        assertEquals(listOf("last-month"), matching(DatabaseFilterCondition.IS, DatabaseRelativeDate.ONE_MONTH_AGO))
        assertEquals(listOf("yesterday", "last-month"), matching(DatabaseFilterCondition.IS_BEFORE, DatabaseRelativeDate.TODAY))
        assertEquals(listOf("today", "tomorrow", "next-week"), matching(DatabaseFilterCondition.IS_ON_OR_AFTER, DatabaseRelativeDate.TODAY))
    }

    @Test
    fun aRelativeDateWinsOverAnExactDateLeftOnTheFilter() {
        val rows = listOf(row("today", dueDate = wednesday), row("old", dueDate = LocalDate(2020, 1, 1)))
        val relativeFilter = filter(
            dueDateColumn,
            DatabaseFilterCondition.IS,
            date = LocalDate(2020, 1, 1),
            relativeDate = DatabaseRelativeDate.TODAY
        )

        assertEquals(listOf("today"), noteIdsOf(query(rows, listOf(relativeFilter), null)))
    }

    @Test
    fun isWithinMatchesDatesInsideTheChosenRangeIncludingItsEnds() {
        val rows = listOf(
            row("monday", dueDate = LocalDate(2026, 9, 28)),
            row("sunday", dueDate = LocalDate(2026, 10, 4)),
            row("week-ago", dueDate = LocalDate(2026, 9, 23)),
            row("in-a-week", dueDate = LocalDate(2026, 10, 7)),
            row("month-start", dueDate = LocalDate(2026, 9, 1)),
            row("new-year", dueDate = LocalDate(2026, 1, 1)),
            row("next-year", dueDate = LocalDate(2027, 1, 1)),
            row("none")
        )

        fun within(dateRange: DatabaseDateRange) =
            noteIdsOf(query(rows, listOf(filter(dueDateColumn, DatabaseFilterCondition.IS_WITHIN, dateRange = dateRange)), null))

        assertEquals(listOf("monday", "sunday"), within(DatabaseDateRange.THIS_WEEK))
        assertEquals(listOf("monday", "week-ago", "month-start"), within(DatabaseDateRange.THIS_MONTH))
        assertEquals(listOf("monday", "sunday", "week-ago", "in-a-week", "month-start", "new-year"), within(DatabaseDateRange.THIS_YEAR))
        assertEquals(listOf("monday", "week-ago"), within(DatabaseDateRange.PAST_7_DAYS))
        assertEquals(listOf("sunday", "in-a-week"), within(DatabaseDateRange.NEXT_7_DAYS))
        assertEquals(listOf("monday", "week-ago", "month-start"), within(DatabaseDateRange.PAST_30_DAYS))
        assertEquals(listOf("sunday", "in-a-week"), within(DatabaseDateRange.NEXT_30_DAYS))
    }

    @Test
    fun isWithinWithoutARangeYetIsIgnored() {
        val rows = listOf(row("dated", dueDate = wednesday), row("none"))
        val unfinishedFilter = filter(dueDateColumn, DatabaseFilterCondition.IS_WITHIN, date = wednesday)

        assertEquals(listOf("dated", "none"), noteIdsOf(query(rows, listOf(unfinishedFilter), null)))
    }

    @Test
    fun statusIsAndIsNotCompareTheChosenOptionIgnoringCase() {
        val rows = listOf(row("reading", status = "Reading"), row("done", status = "Done"), row("none"))

        val isReading = query(rows, listOf(filter(statusColumn, DatabaseFilterCondition.IS, tagName = "reading")), null)
        val isNotReading = query(rows, listOf(filter(statusColumn, DatabaseFilterCondition.IS_NOT, tagName = "reading")), null)

        assertEquals(listOf("reading"), noteIdsOf(isReading))
        assertEquals(listOf("done", "none"), noteIdsOf(isNotReading))
    }

    @Test
    fun tagsContainsMatchesAnyOfTheRowsTags() {
        val rows = listOf(
            row("both", tags = listOf("Design", "Urgent")),
            row("design", tags = listOf("design")),
            row("other", tags = listOf("Later"))
        )

        val result = query(rows, listOf(filter(tagsColumn, DatabaseFilterCondition.CONTAINS, tagName = "DESIGN")), null)

        assertEquals(listOf("both", "design"), noteIdsOf(result))
    }

    @Test
    fun aRowMustMatchEveryFilter() {
        val rows = listOf(
            row("match", name = "Ada", status = "Reading"),
            row("wrong-status", name = "Ada", status = "Done"),
            row("wrong-name", name = "Alan", status = "Reading")
        )
        val filters = listOf(
            filter(nameColumn, DatabaseFilterCondition.CONTAINS, text = "ada"),
            filter(statusColumn, DatabaseFilterCondition.IS, tagName = "Reading")
        )

        assertEquals(listOf("match"), noteIdsOf(query(rows, filters, null)))
    }

    @Test
    fun aFilterWithoutAValueYetIsIgnored() {
        val rows = listOf(row("ada", name = "Ada"), row("none"))
        val unfinishedFilters = listOf(
            filter(nameColumn, DatabaseFilterCondition.CONTAINS, text = "  "),
            filter(statusColumn, DatabaseFilterCondition.IS, tagName = null),
            filter(dueDateColumn, DatabaseFilterCondition.IS_BEFORE, date = null)
        )

        assertEquals(listOf("ada", "none"), noteIdsOf(query(rows, unfinishedFilters, null)))
    }

    @Test
    fun aConditionThatDoesNotFitTheColumnIsIgnored() {
        val rows = listOf(row("ada", name = "Ada"), row("none"))
        val mismatchedFilter = filter(nameColumn, DatabaseFilterCondition.IS_BEFORE, date = LocalDate(2026, 1, 1))

        assertEquals(listOf("ada", "none"), noteIdsOf(query(rows, listOf(mismatchedFilter), null)))
    }

    @Test
    fun sortingByTextIgnoresCaseAndCanBeReversed() {
        val rows = listOf(row("b", title = "banana"), row("a", title = "Apple"), row("c", title = "cherry"))

        val ascending = query(rows, emptyList(), DatabaseSort(DatabaseColumnTarget.NotesTitle))
        val descending = query(rows, emptyList(), DatabaseSort(DatabaseColumnTarget.NotesTitle, isDescending = true))

        assertEquals(listOf("a", "b", "c"), noteIdsOf(ascending))
        assertEquals(listOf("c", "b", "a"), noteIdsOf(descending))
    }

    @Test
    fun sortingByDateIsChronological() {
        val rows = listOf(
            row("october", dueDate = LocalDate(2026, 10, 1)),
            row("january", dueDate = LocalDate(2027, 1, 1)),
            row("september", dueDate = LocalDate(2026, 9, 30))
        )

        val result = query(rows, emptyList(), DatabaseSort(dueDateColumn))

        assertEquals(listOf("september", "october", "january"), noteIdsOf(result))
    }

    @Test
    fun emptyValuesStayLastInBothDirections() {
        val rows = listOf(row("empty", createdAt = 1L), row("b", name = "B", createdAt = 2L), row("a", name = "A", createdAt = 3L))

        val ascending = query(rows, emptyList(), DatabaseSort(nameColumn))
        val descending = query(rows, emptyList(), DatabaseSort(nameColumn, isDescending = true))

        assertEquals(listOf("a", "b", "empty"), noteIdsOf(ascending))
        assertEquals(listOf("b", "a", "empty"), noteIdsOf(descending))
    }

    @Test
    fun rowsWithTheSameValueKeepTheirCreationOrder() {
        val rows = listOf(
            row("second", status = "Done", createdAt = 20L),
            row("first", status = "Done", createdAt = 10L),
            row("third", status = "Done", createdAt = 30L)
        )

        val result = query(rows, emptyList(), DatabaseSort(statusColumn, isDescending = true))

        assertEquals(listOf("first", "second", "third"), noteIdsOf(result))
    }

    @Test
    fun checkboxFiltersSplitRowsAndTreatAMissingCellAsUnchecked() {
        val rows = listOf(row("done", checked = true), row("open", checked = false), row("missing"))

        val checkedRows = query(rows, listOf(filter(checkboxColumn, DatabaseFilterCondition.IS_CHECKED)), null)
        val uncheckedRows = query(rows, listOf(filter(checkboxColumn, DatabaseFilterCondition.IS_UNCHECKED)), null)

        assertEquals(listOf("done"), noteIdsOf(checkedRows))
        assertEquals(listOf("missing", "open"), noteIdsOf(uncheckedRows))
    }

    @Test
    fun sortingByCheckboxPutsUncheckedFirstAndCanBeReversed() {
        val rows = listOf(
            row("done", checked = true, createdAt = 1L),
            row("open", checked = false, createdAt = 2L),
            row("missing", createdAt = 3L)
        )

        val ascending = query(rows, emptyList(), DatabaseSort(checkboxColumn))
        val descending = query(rows, emptyList(), DatabaseSort(checkboxColumn, isDescending = true))

        assertEquals(listOf("open", "missing", "done"), noteIdsOf(ascending))
        assertEquals(listOf("done", "open", "missing"), noteIdsOf(descending))
    }

    @Test
    fun aCheckboxOffersOnlyCheckedAndUncheckedConditions() {
        assertEquals(
            listOf(DatabaseFilterCondition.IS_CHECKED, DatabaseFilterCondition.IS_UNCHECKED),
            filterConditionsFor(PropertyValueType.CHECKBOX)
        )
        assertEquals(false, DatabaseFilterCondition.IS_CHECKED.needsValue)
        assertEquals(false, DatabaseFilterCondition.IS_UNCHECKED.needsValue)
    }

    @Test
    fun numberFiltersCompareTheValueAsANumber() {
        val rows = listOf(row("nine", number = "9"), row("ten", number = "10.0"), row("eleven", number = "11"), row("none"))

        fun matching(condition: DatabaseFilterCondition) =
            noteIdsOf(query(rows, listOf(filter(numberColumn, condition, text = "10")), null))

        assertEquals(listOf("ten"), matching(DatabaseFilterCondition.IS))
        assertEquals(listOf("eleven", "nine", "none"), matching(DatabaseFilterCondition.IS_NOT))
        assertEquals(listOf("eleven"), matching(DatabaseFilterCondition.IS_GREATER_THAN))
        assertEquals(listOf("nine"), matching(DatabaseFilterCondition.IS_LESS_THAN))
    }

    @Test
    fun aNumberFilterWhoseValueIsNotANumberIsIgnored() {
        val rows = listOf(row("nine", number = "9"), row("none"))

        val result = query(rows, listOf(filter(numberColumn, DatabaseFilterCondition.IS_GREATER_THAN, text = "abc")), null)

        assertEquals(listOf("nine", "none"), noteIdsOf(result))
    }

    @Test
    fun sortingByNumberIsNumericNotAlphabetical() {
        val rows = listOf(row("ten", number = "10"), row("nine", number = "9"), row("hundred", number = "100"), row("none"))

        val ascending = query(rows, emptyList(), DatabaseSort(numberColumn))

        assertEquals(listOf("nine", "ten", "hundred", "none"), noteIdsOf(ascending))
    }

    @Test
    fun aCustomSingleChoicePropertyFiltersLikeStatus() {
        val rows = listOf(row("acme", client = "Acme"), row("globex", client = "Globex"), row("none"))

        val result = query(rows, listOf(filter(clientColumn, DatabaseFilterCondition.IS, tagName = "acme")), null)

        assertEquals(listOf("acme"), noteIdsOf(result))
    }

    @Test
    fun aCustomDatePropertySortsChronologically() {
        val rows = listOf(
            row("late", kickoff = LocalDate(2026, 12, 1)),
            row("early", kickoff = LocalDate(2026, 1, 1)),
            row("none")
        )

        val result = query(rows, emptyList(), DatabaseSort(kickoffColumn))

        assertEquals(listOf("early", "late", "none"), noteIdsOf(result))
    }

    @Test
    fun aSecondSortOrdersRowsThatTieOnTheFirstSort() {
        val rows = listOf(
            row("done-late", status = "Done", dueDate = LocalDate(2026, 10, 9)),
            row("todo-early", status = "Todo", dueDate = LocalDate(2026, 10, 1)),
            row("done-early", status = "Done", dueDate = LocalDate(2026, 10, 2)),
            row("todo-late", status = "Todo", dueDate = LocalDate(2026, 10, 8))
        )

        val result = query(rows, emptyList(), listOf(DatabaseSort(statusColumn), DatabaseSort(dueDateColumn, isDescending = true)))

        assertEquals(listOf("done-late", "done-early", "todo-late", "todo-early"), noteIdsOf(result))
    }

    @Test
    fun aSortOnADeletedCustomPropertyIsSkippedAndTheNextSortStillApplies() {
        val rows = listOf(row("b", title = "Beta"), row("a", title = "Alpha"))
        val sorts = listOf(DatabaseSort(DatabaseColumnTarget.CustomProperty("gone-id")), DatabaseSort(DatabaseColumnTarget.NotesTitle))

        assertEquals(listOf("a", "b"), noteIdsOf(query(rows, emptyList(), sorts)))
    }

    @Test
    fun onlyTheFiltersAndSortsOfTheActiveViewAreUsed() {
        val rows = listOf(row("ada", title = "Ada", status = "Done"), row("bob", title = "Bob", status = "Todo"))
        val table = DatabaseView(
            id = "table",
            name = "Table",
            type = DatabaseViewType.TABLE,
            filters = listOf(filter(statusColumn, DatabaseFilterCondition.IS, tagName = "Todo"))
        )
        val board = DatabaseView(
            id = "board",
            name = "Board",
            type = DatabaseViewType.BOARD,
            sorts = listOf(DatabaseSort(DatabaseColumnTarget.NotesTitle, isDescending = true))
        )
        val database = databaseShowing(emptyList(), emptyList()).copy(views = listOf(table, board))

        assertEquals(listOf("bob"), noteIdsOf(applyFiltersAndSort(rows, database.copy(activeViewId = "table"))))
        assertEquals(listOf("bob", "ada"), noteIdsOf(applyFiltersAndSort(rows, database.copy(activeViewId = "board"))))
    }

    @Test
    fun searchMatchesTheTitleOrAnyColumnIgnoringCase() {
        val rows = listOf(
            row("by-title", title = "Dune"),
            row("by-name", name = "Frank Herbert"),
            row("by-status", status = "Reading"),
            row("by-date", dueDate = LocalDate(2026, 10, 2)),
            row("no-match", title = "Emma")
        )
        val database = databaseShowing(emptyList(), emptyList())

        assertEquals(listOf("by-title"), noteIdsOf(rows.matchingSearch("DUNE", database)))
        assertEquals(listOf("by-name"), noteIdsOf(rows.matchingSearch("herb", database)))
        assertEquals(listOf("by-status"), noteIdsOf(rows.matchingSearch("read", database)))
        assertEquals(listOf("by-date"), noteIdsOf(rows.matchingSearch("2026-10", database)))
        assertEquals(noteIdsOf(rows), noteIdsOf(rows.matchingSearch("   ", database)))
    }

    @Test
    fun aFilterOnADeletedCustomPropertyIsIgnored() {
        val rows = listOf(row("acme", client = "Acme"), row("none"))
        val orphanFilter = filter(DatabaseColumnTarget.CustomProperty("gone-id"), DatabaseFilterCondition.IS_NOT_EMPTY)

        assertEquals(listOf("acme", "none"), noteIdsOf(query(rows, listOf(orphanFilter), null)))
    }

    @Test
    fun eachKindOfValueOffersItsOwnFilterConditions() {
        val textConditions = listOf(
            DatabaseFilterCondition.CONTAINS,
            DatabaseFilterCondition.DOES_NOT_CONTAIN,
            DatabaseFilterCondition.IS_EMPTY,
            DatabaseFilterCondition.IS_NOT_EMPTY
        )

        assertEquals(textConditions, filterConditionsFor(PropertyValueType.TEXT))
        assertEquals(textConditions, filterConditionsFor(PropertyValueType.EMAIL))
        assertEquals(textConditions, filterConditionsFor(PropertyValueType.TAGS))
        assertEquals(
            listOf(
                DatabaseFilterCondition.IS,
                DatabaseFilterCondition.IS_BEFORE,
                DatabaseFilterCondition.IS_AFTER,
                DatabaseFilterCondition.IS_ON_OR_BEFORE,
                DatabaseFilterCondition.IS_ON_OR_AFTER,
                DatabaseFilterCondition.IS_WITHIN,
                DatabaseFilterCondition.IS_EMPTY,
                DatabaseFilterCondition.IS_NOT_EMPTY
            ),
            filterConditionsFor(PropertyValueType.DATE)
        )
        assertEquals(
            listOf(
                DatabaseFilterCondition.IS,
                DatabaseFilterCondition.IS_NOT,
                DatabaseFilterCondition.IS_EMPTY,
                DatabaseFilterCondition.IS_NOT_EMPTY
            ),
            filterConditionsFor(PropertyValueType.SINGLE_CHOICE)
        )
    }
}
