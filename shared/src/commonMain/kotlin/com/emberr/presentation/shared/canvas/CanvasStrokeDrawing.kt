package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.emberr.data.local.room.entity.CanvasStrokeEntity
import com.emberr.data.local.room.entity.CanvasStrokeTool
import com.emberr.domain.canvas.CanvasStrokePoint
import com.emberr.domain.canvas.CanvasStrokePoints
import com.emberr.domain.canvas.LINE_PATTERN_DASHED
import com.emberr.domain.canvas.LINE_PATTERN_DOTTED
import com.emberr.domain.canvas.freehand.FreehandInputPoint
import com.emberr.domain.canvas.freehand.FreehandPoint
import com.emberr.domain.canvas.freehand.FreehandStrokeOptions
import com.emberr.domain.canvas.freehand.freehandStroke
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

const val MINIMUM_STROKE_POINT_SPACING_PIXELS = 1.5f
const val HIGHLIGHTER_ALPHA_IN_LIGHT_THEME = 0.35f
const val HIGHLIGHTER_ALPHA_IN_DARK_THEME = 0.55f

private const val PRESSURE_THINNING = 0.6
private const val STROKE_SMOOTHING = 0.5
private const val STROKE_STREAMLINE = 0.5
private const val LINE_ARROW_SIZE_PER_WIDTH = 5f
private const val LINE_MINIMUM_ARROW_SIZE = 8f
private const val ARROW_WING_COSINE = 0.866f
private const val ARROW_WING_SINE = 0.5f
private const val LINE_DASH_LENGTH_PER_WIDTH = 4f
private const val LINE_DASH_GAP_PER_WIDTH = 3f
private const val LINE_DOT_GAP_PER_WIDTH = 2.5f

val CanvasStrokeTool.widthOptions: List<Float>
    get() = when (this) {
        CanvasStrokeTool.PEN -> listOf(2f, 4f, 7f)
        CanvasStrokeTool.HIGHLIGHTER -> listOf(12f, 20f, 32f)
        CanvasStrokeTool.LINE -> listOf(1f, 2f, 4f)
    }

enum class CanvasInkColor(val storageName: String, val color: Color) {
    RED("red", Color(0xFFE03131)),
    GREEN("green", Color(0xFF2F9E44)),
    BLUE("blue", Color(0xFF1971C2)),
    ORANGE("orange", Color(0xFFF08C00));

    companion object {
        fun named(storageName: String?): CanvasInkColor? = entries.firstOrNull { it.storageName == storageName }
    }
}

fun canvasStrokeOutline(
    points: List<CanvasStrokePoint>,
    width: Float,
    usesPressure: Boolean,
    isComplete: Boolean
): List<FreehandPoint> {
    val hasPenPressure = points.isNotEmpty() && points.all { it.pressure != null }
    val options = FreehandStrokeOptions(
        size = width.toDouble(),
        thinning = if (usesPressure) PRESSURE_THINNING else 0.0,
        smoothing = STROKE_SMOOTHING,
        streamline = STROKE_STREAMLINE,
        pressureEasing = { pressure -> sin(pressure * PI / 2) },
        simulatesPressure = !hasPenPressure,
        isComplete = isComplete
    )
    val inputPoints = points.map { point ->
        FreehandInputPoint(point.x.toDouble(), point.y.toDouble(), point.pressure?.toDouble()?.takeIf { hasPenPressure })
    }
    return freehandStroke(inputPoints, options)
}

fun CanvasStrokePoint.isFarEnoughFrom(previous: CanvasStrokePoint?, minimumSpacing: Float): Boolean {
    if (previous == null) return true
    val deltaX = x - previous.x
    val deltaY = y - previous.y
    return deltaX * deltaX + deltaY * deltaY >= minimumSpacing * minimumSpacing
}

fun eraserPositionsBetween(from: Offset, to: Offset, spacing: Float): List<Offset> {
    val steps = ceil((to - from).getDistance() / spacing).toInt().coerceAtLeast(1)
    return (1..steps).map { step -> from + (to - from) * (step.toFloat() / steps) }
}

fun CanvasStrokeEntity.isTouchedBy(
    eraserCenter: Offset,
    eraserRadius: Float,
    relativePoints: List<CanvasStrokePoint>,
    relativeBounds: Rect
): Boolean {
    val reach = eraserRadius + width / 2
    val eraserOnStroke = Offset(eraserCenter.x - x, eraserCenter.y - y)
    if (relativePoints.isEmpty() || !relativeBounds.inflate(reach).contains(eraserOnStroke)) return false
    val offsets = relativePoints.map { Offset(it.x, it.y) }
    if (offsets.size == 1) return (eraserOnStroke - offsets[0]).getDistance() <= reach
    return offsets.zipWithNext().any { (start, end) -> distanceFromPointToSegment(eraserOnStroke, start, end) <= reach }
}

fun List<CanvasStrokePoint>.bounds(): Rect {
    if (isEmpty()) return Rect.Zero
    return Rect(minOf { it.x }, minOf { it.y }, maxOf { it.x }, maxOf { it.y })
}

