package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import com.emberr.data.local.room.entity.CanvasStrokeEntity
import com.emberr.data.local.room.entity.CanvasStrokeTool
import com.emberr.domain.canvas.CanvasStrokePoint
import com.emberr.domain.canvas.defaultStyle
import com.emberr.domain.canvas.freehand.FreehandPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasStrokeDrawingTest {

    private val penWidth = CanvasStrokeTool.PEN.defaultStyle.width
    private val highlighterStyle = CanvasStrokeTool.HIGHLIGHTER.defaultStyle

    private fun straightLine(pressure: Float?) = (0..50).map { step -> CanvasStrokePoint(step * 2f, 0f, pressure) }

    private fun List<FreehandPoint>.height(): Double = maxOf { it.y } - minOf { it.y }

    private fun penOutline(points: List<CanvasStrokePoint>, usesPressure: Boolean = true) =
        canvasStrokeOutline(points, penWidth, usesPressure, isComplete = true)

    private fun strokeFrom(x: Float, y: Float, points: List<CanvasStrokePoint>) = CanvasStrokeEntity(
        strokeId = "stroke",
        noteId = "canvas-1",
        tool = CanvasStrokeTool.PEN,
        x = x,
        y = y,
        points = "",
        width = penWidth,
        createdAt = 1L,
        updatedAt = 1L
    ) to points

    private fun Pair<CanvasStrokeEntity, List<CanvasStrokePoint>>.isTouchedBy(eraserCenter: Offset, eraserRadius: Float) =
        first.isTouchedBy(eraserCenter, eraserRadius, second, second.bounds())

    @Test
    fun aSingleTapLeavesADotAroundThatSpot() {
        val outline = penOutline(listOf(CanvasStrokePoint(40f, 60f)))

        assertTrue(outline.isNotEmpty())
        assertTrue(outline.all { it.x.isFinite() && it.y.isFinite() })
        assertTrue(outline.all { abs(it.x - 40.0) < penWidth && abs(it.y - 60.0) < penWidth })
    }

    @Test
    fun pressingHarderWithAPenDrawsAThickerLine() {
        val hardLine = penOutline(straightLine(pressure = 1f))
        val lightLine = penOutline(straightLine(pressure = 0.1f))

        assertTrue(hardLine.height() > lightLine.height() * 1.5, "hard ${hardLine.height()} light ${lightLine.height()}")
    }

    @Test
    fun aStrokeWherePressureIsMissingForSomePointsIsDrawnAsIfNoneHadPressure() {
        val mixed = straightLine(pressure = null).mapIndexed { index, point -> if (index % 2 == 0) point.copy(pressure = 1f) else point }

        assertEquals(penOutline(straightLine(pressure = null)), penOutline(mixed))
    }

    @Test
    fun aPenWithPressureTurnedOffKeepsTheSameThicknessEvenWithAStylus() {
        val hardLine = penOutline(straightLine(pressure = 1f), usesPressure = false)
        val lightLine = penOutline(straightLine(pressure = 0.1f), usesPressure = false)

        assertEquals(hardLine.height(), lightLine.height(), absoluteTolerance = 0.01)
        assertEquals(penWidth.toDouble(), hardLine.height(), absoluteTolerance = 0.5)
    }

    @Test
    fun theHighlighterStartsOutTheSameWidthNoMatterHowHardYouPress() {
        val hardLine = canvasStrokeOutline(straightLine(pressure = 1f), highlighterStyle.width, highlighterStyle.usesPressure, isComplete = true)
        val lightLine = canvasStrokeOutline(straightLine(pressure = 0.1f), highlighterStyle.width, highlighterStyle.usesPressure, isComplete = true)

        assertEquals(hardLine.height(), lightLine.height(), absoluteTolerance = 0.01)
        assertEquals(highlighterStyle.width.toDouble(), hardLine.height(), absoluteTolerance = 0.5)
    }

    @Test
    fun aPointTooCloseToThePreviousOneIsSkipped() {
        val previous = CanvasStrokePoint(10f, 10f)

        assertTrue(CanvasStrokePoint(0f, 0f).isFarEnoughFrom(null, minimumSpacing = 1f))
        assertFalse(CanvasStrokePoint(10.5f, 10f).isFarEnoughFrom(previous, minimumSpacing = 1f))
        assertTrue(CanvasStrokePoint(10f, 11.5f).isFarEnoughFrom(previous, minimumSpacing = 1f))
    }

    @Test
    fun aFastEraserSwipeIsCheckedAlongTheWholeWayAndNotJustWhereItLanded() {
        val positions = eraserPositionsBetween(Offset(0f, 0f), Offset(100f, 0f), spacing = 10f)

        assertEquals(10, positions.size)
        assertEquals(Offset(100f, 0f), positions.last())
        assertEquals(listOf(Offset(5f, 5f)), eraserPositionsBetween(Offset(5f, 5f), Offset(5f, 5f), spacing = 10f))
    }

    @Test
    fun theDistanceToASegmentIsMeasuredToItsClosestPoint() {
        assertEquals(3f, distanceFromPointToSegment(Offset(5f, 3f), Offset(0f, 0f), Offset(10f, 0f)), absoluteTolerance = 0.001f)
        assertEquals(5f, distanceFromPointToSegment(Offset(13f, 4f), Offset(0f, 0f), Offset(10f, 0f)), absoluteTolerance = 0.001f)
        assertEquals(5f, distanceFromPointToSegment(Offset(3f, 4f), Offset(0f, 0f), Offset(0f, 0f)), absoluteTolerance = 0.001f)
    }

    @Test
    fun theEraserCatchesALineBetweenItsPointsAndMissesItFromFarAway() {
        val line = strokeFrom(x = 0f, y = 0f, points = listOf(CanvasStrokePoint(0f, 0f), CanvasStrokePoint(100f, 0f)))

        assertTrue(line.isTouchedBy(Offset(50f, 8f), eraserRadius = 7f))
        assertFalse(line.isTouchedBy(Offset(50f, 20f), eraserRadius = 7f))
        assertFalse(line.isTouchedBy(Offset(130f, 0f), eraserRadius = 7f))
    }

    @Test
    fun theEraserFindsAStrokeWhereverItWasDrawnOnTheBoard() {
        val movedLine = strokeFrom(x = 500f, y = 300f, points = listOf(CanvasStrokePoint(0f, 0f), CanvasStrokePoint(100f, 0f)))

        assertTrue(movedLine.isTouchedBy(Offset(550f, 300f), eraserRadius = 5f))
        assertFalse(movedLine.isTouchedBy(Offset(50f, 0f), eraserRadius = 5f))
    }

    @Test
    fun theEraserCanHitASingleDot() {
        val dot = strokeFrom(x = 10f, y = 10f, points = listOf(CanvasStrokePoint(0f, 0f)))

        assertTrue(dot.isTouchedBy(Offset(15f, 10f), eraserRadius = 4f))
        assertFalse(dot.isTouchedBy(Offset(30f, 10f), eraserRadius = 4f))
    }

    private fun lineStroke(x: Float, y: Float, points: String) = CanvasStrokeEntity(
        strokeId = "line",
        noteId = "canvas-1",
        tool = CanvasStrokeTool.LINE,
        x = x,
        y = y,
        points = points,
        width = 2f,
        createdAt = 1L,
        updatedAt = 1L,
        usesPressure = false,
        hasArrowHead = true
    )

    @Test
    fun aSavedLineReadsBackAsItsTwoEndsOnTheBoard() {
        val ends = CanvasStrokeCache().lineEndsOf(lineStroke(x = 100f, y = 50f, points = "0,0 300,-200"))

        assertEquals(Offset(100f, 50f) to Offset(130f, 30f), ends)
    }

    @Test
    fun theEraserCatchesALineAnywhereAlongItsLength() {
        val cache = CanvasStrokeCache()
        val line = lineStroke(x = 0f, y = 0f, points = "0,0 1000,0")

        assertTrue(cache.isTouchedByEraser(line, eraserCenter = Offset(55f, 3f), eraserRadius = 3f))
        assertFalse(cache.isTouchedByEraser(line, eraserCenter = Offset(55f, 10f), eraserRadius = 3f))
    }

    @Test
    fun aDottedLineHasADotAtEachEndWithEvenGapsBetween() {
        val dots = evenlySpacedPointsAlong(Offset(0f, 0f), Offset(100f, 0f), spacing = 10f)

        assertEquals(11, dots.size)
        assertEquals(Offset(0f, 0f), dots.first())
        assertEquals(Offset(100f, 0f), dots.last())
        assertEquals(Offset(50f, 0f), dots[5])
    }

    @Test
    fun aDottedLineShorterThanOneGapStillShowsBothEnds() {
        assertEquals(listOf(Offset(0f, 0f), Offset(3f, 4f)), evenlySpacedPointsAlong(Offset(0f, 0f), Offset(3f, 4f), spacing = 10f))
    }
}

