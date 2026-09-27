package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.customPropertyWithId

fun databaseCellBlockId(column: DatabaseColumnTarget, rowNoteId: String): String = when (column) {
    DatabaseColumnTarget.NotesTitle -> "notes-$rowNoteId"
    is DatabaseColumnTarget.Property -> "${column.propertyType.name.lowercase()}-$rowNoteId"
    is DatabaseColumnTarget.CustomProperty -> "${column.propertyId}-$rowNoteId"
}

fun DatabaseBlock.emptyCell(column: DatabaseColumnTarget, rowNoteId: String, now: Long): PropertyBlock? = when (column) {
    DatabaseColumnTarget.NotesTitle -> null
    is DatabaseColumnTarget.Property -> PropertyBlock(
        id = databaseCellBlockId(column, rowNoteId),
        propertyType = column.propertyType,
        updatedAt = now
    )
    is DatabaseColumnTarget.CustomProperty -> customPropertyWithId(column.propertyId)?.let { property ->
        PropertyBlock(
            id = databaseCellBlockId(column, rowNoteId),
            customPropertyId = property.id,
            customLabel = property.name,
            customValueType = property.valueType,
            updatedAt = now
        )
    }
}

fun DatabaseBlock.newRowBlocks(rowNoteId: String, now: Long): List<NoteBlock> =
    columns.mapNotNull { column -> emptyCell(column, rowNoteId, now) }

fun List<NoteBlock>.databaseCellOrNull(column: DatabaseColumnTarget, rowNoteId: String): PropertyBlock? {
    val cellId = databaseCellBlockId(column, rowNoteId)
    return firstOrNull { it.id == cellId } as? PropertyBlock
}

fun List<NoteBlock>.databaseCellForEditing(
    database: DatabaseBlock,
    column: DatabaseColumnTarget,
    rowNoteId: String,
    now: Long
): PropertyBlock? =
    databaseCellOrNull(column, rowNoteId)?.takeUnless { it.isDeleted }
        ?: database.emptyCell(column, rowNoteId, now)

fun List<NoteBlock>.withDatabaseCellPlaced(cell: PropertyBlock): List<NoteBlock> {
    val existingIndex = indexOfFirst { it.id == cell.id }
    if (existingIndex != -1) {
        if (this[existingIndex] == cell) return this
        return toMutableList().apply { set(existingIndex, cell) }
    }
    val firstNonPropertyIndex = indexOfFirst { it !is PropertyBlock }
    val insertIndex = if (firstNonPropertyIndex == -1) size else firstNonPropertyIndex
    return toMutableList().apply { add(insertIndex, cell) }
}

fun List<NoteBlock>.withDatabaseColumnShown(
    database: DatabaseBlock,
    column: DatabaseColumnTarget,
    rowNoteId: String,
    now: Long
): List<NoteBlock> {
    val existingCell = databaseCellOrNull(column, rowNoteId)
    if (existingCell != null && !existingCell.isDeleted) return this
    val freshCell = database.emptyCell(column, rowNoteId, now) ?: return this
    return withDatabaseCellPlaced(freshCell)
}

fun List<NoteBlock>.withDatabaseColumnRemoved(column: DatabaseColumnTarget, rowNoteId: String, now: Long): List<NoteBlock> {
    val existingCell = databaseCellOrNull(column, rowNoteId)
    if (existingCell == null || existingCell.isDeleted) return this
    return withDatabaseCellPlaced(existingCell.copy(isDeleted = true, updatedAt = now))
}

fun List<NoteBlock>.withDatabaseCellRenamed(propertyId: String, rowNoteId: String, newName: String, now: Long): List<NoteBlock> {
    val existingCell = databaseCellOrNull(DatabaseColumnTarget.CustomProperty(propertyId), rowNoteId)
    if (existingCell == null || existingCell.isDeleted || existingCell.customLabel == newName) return this
    return withDatabaseCellPlaced(existingCell.copy(customLabel = newName, updatedAt = now))
}
