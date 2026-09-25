package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasSide
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val CANVAS_MIN_ZOOM = 0.25f
const val CANVAS_MAX_ZOOM = 4f
const val CANVAS_MIN_NODE_WIDTH = 80f
const val CANVAS_MIN_NODE_HEIGHT = 40f
const val CANVAS_DEFAULT_NODE_WIDTH = 250f
const val CANVAS_DEFAULT_NODE_HEIGHT = 80f
const val CANVAS_GROUP_PADDING = 24f
const val CANVAS_GROUP_TITLE_HEIGHT = 32f
const val CANVAS_CLUSTER_RADIUS = 600f

data class CanvasViewport(
    val panOffset: Offset = Offset.Zero,
    val zoom: Float = 1f
) {
    fun pixelsPerUnit(density: Float): Float = zoom * density

    fun worldToScreen(world: Offset, density: Float): Offset = world * pixelsPerUnit(density) + panOffset

    fun screenToWorld(screen: Offset, density: Float): Offset = (screen - panOffset) / pixelsPerUnit(density)

    fun worldRectToScreen(world: Rect, density: Float): Rect = Rect(
        topLeft = worldToScreen(world.topLeft, density),
        bottomRight = worldToScreen(world.bottomRight, density)
    )

    fun pannedBy(screenDelta: Offset): CanvasViewport = copy(panOffset = panOffset + screenDelta)

    fun panOffsetCentering(world: Offset, screenCenter: Offset, density: Float): Offset =
        screenCenter - world * pixelsPerUnit(density)

    fun zoomedAround(screenPoint: Offset, zoomFactor: Float, density: Float): CanvasViewport {
        val worldPointUnderCursor = screenToWorld(screenPoint, density)
        val newZoom = (zoom * zoomFactor).coerceIn(CANVAS_MIN_ZOOM, CANVAS_MAX_ZOOM)
        return CanvasViewport(
            panOffset = screenPoint - worldPointUnderCursor * (newZoom * density),
            zoom = newZoom
        )
    }
}

fun canvasViewportCenteredOn(worldCenter: Offset, zoom: Float, screenCenter: Offset, density: Float): CanvasViewport {
    val clampedZoom = zoom.coerceIn(CANVAS_MIN_ZOOM, CANVAS_MAX_ZOOM)
    return CanvasViewport(panOffset = screenCenter - worldCenter * (clampedZoom * density), zoom = clampedZoom)
}

val CanvasNodeEntity.worldRect: Rect
    get() = Rect(left = x, top = y, right = x + width, bottom = y + height)

val CanvasNodeEntity.groupTitleWorldRect: Rect
    get() = Rect(left = x, top = y - CANVAS_GROUP_TITLE_HEIGHT, right = x + width, bottom = y)

fun busiestAreaCenter(boxes: List<Rect>, clusterRadius: Float): Offset? {
    if (boxes.isEmpty()) return null
    val boxCenters = boxes.map { it.center }
    val busiestCluster = boxCenters
        .map { candidate -> boxCenters.filter { (it - candidate).getDistance() <= clusterRadius } }
        .maxBy { cluster -> cluster.size }
    return busiestCluster.reduce { sum, center -> sum + center } / busiestCluster.size.toFloat()
}

fun Rect.expandedToInclude(other: Rect): Rect = Rect(
    left = min(left, other.left),
    top = min(top, other.top),
    right = max(right, other.right),
    bottom = max(bottom, other.bottom)
)

fun rectCenteredOn(center: Offset, width: Float, height: Float): Rect = Rect(
    left = center.x - width / 2f,
    top = center.y - height / 2f,
    right = center.x + width / 2f,
    bottom = center.y + height / 2f
)

fun Rect.anchorOn(side: CanvasSide): Offset = when (side) {
    CanvasSide.TOP -> Offset(center.x, top)
    CanvasSide.RIGHT -> Offset(right, center.y)
    CanvasSide.BOTTOM -> Offset(center.x, bottom)
    CanvasSide.LEFT -> Offset(left, center.y)
}

fun CanvasSide.outwardDirection(): Offset = when (this) {
    CanvasSide.TOP -> Offset(0f, -1f)
    CanvasSide.RIGHT -> Offset(1f, 0f)
    CanvasSide.BOTTOM -> Offset(0f, 1f)
    CanvasSide.LEFT -> Offset(-1f, 0f)
}

