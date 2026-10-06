package com.emberr.presentation.daily

import com.emberr.domain.model.NoteBlock

object DailyStoredBlocks {

    fun combineDayAndPinnedList(dayBlocks: List<NoteBlock>, pinnedBlocks: List<NoteBlock>): List<NoteBlock> {
        val pinnedBlocksById = pinnedBlocks.associateBy { it.id }
        val dayBlockIds = dayBlocks.mapTo(HashSet()) { it.id }

        val dayBlocksResolved = dayBlocks.map { dayCopy ->
            val pinnedCopy = pinnedBlocksById[dayCopy.id]
            if (pinnedCopy == null) dayCopy else theCopyThatCounts(dayCopy, pinnedCopy)
        }
        val blocksOnlyInPinnedList = pinnedBlocks.filter { it.id !in dayBlockIds }

        return dayBlocksResolved + blocksOnlyInPinnedList
    }

    private fun theCopyThatCounts(dayCopy: NoteBlock, pinnedCopy: NoteBlock): NoteBlock = when {
        dayCopy.isDeleted && !pinnedCopy.isDeleted -> pinnedCopy
        pinnedCopy.isDeleted && !dayCopy.isDeleted -> dayCopy
        pinnedCopy.updatedAt > dayCopy.updatedAt -> pinnedCopy
        else -> dayCopy
    }
}
