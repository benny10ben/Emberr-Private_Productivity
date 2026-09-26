package com.emberr.domain.ai

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeType
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.CanvasContent
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasIndexTextTest {

    private fun node(
        nodeId: String,
        text: String,
        x: Float = 0f,
        y: Float = 0f,
        type: CanvasNodeType = CanvasNodeType.TEXT,
        isDeleted: Boolean = false
    ) = CanvasNodeEntity(
        nodeId = nodeId,
        noteId = "canvas-1",
        x = x,
        y = y,
        width = 200f,
        height = 100f,
        text = text,
        createdAt = 1L,
        updatedAt = 1L,
        isDeleted = isDeleted,
        type = type
    )

    private fun arrow(fromNodeId: String, toNodeId: String) = CanvasEdgeEntity(
        edgeId = "$fromNodeId-$toNodeId",
        noteId = "canvas-1",
        fromNodeId = fromNodeId,
        fromSide = CanvasSide.RIGHT,
        toNodeId = toNodeId,
        toSide = CanvasSide.LEFT,
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun boxesAreReadTopToBottomThenLeftToRight() {
        val canvas = CanvasContent(
            nodes = listOf(
                node("lower", "Second row", x = 0f, y = 300f),
                node("right", "Top right", x = 400f, y = 0f),
                node("left", "Top left", x = 0f, y = 0f)
            )
        )

        assertEquals("Top left\nTop right\nSecond row", canvasTextForIndexing(canvas))
    }

    @Test
    fun groupTitlesAreLabelledAndFreeTextIsIncluded() {
        val canvas = CanvasContent(
            nodes = listOf(
                node("group", "Launch plan", y = 0f, type = CanvasNodeType.GROUP),
                node("note", "Remember the demo", y = 50f, type = CanvasNodeType.FREE_TEXT)
            )
        )

        assertEquals("Group: Launch plan\nRemember the demo", canvasTextForIndexing(canvas))
    }

    @Test
    fun arrowsBecomeRelationshipsUsingTheFirstLineOfEachBox() {
        val canvas = CanvasContent(
            nodes = listOf(
                node("idea", "Idea\nwith details", y = 0f),
                node("plan", "Plan", y = 200f)
            ),
            edges = listOf(arrow("idea", "plan"))
        )

        assertEquals("Idea\nwith details\nPlan\nIdea → Plan", canvasTextForIndexing(canvas))
    }

    @Test
    fun deletedAndEmptyBoxesAreLeftOutAndAnEmptyCanvasHasNoText() {
        val canvas = CanvasContent(
            nodes = listOf(
                node("gone", "Removed", isDeleted = true),
                node("blank", "   "),
                node("kept", "Kept", y = 10f)
            ),
            edges = listOf(arrow("blank", "kept"))
        )

        assertEquals("Kept", canvasTextForIndexing(canvas))
        assertEquals("", canvasTextForIndexing(CanvasContent()))
    }
}
