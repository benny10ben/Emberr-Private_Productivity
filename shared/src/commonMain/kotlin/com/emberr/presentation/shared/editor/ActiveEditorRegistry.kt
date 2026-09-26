package com.emberr.presentation.shared.editor

import com.emberr.presentation.shared.canvas.CanvasViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

object ActiveEditorRegistry {

    private val activeEditors = MutableStateFlow<Set<BaseEditorViewModel>>(emptySet())
    private val activeCanvases = MutableStateFlow<Set<CanvasViewModel>>(emptySet())

    fun register(viewModel: BaseEditorViewModel) {
        activeEditors.update { it + viewModel }
    }

    fun unregister(viewModel: BaseEditorViewModel) {
        activeEditors.update { it - viewModel }
    }

    fun registerCanvas(viewModel: CanvasViewModel) {
        activeCanvases.update { it + viewModel }
    }

    fun unregisterCanvas(viewModel: CanvasViewModel) {
        activeCanvases.update { it - viewModel }
    }

    fun discardAllPendingWrites() {
        activeEditors.value.forEach { editor -> editor.discardPendingWrites() }
    }

    suspend fun flushAllPending() {
        activeEditors.value.forEach { editor ->
            try {
                editor.flushPendingSave()
            } catch (cause: Exception) {
                cause.printStackTrace()
            }
        }
    }

    // Re-embeds every open editor's latest content before the AI reads the vector index, so a note
    // left open beside the chat (the common desktop split-view layout) can't answer from stale
    // embeddings just because the user never closed it.
    suspend fun forceSyncAndIndexAllForAi() {
        activeEditors.value.forEach { editor ->
            try {
                editor.forceSyncAndIndexForAiNow()
            } catch (cause: Exception) {
                cause.printStackTrace()
            }
        }
        activeCanvases.value.forEach { canvas ->
            try {
                canvas.indexForAiNowIfChanged()
            } catch (cause: Exception) {
                cause.printStackTrace()
            }
        }
    }
}
