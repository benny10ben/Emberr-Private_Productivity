package com.emberr.domain.vault

import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.canvas.isGroup
import com.emberr.ui.theme.HighlightColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@Serializable
private data class JsonCanvasFile(
    val nodes: List<JsonCanvasTextNode>,
    val edges: List<JsonCanvasEdge>
)

@Serializable
private data class JsonCanvasTextNode(
    val id: String,
    val type: String,
    val text: String? = null,
    val label: String? = null,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val color: String? = null
)

@Serializable
private data class JsonCanvasEdge(
    val id: String,
    val fromNode: String,
    val fromSide: String,
    val toNode: String,
    val toSide: String
)

object JsonCanvasWriter {

    private val json = Json { prettyPrint = true }

    fun write(canvas: CanvasContent): String {
        val liveCanvas = canvas.liveOnly()
        val file = JsonCanvasFile(
            nodes = liveCanvas.nodes.sortedByDescending { it.isGroup }.map { node ->
                JsonCanvasTextNode(
                    id = node.nodeId,
                    type = if (node.isGroup) "group" else "text",
                    text = node.text.takeUnless { node.isGroup },
                    label = node.text.takeIf { node.isGroup },
                    x = node.x.roundToInt(),
                    y = node.y.roundToInt(),
                    width = node.width.roundToInt(),
                    height = node.height.roundToInt(),
                    color = HighlightColor.entries.firstOrNull { it.storageName == node.color }?.cellBackgroundHex
                )
            },
            edges = liveCanvas.edges.map { edge ->
                JsonCanvasEdge(
                    id = edge.edgeId,
                    fromNode = edge.fromNodeId,
                    fromSide = edge.fromSide.jsonCanvasName(),
                    toNode = edge.toNodeId,
                    toSide = edge.toSide.jsonCanvasName()
                )
            }
        )
        return json.encodeToString(JsonCanvasFile.serializer(), file)
    }

    private fun CanvasSide.jsonCanvasName(): String = name.lowercase()
}
