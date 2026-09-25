package com.emberr.domain.canvas

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeType
import kotlinx.serialization.Serializable

@Serializable
data class CanvasContent(
    val nodes: List<CanvasNodeEntity> = emptyList(),
    val edges: List<CanvasEdgeEntity> = emptyList()
) {
    fun isEmpty(): Boolean = nodes.isEmpty() && edges.isEmpty()

    fun withNoteId(noteId: String): CanvasContent = CanvasContent(
        nodes = nodes.map { it.copy(noteId = noteId) },
        edges = edges.map { it.copy(noteId = noteId) }
    )

    fun liveOnly(): CanvasContent {
        val liveNodes = nodes.filter { !it.isDeleted }
        val liveNodeIds = liveNodes.mapTo(HashSet()) { it.nodeId }
        return CanvasContent(
            nodes = liveNodes,
            edges = edges.filter { !it.isDeleted && it.fromNodeId in liveNodeIds && it.toNodeId in liveNodeIds }
        )
    }

    fun copiedForNote(noteId: String, now: Long, newId: () -> String): CanvasContent {
        val live = liveOnly()
        val copiedNodeIds = live.nodes.associate { it.nodeId to newId() }
        return CanvasContent(
            nodes = live.nodes.map { node ->
                node.copy(nodeId = copiedNodeIds.getValue(node.nodeId), noteId = noteId, updatedAt = now)
            },
            edges = live.edges.map { edge ->
                edge.copy(
                    edgeId = newId(),
                    noteId = noteId,
                    fromNodeId = copiedNodeIds.getValue(edge.fromNodeId),
                    toNodeId = copiedNodeIds.getValue(edge.toNodeId),
                    updatedAt = now
                )
            }
        )
    }
}

val CanvasNodeEntity.isGroup: Boolean
    get() = type == CanvasNodeType.GROUP

fun CanvasNodeEntity.isInside(group: CanvasNodeEntity): Boolean =
    x >= group.x && y >= group.y && x + width <= group.x + group.width && y + height <= group.y + group.height

fun CanvasContent.membersOf(group: CanvasNodeEntity): List<CanvasNodeEntity> =
    nodes.filter { it.nodeId != group.nodeId && it.isInside(group) }

object CanvasMerge {

    fun remoteItemsNewerThanLocal(local: CanvasContent, remote: CanvasContent): CanvasContent {
        val localNodesById = local.nodes.associateBy { it.nodeId }
        val localEdgesById = local.edges.associateBy { it.edgeId }
        return CanvasContent(
            nodes = remote.nodes.filter { remoteNode ->
                val localNode = localNodesById[remoteNode.nodeId]
                localNode == null || remoteNode.updatedAt > localNode.updatedAt
            }.map { remoteNode ->
                val localNode = localNodesById[remoteNode.nodeId]
                if (localNode == null) remoteNode else remoteNode.copy(type = localNode.type)
            },
            edges = remote.edges.filter { remoteEdge ->
                val localEdge = localEdgesById[remoteEdge.edgeId]
                localEdge == null || remoteEdge.updatedAt > localEdge.updatedAt
            }
        )
    }
}
