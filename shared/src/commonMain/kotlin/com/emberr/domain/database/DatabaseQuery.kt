package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseDateGrouping
import com.emberr.domain.model.DatabaseDateRange
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseRelativeDate
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.numberOrNull
import com.emberr.domain.model.valueAsText
import com.emberr.domain.model.valueTypeOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

fun filterConditionsFor(valueType: PropertyValueType): List<DatabaseFilterCondition> = when (valueType) {
    PropertyValueType.DATE -> listOf(
        DatabaseFilterCondition.IS,
        DatabaseFilterCondition.IS_BEFORE,
        DatabaseFilterCondition.IS_AFTER,
        DatabaseFilterCondition.IS_ON_OR_BEFORE,
        DatabaseFilterCondition.IS_ON_OR_AFTER,
        DatabaseFilterCondition.IS_WITHIN,
        DatabaseFilterCondition.IS_EMPTY,
        DatabaseFilterCondition.IS_NOT_EMPTY
    )
    PropertyValueType.SINGLE_CHOICE -> listOf(
        DatabaseFilterCondition.IS,
        DatabaseFilterCondition.IS_NOT,
        DatabaseFilterCondition.IS_EMPTY,
        DatabaseFilterCondition.IS_NOT_EMPTY
    )
    PropertyValueType.TEXT,
    PropertyValueType.PHONE,
    PropertyValueType.EMAIL,
    PropertyValueType.LINK,
    PropertyValueType.TAGS,
    PropertyValueType.FORMULA -> listOf(
        DatabaseFilterCondition.CONTAINS,
        DatabaseFilterCondition.DOES_NOT_CONTAIN,
        DatabaseFilterCondition.IS_EMPTY,
        DatabaseFilterCondition.IS_NOT_EMPTY
    )
    PropertyValueType.CHECKBOX -> listOf(
        DatabaseFilterCondition.IS_CHECKED,
        DatabaseFilterCondition.IS_UNCHECKED
    )
    PropertyValueType.NUMBER -> listOf(
        DatabaseFilterCondition.IS,
        DatabaseFilterCondition.IS_NOT,
        DatabaseFilterCondition.IS_GREATER_THAN,
        DatabaseFilterCondition.IS_LESS_THAN,
        DatabaseFilterCondition.IS_EMPTY,
        DatabaseFilterCondition.IS_NOT_EMPTY
    )
}

fun applyFiltersAndSort(rows: List<DatabaseRow>, database: DatabaseBlock, today: LocalDate = todayInThisTimeZone()): List<DatabaseRow> {
    val view = database.activeView()
    val usableFilters = view.filters.mapNotNull { filter ->
        if (!database.hasColumn(filter.target)) return@mapNotNull null
        val valueType = database.valueTypeOf(filter.target) ?: return@mapNotNull null
        if (filter.isUsable(valueType)) filter to valueType else null
    }
    return rows
        .filter { row -> usableFilters.all { (filter, valueType) -> row.matches(filter, valueType, today) } }
        .sortedWith(rowOrder(database, view.sorts))
}

fun List<DatabaseRow>.matchingSearch(searchText: String, database: DatabaseBlock): List<DatabaseRow> {
    val cleanedSearchText = searchText.trim()
    if (cleanedSearchText.isEmpty()) return this
    val searchedColumns = listOf(DatabaseColumnTarget.NotesTitle) + database.columns
    return filter { row -> searchedColumns.any { row.displayValueAt(it).contains(cleanedSearchText, ignoreCase = true) } }
}

fun DatabaseRelativeDate.dateFrom(today: LocalDate): LocalDate = when (this) {
    DatabaseRelativeDate.TODAY -> today
    DatabaseRelativeDate.TOMORROW -> today.plus(1, DateTimeUnit.DAY)
    DatabaseRelativeDate.YESTERDAY -> today.minus(1, DateTimeUnit.DAY)
    DatabaseRelativeDate.ONE_WEEK_AGO -> today.minus(1, DateTimeUnit.WEEK)
    DatabaseRelativeDate.ONE_WEEK_FROM_NOW -> today.plus(1, DateTimeUnit.WEEK)
    DatabaseRelativeDate.ONE_MONTH_AGO -> today.minus(1, DateTimeUnit.MONTH)
    DatabaseRelativeDate.ONE_MONTH_FROM_NOW -> today.plus(1, DateTimeUnit.MONTH)
}

