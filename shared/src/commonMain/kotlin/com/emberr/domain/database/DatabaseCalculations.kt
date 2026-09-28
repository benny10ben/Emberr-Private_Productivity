package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCalculation
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.valueTypeOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

sealed class DatabaseCalculationResult {
    data class Count(val value: Int) : DatabaseCalculationResult()
    data class Percent(val tenthsOfAPercent: Int) : DatabaseCalculationResult()
    data class NumberValue(val number: Double?) : DatabaseCalculationResult()
    data class DateValue(val date: LocalDate?) : DatabaseCalculationResult()
    data class DayCount(val days: Int?) : DatabaseCalculationResult()
}

private val calculationsForEveryColumn = listOf(
    DatabaseCalculation.COUNT_ALL,
    DatabaseCalculation.COUNT_VALUES,
    DatabaseCalculation.COUNT_UNIQUE_VALUES,
    DatabaseCalculation.COUNT_EMPTY,
    DatabaseCalculation.COUNT_NOT_EMPTY,
    DatabaseCalculation.PERCENT_EMPTY,
    DatabaseCalculation.PERCENT_NOT_EMPTY
)

fun calculationsFor(valueType: PropertyValueType): List<DatabaseCalculation> = when {
    valueType.holdsCheck -> listOf(
        DatabaseCalculation.COUNT_ALL,
        DatabaseCalculation.CHECKED,
        DatabaseCalculation.UNCHECKED,
        DatabaseCalculation.PERCENT_CHECKED,
        DatabaseCalculation.PERCENT_UNCHECKED
    )
    valueType.holdsNumber -> calculationsForEveryColumn + listOf(
        DatabaseCalculation.SUM,
        DatabaseCalculation.AVERAGE,
        DatabaseCalculation.MEDIAN,
        DatabaseCalculation.MIN,
        DatabaseCalculation.MAX,
        DatabaseCalculation.RANGE
    )
    valueType.holdsDate -> calculationsForEveryColumn + listOf(
        DatabaseCalculation.MEDIAN,
        DatabaseCalculation.MIN,
        DatabaseCalculation.MAX,
        DatabaseCalculation.RANGE
    )
    else -> calculationsForEveryColumn
}

fun DatabaseBlock.calculationOf(column: DatabaseColumnTarget): DatabaseCalculation? {
    val calculation = calculations[column.columnKey] ?: return null
    val valueType = valueTypeOf(column) ?: return null
    return calculation.takeIf { it in calculationsFor(valueType) }
}

fun calculate(
    rows: List<DatabaseRow>,
    column: DatabaseColumnTarget,
    valueType: PropertyValueType,
    calculation: DatabaseCalculation
): DatabaseCalculationResult {
    val emptyCount = rows.count { it.isEmptyAt(column) }
    val checkedCount = rows.count { it.isCheckedAt(column) }
    return when (calculation) {
        DatabaseCalculation.COUNT_ALL -> DatabaseCalculationResult.Count(rows.size)
        DatabaseCalculation.COUNT_VALUES -> DatabaseCalculationResult.Count(rows.sumOf { it.valuesAt(column).size })
        DatabaseCalculation.COUNT_UNIQUE_VALUES -> DatabaseCalculationResult.Count(rows.flatMap { it.valuesAt(column) }.distinct().size)
        DatabaseCalculation.COUNT_EMPTY -> DatabaseCalculationResult.Count(emptyCount)
        DatabaseCalculation.COUNT_NOT_EMPTY -> DatabaseCalculationResult.Count(rows.size - emptyCount)
        DatabaseCalculation.CHECKED -> DatabaseCalculationResult.Count(checkedCount)
        DatabaseCalculation.UNCHECKED -> DatabaseCalculationResult.Count(rows.size - checkedCount)
        DatabaseCalculation.PERCENT_EMPTY -> percentOf(emptyCount, rows.size)
        DatabaseCalculation.PERCENT_NOT_EMPTY -> percentOf(rows.size - emptyCount, rows.size)
        DatabaseCalculation.PERCENT_CHECKED -> percentOf(checkedCount, rows.size)
        DatabaseCalculation.PERCENT_UNCHECKED -> percentOf(rows.size - checkedCount, rows.size)
        DatabaseCalculation.SUM,
        DatabaseCalculation.AVERAGE,
        DatabaseCalculation.MEDIAN,
        DatabaseCalculation.MIN,
        DatabaseCalculation.MAX,
        DatabaseCalculation.RANGE -> if (valueType.holdsDate) {
            calculateDates(rows.mapNotNull { it.dateAt(column) }.sorted(), calculation)
        } else {
            calculateNumbers(rows.mapNotNull { it.numberAt(column) }.sorted(), calculation)
        }
    }
}

private fun calculateNumbers(sortedNumbers: List<Double>, calculation: DatabaseCalculation): DatabaseCalculationResult {
    if (sortedNumbers.isEmpty()) return DatabaseCalculationResult.NumberValue(null)
    val middleIndex = sortedNumbers.size / 2
    val number = when (calculation) {
        DatabaseCalculation.SUM -> sortedNumbers.sum()
        DatabaseCalculation.AVERAGE -> sortedNumbers.average()
        DatabaseCalculation.MEDIAN -> if (sortedNumbers.size % 2 == 1) {
            sortedNumbers[middleIndex]
        } else {
            (sortedNumbers[middleIndex - 1] + sortedNumbers[middleIndex]) / 2
        }
        DatabaseCalculation.MIN -> sortedNumbers.first()
        DatabaseCalculation.MAX -> sortedNumbers.last()
        else -> sortedNumbers.last() - sortedNumbers.first()
    }
    return DatabaseCalculationResult.NumberValue(number)
}

private fun calculateDates(sortedDates: List<LocalDate>, calculation: DatabaseCalculation): DatabaseCalculationResult = when {
    calculation == DatabaseCalculation.RANGE ->
        DatabaseCalculationResult.DayCount(if (sortedDates.isEmpty()) null else sortedDates.first().daysUntil(sortedDates.last()))
    sortedDates.isEmpty() -> DatabaseCalculationResult.DateValue(null)
    calculation == DatabaseCalculation.MIN -> DatabaseCalculationResult.DateValue(sortedDates.first())
    calculation == DatabaseCalculation.MAX -> DatabaseCalculationResult.DateValue(sortedDates.last())
    else -> DatabaseCalculationResult.DateValue(sortedDates[(sortedDates.size - 1) / 2])
}

private fun percentOf(part: Int, whole: Int): DatabaseCalculationResult.Percent =
    DatabaseCalculationResult.Percent(if (whole == 0) 0 else (part * 1000.0 / whole).roundToInt())

fun formatCalculatedNumber(number: Double): String {
    val hundredths = (number * 100).roundToLong()
    val sign = if (hundredths < 0) "-" else ""
    val wholePart = abs(hundredths) / 100
    val fractionPart = abs(hundredths) % 100
    return when {
        fractionPart == 0L -> "$sign$wholePart"
        fractionPart % 10 == 0L -> "$sign$wholePart.${fractionPart / 10}"
        else -> "$sign$wholePart.${fractionPart.toString().padStart(2, '0')}"
    }
}

private fun DatabaseRow.valuesAt(column: DatabaseColumnTarget): List<String> {
    val cell = cell(column)
    val values = when {
        column == DatabaseColumnTarget.NotesTitle -> listOf(title)
        cell == null -> emptyList()
        cell.valueType.holdsTags -> cell.tags.map { it.lowercase() }
        else -> listOf(displayValueAt(column))
    }
    return values.map { it.trim() }.filter { it.isNotEmpty() }
}
