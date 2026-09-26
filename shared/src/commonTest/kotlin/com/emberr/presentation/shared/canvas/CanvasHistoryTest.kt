package com.emberr.presentation.shared.canvas

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.data.local.room.entity.CanvasStrokeEntity
import com.emberr.data.local.room.entity.CanvasStrokeTool
import com.emberr.domain.canvas.CanvasContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CanvasHistoryTest {

    private fun node(nodeId: String, x: Float = 0f, text: String = "", updatedAt: Long = 1L) = CanvasNodeEntity(
        nodeId = nodeId,
        noteId = "canvas-1",
        x = x,
        y = 0f,
        width = 200f,
        height = 100f,
        text = text,
        createdAt = 1L,
        updatedAt = updatedAt
    )

    private fun edge(edgeId: String) = CanvasEdgeEntity(
        edgeId = edgeId,
        noteId = "canvas-1",
        fromNodeId = "a",
        fromSide = CanvasSide.RIGHT,
        toNodeId = "b",
        toSide = CanvasSide.LEFT,
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun undoingAMoveOnlyTouchesTheMovedBoxAndLeavesAnotherDevicesEditAlone() {
        val history = CanvasHistory()
        val start = CanvasContent(nodes = listOf(node("a", x = 0f), node("b", text = "before")))
        history.beginStep(start)
        history.recordTouchedNode("a")
        val afterMoveAndRemoteEdit = CanvasContent(nodes = listOf(node("a", x = 300f), node("b", text = "edited on another device")))

        val step = assertNotNull(history.takeStepToUndo(afterMoveAndRemoteEdit))

        assertEquals(setOf("a"), step.nodesBefore.keys)
        assertEquals(0f, step.nodesBefore.getValue("a")?.x)
        assertEquals(300f, step.nodesAfter.getValue("a")?.x)
    }

    @Test
    fun aCreatedBoxHasNoBeforeStateAndADeletedArrowHasNoAfterState() {
        val history = CanvasHistory()
        history.beginStep(CanvasContent(edges = listOf(edge("e"))))
        history.recordTouchedNode("new-box")
        history.recordTouchedEdge("e")

        val step = assertNotNull(history.takeStepToUndo(CanvasContent(nodes = listOf(node("new-box")))))

        assertNull(step.nodesBefore.getValue("new-box"))
        assertNotNull(step.nodesAfter.getValue("new-box"))
        assertNotNull(step.edgesBefore.getValue("e"))
        assertNull(step.edgesAfter.getValue("e"))
    }

    @Test
    fun aStepThatTouchedNothingOrEndedWhereItStartedIsNotRemembered() {
        val history = CanvasHistory()
        val content = CanvasContent(nodes = listOf(node("a", updatedAt = 1L)))

        history.beginStep(content)
        history.beginStep(content)
        history.recordTouchedNode("a")
        val sameButRestamped = CanvasContent(nodes = listOf(node("a", updatedAt = 99L)))

        assertNull(history.takeStepToUndo(sameButRestamped))
    }

    @Test
    fun redoGivesBackTheStepThatWasJustUndone() {
        val history = CanvasHistory()
        history.beginStep(CanvasContent())
        history.recordTouchedNode("a")
        val step = history.takeStepToUndo(CanvasContent(nodes = listOf(node("a"))))

        assertEquals(step, history.takeStepToRedo(CanvasContent()))
        assertNull(history.takeStepToRedo(CanvasContent()))
    }

    @Test
    fun aNewChangeAfterUndoClearsTheRedoStack() {
        val history = CanvasHistory()
        history.beginStep(CanvasContent())
        history.recordTouchedNode("a")
        history.takeStepToUndo(CanvasContent(nodes = listOf(node("a"))))

        history.beginStep(CanvasContent())
        history.recordTouchedNode("b")
        history.beginStep(CanvasContent(nodes = listOf(node("b"))))

        assertNull(history.takeStepToRedo(CanvasContent(nodes = listOf(node("b")))))
    }

    @Test
    fun stepsAreUndoneNewestFirst() {
        val history = CanvasHistory()
        history.beginStep(CanvasContent())
        history.recordTouchedNode("first")
        history.beginStep(CanvasContent(nodes = listOf(node("first"))))
        history.recordTouchedNode("second")
        val current = CanvasContent(nodes = listOf(node("first"), node("second")))

        assertEquals(setOf("second"), history.takeStepToUndo(current)?.nodesBefore?.keys)
        assertEquals(setOf("first"), history.takeStepToUndo(current)?.nodesBefore?.keys)
        assertNull(history.takeStepToUndo(current))
    }

    @Test
    fun clearingForgetsEverything() {
        val history = CanvasHistory()
        history.beginStep(CanvasContent())
        history.recordTouchedNode("a")
        history.takeStepToUndo(CanvasContent(nodes = listOf(node("a"))))

        history.clear()

        assertNull(history.takeStepToUndo(CanvasContent()))
        assertNull(history.takeStepToRedo(CanvasContent()))
    }

    private fun stroke(strokeId: String) = CanvasStrokeEntity(
        strokeId = strokeId,
        noteId = "canvas-1",
        tool = CanvasStrokeTool.PEN,
        x = 0f,
        y = 0f,
        points = "0,0 10,10",
        width = 4f,
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun aDrawnStrokeHasNoBeforeStateAndAnErasedStrokeHasNoAfterState() {
        val history = CanvasHistory()
        history.beginStep(CanvasContent(strokes = listOf(stroke("erased"))))
        history.recordTouchedStroke("drawn")
        history.recordTouchedStroke("erased")

        val step = assertNotNull(history.takeStepToUndo(CanvasContent(strokes = listOf(stroke("drawn")))))

        assertNull(step.strokesBefore.getValue("drawn"))
        assertEquals(stroke("drawn"), step.strokesAfter.getValue("drawn"))
        assertEquals(stroke("erased"), step.strokesBefore.getValue("erased"))
        assertNull(step.strokesAfter.getValue("erased"))
    }

    @Test
    fun erasingSeveralStrokesInOneDragIsOneStep() {
        val history = CanvasHistory()
        history.beginStep(CanvasContent(strokes = listOf(stroke("a"), stroke("b"))))
        history.recordTouchedStroke("a")
        history.recordTouchedStroke("b")

        val step = assertNotNull(history.takeStepToUndo(CanvasContent()))

        assertEquals(setOf("a", "b"), step.strokesBefore.keys)
        assertNull(history.takeStepToUndo(CanvasContent()))
    }
}
