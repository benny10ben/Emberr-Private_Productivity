package com.emberr.presentation.shared.canvas

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.domain.canvas.CanvasContent

private const val MAXIMUM_HISTORY_STEPS = 100

data class CanvasHistoryStep(
    val nodesBefore: Map<String, CanvasNodeEntity?>,
    val nodesAfter: Map<String, CanvasNodeEntity?>,
    val edgesBefore: Map<String, CanvasEdgeEntity?>,
    val edgesAfter: Map<String, CanvasEdgeEntity?>
)

class CanvasHistory {

    private val undoSteps = ArrayDeque<CanvasHistoryStep>()
    private val redoSteps = ArrayDeque<CanvasHistoryStep>()
    private var openStepStart: CanvasContent? = null
    private val openStepNodeIds = LinkedHashSet<String>()
    private val openStepEdgeIds = LinkedHashSet<String>()

    fun beginStep(current: CanvasContent) {
        closeStep(current)
        openStepStart = current
    }

    fun recordTouchedNode(nodeId: String) {
        if (openStepStart != null) openStepNodeIds.add(nodeId)
    }

    fun recordTouchedEdge(edgeId: String) {
        if (openStepStart != null) openStepEdgeIds.add(edgeId)
    }

    fun takeStepToUndo(current: CanvasContent): CanvasHistoryStep? {
        closeStep(current)
        val step = undoSteps.removeLastOrNull() ?: return null
        redoSteps.addLast(step)
        return step
    }

    fun takeStepToRedo(current: CanvasContent): CanvasHistoryStep? {
        closeStep(current)
        val step = redoSteps.removeLastOrNull() ?: return null
        undoSteps.addLast(step)
        return step
    }

    fun clear() {
        undoSteps.clear()
        redoSteps.clear()
        openStepStart = null
        openStepNodeIds.clear()
        openStepEdgeIds.clear()
    }

    private fun closeStep(current: CanvasContent) {
        val start = openStepStart ?: return
        openStepStart = null
        val touchedNodeIds = openStepNodeIds.toList()
        val touchedEdgeIds = openStepEdgeIds.toList()
        openStepNodeIds.clear()
        openStepEdgeIds.clear()
        if (touchedNodeIds.isEmpty() && touchedEdgeIds.isEmpty()) return

        val nodesAtStart = start.nodes.associateBy { it.nodeId }
        val nodesNow = current.nodes.associateBy { it.nodeId }
        val edgesAtStart = start.edges.associateBy { it.edgeId }
        val edgesNow = current.edges.associateBy { it.edgeId }
        val step = CanvasHistoryStep(
            nodesBefore = touchedNodeIds.associateWith { nodesAtStart[it] },
            nodesAfter = touchedNodeIds.associateWith { nodesNow[it] },
            edgesBefore = touchedEdgeIds.associateWith { edgesAtStart[it] },
            edgesAfter = touchedEdgeIds.associateWith { edgesNow[it] }
        )
        if (step.changesNothing()) return

        undoSteps.addLast(step)
        if (undoSteps.size > MAXIMUM_HISTORY_STEPS) undoSteps.removeFirst()
        redoSteps.clear()
    }

    private fun CanvasHistoryStep.changesNothing(): Boolean =
        nodesBefore.all { (nodeId, before) -> before.withoutTimestamp() == nodesAfter[nodeId].withoutTimestamp() } &&
            edgesBefore.all { (edgeId, before) -> before.withoutTimestamp() == edgesAfter[edgeId].withoutTimestamp() }

    private fun CanvasNodeEntity?.withoutTimestamp(): CanvasNodeEntity? = this?.copy(updatedAt = 0L)

    private fun CanvasEdgeEntity?.withoutTimestamp(): CanvasEdgeEntity? = this?.copy(updatedAt = 0L)
}
