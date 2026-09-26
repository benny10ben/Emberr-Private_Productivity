package com.emberr.domain.sync

import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent

object NoteMergeHelper {

    fun mergeNoteContent(
        localContent: NoteContent?,
        localUpdatedAt: Long,
        remoteContent: NoteContent,
        remoteUpdatedAt: Long
    ): NoteContent {
        if (localContent == null) return remoteContent

        // Determines base content using last-write-wins (strictly greater timestamp).
        // On exact timestamp ties, local content is preserved.
        val remoteWins = remoteUpdatedAt > localUpdatedAt
        val baseContent  = if (remoteWins) remoteContent else localContent
        val otherContent = if (remoteWins) localContent  else remoteContent
        val baseIds = baseContent.blocks.mapTo(HashSet()) { it.id }
        val otherById = otherContent.blocks.associateBy { it.id }
        val mergedTree = rebuildTree(baseContent.blocks, otherById)
        val otherOnly = otherContent.blocks.filter { it.id !in baseIds }

        val result = mergedTree.toMutableList()
        if (otherOnly.isNotEmpty()) {
            val otherOrder = otherContent.blocks.map { it.id }
            otherOnly.forEach { block ->
                val idx = otherOrder.indexOf(block.id)
                val precedingId = otherOrder.subList(0, idx)
                    .lastOrNull { id -> result.any { it.id == id } }
                val insertAfter = if (precedingId != null) {
                    result.indexOfFirst { it.id == precedingId }
                } else -1
                result.add(insertAfter + 1, block)
            }
        }

        return NoteContent(blocks = result.distinctBy { it.id })
    }

    /**
     * Rebuilds the block tree, applying pure last-write-wins based on updatedAt timestamps.
     */
    private fun rebuildTree(
        baseBlocks: List<NoteBlock>,
        otherById: Map<String, NoteBlock>
    ): List<NoteBlock> = baseBlocks.map { baseBlock ->
        val other = otherById[baseBlock.id]
        when {
            other == null -> baseBlock
            other.updatedAt > baseBlock.updatedAt -> other
            else -> baseBlock
        }
    }
}
