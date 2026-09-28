package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseSettingTime
import com.emberr.domain.model.columnKey

private const val TITLE_SETTING_KEY = "title"
private const val SORT_SETTING_KEY = "sort"
private const val DEFAULT_TEMPLATE_SETTING_KEY = "default_template"
private const val ROW_COUNT_SETTING_KEY = "row_count"
private const val ACTIVE_VIEW_SETTING_KEY = "active_view"
private const val NOTES_POSITION_SETTING_KEY = "notes_position"

private fun columnSettingKey(column: DatabaseColumnTarget) = "column:${column.columnKey}"
private fun widthSettingKey(columnKey: String) = "width:$columnKey"
private fun calculationSettingKey(columnKey: String) = "calculation:$columnKey"
private fun formulaSettingKey(columnKey: String) = "formula:$columnKey"
private fun viewSettingKey(viewId: String) = "view:$viewId"
private fun propertySettingKey(propertyId: String) = "property:$propertyId"
private fun filterSettingKey(filterId: String) = "filter:$filterId"
private fun cellStyleSettingKey(styleKey: String) = "cell_style:$styleKey"
private fun rowStyleSettingKey(rowNoteId: String) = "row_style:$rowNoteId"
private fun columnStyleSettingKey(columnKey: String) = "column_style:$columnKey"

fun DatabaseBlock.withSettingTimesStamped(before: DatabaseBlock, now: Long): DatabaseBlock {
    val settingsBefore = before.settingsByKey()
    val settingsAfter = settingsByKey()
    val changedKeys = (settingsBefore.keys + settingsAfter.keys).filter { settingsBefore[it] != settingsAfter[it] }
    val stampedTimes = changedKeys.associateWith { key -> DatabaseSettingTime(updatedAt = now, isDeleted = key !in settingsAfter) }
    return copy(settingTimes = before.settingTimes + stampedTimes)
}

fun mergeDatabaseBlocks(first: DatabaseBlock, second: DatabaseBlock): DatabaseBlock {
    val newer = if (second.updatedAt > first.updatedAt) second else first
    val older = if (newer === second) first else second
    val merge = DatabaseSettingsMerge(newer, older)
    return newer.copy(
        title = merge.mergedValue(TITLE_SETTING_KEY, newer.title, older.title) ?: newer.title,
        columns = merge.mergedList(newer.columns, older.columns, ::columnSettingKey),
        notesColumnAfterKey = merge.mergedValue(NOTES_POSITION_SETTING_KEY, newer.notesColumnAfterKey, older.notesColumnAfterKey),
        customProperties = merge.mergedList(newer.customProperties, older.customProperties) { propertySettingKey(it.id) },
        columnWidths = merge.mergedList(newer.columnWidths.entries.toList(), older.columnWidths.entries.toList()) { widthSettingKey(it.key) }
            .associate { it.key to it.value },
        calculations = merge.mergedList(newer.calculations.entries.toList(), older.calculations.entries.toList()) { calculationSettingKey(it.key) }
            .associate { it.key to it.value },
        formulas = merge.mergedList(newer.formulas.entries.toList(), older.formulas.entries.toList()) { formulaSettingKey(it.key) }
            .associate { it.key to it.value },
        filters = merge.mergedList(newer.filters, older.filters) { filterSettingKey(it.id) },
        cellStyles = merge.mergedList(newer.cellStyles.entries.toList(), older.cellStyles.entries.toList()) { cellStyleSettingKey(it.key) }
            .associate { it.key to it.value },
        rowStyles = merge.mergedList(newer.rowStyles.entries.toList(), older.rowStyles.entries.toList()) { rowStyleSettingKey(it.key) }
            .associate { it.key to it.value },
        columnStyles = merge.mergedList(newer.columnStyles.entries.toList(), older.columnStyles.entries.toList()) { columnStyleSettingKey(it.key) }
            .associate { it.key to it.value },
        sort = merge.mergedValue(SORT_SETTING_KEY, newer.sort, older.sort),
        defaultTemplateId = merge.mergedValue(DEFAULT_TEMPLATE_SETTING_KEY, newer.defaultTemplateId, older.defaultTemplateId),
        showsRowCount = merge.mergedValue(ROW_COUNT_SETTING_KEY, newer.showsRowCount.takeIf { it }, older.showsRowCount.takeIf { it }) ?: false,
        views = merge.mergedList(newer.views, older.views) { viewSettingKey(it.id) },
        activeViewId = merge.mergedValue(ACTIVE_VIEW_SETTING_KEY, newer.activeViewId, older.activeViewId),
        settingTimes = merge.mergedTimes()
    )
}

