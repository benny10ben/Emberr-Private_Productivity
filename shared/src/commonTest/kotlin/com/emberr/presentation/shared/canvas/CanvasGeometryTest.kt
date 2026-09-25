package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.emberr.data.local.room.entity.CanvasSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasGeometryTest {

    private val density = 2f

    private fun assertClose(expected: Offset, actual: Offset) {
        assertTrue((expected - actual).getDistance() < 0.001f, "expected $expected but was $actual")
    }

    @Test
    fun screenToWorldUndoesWorldToScreen() {
        val viewport = CanvasViewport(panOffset = Offset(37f, -12f), zoom = 1.7f)
        val world = Offset(120f, 45f)

        assertClose(world, viewport.screenToWorld(viewport.worldToScreen(world, density), density))
    }

    @Test
    fun zoomingKeepsThePointUnderTheCursorInPlace() {
        val viewport = CanvasViewport(panOffset = Offset(50f, 80f), zoom = 1f)
        val cursor = Offset(300f, 200f)
        val worldUnderCursor = viewport.screenToWorld(cursor, density)

        val zoomed = viewport.zoomedAround(cursor, zoomFactor = 1.5f, density = density)

        assertEquals(1.5f, zoomed.zoom)
        assertClose(cursor, zoomed.worldToScreen(worldUnderCursor, density))
    }

    @Test
    fun zoomStaysWithinItsLimits() {
        val viewport = CanvasViewport()

        assertEquals(CANVAS_MAX_ZOOM, viewport.zoomedAround(Offset.Zero, 100f, density).zoom)
        assertEquals(CANVAS_MIN_ZOOM, viewport.zoomedAround(Offset.Zero, 0.001f, density).zoom)
    }

    @Test
    fun panningMovesEverythingOnScreenByTheSameAmount() {
        val viewport = CanvasViewport(panOffset = Offset(10f, 10f), zoom = 2f)
        val world = Offset(5f, 5f)

        val panned = viewport.pannedBy(Offset(30f, -20f))

        assertClose(viewport.worldToScreen(world, density) + Offset(30f, -20f), panned.worldToScreen(world, density))
    }

    @Test
    fun theClosestSideIsPickedForADroppedArrow() {
        val box = Rect(left = 0f, top = 0f, right = 200f, bottom = 100f)

        assertEquals(CanvasSide.LEFT, box.sideClosestTo(Offset(5f, 50f)))
        assertEquals(CanvasSide.RIGHT, box.sideClosestTo(Offset(198f, 40f)))
        assertEquals(CanvasSide.TOP, box.sideClosestTo(Offset(100f, 3f)))
        assertEquals(CanvasSide.BOTTOM, box.sideClosestTo(Offset(100f, 97f)))
    }

    @Test
    fun anchorsSitInTheMiddleOfEachSide() {
        val box = Rect(left = 0f, top = 0f, right = 200f, bottom = 100f)

        assertEquals(Offset(100f, 0f), box.anchorOn(CanvasSide.TOP))
        assertEquals(Offset(200f, 50f), box.anchorOn(CanvasSide.RIGHT))
        assertEquals(Offset(100f, 100f), box.anchorOn(CanvasSide.BOTTOM))
        assertEquals(Offset(0f, 50f), box.anchorOn(CanvasSide.LEFT))
    }

    @Test
    fun aBoxDrawnInAnyDirectionBecomesTheSameRectangle() {
        val dragDownRight = rectBetween(Offset(10f, 20f), Offset(110f, 70f))
        val dragUpLeft = rectBetween(Offset(110f, 70f), Offset(10f, 20f))

        assertEquals(dragDownRight, dragUpLeft)
        assertEquals(Rect(10f, 20f, 110f, 70f), dragDownRight)
    }

    @Test
    fun aClickOnTheArrowCountsAsHittingItAndAClickFarAwayDoesNot() {
        val curve = edgeCurve(
            start = Offset(0f, 0f),
            startSide = CanvasSide.RIGHT,
            end = Offset(300f, 0f),
            endSide = CanvasSide.LEFT,
            controlDistance = 50f
        )

        assertTrue(curve.distanceTo(Offset(150f, 1f)) < 2f)
        assertTrue(curve.distanceTo(Offset(150f, 80f)) > 50f)
    }

    @Test
    fun grabbingTheArrowNearItsHeadOrTailIsToldApartByWhichHalfWasGrabbed() {
        val curve = edgeCurve(Offset(0f, 0f), CanvasSide.RIGHT, Offset(300f, 0f), CanvasSide.LEFT, controlDistance = 50f)

        assertTrue(curve.closestProgressTo(Offset(250f, 2f)) > 0.5f)
        assertTrue(curve.closestProgressTo(Offset(40f, -2f)) < 0.5f)
        assertEquals(0f, curve.closestProgressTo(Offset(-100f, 0f)))
        assertEquals(1f, curve.closestProgressTo(Offset(400f, 0f)))
    }

    @Test
    fun aLooseArrowEndWithNoBoxBendsStraightTowardThePointer() {
        val curve = edgeCurve(Offset(10f, 10f), null, Offset(200f, 50f), CanvasSide.LEFT, controlDistance = 40f)

        assertClose(Offset(10f, 10f), curve.startControl)
        assertClose(Offset(160f, 50f), curve.endControl)
    }

    @Test
    fun grabbingNearABoxEdgeOrCornerPicksThoseEdgesForResizing() {
        val box = Rect(left = 0f, top = 0f, right = 200f, bottom = 100f)

        assertEquals(CanvasResizeEdges(left = false, top = false, right = true, bottom = false), box.resizeEdgesAt(Offset(203f, 50f), 6f))
        assertEquals(CanvasResizeEdges(left = true, top = true, right = false, bottom = false), box.resizeEdgesAt(Offset(2f, -3f), 6f))
        assertEquals(CanvasResizeEdges(left = false, top = false, right = true, bottom = true), box.resizeEdgesAt(Offset(198f, 104f), 6f))
        assertEquals(null, box.resizeEdgesAt(Offset(100f, 50f), 6f))
        assertEquals(null, box.resizeEdgesAt(Offset(300f, 50f), 6f))
    }

    @Test
    fun aBoxTooThinForBothEdgesPicksTheCloserOne() {
        val thinBox = Rect(left = 0f, top = 0f, right = 8f, bottom = 100f)

        val edges = thinBox.resizeEdgesAt(Offset(6f, 50f), 6f)

        assertEquals(CanvasResizeEdges(left = false, top = false, right = true, bottom = false), edges)
    }

    @Test
    fun draggingAnEdgeMovesOnlyThatEdgeAndStopsAtTheMinimumSize() {
        val box = Rect(left = 100f, top = 100f, right = 300f, bottom = 200f)
        val leftEdge = CanvasResizeEdges(left = true, top = false, right = false, bottom = false)
        val bottomRightCorner = CanvasResizeEdges(left = false, top = false, right = true, bottom = true)

        assertEquals(Rect(60f, 100f, 300f, 200f), box.resizedBy(leftEdge, Offset(-40f, 25f), 80f, 40f))
        assertEquals(Rect(220f, 100f, 300f, 200f), box.resizedBy(leftEdge, Offset(500f, 0f), 80f, 40f))
        assertEquals(Rect(100f, 100f, 350f, 260f), box.resizedBy(bottomRightCorner, Offset(50f, 60f), 80f, 40f))
        assertEquals(Rect(100f, 100f, 180f, 140f), box.resizedBy(bottomRightCorner, Offset(-900f, -900f), 80f, 40f))
    }

    @Test
    fun aGroupAroundBoxesStretchesToCoverAllOfThem() {
        val first = Rect(left = 0f, top = 0f, right = 100f, bottom = 50f)
        val second = Rect(left = 300f, top = -40f, right = 400f, bottom = 20f)

        assertEquals(Rect(0f, -40f, 400f, 50f), first.expandedToInclude(second))
    }

    @Test
    fun aDoubleClickedBoxIsCenteredOnTheClick() {
        assertEquals(Rect(75f, 60f, 325f, 140f), rectCenteredOn(Offset(200f, 100f), 250f, 80f))
    }

    @Test
    fun theBusiestAreaIsTheMiddleOfTheBiggestClusterOfBoxes() {
        val clusteredBoxes = listOf(
            rectCenteredOn(Offset(1000f, 1000f), 100f, 50f),
            rectCenteredOn(Offset(1100f, 1000f), 100f, 50f),
            rectCenteredOn(Offset(1000f, 1100f), 100f, 50f)
        )
        val loneBox = rectCenteredOn(Offset(-5000f, 0f), 100f, 50f)

        val center = busiestAreaCenter(clusteredBoxes + loneBox, clusterRadius = 600f)

        assertClose(Offset(3100f / 3f, 3100f / 3f), center ?: Offset.Unspecified)
    }

    @Test
    fun anEmptyCanvasHasNoBusiestArea() {
        assertEquals(null, busiestAreaCenter(emptyList(), clusterRadius = 600f))
    }

    @Test
    fun centeringPutsTheWorldPointInTheMiddleOfTheScreen() {
        val viewport = CanvasViewport(panOffset = Offset(40f, 40f), zoom = 1.5f)
        val screenCenter = Offset(400f, 300f)
        val world = Offset(1000f, -200f)

        val centered = viewport.copy(panOffset = viewport.panOffsetCentering(world, screenCenter, density))

        assertClose(screenCenter, centered.worldToScreen(world, density))
    }

    @Test
    fun theArrowStartsAndEndsOnTheBoxAnchors() {
        val curve = edgeCurve(Offset(1f, 2f), CanvasSide.BOTTOM, Offset(40f, 90f), CanvasSide.TOP, controlDistance = 30f)

        assertClose(Offset(1f, 2f), curve.pointAt(0f))
        assertClose(Offset(40f, 90f), curve.pointAt(1f))
        assertClose(Offset(1f, 32f), curve.startControl)
        assertClose(Offset(40f, 60f), curve.endControl)
    }
}
