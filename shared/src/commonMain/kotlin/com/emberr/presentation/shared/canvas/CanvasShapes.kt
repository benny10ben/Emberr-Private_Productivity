package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeShape
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.isImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val CANVAS_DOUBLE_LINE_GAP = 5f
private const val HEXAGON_HEIGHT_TO_CORNER_INSET = 0.2887f

data class CanvasTextPadding(val start: Float, val top: Float, val end: Float, val bottom: Float) {
    companion object {
        fun symmetric(horizontal: Float, vertical: Float) = CanvasTextPadding(horizontal, vertical, horizontal, vertical)
    }
}

val CanvasNodeShape.isPlainCard: Boolean
    get() = this == CanvasNodeShape.RECTANGLE || this == CanvasNodeShape.SQUARE

val CanvasNodeShape.hasRectangularTextArea: Boolean
    get() = isPlainCard || this == CanvasNodeShape.DOUBLE_RECTANGLE || this == CanvasNodeShape.DOUBLE_SQUARE

val CanvasNodeShape.keepsEqualSides: Boolean
    get() = this == CanvasNodeShape.SQUARE || this == CanvasNodeShape.CIRCLE ||
        this == CanvasNodeShape.DOUBLE_SQUARE || this == CanvasNodeShape.DOUBLE_CIRCLE

val CanvasNodeShape.defaultWorldSize: Size
    get() = when (this) {
        CanvasNodeShape.RECTANGLE -> Size(CANVAS_DEFAULT_NODE_WIDTH, CANVAS_DEFAULT_NODE_HEIGHT)
        CanvasNodeShape.SQUARE, CanvasNodeShape.CIRCLE,
        CanvasNodeShape.DOUBLE_SQUARE, CanvasNodeShape.DOUBLE_CIRCLE -> Size(160f, 160f)
        CanvasNodeShape.OVAL -> Size(240f, 150f)
        CanvasNodeShape.TRIANGLE, CanvasNodeShape.DOUBLE_TRIANGLE -> Size(200f, 170f)
        CanvasNodeShape.DIAMOND -> Size(220f, 150f)
        CanvasNodeShape.PENTAGON -> Size(190f, 180f)
        CanvasNodeShape.HEXAGON -> Size(220f, 150f)
        CanvasNodeShape.PARALLELOGRAM -> Size(250f, 100f)
        CanvasNodeShape.TRAPEZOID -> Size(250f, 110f)
        CanvasNodeShape.PILL -> Size(250f, 80f)
        CanvasNodeShape.DATABASE -> Size(170f, 190f)
        CanvasNodeShape.DOUBLE_RECTANGLE -> Size(250f, 90f)
    }

fun CanvasNodeShape.textPadding(width: Float, height: Float): CanvasTextPadding = when (this) {
    CanvasNodeShape.RECTANGLE, CanvasNodeShape.SQUARE -> CanvasTextPadding.symmetric(12f, 10f)
    CanvasNodeShape.DOUBLE_RECTANGLE, CanvasNodeShape.DOUBLE_SQUARE ->
        CanvasTextPadding.symmetric(12f + CANVAS_DOUBLE_LINE_GAP, 10f + CANVAS_DOUBLE_LINE_GAP)
    CanvasNodeShape.CIRCLE, CanvasNodeShape.OVAL, CanvasNodeShape.DOUBLE_CIRCLE ->
        CanvasTextPadding.symmetric(width * 0.15f, height * 0.15f)
    CanvasNodeShape.TRIANGLE, CanvasNodeShape.DOUBLE_TRIANGLE ->
        CanvasTextPadding(width * 0.25f, height * 0.45f, width * 0.25f, height * 0.08f)
    CanvasNodeShape.DIAMOND -> CanvasTextPadding.symmetric(width * 0.25f, height * 0.25f)
    CanvasNodeShape.PENTAGON -> CanvasTextPadding(width * 0.18f, height * 0.3f, width * 0.18f, height * 0.1f)
    CanvasNodeShape.HEXAGON -> CanvasTextPadding.symmetric(hexagonCornerInset(width, height) + 8f, 10f)
    CanvasNodeShape.PARALLELOGRAM, CanvasNodeShape.TRAPEZOID ->
        CanvasTextPadding.symmetric(slantedSideInset(width, height) + 8f, 10f)
    CanvasNodeShape.PILL -> CanvasTextPadding.symmetric(height * 0.3f + 12f, 10f)
    CanvasNodeShape.DATABASE -> {
        val rimHeight = databaseRimHeight(width, height)
        CanvasTextPadding(12f, rimHeight + 8f, 12f, rimHeight / 2f + 8f)
    }
}

fun Rect.resizedForShape(shape: CanvasNodeShape, edges: CanvasResizeEdges, delta: Offset): Rect {
    val resized = resizedBy(edges, delta, CANVAS_MIN_NODE_WIDTH, CANVAS_MIN_NODE_HEIGHT)
    return if (shape.keepsEqualSides) resized.withEqualSides(edges, minimumSide = CANVAS_MIN_NODE_WIDTH) else resized
}

