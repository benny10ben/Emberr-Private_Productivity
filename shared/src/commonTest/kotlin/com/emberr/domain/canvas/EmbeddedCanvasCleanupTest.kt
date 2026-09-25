package com.emberr.domain.canvas

import com.emberr.domain.canvas.EmbeddedCanvasCleanup.DELETED_BLOCK_GRACE_PERIOD_MILLIS
import com.emberr.domain.model.CanvasBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NOW = 100L * 24 * 60 * 60 * 1000

class EmbeddedCanvasCleanupTest {

    private fun canvasBlock(canvasNoteId: String, isDeleted: Boolean, updatedAt: Long) = CanvasBlock(
        id = "block-$canvasNoteId-$updatedAt",
        canvasNoteId = canvasNoteId,
        isDeleted = isDeleted,
        updatedAt = updatedAt
    )

    private val longAgo = NOW - DELETED_BLOCK_GRACE_PERIOD_MILLIS
    private val recently = NOW - DELETED_BLOCK_GRACE_PERIOD_MILLIS + 1

    @Test
    fun aCanvasWhoseOnlyBlockWasDeletedLongAgoIsDeletedAtLaunch() {
        val result = EmbeddedCanvasCleanup.canvasesToDeleteAtLaunch(
            embeddedCanvasIds = setOf("canvas-a"),
            canvasBlocks = listOf(canvasBlock("canvas-a", isDeleted = true, updatedAt = longAgo)),
            now = NOW
        )

        assertEquals(setOf("canvas-a"), result)
    }

    @Test
    fun aCanvasWhoseBlockWasDeletedRecentlyIsKeptBecauseUndoOrAnotherDeviceMayBringItBack() {
        val result = EmbeddedCanvasCleanup.canvasesToDeleteAtLaunch(
            embeddedCanvasIds = setOf("canvas-a"),
            canvasBlocks = listOf(canvasBlock("canvas-a", isDeleted = true, updatedAt = recently)),
            now = NOW
        )

        assertEquals(emptySet(), result)
    }

    @Test
    fun aCanvasStillShownByALiveBlockIsKeptEvenIfAnotherCopyWasDeleted() {
        val result = EmbeddedCanvasCleanup.canvasesToDeleteAtLaunch(
            embeddedCanvasIds = setOf("canvas-a"),
            canvasBlocks = listOf(
                canvasBlock("canvas-a", isDeleted = true, updatedAt = longAgo),
                canvasBlock("canvas-a", isDeleted = false, updatedAt = longAgo)
            ),
            now = NOW
        )

        assertEquals(emptySet(), result)
    }

    @Test
    fun aCanvasNoBlockMentionsIsKeptBecauseItsNoteMayNotHaveSyncedYet() {
        val result = EmbeddedCanvasCleanup.canvasesToDeleteAtLaunch(
            embeddedCanvasIds = setOf("canvas-a"),
            canvasBlocks = emptyList(),
            now = NOW
        )

        assertEquals(emptySet(), result)
    }

    @Test
    fun blocksPointingAtCanvasesThatAreNotEmbeddedAreIgnored() {
        val result = EmbeddedCanvasCleanup.canvasesToDeleteAtLaunch(
            embeddedCanvasIds = setOf("canvas-a"),
            canvasBlocks = listOf(canvasBlock("canvas-b", isDeleted = true, updatedAt = longAgo)),
            now = NOW
        )

        assertEquals(emptySet(), result)
    }

    @Test
    fun aDeletedBlockStillCountsAsMentioningItsCanvas() {
        val result = EmbeddedCanvasCleanup.canvasesNoBlockMentions(
            embeddedCanvasIds = setOf("canvas-a", "canvas-b", "canvas-c"),
            canvasBlocks = listOf(
                canvasBlock("canvas-a", isDeleted = false, updatedAt = recently),
                canvasBlock("canvas-b", isDeleted = true, updatedAt = recently)
            )
        )

        assertEquals(setOf("canvas-c"), result)
    }

    @Test
    fun anUnmentionedCanvasUntouchedForTheGracePeriodIsOldEnoughToDeleteWhenEmpty() {
        assertTrue(EmbeddedCanvasCleanup.isUntouchedLongEnoughToDeleteWhenEmpty(lastEditedAt = longAgo, now = NOW))
    }

    @Test
    fun anUnmentionedCanvasEditedRecentlyIsNotOldEnoughToDelete() {
        assertFalse(EmbeddedCanvasCleanup.isUntouchedLongEnoughToDeleteWhenEmpty(lastEditedAt = recently, now = NOW))
    }

    @Test
    fun onlyCanvasesWithoutAnyLiveBlockAreReportedAsUnused() {
        val result = EmbeddedCanvasCleanup.canvasesNoLiveBlockUses(
            candidateCanvasIds = setOf("canvas-a", "canvas-b", "canvas-c"),
            canvasBlocks = listOf(
                canvasBlock("canvas-a", isDeleted = false, updatedAt = recently),
                canvasBlock("canvas-b", isDeleted = true, updatedAt = recently)
            )
        )

        assertEquals(setOf("canvas-b", "canvas-c"), result)
    }
}
