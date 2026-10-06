package com.emberr.presentation.shared.editor

import com.emberr.domain.model.NoteBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class EditorDiskReconciler {

    private val blockIdsShownInEditor = MutableStateFlow<Set<String>>(emptySet())

    fun rememberBlocksShown(blocks: List<NoteBlock>) {
        blockIdsShownInEditor.update { shownIds ->
            if (blocks.all { it.id in shownIds }) shownIds else shownIds + blocks.map { it.id }
        }
    }

    fun forgetBlocksShown() {
        blockIdsShownInEditor.value = emptySet()
    }

    fun reconcile(
        editorBlocks: List<NoteBlock>,
        diskBlocks: List<NoteBlock>,
        keepsEditorCopy: (NoteBlock) -> Boolean = { false }
    ): List<NoteBlock> {
        rememberBlocksShown(editorBlocks)
        val diskBlocksById = diskBlocks.associateBy { it.id }

        val editorBlocksWithNewerDiskCopies = editorBlocks.map { editorBlock ->
            val diskBlock = diskBlocksById[editorBlock.id]
            val diskCopyIsNewer = diskBlock != null && diskBlock.updatedAt > editorBlock.updatedAt
            if (diskCopyIsNewer && !keepsEditorCopy(editorBlock)) diskBlock else editorBlock
        }

        val shownIds = blockIdsShownInEditor.value
        val blocksAddedOutsideEditor = diskBlocks.filter { !it.isDeleted && it.id !in shownIds }

        return editorBlocksWithNewerDiskCopies + blocksAddedOutsideEditor
    }
}
