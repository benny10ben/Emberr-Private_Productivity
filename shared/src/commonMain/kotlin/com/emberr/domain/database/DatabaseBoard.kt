package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseDateGrouping
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.valueTypeOf
import com.emberr.domain.model.withStartDateMovedTo
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

const val NO_VALUE_GROUP_KEY = "no-value"
private const val CHECKED_GROUP_KEY = "checked"
private const val UNCHECKED_GROUP_KEY = "unchecked"

sealed class DatabaseBoardGroupValue {
    data class Option(val name: String) : DatabaseBoardGroupValue()
    data class Checkbox(val isChecked: Boolean) : DatabaseBoardGroupValue()
    data class DatePeriod(val start: LocalDate, val grouping: DatabaseDateGrouping) : DatabaseBoardGroupValue()
    data object NoValue : DatabaseBoardGroupValue()
}

data class DatabaseBoardGroup(
    val key: String,
    val value: DatabaseBoardGroupValue,
    val rows: List<DatabaseRow>
)

val valueTypesThatCanGroupABoard: List<PropertyValueType> = listOf(
    PropertyValueType.SINGLE_CHOICE,
    PropertyValueType.TAGS,
    PropertyValueType.CHECKBOX,
    PropertyValueType.DATE
)

fun canGroupBy(valueType: PropertyValueType): Boolean = valueType in valueTypesThatCanGroupABoard

fun DatabaseView.groupedBy(columnKey: String): DatabaseView =
    copy(groupByColumnKey = columnKey, hiddenGroupKeys = emptyList(), collapsedGroupKeys = emptyList())

fun DatabaseBlock.withViewGroupedBy(viewId: String, columnKey: String): DatabaseBlock =
    withViewChanged(viewId) { it.groupedBy(columnKey) }

fun DatabaseBlock.groupableColumns(): List<DatabaseColumnTarget> =
    columns.filter { column -> valueTypeOf(column)?.let(::canGroupBy) == true }

fun DatabaseBlock.groupByColumn(view: DatabaseView): DatabaseColumnTarget? {
    val groupableColumns = groupableColumns()
    return groupableColumns.firstOrNull { it.columnKey == view.groupByColumnKey }
        ?: groupableColumns.firstOrNull { it == DatabaseColumnTarget.Property(PropertyType.STATUS) }
        ?: groupableColumns.firstOrNull()
}

fun periodStartOf(date: LocalDate, grouping: DatabaseDateGrouping): LocalDate = when (grouping) {
    DatabaseDateGrouping.DAY -> date
    DatabaseDateGrouping.WEEK -> date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    DatabaseDateGrouping.MONTH -> LocalDate(date.year, date.month, 1)
    DatabaseDateGrouping.YEAR -> LocalDate(date.year, 1, 1)
}

fun boardGroups(
    rows: List<DatabaseRow>,
    column: DatabaseColumnTarget,
    valueType: PropertyValueType,
    savedOptionNames: List<String>,
    dateGrouping: DatabaseDateGrouping
): List<DatabaseBoardGroup> = when {
    valueType.holdsCheck -> listOf(false, true).map { isChecked ->
        DatabaseBoardGroup(
            key = if (isChecked) CHECKED_GROUP_KEY else UNCHECKED_GROUP_KEY,
            value = DatabaseBoardGroupValue.Checkbox(isChecked),
            rows = rows.filter { it.isCheckedAt(column) == isChecked }
        )
    }
    valueType.holdsDate -> {
        val rowsByPeriod = rows.filter { it.dateAt(column) != null }
            .groupBy { periodStartOf(it.dateAt(column)!!, dateGrouping) }
        rowsByPeriod.keys.sorted().map { periodStart ->
            DatabaseBoardGroup(
                key = periodStart.toString(),
                value = DatabaseBoardGroupValue.DatePeriod(periodStart, dateGrouping),
                rows = rowsByPeriod.getValue(periodStart)
            )
        } + noValueGroup(rows.filter { it.dateAt(column) == null })
    }
    else -> {
        val optionNames = (savedOptionNames + rows.flatMap { it.tagsAt(column) }).distinctBy { it.lowercase() }
        optionNames.map { optionName ->
            DatabaseBoardGroup(
                key = optionName.lowercase(),
                value = DatabaseBoardGroupValue.Option(optionName),
                rows = rows.filter { row -> row.tagsAt(column).any { it.equals(optionName, ignoreCase = true) } }
            )
        } + noValueGroup(rows.filter { it.tagsAt(column).isEmpty() })
    }
}

private fun noValueGroup(rows: List<DatabaseRow>) =
    DatabaseBoardGroup(key = NO_VALUE_GROUP_KEY, value = DatabaseBoardGroupValue.NoValue, rows = rows)

fun List<DatabaseBoardGroup>.shownIn(view: DatabaseView): List<DatabaseBoardGroup> =
    filter { group -> group.key !in view.hiddenGroupKeys && !(view.hidesEmptyGroups && group.rows.isEmpty()) }