fun Rect.resizedFor(node: CanvasNodeEntity, edges: CanvasResizeEdges, delta: Offset): Rect =
    if (node.isImage) resizedKeepingAspectRatio(edges, delta, minimumShortSide = CANVAS_MIN_NODE_HEIGHT) else resizedForShape(node.shape, edges, delta)

fun Rect.resizedKeepingAspectRatio(grabbedEdges: CanvasResizeEdges, delta: Offset, minimumShortSide: Float): Rect {
    val isChangingWidth = grabbedEdges.left || grabbedEdges.right
    val isChangingHeight = grabbedEdges.top || grabbedEdges.bottom
    val draggedWidth = width + if (grabbedEdges.left) -delta.x else if (grabbedEdges.right) delta.x else 0f
    val draggedHeight = height + if (grabbedEdges.top) -delta.y else if (grabbedEdges.bottom) delta.y else 0f
    val scale = when {
        isChangingWidth && isChangingHeight -> max(draggedWidth / width, draggedHeight / height)
        isChangingWidth -> draggedWidth / width
        else -> draggedHeight / height
    }.coerceAtLeast(minimumShortSide / min(width, height))
    val newWidth = width * scale
    val newHeight = height * scale
    val newLeft = if (grabbedEdges.left) right - newWidth else left
    val newTop = if (grabbedEdges.top) bottom - newHeight else top
    return Rect(newLeft, newTop, newLeft + newWidth, newTop + newHeight)
}

fun Rect.withEqualSides(grabbedEdges: CanvasResizeEdges, minimumSide: Float): Rect {
    val isChangingWidth = grabbedEdges.left || grabbedEdges.right
    val isChangingHeight = grabbedEdges.top || grabbedEdges.bottom
    val side = when {
        isChangingWidth && isChangingHeight -> max(width, height)
        isChangingWidth -> width
        else -> height
    }.coerceAtLeast(minimumSide)
    val newLeft = if (grabbedEdges.left) right - side else left
    val newTop = if (grabbedEdges.top) bottom - side else top
    return Rect(newLeft, newTop, newLeft + side, newTop + side)
}

fun CanvasNodeShape.polygonCorners(size: Size): List<Offset>? {
    val width = size.width
    val height = size.height
    return when (this) {
        CanvasNodeShape.TRIANGLE, CanvasNodeShape.DOUBLE_TRIANGLE ->
            listOf(Offset(width / 2f, 0f), Offset(width, height), Offset(0f, height))
        CanvasNodeShape.DIAMOND ->
            listOf(Offset(width / 2f, 0f), Offset(width, height / 2f), Offset(width / 2f, height), Offset(0f, height / 2f))
        CanvasNodeShape.PENTAGON -> listOf(
            Offset(width / 2f, 0f),
            Offset(width, height * 0.38f),
            Offset(width * 0.81f, height),
            Offset(width * 0.19f, height),
            Offset(0f, height * 0.38f)
        )
        CanvasNodeShape.HEXAGON -> {
            val inset = hexagonCornerInset(width, height)
            listOf(
                Offset(inset, 0f),
                Offset(width - inset, 0f),
                Offset(width, height / 2f),
                Offset(width - inset, height),
                Offset(inset, height),
                Offset(0f, height / 2f)
            )
        }
        CanvasNodeShape.PARALLELOGRAM -> {
            val slant = slantedSideInset(width, height)
            listOf(Offset(slant, 0f), Offset(width, 0f), Offset(width - slant, height), Offset(0f, height))
        }
        CanvasNodeShape.TRAPEZOID -> {
            val slant = slantedSideInset(width, height)
            listOf(Offset(slant, 0f), Offset(width - slant, 0f), Offset(width, height), Offset(0f, height))
        }
        else -> null
    }
}

fun CanvasNodeEntity.anchorOn(side: CanvasSide): Offset =
    Offset(x, y) + shape.anchorOffsetOn(side, Size(width, height))

fun CanvasNodeShape.anchorOffsetOn(side: CanvasSide, size: Size): Offset {
    val boxAnchor = Rect(Offset.Zero, size).anchorOn(side)
    val corners = polygonCorners(size) ?: return boxAnchor
    val isVerticalLine = side == CanvasSide.TOP || side == CanvasSide.BOTTOM
    val crossings = outlineCrossings(corners, lineAt = if (isVerticalLine) boxAnchor.x else boxAnchor.y, isVerticalLine)
    return when (side) {
        CanvasSide.TOP -> crossings.minOrNull()?.let { Offset(boxAnchor.x, it) }
        CanvasSide.BOTTOM -> crossings.maxOrNull()?.let { Offset(boxAnchor.x, it) }
        CanvasSide.LEFT -> crossings.minOrNull()?.let { Offset(it, boxAnchor.y) }
        CanvasSide.RIGHT -> crossings.maxOrNull()?.let { Offset(it, boxAnchor.y) }
    } ?: boxAnchor
}

