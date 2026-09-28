package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.deepCopyWithNewIds

fun List<NoteBlock>.copiedForRow(sourceRowNoteId: String, copyRowNoteId: String): List<NoteBlock> =
    filterNot { it.isDeleted }.map { block ->
        val column = (block as? PropertyBlock)?.databaseColumn()
        if (block is PropertyBlock && column != null && block.id == databaseCellBlockId(column, sourceRowNoteId)) {
            block.copy(id = databaseCellBlockId(column, copyRowNoteId))
        } else {
            block.deepCopyWithNewIds()
        }
    }

fun DatabaseBlock.rowBlocksFromTemplate(
    templateBlocks: List<NoteBlock>,
    templateNoteId: String,
    rowNoteId: String,
    now: Long
): List<NoteBlock> =
    columns.fold(templateBlocks.copiedForRow(templateNoteId, rowNoteId)) { blocks, column ->
        blocks.withDatabaseColumnShown(this, column, rowNoteId, now)
    }
