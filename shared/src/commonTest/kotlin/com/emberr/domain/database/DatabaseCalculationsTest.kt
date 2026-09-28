package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCalculation
import com.emberr.domain.model.DatabaseCalculationGroup
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseCalculationsTest {

    private val nameColumn = DatabaseColumnTarget.Property(PropertyType.NAME)
    private val tagsColumn = DatabaseColumnTarget.Property(PropertyType.TAGS)
    private val dueDateColumn = DatabaseColumnTarget.Property(PropertyType.DUE_DATE)
    private val checkboxColumn = DatabaseColumnTarget.Property(PropertyType.CHECKBOX)
    private val numberColumn = DatabaseColumnTarget.Property(PropertyType.NUMBER)

    private fun row(
        noteId: String,
        title: String = "",
        name: String? = null,
        tags: List<String> = emptyList(),
        dueDate: LocalDate? = null,
        checked: Boolean? = null,
        number: String? = null
    ): DatabaseRow {
        val cells = listOfNotNull(
            name?.let { nameColumn to PropertyBlock(id = "name-$noteId", propertyType = PropertyType.NAME, text = it) },
            tagsColumn to PropertyBlock(id = "tags-$noteId", propertyType = PropertyType.TAGS, tags = tags),
            dueDate?.let { dueDateColumn to PropertyBlock(id = "due_date-$noteId", propertyType = PropertyType.DUE_DATE, date = it) },
            checked?.let { checkboxColumn to PropertyBlock(id = "checkbox-$noteId", propertyType = PropertyType.CHECKBOX, isChecked = it) },
            number?.let { numberColumn to PropertyBlock(id = "number-$noteId", propertyType = PropertyType.NUMBER, text = it) }
        )
        return DatabaseRow(noteId = noteId, title = title, createdAt = 0L, cellsByColumn = cells.toMap())
    }

    private fun count(value: Int) = DatabaseCalculationResult.Count(value)

    private fun calculate(rows: List<DatabaseRow>, column: DatabaseColumnTarget, calculation: DatabaseCalculation) =
        calculate(rows, column, valueTypeOf(column), calculation)

    private fun valueTypeOf(column: DatabaseColumnTarget): PropertyValueType =
        (column as? DatabaseColumnTarget.Property)?.propertyType?.valueType ?: PropertyValueType.TEXT

    @Test
    fun countsLookAtWhetherEachRowHasAValue() {
        val rows = listOf(row("a", name = "Ada"), row("b", name = "  "), row("c"), row("d", name = "Alan"))

        assertEquals(count(4), calculate(rows, nameColumn, DatabaseCalculation.COUNT_ALL))
        assertEquals(count(2), calculate(rows, nameColumn, DatabaseCalculation.COUNT_EMPTY))
        assertEquals(count(2), calculate(rows, nameColumn, DatabaseCalculation.COUNT_NOT_EMPTY))
    }

    @Test
    fun countValuesCountsEveryTagAndUniqueValuesIgnoresCase() {
        val rows = listOf(row("a", tags = listOf("Design", "Urgent")), row("b", tags = listOf("design")), row("c"))

        assertEquals(count(3), calculate(rows, tagsColumn, DatabaseCalculation.COUNT_VALUES))
        assertEquals(count(2), calculate(rows, tagsColumn, DatabaseCalculation.COUNT_UNIQUE_VALUES))
    }

    @Test
    fun theNotesColumnCountsRowTitles() {
        val rows = listOf(row("a", title = "Dune"), row("b", title = "Dune"), row("c", title = "Emma"), row("d"))

        assertEquals(count(3), calculate(rows, DatabaseColumnTarget.NotesTitle, DatabaseCalculation.COUNT_VALUES))
        assertEquals(count(2), calculate(rows, DatabaseColumnTarget.NotesTitle, DatabaseCalculation.COUNT_UNIQUE_VALUES))
    }

    @Test
    fun percentagesAreKeptToOneDecimalAndAreZeroWithoutRows() {
        val rows = listOf(row("a", name = "Ada"), row("b"), row("c"))

        assertEquals(DatabaseCalculationResult.Percent(667), calculate(rows, nameColumn, DatabaseCalculation.PERCENT_EMPTY))
        assertEquals(DatabaseCalculationResult.Percent(333), calculate(rows, nameColumn, DatabaseCalculation.PERCENT_NOT_EMPTY))
        assertEquals(DatabaseCalculationResult.Percent(0), calculate(emptyList(), nameColumn, DatabaseCalculation.PERCENT_EMPTY))
    }

    @Test
    fun checkboxCalculationsTreatAMissingCellAsUnchecked() {
        val rows = listOf(row("a", checked = true), row("b", checked = false), row("c"), row("d", checked = true))

        assertEquals(count(2), calculate(rows, checkboxColumn, DatabaseCalculation.CHECKED))
        assertEquals(count(2), calculate(rows, checkboxColumn, DatabaseCalculation.UNCHECKED))
        assertEquals(DatabaseCalculationResult.Percent(500), calculate(rows, checkboxColumn, DatabaseCalculation.PERCENT_CHECKED))
    }

    @Test
    fun dateCalculationsFindTheEarliestLatestMiddleAndTheDaysBetween() {
        val rows = listOf(
            row("a", dueDate = LocalDate(2026, 10, 5)),
            row("b", dueDate = LocalDate(2026, 9, 27)),
            row("c", dueDate = LocalDate(2026, 9, 30)),
            row("d")
        )

        assertEquals(DatabaseCalculationResult.DateValue(LocalDate(2026, 9, 27)), calculate(rows, dueDateColumn, DatabaseCalculation.MIN))
        assertEquals(DatabaseCalculationResult.DateValue(LocalDate(2026, 10, 5)), calculate(rows, dueDateColumn, DatabaseCalculation.MAX))
        assertEquals(DatabaseCalculationResult.DateValue(LocalDate(2026, 9, 30)), calculate(rows, dueDateColumn, DatabaseCalculation.MEDIAN))
        assertEquals(DatabaseCalculationResult.DayCount(8), calculate(rows, dueDateColumn, DatabaseCalculation.RANGE))
        assertEquals(DatabaseCalculationResult.DayCount(null), calculate(listOf(row("d")), dueDateColumn, DatabaseCalculation.RANGE))
        assertEquals(DatabaseCalculationResult.DateValue(null), calculate(listOf(row("d")), dueDateColumn, DatabaseCalculation.MIN))
    }

    @Test
    fun numberCalculationsSkipEmptyCellsAndCellsThatAreNotNumbers() {
        val rows = listOf(row("a", number = "4"), row("b", number = "10.5"), row("c", number = "-2"), row("d", number = ""), row("e"), row("f", number = "abc"))

        fun numberResult(calculation: DatabaseCalculation) = (calculate(rows, numberColumn, calculation) as DatabaseCalculationResult.NumberValue).number

        assertEquals(12.5, numberResult(DatabaseCalculation.SUM))
        assertEquals(12.5 / 3, numberResult(DatabaseCalculation.AVERAGE))
        assertEquals(4.0, numberResult(DatabaseCalculation.MEDIAN))
        assertEquals(-2.0, numberResult(DatabaseCalculation.MIN))
        assertEquals(10.5, numberResult(DatabaseCalculation.MAX))
        assertEquals(12.5, numberResult(DatabaseCalculation.RANGE))
    }

    @Test
    fun theMedianOfAnEvenCountIsTheAverageOfTheMiddleTwo() {
        val rows = listOf(row("a", number = "1"), row("b", number = "3"), row("c", number = "8"), row("d", number = "10"))

        assertEquals(DatabaseCalculationResult.NumberValue(5.5), calculate(rows, numberColumn, DatabaseCalculation.MEDIAN))
    }

    @Test
    fun numberCalculationsWithoutAnyNumberHaveNoResult() {
        assertEquals(DatabaseCalculationResult.NumberValue(null), calculate(listOf(row("a")), numberColumn, DatabaseCalculation.SUM))
    }

    @Test
    fun calculatedNumbersShowAtMostTwoDecimalsAndNoScientificNotation() {
        assertEquals("12", formatCalculatedNumber(12.0))
        assertEquals("12.5", formatCalculatedNumber(12.5))
        assertEquals("4.17", formatCalculatedNumber(12.5 / 3))
        assertEquals("-0.25", formatCalculatedNumber(-0.25))
        assertEquals("10000000", formatCalculatedNumber(10_000_000.0))
    }

    @Test
    fun eachKindOfColumnOffersItsOwnCalculations() {
        assertEquals(setOf(DatabaseCalculationGroup.COUNT, DatabaseCalculationGroup.PERCENT), calculationsFor(PropertyValueType.TEXT).map { it.group }.toSet())
        assertEquals(
            listOf(DatabaseCalculation.MEDIAN, DatabaseCalculation.MIN, DatabaseCalculation.MAX, DatabaseCalculation.RANGE),
            calculationsFor(PropertyValueType.DATE).filter { it.group == DatabaseCalculationGroup.MORE }
        )
        assertEquals(
            listOf(
                DatabaseCalculation.SUM,
                DatabaseCalculation.AVERAGE,
                DatabaseCalculation.MEDIAN,
                DatabaseCalculation.MIN,
                DatabaseCalculation.MAX,
                DatabaseCalculation.RANGE
            ),
            calculationsFor(PropertyValueType.NUMBER).filter { it.group == DatabaseCalculationGroup.MORE }
        )
        assertEquals(
            listOf(
                DatabaseCalculation.COUNT_ALL,
                DatabaseCalculation.CHECKED,
                DatabaseCalculation.UNCHECKED,
                DatabaseCalculation.PERCENT_CHECKED,
                DatabaseCalculation.PERCENT_UNCHECKED
            ),
            calculationsFor(PropertyValueType.CHECKBOX)
        )
    }

    @Test
    fun aSavedCalculationThatDoesNotFitTheColumnIsIgnored() {
        val database = DatabaseBlock(
            id = "block",
            databaseId = "tasks",
            columns = listOf(nameColumn, dueDateColumn),
            calculations = mapOf(PropertyType.NAME.name to DatabaseCalculation.SUM, PropertyType.DUE_DATE.name to DatabaseCalculation.MAX)
        )

        assertNull(database.calculationOf(nameColumn))
        assertEquals(DatabaseCalculation.MAX, database.calculationOf(dueDateColumn))
    }
}