private fun outlineCrossings(corners: List<Offset>, lineAt: Float, isVerticalLine: Boolean): List<Float> {
    fun Offset.alongLine() = if (isVerticalLine) x else y
    fun Offset.acrossLine() = if (isVerticalLine) y else x
    return corners.indices.mapNotNull { index ->
        val start = corners[index]
        val end = corners[(index + 1) % corners.size]
        val isParallelToLine = start.alongLine() == end.alongLine()
        val missesLine = lineAt < min(start.alongLine(), end.alongLine()) || lineAt > max(start.alongLine(), end.alongLine())
        if (isParallelToLine || missesLine) return@mapNotNull null
        val progress = (lineAt - start.alongLine()) / (end.alongLine() - start.alongLine())
        start.acrossLine() + (end.acrossLine() - start.acrossLine()) * progress
    }
}

fun triangleCornersInsetBy(corners: List<Offset>, distance: Float): List<Offset> {
    val (first, second, third) = corners
    val sideOppositeFirst = (second - third).getDistance()
    val sideOppositeSecond = (first - third).getDistance()
    val sideOppositeThird = (first - second).getDistance()
    val perimeter = sideOppositeFirst + sideOppositeSecond + sideOppositeThird
    val incenter = (first * sideOppositeFirst + second * sideOppositeSecond + third * sideOppositeThird) / perimeter
    val doubledArea = abs((second.x - first.x) * (third.y - first.y) - (third.x - first.x) * (second.y - first.y))
    val inradius = doubledArea / perimeter
    val scale = ((inradius - distance) / inradius).coerceAtLeast(0f)
    return corners.map { corner -> incenter + (corner - incenter) * scale }
}

fun CanvasNodeShape.outlinePath(size: Size, cornerRadius: Float): Path {
    val shape = this
    val bounds = Rect(Offset.Zero, size)
    val corners = polygonCorners(size)
    return Path().apply {
        when {
            corners != null -> addPolygon(corners)
            shape == CanvasNodeShape.CIRCLE || shape == CanvasNodeShape.OVAL || shape == CanvasNodeShape.DOUBLE_CIRCLE ->
                addOval(bounds)
            shape == CanvasNodeShape.PILL -> addRoundRect(RoundRect(bounds, CornerRadius(size.height / 2f)))
            shape == CanvasNodeShape.DATABASE -> addCylinder(size)
            else -> addRoundRect(RoundRect(bounds, CornerRadius(cornerRadius)))
        }
    }
}

fun CanvasNodeShape.detailLinePath(size: Size, doubleLineGap: Float, cornerRadius: Float): Path? {
    val innerBounds = Rect(Offset.Zero, size).deflate(doubleLineGap)
    return when (this) {
        CanvasNodeShape.DOUBLE_RECTANGLE, CanvasNodeShape.DOUBLE_SQUARE -> Path().apply {
            addRoundRect(RoundRect(innerBounds, CornerRadius((cornerRadius - doubleLineGap).coerceAtLeast(0f))))
        }
        CanvasNodeShape.DOUBLE_CIRCLE -> Path().apply { addOval(innerBounds) }
        CanvasNodeShape.DOUBLE_TRIANGLE -> Path().apply {
            addPolygon(triangleCornersInsetBy(polygonCorners(size).orEmpty(), doubleLineGap))
        }
        CanvasNodeShape.DATABASE -> Path().apply {
            val rimHeight = databaseRimHeight(size.width, size.height)
            arcTo(Rect(0f, 0f, size.width, rimHeight), 0f, 180f, forceMoveTo = true)
        }
        else -> null
    }
}

class CanvasNodeOutlineShape(private val nodeShape: CanvasNodeShape, private val cornerRadius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(nodeShape.outlinePath(size, with(density) { cornerRadius.toPx() }))
}

private fun Path.addPolygon(corners: List<Offset>) {
    corners.forEachIndexed { index, corner ->
        if (index == 0) moveTo(corner.x, corner.y) else lineTo(corner.x, corner.y)
    }
    close()
}

private fun Path.addCylinder(size: Size) {
    val rimHeight = databaseRimHeight(size.width, size.height)
    moveTo(0f, rimHeight / 2f)
    arcTo(Rect(0f, 0f, size.width, rimHeight), 180f, 180f, forceMoveTo = false)
    lineTo(size.width, size.height - rimHeight / 2f)
    arcTo(Rect(0f, size.height - rimHeight, size.width, size.height), 0f, 180f, forceMoveTo = false)
    close()
}

private fun hexagonCornerInset(width: Float, height: Float): Float = min(width / 4f, height * HEXAGON_HEIGHT_TO_CORNER_INSET)

private fun slantedSideInset(width: Float, height: Float): Float = min(width / 4f, height / 2f)

private fun databaseRimHeight(width: Float, height: Float): Float = min(height * 0.3f, width * 0.25f)
