package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.emberr.data.local.room.entity.CanvasNodeShape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasShapesTest {

    private val box = Rect(left = 100f, top = 100f, right = 300f, bottom = 300f)
    private val rightEdge = CanvasResizeEdges(left = false, top = false, right = true, bottom = false)
    private val bottomEdge = CanvasResizeEdges(left = false, top = false, right = false, bottom = true)
    private val topLeftCorner = CanvasResizeEdges(left = true, top = true, right = false, bottom = false)
    private val bottomRightCorner = CanvasResizeEdges(left = false, top = false, right = true, bottom = true)

    private fun assertClose(expected: Offset, actual: Offset) {
        assertTrue((expected - actual).getDistance() < 0.01f, "expected $expected but was $actual")
    }

    @Test
    fun draggingOneSideOfASquareResizesBothSides() {
        assertEquals(Rect(100f, 100f, 350f, 350f), box.resizedForShape(CanvasNodeShape.SQUARE, rightEdge, Offset(50f, 0f)))
        assertEquals(Rect(100f, 100f, 250f, 250f), box.resizedForShape(CanvasNodeShape.CIRCLE, bottomEdge, Offset(0f, -50f)))
    }

    @Test
    fun draggingACornerOfASquareFollowsTheLongerSideAndKeepsTheOppositeCornerFixed() {
        assertEquals(Rect(100f, 100f, 360f, 360f), box.resizedForShape(CanvasNodeShape.SQUARE, bottomRightCorner, Offset(60f, 10f)))
        assertEquals(Rect(40f, 40f, 300f, 300f), box.resizedForShape(CanvasNodeShape.DOUBLE_CIRCLE, topLeftCorner, Offset(-60f, -10f)))
    }

    @Test
    fun aSquareNeverShrinksBelowTheMinimumBoxWidth() {
        val shrunk = box.resizedForShape(CanvasNodeShape.SQUARE, bottomEdge, Offset(0f, -500f))

        assertEquals(CANVAS_MIN_NODE_WIDTH, shrunk.width)
        assertEquals(CANVAS_MIN_NODE_WIDTH, shrunk.height)
    }

    @Test
    fun otherShapesResizeFreely() {
        assertEquals(Rect(100f, 100f, 350f, 300f), box.resizedForShape(CanvasNodeShape.RECTANGLE, rightEdge, Offset(50f, 0f)))
        assertEquals(Rect(100f, 100f, 350f, 300f), box.resizedForShape(CanvasNodeShape.OVAL, rightEdge, Offset(50f, 0f)))
    }

    @Test
    fun theInnerLineOfADoubleTriangleIsTheSameDistanceFromEverySide() {
        val corners = CanvasNodeShape.DOUBLE_TRIANGLE.polygonCorners(Size(200f, 170f)).orEmpty()

        val inner = triangleCornersInsetBy(corners, distance = 5f)

        assertClose(Offset(100f, 170f - 5f), Offset((inner[1].x + inner[2].x) / 2f, inner[1].y))
        assertEquals(inner[1].y, inner[2].y)
        assertEquals(100f, inner[0].x, 0.01f)
        val slantLength = (corners[1] - corners[0]).getDistance()
        val slantDirection = (corners[1] - corners[0]) / slantLength
        val innerApexFromOuterApex = inner[0] - corners[0]
        val distanceFromSlant = abs(innerApexFromOuterApex.x * slantDirection.y - innerApexFromOuterApex.y * slantDirection.x)
        assertEquals(5f, distanceFromSlant, 0.01f)
    }

    @Test
    fun polygonShapesStayInsideTheirBox() {
        val size = Size(220f, 150f)
        CanvasNodeShape.entries.forEach { shape ->
            shape.polygonCorners(size)?.forEach { corner ->
                assertTrue(corner.x in 0f..size.width && corner.y in 0f..size.height, "$shape has a corner outside at $corner")
            }
        }
    }

    @Test
    fun roundShapesAndBoxesAreNotPolygons() {
        listOf(CanvasNodeShape.RECTANGLE, CanvasNodeShape.CIRCLE, CanvasNodeShape.PILL, CanvasNodeShape.DATABASE).forEach { shape ->
            assertNull(shape.polygonCorners(Size(100f, 100f)))
        }
    }

    @Test
    fun aNewRectangleKeepsTheOldDefaultBoxSize() {
        assertEquals(Size(CANVAS_DEFAULT_NODE_WIDTH, CANVAS_DEFAULT_NODE_HEIGHT), CanvasNodeShape.RECTANGLE.defaultWorldSize)
    }

    @Test
    fun shapesThatKeepEqualSidesStartAsSquares() {
        CanvasNodeShape.entries.filter { it.keepsEqualSides }.forEach { shape ->
            assertEquals(shape.defaultWorldSize.width, shape.defaultWorldSize.height, "$shape")
        }
    }
}