fun List<DatabaseBoardGroup>.hiddenIn(view: DatabaseView): List<DatabaseBoardGroup> =
    filter { group -> group.key in view.hiddenGroupKeys }

fun PropertyBlock.movedBetweenGroups(from: DatabaseBoardGroupValue, to: DatabaseBoardGroupValue): PropertyBlock = when {
    valueType.holdsTags -> {
        val tagsWithoutOldGroup = if (from is DatabaseBoardGroupValue.Option) {
            tags.filterNot { it.equals(from.name, ignoreCase = true) }
        } else {
            tags
        }
        val movedTags = when {
            to !is DatabaseBoardGroupValue.Option -> if (valueType.allowsManyTags) tagsWithoutOldGroup else emptyList()
            valueType.allowsManyTags -> (tagsWithoutOldGroup + to.name).distinctBy { it.lowercase() }
            else -> listOf(to.name)
        }
        copy(tags = movedTags)
    }
    valueType.holdsCheck -> copy(isChecked = to is DatabaseBoardGroupValue.Checkbox && to.isChecked)
    valueType.holdsDate -> when (to) {
        is DatabaseBoardGroupValue.DatePeriod -> {
            val alreadyInPeriod = date?.let { periodStartOf(it, to.grouping) } == to.start
            if (alreadyInPeriod) this else withStartDateMovedTo(to.start)
        }
        else -> withStartDateMovedTo(null)
    }
    else -> this
}

fun List<DatabaseRow>.inManualOrder(manualRowOrder: List<String>): List<DatabaseRow> {
    if (manualRowOrder.isEmpty()) return this
    val positions = manualRowOrder.withIndex().associate { (position, rowNoteId) -> rowNoteId to position }
    return sortedBy { positions[it.noteId] ?: Int.MAX_VALUE }
}

fun <Item> List<Item>.withItemMovedBefore(item: Item, anchor: Item?): List<Item> {
    if (item == anchor || item !in this) return this
    val withoutItem = this - item
    val anchorPosition = if (anchor == null) -1 else withoutItem.indexOf(anchor)
    return if (anchorPosition == -1) withoutItem + item else withoutItem.toMutableList().apply { add(anchorPosition, item) }
}

fun manualRowOrderAfterDrop(
    shownRowIds: List<String>,
    previousManualOrder: List<String>,
    draggedRowId: String,
    beforeRowId: String?,
    lastOtherRowIdInTargetColumn: String?
): List<String> {
    val withoutDragged = shownRowIds - draggedRowId
    val insertPosition = when {
        beforeRowId != null -> withoutDragged.indexOf(beforeRowId)
        lastOtherRowIdInTargetColumn != null -> withoutDragged.indexOf(lastOtherRowIdInTargetColumn) + 1
        else -> -1
    }
    val reorderedShownRows = if (insertPosition < 0) {
        shownRowIds
    } else {
        withoutDragged.toMutableList().apply { add(insertPosition, draggedRowId) }
    }
    val shownRowIdSet = shownRowIds.toSet()
    return reorderedShownRows + previousManualOrder.filterNot { it in shownRowIdSet }
}

fun manualRowOrderWithCopiesPlaced(
    shownRowIds: List<String>,
    previousManualOrder: List<String>,
    sourceAndCopyRowIds: List<Pair<String, String>>
): List<String> {
    var shownRowIdsSoFar = shownRowIds
    var manualOrderSoFar = previousManualOrder
    sourceAndCopyRowIds.forEach { (sourceRowId, copyRowId) ->
        if (sourceRowId !in shownRowIdsSoFar) return@forEach
        manualOrderSoFar = manualRowOrderWithRowPlaced(shownRowIdsSoFar, manualOrderSoFar, copyRowId, sourceRowId, isAfter = true)
        shownRowIdsSoFar = shownRowIdsSoFar.toMutableList().apply { add(indexOf(sourceRowId) + 1, copyRowId) }
    }
    return manualOrderSoFar
}

fun manualRowOrderWithRowPlaced(
    shownRowIds: List<String>,
    previousManualOrder: List<String>,
    rowId: String,
    nextToRowId: String,
    isAfter: Boolean
): List<String> = manualRowOrderAfterDrop(
    shownRowIds = shownRowIds,
    previousManualOrder = previousManualOrder,
    draggedRowId = rowId,
    beforeRowId = nextToRowId.takeUnless { isAfter },
    lastOtherRowIdInTargetColumn = nextToRowId.takeIf { isAfter }
)

fun dropMovesRow(shownRowIds: List<String>, draggedRowId: String, nextToRowId: String, isAfter: Boolean): Boolean {
    val draggedPosition = shownRowIds.indexOf(draggedRowId)
    val nextToPosition = shownRowIds.indexOf(nextToRowId)
    if (draggedPosition < 0 || nextToPosition < 0) return false
    val dropPosition = if (isAfter) nextToPosition + 1 else nextToPosition
    return dropPosition != draggedPosition && dropPosition != draggedPosition + 1
}