private class DatabaseSettingsMerge(private val newer: DatabaseBlock, private val older: DatabaseBlock) {

    fun <Item> mergedList(newerItems: List<Item>, olderItems: List<Item>, keyOf: (Item) -> String): List<Item> {
        val newerItemsByKey = newerItems.associateBy(keyOf)
        val olderItemsByKey = olderItems.associateBy(keyOf)
        val keysInOrder = (newerItems.map(keyOf) + olderItems.map(keyOf)).distinct()
        return keysInOrder.mapNotNull { key ->
            if (olderSideWins(key, key in newerItemsByKey, key in olderItemsByKey)) olderItemsByKey[key] else newerItemsByKey[key]
        }
    }

    fun <Value : Any> mergedValue(key: String, newerValue: Value?, olderValue: Value?): Value? =
        if (olderSideWins(key, newerValue != null, olderValue != null)) olderValue else newerValue

    fun mergedTimes(): Map<String, DatabaseSettingTime> = buildMap {
        putAll(newer.settingTimes)
        older.settingTimes.forEach { (key, olderTime) ->
            val newerTime = get(key)
            if (newerTime == null || olderTime.updatedAt > newerTime.updatedAt) put(key, olderTime)
        }
    }

    private fun olderSideWins(key: String, newerHasIt: Boolean, olderHasIt: Boolean): Boolean {
        val newerTime = timeOf(newer, key, newerHasIt) ?: return true
        val olderTime = timeOf(older, key, olderHasIt) ?: return false
        return olderTime > newerTime
    }

    private fun timeOf(block: DatabaseBlock, key: String, blockHasIt: Boolean): Long? =
        block.settingTimes[key]?.updatedAt ?: if (blockHasIt) 0L else null
}

private fun DatabaseBlock.settingsByKey(): Map<String, Any> = buildMap {
    put(TITLE_SETTING_KEY, title)
    sort?.let { put(SORT_SETTING_KEY, it) }
    defaultTemplateId?.let { put(DEFAULT_TEMPLATE_SETTING_KEY, it) }
    if (showsRowCount) put(ROW_COUNT_SETTING_KEY, true)
    views.forEach { put(viewSettingKey(it.id), it) }
    activeViewId?.let { put(ACTIVE_VIEW_SETTING_KEY, it) }
    columns.forEach { put(columnSettingKey(it), it) }
    notesColumnAfterKey?.let { put(NOTES_POSITION_SETTING_KEY, it) }
    columnWidths.forEach { (columnKey, width) -> put(widthSettingKey(columnKey), width) }
    calculations.forEach { (columnKey, calculation) -> put(calculationSettingKey(columnKey), calculation) }
    formulas.forEach { (columnKey, formula) -> put(formulaSettingKey(columnKey), formula) }
    customProperties.forEach { put(propertySettingKey(it.id), it) }
    filters.forEach { put(filterSettingKey(it.id), it) }
    cellStyles.forEach { (styleKey, style) -> put(cellStyleSettingKey(styleKey), style) }
    rowStyles.forEach { (rowNoteId, style) -> put(rowStyleSettingKey(rowNoteId), style) }
    columnStyles.forEach { (columnKey, style) -> put(columnStyleSettingKey(columnKey), style) }
}
