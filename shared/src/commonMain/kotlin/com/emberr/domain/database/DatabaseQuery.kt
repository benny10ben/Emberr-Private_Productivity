package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.valueAsText
import com.emberr.domain.model.valueTypeOf
import kotlinx.datetime.LocalDate

fun filterConditionsFor(valueType: PropertyValueType): List<DatabaseFilterCondition> = when (valueType) {
    PropertyValueType.DATE -> listOf(
        DatabaseFilterCondition.IS,
        DatabaseFilterCondition.IS_BEFORE,
        DatabaseFilterCondition.IS_AFTER,
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
    PropertyValueType.TAGS -> listOf(
        DatabaseFilterCondition.CONTAINS,
        DatabaseFilterCondition.DOES_NOT_CONTAIN,
        DatabaseFilterCondition.IS_EMPTY,
        DatabaseFilterCondition.IS_NOT_EMPTY
    )
}

fun applyFiltersAndSort(rows: List<DatabaseRow>, database: DatabaseBlock): List<DatabaseRow> {
    val usableFilters = database.filters.mapNotNull { filter ->
        val valueType = database.valueTypeOf(filter.target) ?: return@mapNotNull null
        if (filter.isUsable(valueType)) filter to valueType else null
    }
    return rows
        .filter { row -> usableFilters.all { (filter, valueType) -> row.matches(filter, valueType) } }
        .sortedWith(rowOrder(database))
}

private fun DatabaseFilter.isUsable(valueType: PropertyValueType): Boolean {
    if (condition !in filterConditionsFor(valueType)) return false
    if (!condition.needsValue) return true
    return when {
        valueType.holdsDate -> date != null
        valueType.holdsTags -> !tagName.isNullOrBlank()
        else -> text.isNotBlank()
    }
}

private fun DatabaseRow.matches(filter: DatabaseFilter, valueType: PropertyValueType): Boolean = when {
    filter.condition == DatabaseFilterCondition.IS_EMPTY -> isEmptyAt(filter.target)
    filter.condition == DatabaseFilterCondition.IS_NOT_EMPTY -> !isEmptyAt(filter.target)
    valueType.holdsDate -> dateMatches(dateAt(filter.target), filter)
    valueType.holdsTags -> tagsMatch(tagsAt(filter.target), filter)
    else -> textMatches(displayValueAt(filter.target), filter)
}

private fun textMatches(value: String, filter: DatabaseFilter): Boolean {
    val containsText = value.contains(filter.text.trim(), ignoreCase = true)
    return when (filter.condition) {
        DatabaseFilterCondition.CONTAINS -> containsText
        DatabaseFilterCondition.DOES_NOT_CONTAIN -> !containsText
        else -> true
    }
}

private fun dateMatches(value: LocalDate?, filter: DatabaseFilter): Boolean {
    val filterDate = filter.date ?: return true
    return when (filter.condition) {
        DatabaseFilterCondition.IS -> value == filterDate
        DatabaseFilterCondition.IS_BEFORE -> value != null && value < filterDate
        DatabaseFilterCondition.IS_AFTER -> value != null && value > filterDate
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

private fun rowOrder(database: DatabaseBlock): Comparator<DatabaseRow> {
    val creationOrder = compareBy<DatabaseRow>({ it.createdAt }, { it.noteId })
    val sort = database.sort ?: return creationOrder
    val valueType = database.valueTypeOf(sort.target) ?: return creationOrder
    return Comparator<DatabaseRow> { first, second -> compareSortValues(first, second, sort, valueType) }.then(creationOrder)
}

private fun compareSortValues(first: DatabaseRow, second: DatabaseRow, sort: DatabaseSort, valueType: PropertyValueType): Int {
    val firstIsEmpty = first.isEmptyAt(sort.target)
    val secondIsEmpty = second.isEmptyAt(sort.target)
    if (firstIsEmpty || secondIsEmpty) return firstIsEmpty.compareTo(secondIsEmpty)

    val (earlier, later) = if (sort.isDescending) second to first else first to second
    return if (valueType.holdsDate) {
        compareValues(earlier.dateAt(sort.target), later.dateAt(sort.target))
    } else {
        earlier.displayValueAt(sort.target).compareTo(later.displayValueAt(sort.target), ignoreCase = true)
    }
}

private fun DatabaseRow.isEmptyAt(target: DatabaseColumnTarget): Boolean = displayValueAt(target).isBlank()

private fun DatabaseRow.displayValueAt(target: DatabaseColumnTarget): String =
    if (target == DatabaseColumnTarget.NotesTitle) title else cell(target)?.valueAsText().orEmpty()

private fun DatabaseRow.dateAt(target: DatabaseColumnTarget): LocalDate? = cell(target)?.date

private fun DatabaseRow.tagsAt(target: DatabaseColumnTarget): List<String> = cell(target)?.tags.orEmpty()