fun Rect.sideClosestTo(point: Offset): CanvasSide = listOf(
    CanvasSide.TOP to abs(point.y - top),
    CanvasSide.RIGHT to abs(point.x - right),
    CanvasSide.BOTTOM to abs(point.y - bottom),
    CanvasSide.LEFT to abs(point.x - left)
).minBy { (_, distance) -> distance }.first

data class CanvasResizeEdges(
    val left: Boolean,
    val top: Boolean,
    val right: Boolean,
    val bottom: Boolean
)

fun Rect.resizeEdgesAt(point: Offset, grabDistance: Float): CanvasResizeEdges? {
    if (!inflate(grabDistance).contains(point)) return null
    val distanceToLeft = abs(point.x - left)
    val distanceToRight = abs(point.x - right)
    val distanceToTop = abs(point.y - top)
    val distanceToBottom = abs(point.y - bottom)
    val edges = CanvasResizeEdges(
        left = distanceToLeft <= grabDistance && distanceToLeft <= distanceToRight,
        top = distanceToTop <= grabDistance && distanceToTop <= distanceToBottom,
        right = distanceToRight <= grabDistance && distanceToRight < distanceToLeft,
        bottom = distanceToBottom <= grabDistance && distanceToBottom < distanceToTop
    )
    return edges.takeIf { it.left || it.top || it.right || it.bottom }
}

fun Rect.resizedBy(edges: CanvasResizeEdges, delta: Offset, minimumWidth: Float, minimumHeight: Float): Rect = Rect(
    left = if (edges.left) min(left + delta.x, right - minimumWidth) else left,
    top = if (edges.top) min(top + delta.y, bottom - minimumHeight) else top,
    right = if (edges.right) max(right + delta.x, left + minimumWidth) else right,
    bottom = if (edges.bottom) max(bottom + delta.y, top + minimumHeight) else bottom
)

fun rectBetween(firstCorner: Offset, secondCorner: Offset): Rect = Rect(
    left = min(firstCorner.x, secondCorner.x),
    top = min(firstCorner.y, secondCorner.y),
    right = max(firstCorner.x, secondCorner.x),
    bottom = max(firstCorner.y, secondCorner.y)
)

data class CanvasCurve(
    val start: Offset,
    val startControl: Offset,
    val endControl: Offset,
    val end: Offset
) {
    fun pointAt(progress: Float): Offset {
        val remaining = 1f - progress
        return start * (remaining * remaining * remaining) +
            startControl * (3f * remaining * remaining * progress) +
            endControl * (3f * remaining * progress * progress) +
            end * (progress * progress * progress)
    }

    fun closestProgressTo(point: Offset, samples: Int = 48): Float =
        (0..samples).minBy { step -> (pointAt(step.toFloat() / samples) - point).getDistance() }.toFloat() / samples

    fun distanceTo(point: Offset, samples: Int = 24): Float {
        var closest = Float.MAX_VALUE
        var previous = start
        for (step in 1..samples) {
            val current = pointAt(step.toFloat() / samples)
            closest = min(closest, distanceFromPointToSegment(point, previous, current))
            previous = current
        }
        return closest
    }
}

fun edgeCurve(start: Offset, startSide: CanvasSide?, end: Offset, endSide: CanvasSide?, controlDistance: Float): CanvasCurve =
    CanvasCurve(
        start = start,
        startControl = if (startSide == null) start else start + startSide.outwardDirection() * controlDistance,
        endControl = if (endSide == null) end else end + endSide.outwardDirection() * controlDistance,
        end = end
    )

fun edgeControlDistance(start: Offset, end: Offset, minimum: Float, maximum: Float): Float =
    ((end - start).getDistance() / 2f).coerceIn(minimum, maximum)

private fun distanceFromPointToSegment(point: Offset, segmentStart: Offset, segmentEnd: Offset): Float {
    val segment = segmentEnd - segmentStart
    val lengthSquared = segment.x * segment.x + segment.y * segment.y
    if (lengthSquared == 0f) return (point - segmentStart).getDistance()
    val toPoint = point - segmentStart
    val projection = ((toPoint.x * segment.x + toPoint.y * segment.y) / lengthSquared).coerceIn(0f, 1f)
    return (point - (segmentStart + segment * projection)).getDistance()
}
