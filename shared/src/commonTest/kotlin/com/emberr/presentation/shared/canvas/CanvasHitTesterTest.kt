package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeType
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.CanvasContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CanvasHitTesterTest {

    private fun node(nodeId: String, x: Float, y: Float, width: Float, height: Float, type: CanvasNodeType = CanvasNodeType.TEXT) =
        CanvasNodeEntity(
            nodeId = nodeId,
            noteId = "canvas-1",
            x = x,
            y = y,
            width = width,
            height = height,
            text = "",
            createdAt = 1L,
            updatedAt = 1L,
            type = type
        )

    private val group = node("group", x = 0f, y = 0f, width = 600f, height = 400f, type = CanvasNodeType.GROUP)
    private val boxInsideGroup = node("inside", x = 50f, y = 50f, width = 200f, height = 100f)
    private val topBox = node("top", x = 100f, y = 80f, width = 200f, height = 100f)
    private val tester = CanvasHitTester(
        canvas = CanvasContent(nodes = listOf(group, boxInsideGroup, topBox)),
        viewport = CanvasViewport(),
        density = 1f
    )

    @Test
    fun aBoxIsFoundBeforeTheGroupBehindIt() {
        assertEquals("inside", tester.nodeAt(Offset(60f, 60f))?.nodeId)
        assertEquals("group", tester.nodeAt(Offset(500f, 300f))?.nodeId)
    }

    @Test
    fun theBoxCreatedLastIsOnTopWhereBoxesOverlap() {
        assertEquals("top", tester.textNodeAt(Offset(150f, 120f))?.nodeId)
    }

    @Test
    fun theGroupTitleAboveTheGroupCountsAsTheGroup() {
        assertEquals("group", tester.groupAt(Offset(20f, -10f))?.nodeId)
        assertNull(tester.nodeAt(Offset(20f, -100f)))
    }

    @Test
    fun touchResizingOnlyLooksAtTheSelectedBox() {
        val nearTopBoxRightEdge = Offset(298f, 120f)

        assertEquals("top", tester.resizeZoneAt(nearTopBoxRightEdge, 6f)?.first?.nodeId)
        assertNull(tester.resizeZoneAt(nearTopBoxRightEdge, 6f, onlyNodeIds = setOf("inside")))
    }

    @Test
    fun aConnectionPointIsOnlyFoundOnTheCandidateBoxes() {
        val topBoxRightMiddle = Offset(300f, 130f)

        assertEquals(CanvasHandle("top", CanvasSide.RIGHT), tester.handleAt(topBoxRightMiddle, listOf("top"), 10f))
        assertNull(tester.handleAt(topBoxRightMiddle, listOf("inside"), 10f))
    }

    @Test
    fun theEditingAreaOfABoxLeavesItsEdgesFreeForResizing() {
        val area = tester.editingAreaOf("top", edgeGrabDistance = 6f)

        assertEquals(106f, area?.left)
        assertEquals(294f, area?.right)
        assertNull(tester.editingAreaOf(null, 6f))
    }

    @Test
    fun freeTextHasNoResizeEdgesAndStillBlocksResizingTheBoxUnderIt() {
        val freeText = node("text", x = 400f, y = 200f, width = 120f, height = 32f, type = CanvasNodeType.FREE_TEXT)
        val boxUnderText = node("under", x = 390f, y = 190f, width = 300f, height = 200f)
        val textTester = CanvasHitTester(CanvasContent(nodes = listOf(boxUnderText, freeText)), CanvasViewport(), density = 1f)

        assertNull(textTester.resizeZoneAt(Offset(519f, 215f), 6f))
        assertEquals("under", textTester.resizeZoneAt(Offset(689f, 300f), 6f)?.first?.nodeId)
    }

    @Test
    fun theWholeFreeTextIsItsEditingAreaBecauseItCannotBeResized() {
        val freeText = node("text", x = 400f, y = 200f, width = 120f, height = 32f, type = CanvasNodeType.FREE_TEXT)
        val textTester = CanvasHitTester(CanvasContent(nodes = listOf(freeText)), CanvasViewport(), density = 1f)

        val area = textTester.editingAreaOf("text", edgeGrabDistance = 14f)

        assertEquals(400f, area?.left)
        assertEquals(232f, area?.bottom)
    }
}
