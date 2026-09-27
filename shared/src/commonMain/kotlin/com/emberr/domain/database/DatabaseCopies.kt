package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.NoteContent

fun NoteContent.withFreshDatabaseIds(newDatabaseId: () -> String): NoteContent {
    val freshIdsByOldId = mutableMapOf<String, String>()
    return copy(
        blocks = blocks.map { block ->
            if (block is DatabaseBlock) {
                block.copy(databaseId = freshIdsByOldId.getOrPut(block.databaseId, newDatabaseId))
            } else {
                block
            }
        }
    )
}
