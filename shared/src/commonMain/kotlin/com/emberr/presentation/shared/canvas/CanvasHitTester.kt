package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.canvas.isFreeText
import com.emberr.domain.canvas.isGroup

internal data class CanvasHandle(val nodeId: String, val side: CanvasSide)

internal class CanvasHitTester(
    private val canvas: CanvasContent,
    private val viewport: CanvasViewport,
    private val density: Float
) {

    fun screenRectOf(node: CanvasNodeEntity): Rect = viewport.worldRectToScreen(node.worldRect, density)

    fun screenAnchorOf(node: CanvasNodeEntity, side: CanvasSide): Offset = viewport.worldToScreen(node.anchorOn(side), density)

    fun groupTitleScreenRectOf(group: CanvasNodeEntity): Rect =
        viewport.worldRectToScreen(group.groupTitleWorldRect, density)

    fun textNodesTopFirst(): List<CanvasNodeEntity> = canvas.nodes.filter { !it.isGroup }.asReversed()

    fun groupsSmallestFirst(): List<CanvasNodeEntity> =
        canvas.nodes.filter { it.isGroup }.sortedBy { it.width * it.height }

    fun textNodeAt(screenPoint: Offset, margin: Float = 0f): CanvasNodeEntity? =
        textNodesTopFirst().firstOrNull { screenRectOf(it).inflate(margin).contains(screenPoint) }

    fun groupAt(screenPoint: Offset, margin: Float = 0f): CanvasNodeEntity? =
        groupsSmallestFirst().firstOrNull { group ->
            screenRectOf(group).inflate(margin).contains(screenPoint) ||
                groupTitleScreenRectOf(group).contains(screenPoint)
        }

    fun nodeAt(screenPoint: Offset, margin: Float = 0f): CanvasNodeEntity? =
        textNodeAt(screenPoint, margin) ?: groupAt(screenPoint, margin)

    fun handleAt(screenPoint: Offset, candidateNodeIds: Collection<String>, hitRadius: Float): CanvasHandle? {
        for (node in canvas.nodes.filter { it.nodeId in candidateNodeIds }) {
            val side = CanvasSide.entries.firstOrNull { side ->
                (screenAnchorOf(node, side) - screenPoint).getDistance() <= hitRadius
            }
            if (side != null) return CanvasHandle(node.nodeId, side)
        }
        return null
    }

    fun resizeZoneAt(
        screenPoint: Offset,
        grabDistance: Float,
        onlyNodeIds: Set<String>? = null
    ): Pair<CanvasNodeEntity, CanvasResizeEdges>? {
        for (node in textNodesTopFirst() + groupsSmallestFirst()) {
            if (onlyNodeIds != null && node.nodeId !in onlyNodeIds) continue
            val screenRect = screenRectOf(node)
            if (!node.isFreeText) {
                val edges = screenRect.resizeEdgesAt(screenPoint, grabDistance)
                if (edges != null) return node to edges
            }
            if (screenRect.contains(screenPoint)) return null
        }
        return null
    }

    fun edgeAt(screenPoint: Offset, hitDistance: Float): CanvasEdgeEntity? {
        val nodesById = canvas.nodes.associateBy { it.nodeId }
        return canvas.edges.lastOrNull { edge ->
            val curve = screenCurveFor(edge, nodesById, viewport, density) ?: return@lastOrNull false
            curve.distanceTo(screenPoint) <= hitDistance
        }
    }

    fun snapTargetNear(screenPoint: Offset, anchoredNodeId: String, snapRadius: Float): CanvasHandle? {
        val candidateNodes = (textNodesTopFirst() + groupsSmallestFirst()).filter { it.nodeId != anchoredNodeId }
        val nearestPoint = candidateNodes.flatMap { node ->
            CanvasSide.entries.map { side ->
                CanvasHandle(node.nodeId, side) to (screenAnchorOf(node, side) - screenPoint).getDistance()
            }
        }.minByOrNull { (_, distance) -> distance }
        if (nearestPoint != null && nearestPoint.second <= snapRadius) return nearestPoint.first

        val nodeUnderPointer = candidateNodes.firstOrNull { screenRectOf(it).contains(screenPoint) } ?: return null
        val closestSide = nodeUnderPointer.worldRect.sideClosestTo(viewport.screenToWorld(screenPoint, density))
        return CanvasHandle(nodeUnderPointer.nodeId, closestSide)
    }

    fun editingAreaOf(editingNodeId: String?, edgeGrabDistance: Float): Rect? {
        val editingNode = canvas.nodes.firstOrNull { it.nodeId == editingNodeId } ?: return null
        return when {
            editingNode.isGroup -> groupTitleScreenRectOf(editingNode)
            editingNode.isFreeText -> screenRectOf(editingNode)
            else -> screenRectOf(editingNode).deflate(edgeGrabDistance)
        }
    }
}
