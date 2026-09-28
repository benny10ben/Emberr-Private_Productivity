package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.customPropertyWithId

fun DatabaseBlock.builtInPropertiesNotYetAdded(): List<PropertyType> =
    PropertyType.entries.filterNot { DatabaseColumnTarget.Property(it) in columns }

fun DatabaseBlock.customPropertiesNotShown(): List<DatabaseCustomProperty> =
    customProperties.filterNot { DatabaseColumnTarget.CustomProperty(it.id) in columns }

fun DatabaseBlock.visibleColumns(): List<DatabaseColumnTarget> {
    val hiddenColumnKeys = activeView().hiddenColumnKeys
    return columns.filterNot { it.columnKey in hiddenColumnKeys }
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

fun DatabaseBlock.withColumnAdded(column: DatabaseColumnTarget): DatabaseBlock {
    if (column == DatabaseColumnTarget.NotesTitle || column in columns) return this
    if (column is DatabaseColumnTarget.CustomProperty && customPropertyWithId(column.propertyId) == null) return this
    return copy(columns = columns + column)
}

fun DatabaseBlock.withColumnRemoved(column: DatabaseColumnTarget): DatabaseBlock = copy(
    columns = columns - column,
    columnWidths = columnWidths - column.columnKey,
    calculations = calculations - column.columnKey,
    views = views.map { view -> view.copy(hiddenColumnKeys = view.hiddenColumnKeys - column.columnKey) },
    filters = filters.filterNot { it.target == column },
    sort = sort?.takeUnless { it.target == column }
)

fun DatabaseBlock.withDatabasePropertyCreated(property: DatabaseCustomProperty): DatabaseBlock =
    copy(customProperties = customProperties + property).withColumnAdded(DatabaseColumnTarget.CustomProperty(property.id))

fun DatabaseBlock.withDatabasePropertyRenamed(propertyId: String, newName: String): DatabaseBlock =
    copy(customProperties = customProperties.map { if (it.id == propertyId) it.copy(name = newName) else it })

fun DatabaseBlock.withDatabasePropertyDeleted(propertyId: String): DatabaseBlock =
    withColumnRemoved(DatabaseColumnTarget.CustomProperty(propertyId))
        .copy(customProperties = customProperties.filterNot { it.id == propertyId })

fun DatabaseBlock.isPropertyNameTaken(name: String, ignoringPropertyId: String?): Boolean {
    val cleanedName = name.trim()
    return PropertyType.entries.any { it.label.equals(cleanedName, ignoreCase = true) } ||
        customProperties.any { it.id != ignoringPropertyId && it.name.equals(cleanedName, ignoreCase = true) }
}