fun List<FreehandPoint>.toSmoothPath(): Path {
    val path = Path()
    if (isEmpty()) return path
    path.moveTo(this[0].x.toFloat(), this[0].y.toFloat())
    for (index in 0 until lastIndex) {
        val current = this[index]
        val next = this[index + 1]
        path.quadraticTo(
            current.x.toFloat(),
            current.y.toFloat(),
            ((current.x + next.x) / 2).toFloat(),
            ((current.y + next.y) / 2).toFloat()
        )
    }
    path.close()
    return path
}

class CanvasStrokeCache {

    private class CachedStroke(val points: String, val width: Float, val usesPressure: Boolean) {
        val decodedPoints: List<CanvasStrokePoint> by lazy { CanvasStrokePoints.decode(points) }
        val bounds: Rect by lazy { decodedPoints.bounds() }
        val path: Path by lazy { canvasStrokeOutline(decodedPoints, width, usesPressure, isComplete = true).toSmoothPath() }

        fun isBuiltFrom(stroke: CanvasStrokeEntity): Boolean =
            points == stroke.points && width == stroke.width && usesPressure == stroke.usesPressure
    }

    private val strokesById = HashMap<String, CachedStroke>()

    private fun cachedFor(stroke: CanvasStrokeEntity): CachedStroke {
        val cached = strokesById[stroke.strokeId]
        if (cached != null && cached.isBuiltFrom(stroke)) return cached
        return CachedStroke(stroke.points, stroke.width, stroke.usesPressure).also { strokesById[stroke.strokeId] = it }
    }

    fun pathFor(stroke: CanvasStrokeEntity): Path = cachedFor(stroke).path

    fun pointsFor(stroke: CanvasStrokeEntity): List<CanvasStrokePoint> = cachedFor(stroke).decodedPoints

    fun isTouchedByEraser(stroke: CanvasStrokeEntity, eraserCenter: Offset, eraserRadius: Float): Boolean {
        val cached = cachedFor(stroke)
        return stroke.isTouchedBy(eraserCenter, eraserRadius, cached.decodedPoints, cached.bounds)
    }

    fun forgetStrokesNotIn(strokes: List<CanvasStrokeEntity>) {
        if (strokesById.size <= strokes.size) return
        val liveStrokeIds = strokes.mapTo(HashSet()) { it.strokeId }
        strokesById.keys.retainAll(liveStrokeIds)
    }
}

fun DrawScope.drawCanvasLine(
    start: Offset,
    end: Offset,
    width: Float,
    color: Color,
    pattern: String?,
    hasArrowHead: Boolean
) {
    val direction = end - start
    val length = direction.getDistance()
    if (length < 0.01f) return
    if (pattern == LINE_PATTERN_DOTTED) {
        val dots = evenlySpacedPointsAlong(start, end, spacing = width * LINE_DOT_GAP_PER_WIDTH)
        drawPoints(dots, PointMode.Points, color, strokeWidth = width, cap = StrokeCap.Round)
    } else {
        val pathEffect = if (pattern == LINE_PATTERN_DASHED) {
            PathEffect.dashPathEffect(floatArrayOf(width * LINE_DASH_LENGTH_PER_WIDTH, width * LINE_DASH_GAP_PER_WIDTH))
        } else {
            null
        }
        val linePath = Path().apply {
            moveTo(start.x, start.y)
            lineTo(end.x, end.y)
        }
        drawPath(linePath, color, style = Stroke(width = width, cap = StrokeCap.Round, pathEffect = pathEffect))
    }
    if (!hasArrowHead) return
    val wingLength = (width * LINE_ARROW_SIZE_PER_WIDTH).coerceAtLeast(LINE_MINIMUM_ARROW_SIZE).coerceAtMost(length)
    drawOpenArrowHead(tip = end, directionIntoTip = direction / length, wingLength = wingLength, color = color, strokeWidth = width)
}

fun DrawScope.drawOpenArrowHead(tip: Offset, directionIntoTip: Offset, wingLength: Float, color: Color, strokeWidth: Float) {
    val backwards = -directionIntoTip
    val firstWing = Offset(
        backwards.x * ARROW_WING_COSINE - backwards.y * ARROW_WING_SINE,
        backwards.x * ARROW_WING_SINE + backwards.y * ARROW_WING_COSINE
    )
    val secondWing = Offset(
        backwards.x * ARROW_WING_COSINE + backwards.y * ARROW_WING_SINE,
        -backwards.x * ARROW_WING_SINE + backwards.y * ARROW_WING_COSINE
    )
    val arrowPath = Path().apply {
        val firstWingEnd = tip + firstWing * wingLength
        val secondWingEnd = tip + secondWing * wingLength
        moveTo(firstWingEnd.x, firstWingEnd.y)
        lineTo(tip.x, tip.y)
        lineTo(secondWingEnd.x, secondWingEnd.y)
    }
    drawPath(arrowPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

fun evenlySpacedPointsAlong(start: Offset, end: Offset, spacing: Float): List<Offset> {
    val steps = (((end - start).getDistance() / spacing).roundToInt()).coerceAtLeast(1)
    return (0..steps).map { step -> start + (end - start) * (step.toFloat() / steps) }
}

fun CanvasStrokeCache.lineEndsOf(stroke: CanvasStrokeEntity): Pair<Offset, Offset>? {
    val points = pointsFor(stroke)
    if (points.size < 2) return null
    val origin = Offset(stroke.x, stroke.y)
    return origin + Offset(points.first().x, points.first().y) to origin + Offset(points.last().x, points.last().y)
}

