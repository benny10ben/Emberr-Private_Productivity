package com.emberr.domain.sync

import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanNoteContentMergeTest {

    private fun text(id: String, body: String, updatedAt: Long) =
        TextBlock(id = id, text = body, updatedAt = updatedAt)

    private fun contentOf(vararg blocks: TextBlock) = NoteContent(blocks = blocks.toList())

    private fun textAt(content: NoteContent, index: Int) = (content.blocks[index] as TextBlock).text

    @Test
    fun aDeviceWithNoLocalCopyJustTakesTheIncomingOne() {
        val remote = contentOf(text("block-1", "theirs", 100L))

        val merged = NoteMergeHelper.mergeNoteContent(
            localContent = null,
            localUpdatedAt = 0L,
            remoteContent = remote,
            remoteUpdatedAt = 100L
        )

        assertEquals(remote, merged)
    }

    @Test
    fun theNewerSideDecidesTheBlockOrder() {
        val local = contentOf(text("block-1", "one", 100L), text("block-2", "two", 100L))
        val remote = contentOf(text("block-2", "two", 100L), text("block-1", "one", 100L))

        val merged = NoteMergeHelper.mergeNoteContent(local, 100L, remote, 200L)

        assertEquals(listOf("block-2", "block-1"), merged.blocks.map { it.id })
    }

    @Test
    fun onAnExactTieTheLocalOrderIsKept() {
        val local = contentOf(text("block-1", "one", 100L), text("block-2", "two", 100L))
        val remote = contentOf(text("block-2", "two", 100L), text("block-1", "one", 100L))

        val merged = NoteMergeHelper.mergeNoteContent(local, 100L, remote, 100L)

        assertEquals(listOf("block-1", "block-2"), merged.blocks.map { it.id })
    }

    @Test
    fun theNewerEditOfEachIndividualBlockWinsRegardlessOfWhichSideWonOverall() {
        val local = contentOf(text("block-1", "edited here last", 500L))
        val remote = contentOf(text("block-1", "edited there first", 100L))

        val merged = NoteMergeHelper.mergeNoteContent(local, 100L, remote, 200L)

        assertEquals("edited here last", textAt(merged, 0))
    }

    @Test
    fun aBlockEditedOnlyOnTheOtherSideIsPickedUp() {
        val local = contentOf(text("block-1", "stale", 100L))
        val remote = contentOf(text("block-1", "fresh", 500L))

        val merged = NoteMergeHelper.mergeNoteContent(local, 500L, remote, 100L)

        assertEquals("fresh", textAt(merged, 0))
    }

    @Test
    fun aBlockOnlyTheOtherSideHasIsSlottedInAfterTheBlockItFollowedThere() {
        val local = contentOf(
            text("block-a", "a", 100L),
            text("block-b", "b", 100L),
            text("block-c", "c", 100L),
            text("block-d", "d", 100L)
        )
        val remote = contentOf(text("block-a", "a", 100L), text("block-c", "c", 100L))

        val merged = NoteMergeHelper.mergeNoteContent(local, 100L, remote, 200L)

        assertEquals(
            listOf("block-a", "block-b", "block-c", "block-d"),
            merged.blocks.map { it.id }
        )
    }

    @Test
    fun aBlockOnlyTheOtherSideHasAtTheVeryTopLandsAtTheTop() {
        val local = contentOf(text("block-a", "a", 100L), text("block-b", "b", 100L))
        val remote = contentOf(text("block-b", "b", 100L))

        val merged = NoteMergeHelper.mergeNoteContent(local, 100L, remote, 200L)

        assertEquals(listOf("block-a", "block-b"), merged.blocks.map { it.id })
    }

    @Test
    fun nothingIsEverListedTwiceAfterAMerge() {
        val local = contentOf(text("block-1", "one", 100L), text("block-2", "two", 100L))
        val remote = contentOf(text("block-2", "two", 200L), text("block-3", "three", 200L))

        val merged = NoteMergeHelper.mergeNoteContent(local, 100L, remote, 200L)

        assertEquals(merged.blocks.size, merged.blocks.map { it.id }.distinct().size)
        assertEquals(setOf("block-1", "block-2", "block-3"), merged.blocks.map { it.id }.toSet())
    }

    @Test
    fun aMergedNoteIsAlwaysStampedWithTheCurrentContentVersion() {
        val merged = NoteMergeHelper.mergeNoteContent(
            localContent = contentOf(text("block-1", "one", 100L)),
            localUpdatedAt = 100L,
            remoteContent = contentOf(text("block-1", "one", 100L)),
            remoteUpdatedAt = 200L
        )

        assertEquals(1, merged.version)
        assertNull(merged.blocks.firstOrNull { it.id != "block-1" })
    }
}
