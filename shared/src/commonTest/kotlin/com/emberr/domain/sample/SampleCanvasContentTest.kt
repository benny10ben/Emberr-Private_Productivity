package com.emberr.domain.sample

import com.emberr.domain.canvas.isGroup
import com.emberr.domain.canvas.membersOf
import com.emberr.presentation.shared.canvas.CANVAS_GROUP_TITLE_HEIGHT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleCanvasContentTest {

    private val canvas = SampleCanvasContent.build(noteId = "canvas_note", createdAt = 1_000L)

    @Test
    fun everyItemBelongsToTheSampleCanvasNote() {
        assertTrue(canvas.nodes.all { it.noteId == "canvas_note" })
        assertTrue(canvas.edges.all { it.noteId == "canvas_note" })
    }

    @Test
    fun nodeAndEdgeIdsAreUnique() {
        assertEquals(canvas.nodes.size, canvas.nodes.map { it.nodeId }.toSet().size)
        assertEquals(canvas.edges.size, canvas.edges.map { it.edgeId }.toSet().size)
    }

    @Test
    fun theTourGroupHoldsTheFirstThreeCardsButNotTheToolbarCard() {
        val tourGroup = canvas.nodes.single { it.isGroup }

        assertEquals(
            setOf("sample_canvas_cards", "sample_canvas_arrows", "sample_canvas_groups"),
            canvas.membersOf(tourGroup).map { it.nodeId }.toSet()
        )
    }

    @Test
    fun everyArrowConnectsTwoCardsThatExist() {
        val nodeIds = canvas.nodes.map { it.nodeId }.toSet()

        assertTrue(canvas.edges.all { it.fromNodeId in nodeIds && it.toNodeId in nodeIds })
    }

    @Test
    fun theCanvasOpensCenteredOnItsContent() {
        val tourGroup = canvas.nodes.single { it.isGroup }
        val contentLeft = canvas.nodes.minOf { it.x }
        val contentRight = canvas.nodes.maxOf { it.x + it.width }
        val contentTop = tourGroup.y - CANVAS_GROUP_TITLE_HEIGHT
        val contentBottom = canvas.nodes.maxOf { it.y + it.height }

        assertEquals((contentLeft + contentRight) / 2f, SampleCanvasContent.startingViewPosition.centerX)
        assertEquals((contentTop + contentBottom) / 2f, SampleCanvasContent.startingViewPosition.centerY)
    }
}
