package com.emberr.presentation.daily

import com.emberr.domain.model.TextBlock
import com.emberr.presentation.shared.editor.EditorDiskReconciler
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyStoredBlocksTest {

    private fun text(id: String, body: String, updatedAt: Long = 100L, isDeleted: Boolean = false, isPinned: Boolean = false) =
        TextBlock(id = id, text = body, updatedAt = updatedAt, isDeleted = isDeleted, isPinned = isPinned)

    @Test
    fun aBlockMovedIntoThePinnedListCountsAsPinnedNotDeleted() {
        val dayBlocks = listOf(text("p", "task", updatedAt = 200L, isDeleted = true))
        val pinnedBlocks = listOf(text("p", "task", updatedAt = 150L, isPinned = true))

        val result = DailyStoredBlocks.combineDayAndPinnedList(dayBlocks, pinnedBlocks).single()

        assertEquals(false, result.isDeleted)
        assertEquals(true, result.isPinned)
    }

    @Test
    fun aBlockMovedBackIntoTheDayCountsAsUnpinnedNotDeleted() {
        val dayBlocks = listOf(text("u", "task", updatedAt = 150L))
        val pinnedBlocks = listOf(text("u", "task", updatedAt = 200L, isDeleted = true, isPinned = true))

        val result = DailyStoredBlocks.combineDayAndPinnedList(dayBlocks, pinnedBlocks).single()

        assertEquals(false, result.isDeleted)
        assertEquals(false, result.isPinned)
    }

    @Test
    fun aPinnedBlockDeletedFromThePinnedListStaysDeleted() {
        val dayBlocks = listOf(text("p", "task", updatedAt = 100L, isDeleted = true))
        val pinnedBlocks = listOf(text("p", "task", updatedAt = 200L, isDeleted = true, isPinned = true))

        val result = DailyStoredBlocks.combineDayAndPinnedList(dayBlocks, pinnedBlocks).single()

        assertEquals(true, result.isDeleted)
        assertEquals(200L, result.updatedAt)
    }

    @Test
    fun blocksStoredInOnlyOnePlaceAreAllIncluded() {
        val dayBlocks = listOf(text("a", "day block"))
        val pinnedBlocks = listOf(text("q", "pinned block", isPinned = true))

        val result = DailyStoredBlocks.combineDayAndPinnedList(dayBlocks, pinnedBlocks)

        assertEquals(listOf("a", "q"), result.map { it.id })
    }

    @Test
    fun theNewerCopyWinsWhenTheBlockIsLiveInBothPlaces() {
        val dayBlocks = listOf(text("b", "older", updatedAt = 100L))
        val pinnedBlocks = listOf(text("b", "newer", updatedAt = 200L, isPinned = true))

        val result = DailyStoredBlocks.combineDayAndPinnedList(dayBlocks, pinnedBlocks).single()

        assertEquals("newer", (result as TextBlock).text)
    }

    @Test
    fun aBlockPinnedOnAnotherDeviceWhileTheUserTypesIsKeptAndStaysPinned() {
        val reconciler = EditorDiskReconciler()
        val editorBlocks = listOf(text("p", "task", updatedAt = 100L), text("typing", "words", updatedAt = 300L))
        val dayBlocks = listOf(text("p", "task", updatedAt = 200L, isDeleted = true), text("typing", "wor", updatedAt = 250L))
        val pinnedBlocks = listOf(text("p", "task", updatedAt = 150L, isPinned = true))

        val result = reconciler.reconcile(editorBlocks, DailyStoredBlocks.combineDayAndPinnedList(dayBlocks, pinnedBlocks))

        val pinnedBlock = result.first { it.id == "p" }
        assertEquals(false, pinnedBlock.isDeleted)
        assertEquals(true, pinnedBlock.isPinned)
        assertEquals("words", (result.first { it.id == "typing" } as TextBlock).text)
    }

    @Test
    fun aNewPinnedBlockFromAnotherDeviceIsKeptWhileTheUserTypes() {
        val reconciler = EditorDiskReconciler()
        val editorBlocks = listOf(text("typing", "words", updatedAt = 300L))
        val dayBlocks = listOf(text("typing", "wor", updatedAt = 250L))
        val pinnedBlocks = listOf(text("q", "pinned on the laptop", updatedAt = 280L, isPinned = true))

        val result = reconciler.reconcile(editorBlocks, DailyStoredBlocks.combineDayAndPinnedList(dayBlocks, pinnedBlocks))

        assertEquals(listOf("typing", "q"), result.map { it.id })
        assertEquals(true, result.last().isPinned)
    }
}
