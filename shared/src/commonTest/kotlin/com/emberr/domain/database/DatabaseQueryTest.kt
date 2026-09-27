package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseSort
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

    private fun query(rows: List<DatabaseRow>, filters: List<DatabaseFilter>, sort: DatabaseSort?): List<DatabaseRow> =
        applyFiltersAndSort(
            rows,
            DatabaseBlock(
                id = "block-1",
                databaseId = "database-1",
                columns = listOf(nameColumn, statusColumn, tagsColumn, dueDateColumn, clientColumn, kickoffColumn),
                customProperties = listOf(
                    DatabaseCustomProperty(id = "client-id", name = "Client", valueType = PropertyValueType.SINGLE_CHOICE),
                    DatabaseCustomProperty(id = "kickoff-id", name = "Kickoff", valueType = PropertyValueType.DATE)
                ),
                filters = filters,
                sort = sort
            )
        )

    private fun filter(
        target: DatabaseColumnTarget,
        condition: DatabaseFilterCondition,
        text: String = "",
        date: LocalDate? = null,
        tagName: String? = null
    ) = DatabaseFilter(id = "filter", target = target, condition = condition, text = text, date = date, tagName = tagName)

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