fun DatabaseDateRange.daysFrom(today: LocalDate): ClosedRange<LocalDate> = when (this) {
    DatabaseDateRange.THIS_WEEK -> periodStartOf(today, DatabaseDateGrouping.WEEK).let { start -> start..start.plus(6, DateTimeUnit.DAY) }
    DatabaseDateRange.THIS_MONTH -> periodStartOf(today, DatabaseDateGrouping.MONTH).let { start ->
        start..start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    }
    DatabaseDateRange.THIS_YEAR -> periodStartOf(today, DatabaseDateGrouping.YEAR).let { start ->
        start..start.plus(1, DateTimeUnit.YEAR).minus(1, DateTimeUnit.DAY)
    }
    DatabaseDateRange.PAST_7_DAYS -> today.minus(7, DateTimeUnit.DAY)..today
    DatabaseDateRange.NEXT_7_DAYS -> today..today.plus(7, DateTimeUnit.DAY)
    DatabaseDateRange.PAST_30_DAYS -> today.minus(30, DateTimeUnit.DAY)..today
    DatabaseDateRange.NEXT_30_DAYS -> today..today.plus(30, DateTimeUnit.DAY)
}

fun DatabaseFilter.isMetBy(row: DatabaseRow, database: DatabaseBlock, today: LocalDate): Boolean {
    if (!database.hasColumn(target)) return false
    val valueType = database.valueTypeOf(target) ?: return false
    return isUsable(valueType) && row.matches(this, valueType, today)
}

private fun DatabaseBlock.hasColumn(target: DatabaseColumnTarget): Boolean =
    target == DatabaseColumnTarget.NotesTitle || target in columns

private fun DatabaseFilter.isUsable(valueType: PropertyValueType): Boolean {
    if (condition !in filterConditionsFor(valueType)) return false
    if (!condition.needsValue) return true
    return when {
        valueType.holdsDate && condition == DatabaseFilterCondition.IS_WITHIN -> dateRange != null
        valueType.holdsDate -> relativeDate != null || date != null
        valueType.holdsTags -> !tagName.isNullOrBlank()
        valueType.holdsNumber -> text.trim().toDoubleOrNull() != null
        else -> text.isNotBlank()
    }
}

private fun DatabaseRow.matches(filter: DatabaseFilter, valueType: PropertyValueType, today: LocalDate): Boolean = when {
    filter.condition == DatabaseFilterCondition.IS_EMPTY -> isEmptyAt(filter.target)
    filter.condition == DatabaseFilterCondition.IS_NOT_EMPTY -> !isEmptyAt(filter.target)
    filter.condition == DatabaseFilterCondition.IS_CHECKED -> isCheckedAt(filter.target)
    filter.condition == DatabaseFilterCondition.IS_UNCHECKED -> !isCheckedAt(filter.target)
    valueType.holdsDate -> dateMatches(dateAt(filter.target), filter, today)
    valueType.holdsTags -> tagsMatch(tagsAt(filter.target), filter)
    valueType.holdsNumber -> numberMatches(numberAt(filter.target), filter)
    else -> textMatches(displayValueAt(filter.target), filter)
}

private fun numberMatches(value: Double?, filter: DatabaseFilter): Boolean {
    val filterNumber = filter.text.trim().toDoubleOrNull() ?: return true
    return when (filter.condition) {
        DatabaseFilterCondition.IS -> value == filterNumber
        DatabaseFilterCondition.IS_NOT -> value != filterNumber
        DatabaseFilterCondition.IS_GREATER_THAN -> value != null && value > filterNumber
        DatabaseFilterCondition.IS_LESS_THAN -> value != null && value < filterNumber
        else -> true
    }
}

private fun textMatches(value: String, filter: DatabaseFilter): Boolean {
    val containsText = value.contains(filter.text.trim(), ignoreCase = true)
    return when (filter.condition) {
        DatabaseFilterCondition.CONTAINS -> containsText
        DatabaseFilterCondition.DOES_NOT_CONTAIN -> !containsText
        else -> true
    }
}

