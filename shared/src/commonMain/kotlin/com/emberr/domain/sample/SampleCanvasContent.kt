package com.emberr.domain.sample

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeType
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.canvas.CanvasViewPosition

object SampleCanvasContent {

    private const val CARD_LEFT = 24f
    private const val CARD_WIDTH = 232f
    private const val CARD_HEIGHT = 64f

    val startingViewPosition = CanvasViewPosition(centerX = 140f, centerY = 228f, zoom = 1f)

    private fun card(noteId: String, nodeId: String, top: Float, text: String, colorName: String, createdAt: Long) = CanvasNodeEntity(
        nodeId = nodeId,
        noteId = noteId,
        x = CARD_LEFT,
        y = top,
        width = CARD_WIDTH,
        height = CARD_HEIGHT,
        text = text,
        createdAt = createdAt,
        updatedAt = createdAt,
        color = colorName
    )

    private fun arrow(noteId: String, edgeId: String, fromNodeId: String, toNodeId: String, createdAt: Long) = CanvasEdgeEntity(
        edgeId = edgeId,
        noteId = noteId,
        fromNodeId = fromNodeId,
        fromSide = CanvasSide.BOTTOM,
        toNodeId = toNodeId,
        toSide = CanvasSide.TOP,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    fun build(noteId: String, createdAt: Long): CanvasContent {
        val ideasCard = card(noteId, "sample_canvas_cards", top = 64f, text = "Cards hold your ideas", colorName = "yellow", createdAt = createdAt)
        val arrowsCard = card(noteId, "sample_canvas_arrows", top = 160f, text = "Arrows show how they connect", colorName = "blue", createdAt = createdAt)
        val groupsCard = card(noteId, "sample_canvas_groups", top = 256f, text = "Groups keep related cards together", colorName = "green", createdAt = createdAt)
        val toolbarCard = card(noteId, "sample_canvas_toolbar", top = 384f, text = "Draw, highlight or add images from the toolbar", colorName = "pink", createdAt = createdAt)

        val tourGroup = CanvasNodeEntity(
            nodeId = "sample_canvas_tour_group",
            noteId = noteId,
            x = 0f,
            y = 40f,
            width = 280f,
            height = 304f,
            text = "Canvas tour",
            createdAt = createdAt,
            updatedAt = createdAt,
            type = CanvasNodeType.GROUP
        )

        return CanvasContent(
            nodes = listOf(tourGroup, ideasCard, arrowsCard, groupsCard, toolbarCard),
            edges = listOf(
                arrow(noteId, "sample_canvas_arrow_ideas_to_arrows", ideasCard.nodeId, arrowsCard.nodeId, createdAt),
                arrow(noteId, "sample_canvas_arrow_arrows_to_groups", arrowsCard.nodeId, groupsCard.nodeId, createdAt)
            )
        )
    }
}
