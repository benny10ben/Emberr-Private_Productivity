package com.emberr.domain.ai

import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.canvas.isGroup

private const val ARROW_LABEL_MAX_LENGTH = 80

fun canvasTextForIndexing(canvas: CanvasContent): String {
    val liveCanvas = canvas.liveOnly()
    val nodesById = liveCanvas.nodes.associateBy { it.nodeId }
    val nodeLines = liveCanvas.nodes
        .sortedWith(compareBy({ it.y }, { it.x }))
        .mapNotNull { node ->
            val text = node.text.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            if (node.isGroup) "Group: $text" else text
        }
    val arrowLines = liveCanvas.edges.mapNotNull { edge ->
        val fromLabel = nodesById[edge.fromNodeId]?.let { arrowLabelOf(it) } ?: return@mapNotNull null
        val toLabel = nodesById[edge.toNodeId]?.let { arrowLabelOf(it) } ?: return@mapNotNull null
        "$fromLabel → $toLabel"
    }
    return (nodeLines + arrowLines).joinToString("\n")
}

private fun arrowLabelOf(node: CanvasNodeEntity): String? =
    node.text.trim().lineSequence().firstOrNull()?.trim()?.take(ARROW_LABEL_MAX_LENGTH)?.takeIf { it.isNotEmpty() }
