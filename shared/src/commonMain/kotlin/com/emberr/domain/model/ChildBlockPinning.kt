package com.emberr.domain.model

fun List<NoteBlock>.withChildrenPinnedLikeTheirToggleOrCallout(now: Long): List<NoteBlock> {
    if (none { it.holdsChildBlocks() && !it.isDeleted }) return this
    val outermostParents = outermostToggleOrCalloutAboveEachBlock()
    return mapIndexed { index, block ->
        val parent = outermostParents[index]
        if (parent != null && block.isPinned != parent.isPinned) block.withPin(parent.isPinned, now) else block
    }
}

fun List<NoteBlock>.idsOfBlocksInsideAToggleOrCallout(): Set<String> {
    val outermostParents = outermostToggleOrCalloutAboveEachBlock()
    return filterIndexed { index, _ -> outermostParents[index] != null }.mapTo(HashSet()) { it.id }
}

private fun NoteBlock.holdsChildBlocks(): Boolean = this is ToggleBlock || this is CalloutBlock

private fun List<NoteBlock>.outermostToggleOrCalloutAboveEachBlock(): List<NoteBlock?> {
    val openParents = ArrayDeque<NoteBlock>()
    return map { block ->
        if (block.isDeleted) return@map null
        while (openParents.isNotEmpty() && block.indentationLevel <= openParents.last().indentationLevel) {
            openParents.removeLast()
        }
        val outermostParent = openParents.firstOrNull()
        if (block.holdsChildBlocks()) openParents.addLast(block)
        outermostParent
    }
}
