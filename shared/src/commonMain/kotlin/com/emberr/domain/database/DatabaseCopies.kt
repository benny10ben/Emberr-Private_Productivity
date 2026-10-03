package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.deepCopyWithNewIds
import com.emberr.domain.model.withId

fun List<NoteBlock>.copiedForRow(
    sourceRowNoteId: String,
    copyRowNoteId: String,
    keepsBlockIdsSameOnEveryDevice: Boolean = false
): List<NoteBlock> =
    filterNot { it.isDeleted }.map { block ->
        val column = (block as? PropertyBlock)?.databaseColumn()
        when {
            block is PropertyBlock && column != null && block.id == databaseCellBlockId(column, sourceRowNoteId) ->
                block.copy(id = databaseCellBlockId(column, copyRowNoteId))
            keepsBlockIdsSameOnEveryDevice -> block.withId("$copyRowNoteId-${block.id}")
            else -> block.deepCopyWithNewIds()
        }
    }

fun DatabaseBlock.withRowIdsReplaced(copyIdsBySourceRowId: Map<String, String>): DatabaseBlock = copy(
    rowStyles = rowStyles.entries.mapNotNull { (rowNoteId, style) -> copyIdsBySourceRowId[rowNoteId]?.let { it to style } }.toMap(),
    cellStyles = cellStyles.withCellRowIdsReplaced(copyIdsBySourceRowId),
    views = views.map { view -> view.copy(manualRowOrder = view.manualRowOrder.mapNotNull { copyIdsBySourceRowId[it] }) }
)

fun DatabaseBlock.withRowStylesCopied(sourceRowNoteId: String, copyRowNoteId: String): DatabaseBlock {
    val sourceRowStyle = rowStyles[sourceRowNoteId]
    return copy(
        rowStyles = if (sourceRowStyle == null) rowStyles else rowStyles + (copyRowNoteId to sourceRowStyle),
        cellStyles = cellStyles + cellStyles.withCellRowIdsReplaced(mapOf(sourceRowNoteId to copyRowNoteId))
    )
}

fun DatabaseBlock.rowBlocksFromTemplate(
    templateBlocks: List<NoteBlock>,
    templateNoteId: String,
    rowNoteId: String,
    now: Long,
    keepsBlockIdsSameOnEveryDevice: Boolean = false
): List<NoteBlock> =
    columns.fold(templateBlocks.copiedForRow(templateNoteId, rowNoteId, keepsBlockIdsSameOnEveryDevice)) { blocks, column ->
        blocks.withDatabaseColumnShown(this, column, rowNoteId, now)
    }
