package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.labelOf
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.flow.first

fun DatabaseBlock.asExportBlocks(rows: List<DatabaseRow>): List<NoteBlock> {
    val tableColumns = columnsInTableOrder()
    val headerRow = tableColumns.map { labelOf(it) }
    val valueRows = applyFiltersAndSort(rows.withFormulaResults(this), this).map { row ->
        tableColumns.map { column ->
            if (column == DatabaseColumnTarget.NotesTitle) row.title.ifBlank { "Untitled" } else row.displayValueAt(column)
        }
    }
    val table = TableBlock(
        id = id,
        rows = listOf(headerRow) + valueRows,
        indentationLevel = indentationLevel,
        isPinned = isPinned,
        updatedAt = updatedAt
    )
    if (title.isBlank()) return listOf(table)

    val titleLine = TextBlock(
        id = "$id-title",
        text = title,
        isBold = true,
        indentationLevel = indentationLevel,
        isPinned = isPinned,
        updatedAt = updatedAt
    )
    return listOf(titleLine, table)
}

suspend fun List<NoteBlock>.withDatabasesAsTables(repository: NoteRepository): List<NoteBlock> =
    flatMap { block ->
        if (block is DatabaseBlock && !block.isDeleted) {
            val settings = repository.observeDatabaseSettings(block.databaseId).first() ?: return@flatMap emptyList()
            block.withSharedSettingsFrom(settings).asExportBlocks(repository.observeDatabaseRows(block.databaseId).first())
        } else {
            listOf(block)
        }
    }
