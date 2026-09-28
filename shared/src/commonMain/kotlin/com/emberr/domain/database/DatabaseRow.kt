package com.emberr.domain.database

import androidx.compose.runtime.Immutable
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyBlock

@Immutable
data class DatabaseRow(
    val noteId: String,
    val title: String,
    val createdAt: Long,
    val cellsByColumn: Map<DatabaseColumnTarget, PropertyBlock>,
    val icon: String? = null,
    val coverImagePath: String? = null
) {
    fun cell(column: DatabaseColumnTarget): PropertyBlock? = cellsByColumn[column]
}

fun buildDatabaseRow(
    noteId: String,
    title: String,
    createdAt: Long,
    blocks: List<NoteBlock>,
    icon: String? = null,
    coverImagePath: String? = null
): DatabaseRow {
    val liveCellsByColumn = blocks.mapNotNull { block ->
        val cell = block as? PropertyBlock ?: return@mapNotNull null
        if (cell.isDeleted) return@mapNotNull null
        val column = cell.databaseColumn() ?: return@mapNotNull null
        if (cell.id != databaseCellBlockId(column, noteId)) return@mapNotNull null
        column to cell
    }.toMap()
    return DatabaseRow(
        noteId = noteId,
        title = title,
        createdAt = createdAt,
        cellsByColumn = liveCellsByColumn,
        icon = icon,
        coverImagePath = coverImagePath
    )
}

fun PropertyBlock.databaseColumn(): DatabaseColumnTarget? {
    val builtInType = propertyType
    val customId = customPropertyId
    return when {
        builtInType != null -> DatabaseColumnTarget.Property(builtInType)
        customId != null -> DatabaseColumnTarget.CustomProperty(customId)
        else -> null
    }
}
