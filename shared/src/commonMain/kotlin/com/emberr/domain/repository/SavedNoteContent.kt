package com.emberr.domain.repository

import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.withDeleted

object SavedNoteContent {

    fun blocksTheNoteHoldsAfterSaving(
        blocksBeingSaved: List<NoteBlock>,
        blocksAlreadyStored: List<NoteBlock>,
        removedAt: Long
    ): List<NoteBlock> {
        val idsStillBeingSaved = blocksBeingSaved.mapTo(HashSet()) { it.id }
        val blocksTheSaveLeftOut = blocksAlreadyStored
            .filterNot { it.id in idsStillBeingSaved }
            .map { block -> if (block.isDeleted) block else block.withDeleted(true, removedAt) }

        return blocksBeingSaved + blocksTheSaveLeftOut
    }
}
