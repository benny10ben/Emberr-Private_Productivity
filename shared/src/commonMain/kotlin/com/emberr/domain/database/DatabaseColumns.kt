package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.NOTES_COLUMN_KEY
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.customPropertyWithId

fun DatabaseBlock.builtInPropertiesNotYetAdded(): List<PropertyType> =
    PropertyType.entries.filterNot { DatabaseColumnTarget.Property(it) in columns }

fun DatabaseBlock.customPropertiesNotShown(): List<DatabaseCustomProperty> =
    customProperties.filterNot { DatabaseColumnTarget.CustomProperty(it.id) in columns }

fun DatabaseBlock.sharedPropertiesNotYetAdded(sharedProperties: List<DatabaseCustomProperty>): List<DatabaseCustomProperty> =
    sharedProperties.filter { customPropertyWithId(it.id) == null && !isPropertyNameTaken(it.name, ignoringPropertyId = null) }

fun DatabaseBlock.columnWithKey(columnKey: String?): DatabaseColumnTarget? = when (columnKey) {
    null -> null
    NOTES_COLUMN_KEY -> DatabaseColumnTarget.NotesTitle
    else -> columns.firstOrNull { it.columnKey == columnKey }
}

fun DatabaseBlock.visibleColumns(): List<DatabaseColumnTarget> {
    val hiddenColumnKeys = activeView().hiddenColumnKeys
    return columns.filterNot { it.columnKey in hiddenColumnKeys }
}

fun DatabaseBlock.columnsInTableOrder(): List<DatabaseColumnTarget> {
    val notesPosition = columns.indexOfFirst { it.columnKey == notesColumnAfterKey } + 1
    return columns.toMutableList().apply { add(notesPosition, DatabaseColumnTarget.NotesTitle) }
}

fun DatabaseBlock.visibleColumnsInTableOrder(): List<DatabaseColumnTarget> {
    val hiddenColumnKeys = activeView().hiddenColumnKeys
    return columnsInTableOrder().filter { it == DatabaseColumnTarget.NotesTitle || it.columnKey !in hiddenColumnKeys }
}

fun DatabaseBlock.frozenColumns(): Set<DatabaseColumnTarget> {
    if (!activeView().freezesTitleColumn) return emptySet()
    val tableColumns = visibleColumnsInTableOrder()
    return tableColumns.take(tableColumns.indexOf(DatabaseColumnTarget.NotesTitle) + 1).toSet()
}

private fun DatabaseBlock.withTableOrder(tableOrder: List<DatabaseColumnTarget>): DatabaseBlock {
    val notesPosition = tableOrder.indexOf(DatabaseColumnTarget.NotesTitle)
    return copy(
        columns = tableOrder - DatabaseColumnTarget.NotesTitle,
        notesColumnAfterKey = tableOrder.getOrNull(notesPosition - 1)?.columnKey
    )
}

fun DatabaseBlock.withColumnShown(viewId: String, column: DatabaseColumnTarget, isShown: Boolean): DatabaseBlock =
    withViewChanged(viewId) { view ->
        val hiddenColumnKeys = if (isShown) {
            view.hiddenColumnKeys - column.columnKey
        } else {
            (view.hiddenColumnKeys + column.columnKey).distinct()
        }
        view.copy(hiddenColumnKeys = hiddenColumnKeys)
    }

fun DatabaseBlock.withColumnAdded(column: DatabaseColumnTarget, beforeColumn: DatabaseColumnTarget? = null): DatabaseBlock {
    if (column == DatabaseColumnTarget.NotesTitle || column in columns) return this
    if (column is DatabaseColumnTarget.CustomProperty && customPropertyWithId(column.propertyId) == null) return this
    return withTableOrder((columnsInTableOrder() + column).withItemMovedBefore(column, beforeColumn))
}

fun DatabaseBlock.withColumnMovedBefore(column: DatabaseColumnTarget, beforeColumn: DatabaseColumnTarget?): DatabaseBlock =
    withTableOrder(columnsInTableOrder().withItemMovedBefore(column, beforeColumn))

fun DatabaseBlock.withColumnRemoved(column: DatabaseColumnTarget): DatabaseBlock = copy(
    columns = columns - column,
    notesColumnAfterKey = if (notesColumnAfterKey == column.columnKey) {
        columns.getOrNull(columns.indexOf(column) - 1)?.columnKey
    } else {
        notesColumnAfterKey
    },
    columnWidths = columnWidths - column.columnKey,
    calculations = calculations - column.columnKey,
    formulas = formulas - column.columnKey,
    numberFormats = numberFormats - column.columnKey,
    colorRules = colorRules.filterNot { it.condition.target == column },
    columnStyles = columnStyles - column.columnKey,
    cellStyles = cellStyles.withoutCellsOfColumn(column.columnKey),
    views = views.map { view ->
        view.copy(
            hiddenColumnKeys = view.hiddenColumnKeys - column.columnKey,
            filters = view.filters.filterNot { it.target == column },
            sorts = view.sorts.filterNot { it.target == column }
        )
    }
)

fun DatabaseBlock.withDatabasePropertyCreated(property: DatabaseCustomProperty, beforeColumn: DatabaseColumnTarget? = null): DatabaseBlock =
    copy(customProperties = customProperties + property).withColumnAdded(DatabaseColumnTarget.CustomProperty(property.id), beforeColumn)

fun DatabaseBlock.withDatabasePropertyRenamed(propertyId: String, newName: String): DatabaseBlock {
    val oldName = customPropertyWithId(propertyId)?.name ?: return this
    return copy(
        customProperties = customProperties.map { if (it.id == propertyId) it.copy(name = newName) else it },
        formulas = formulas.mapValues { (_, formula) -> formula.withPropertyReferenceRenamed(oldName, newName) }
    )
}

fun DatabaseBlock.withDatabasePropertyDeleted(propertyId: String): DatabaseBlock =
    withColumnRemoved(DatabaseColumnTarget.CustomProperty(propertyId))
        .copy(customProperties = customProperties.filterNot { it.id == propertyId })

fun DatabaseBlock.isPropertyNameTaken(name: String, ignoringPropertyId: String?): Boolean {
    val cleanedName = name.trim()
    return PropertyType.entries.any { it.label.equals(cleanedName, ignoreCase = true) } ||
        customProperties.any { it.id != ignoringPropertyId && it.name.equals(cleanedName, ignoreCase = true) }
}