private fun dateMatches(value: LocalDate?, filter: DatabaseFilter, today: LocalDate): Boolean {
    if (filter.condition == DatabaseFilterCondition.IS_WITHIN) {
        val range = filter.dateRange?.daysFrom(today) ?: return true
        return value != null && value in range
    }
    val filterDate = filter.relativeDate?.dateFrom(today) ?: filter.date ?: return true
    return when (filter.condition) {
        DatabaseFilterCondition.IS -> value == filterDate
        DatabaseFilterCondition.IS_BEFORE -> value != null && value < filterDate
        DatabaseFilterCondition.IS_AFTER -> value != null && value > filterDate
        DatabaseFilterCondition.IS_ON_OR_BEFORE -> value != null && value <= filterDate
        DatabaseFilterCondition.IS_ON_OR_AFTER -> value != null && value >= filterDate
        else -> true
    }
}

private fun tagsMatch(tags: List<String>, filter: DatabaseFilter): Boolean {
    val hasTag = tags.any { it.equals(filter.tagName, ignoreCase = true) }
    return when (filter.condition) {
        DatabaseFilterCondition.IS, DatabaseFilterCondition.CONTAINS -> hasTag
        DatabaseFilterCondition.IS_NOT, DatabaseFilterCondition.DOES_NOT_CONTAIN -> !hasTag
        else -> true
    }
}

private fun rowOrder(database: DatabaseBlock, sorts: List<DatabaseSort>): Comparator<DatabaseRow> {
    val creationOrder = compareBy<DatabaseRow>({ it.createdAt }, { it.noteId })
    val sortOrders = sorts.mapNotNull { sort ->
        val valueType = database.valueTypeOf(sort.target) ?: return@mapNotNull null
        Comparator<DatabaseRow> { first, second -> compareSortValues(first, second, sort, valueType) }
    }
    return (sortOrders + creationOrder).reduce { order, nextOrder -> order.then(nextOrder) }
}

private fun compareSortValues(first: DatabaseRow, second: DatabaseRow, sort: DatabaseSort, valueType: PropertyValueType): Int {
    val (earlier, later) = if (sort.isDescending) second to first else first to second
    if (valueType.holdsCheck) return earlier.isCheckedAt(sort.target).compareTo(later.isCheckedAt(sort.target))

    val firstIsEmpty = first.isEmptyAt(sort.target)
    val secondIsEmpty = second.isEmptyAt(sort.target)
    if (firstIsEmpty || secondIsEmpty) return firstIsEmpty.compareTo(secondIsEmpty)

    return if (valueType.holdsDate) {
        compareValues(earlier.dateAt(sort.target), later.dateAt(sort.target))
    } else if (valueType.holdsNumber) {
        compareValues(earlier.numberAt(sort.target), later.numberAt(sort.target))
    } else if (valueType.holdsFormula) {
        compareFormulaResults(earlier.formulaResult(sort.target), later.formulaResult(sort.target))
    } else {
        earlier.displayValueAt(sort.target).compareTo(later.displayValueAt(sort.target), ignoreCase = true)
    }
}

internal fun DatabaseRow.isEmptyAt(target: DatabaseColumnTarget): Boolean = displayValueAt(target).isBlank()

internal fun DatabaseRow.displayValueAt(target: DatabaseColumnTarget): String =
    if (target == DatabaseColumnTarget.NotesTitle) title else formulaResult(target)?.displayText ?: cell(target)?.valueAsText().orEmpty()

internal fun DatabaseRow.dateAt(target: DatabaseColumnTarget): LocalDate? =
    (formulaResult(target) as? FormulaValue.DateValue)?.date ?: cell(target)?.date

internal fun DatabaseRow.tagsAt(target: DatabaseColumnTarget): List<String> = cell(target)?.tags.orEmpty()

internal fun DatabaseRow.numberAt(target: DatabaseColumnTarget): Double? =
    (formulaResult(target) as? FormulaValue.NumberValue)?.number ?: cell(target)?.numberOrNull()

internal fun DatabaseRow.isCheckedAt(target: DatabaseColumnTarget): Boolean =
    (formulaResult(target) as? FormulaValue.BooleanValue)?.isTrue ?: (cell(target)?.isChecked == true)
