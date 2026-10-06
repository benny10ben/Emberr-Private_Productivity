package com.emberr.presentation.shared.editor

import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class EditorDiskReconcilerTest {

    private fun text(id: String, body: String, updatedAt: Long = 100L, isDeleted: Boolean = false) =
        TextBlock(id = id, text = body, updatedAt = updatedAt, isDeleted = isDeleted)

    @Test
    fun aBlockAnotherDeviceAddedWhileTheUserWasTypingIsKeptInTheSave() {
        val reconciler = EditorDiskReconciler()
        val editorBlocks = listOf(text("typing", "half a sentence", updatedAt = 300L))
        val diskBlocks = listOf(text("typing", "half", updatedAt = 200L), text("synced", "from the desktop", updatedAt = 250L))

        val result = reconciler.reconcile(editorBlocks, diskBlocks)

        assertEquals(listOf("typing", "synced"), result.map { it.id })
        assertEquals("half a sentence", (result[0] as TextBlock).text)
    }

    @Test
    fun aBlockTheUserRemovedFromTheEditorIsNotAddedBackFromDisk() {
        val reconciler = EditorDiskReconciler()
        reconciler.rememberBlocksShown(listOf(text("a", "first"), text("b", "second")))

        val result = reconciler.reconcile(
            editorBlocks = listOf(text("a", "first")),
            diskBlocks = listOf(text("a", "first"), text("b", "second"))
        )

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun aBlockAlreadyDeletedOnDiskIsNotAddedToTheSave() {
        val reconciler = EditorDiskReconciler()

        val result = reconciler.reconcile(
            editorBlocks = listOf(text("a", "first")),
            diskBlocks = listOf(text("a", "first"), text("old", "gone", isDeleted = true))
        )

        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun aNewerCopyOnDiskReplacesTheStaleCopyInTheEditor() {
        val reconciler = EditorDiskReconciler()

        val result = reconciler.reconcile(
            editorBlocks = listOf(text("a", "before the sync", updatedAt = 100L)),
            diskBlocks = listOf(text("a", "edited on the phone", updatedAt = 200L))
        )

        assertEquals("edited on the phone", (result.single() as TextBlock).text)
    }

    @Test
    fun aNewerDeletionOnDiskReplacesTheEditorCopy() {
        val reconciler = EditorDiskReconciler()

        val result = reconciler.reconcile(
            editorBlocks = listOf(text("a", "still here", updatedAt = 100L)),
            diskBlocks = listOf(text("a", "still here", updatedAt = 200L, isDeleted = true))
        )

        assertEquals(true, result.single().isDeleted)
    }

    @Test
    fun theEditorCopyWinsWhenItIsNewerThanTheDiskCopy() {
        val reconciler = EditorDiskReconciler()

        val result = reconciler.reconcile(
            editorBlocks = listOf(text("a", "just typed", updatedAt = 300L)),
            diskBlocks = listOf(text("a", "last save", updatedAt = 200L))
        )

        assertEquals("just typed", (result.single() as TextBlock).text)
    }

    @Test
    fun aBlockTheCallerKeepsIsNotReplacedEvenByANewerDiskCopy() {
        val reconciler = EditorDiskReconciler()

        val result = reconciler.reconcile(
            editorBlocks = listOf(text("pinned", "lives in global_pinned", updatedAt = 100L)),
            diskBlocks = listOf(text("pinned", "lives in global_pinned", updatedAt = 200L, isDeleted = true)),
            keepsEditorCopy = { true }
        )

        assertEquals(false, result.single().isDeleted)
    }

    @Test
    fun afterForgettingABlockShownInAnotherNoteCountsAsAddedOutsideTheEditor() {
        val reconciler = EditorDiskReconciler()
        reconciler.rememberBlocksShown(listOf(text("moved", "was in the previous note")))
        reconciler.forgetBlocksShown()

        val result = reconciler.reconcile(
            editorBlocks = listOf(text("a", "first")),
            diskBlocks = listOf(text("a", "first"), text("moved", "was in the previous note"))
        )

        assertEquals(listOf("a", "moved"), result.map { it.id })
    }
}
