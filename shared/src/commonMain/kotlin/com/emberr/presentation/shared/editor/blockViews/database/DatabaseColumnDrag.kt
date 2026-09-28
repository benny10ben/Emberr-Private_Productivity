package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.util.system.isDesktopPlatform
import kotlinx.coroutines.CancellationException

internal val ColumnAutoScrollEdgeWidth = 48.dp
internal val ColumnAutoScrollMaxStep = 14.dp
private val ColumnDropLineWidth = 2.dp
private const val DraggedColumnTintAlpha = 0.06f

@Composable
internal fun draggedColumnBackground(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = DraggedColumnTintAlpha).compositeOver(MaterialTheme.colorScheme.background)

internal data class HeaderSpan(val left: Float, val right: Float) {
    val center: Float get() = (left + right) / 2
}

internal fun columnToDropBefore(
    columns: List<DatabaseColumnTarget>,
    headerSpans: Map<DatabaseColumnTarget, HeaderSpan>,
    pointerX: Float
): DatabaseColumnTarget? =
    columns.firstOrNull { column -> headerSpans[column]?.let { pointerX < it.center } == true }

internal fun dropKeepsColumnInPlace(
    columns: List<DatabaseColumnTarget>,
    draggedColumn: DatabaseColumnTarget,
    beforeColumn: DatabaseColumnTarget?
): Boolean = beforeColumn == draggedColumn || beforeColumn == columns.getOrNull(columns.indexOf(draggedColumn) + 1)

internal fun columnAutoScrollStep(pointerInViewport: Float, viewportWidth: Float, edgeWidth: Float, maxStep: Float): Float {
    if (viewportWidth <= 0f || edgeWidth <= 0f) return 0f
    val distanceIntoLeftEdge = edgeWidth - pointerInViewport
    val distanceIntoRightEdge = pointerInViewport - (viewportWidth - edgeWidth)
    return when {
        distanceIntoLeftEdge > 0f -> -maxStep * (distanceIntoLeftEdge / edgeWidth).coerceAtMost(1f)
        distanceIntoRightEdge > 0f -> maxStep * (distanceIntoRightEdge / edgeWidth).coerceAtMost(1f)
        else -> 0f
    }
}

@Stable
internal class DatabaseColumnDragState {
    var draggedColumn by mutableStateOf<DatabaseColumnTarget?>(null)
        private set
    var pointerX by mutableStateOf(0f)
    private var grabX by mutableStateOf(0f)
    val headerSpans = mutableStateMapOf<DatabaseColumnTarget, HeaderSpan>()

    val isDragging: Boolean get() = draggedColumn != null

    fun start(column: DatabaseColumnTarget, grabXInHeader: Float) {
        val headerLeft = headerSpans[column]?.left ?: return
        draggedColumn = column
        grabX = grabXInHeader
        pointerX = headerLeft + grabXInHeader
    }

    fun moveTo(column: DatabaseColumnTarget, pointerXInHeader: Float) {
        if (draggedColumn != column) return
        val headerLeft = headerSpans[column]?.left ?: return
        pointerX = headerLeft + pointerXInHeader
    }

    fun offsetOf(column: DatabaseColumnTarget): Float {
        if (draggedColumn != column) return 0f
        val headerLeft = headerSpans[column]?.left ?: return 0f
        return pointerX - headerLeft - grabX
    }

    fun reset() {
        draggedColumn = null
    }
}

internal fun Modifier.columnDropLine(
    dragState: DatabaseColumnDragState,
    columns: List<DatabaseColumnTarget>,
    lineColor: Color
): Modifier = drawWithContent {
    drawContent()
    val draggedColumn = dragState.draggedColumn ?: return@drawWithContent
    val beforeColumn = columnToDropBefore(columns, dragState.headerSpans, dragState.pointerX)
    if (dropKeepsColumnInPlace(columns, draggedColumn, beforeColumn)) return@drawWithContent
    val lineX = (
        if (beforeColumn == null) {
            columns.lastOrNull()?.let { dragState.headerSpans[it]?.right }
        } else {
            dragState.headerSpans[beforeColumn]?.left
        }
    ) ?: return@drawWithContent
    drawLine(lineColor, Offset(lineX, 0f), Offset(lineX, size.height), ColumnDropLineWidth.toPx())
}

internal fun Modifier.raisedWhileColumnDragged(dragState: DatabaseColumnDragState, column: DatabaseColumnTarget): Modifier =
    zIndex(if (dragState.draggedColumn == column) 1f else 0f)

internal fun Modifier.followsColumnDrag(
    dragState: DatabaseColumnDragState,
    column: DatabaseColumnTarget,
    draggedBackground: Color
): Modifier = this
    .graphicsLayer { translationX = dragState.offsetOf(column) }
    .then(if (dragState.draggedColumn == column) Modifier.background(draggedBackground) else Modifier)

internal fun Modifier.columnDragGesture(
    dragState: DatabaseColumnDragState,
    column: DatabaseColumnTarget,
    isEnabled: Boolean,
    onDrop: () -> Unit
): Modifier = pointerInput(column, isEnabled) {
    if (!isEnabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val dragStart = if (isDesktopPlatform) {
            awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
        } else {
            awaitLongPressOrCancellation(down.id)
        } ?: return@awaitEachGesture

        dragState.start(column, grabXInHeader = down.position.x)
        dragState.moveTo(column, dragStart.position.x)
        try {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == dragStart.id }
                if (change == null || change.changedToUpIgnoreConsumed()) {
                    change?.consume()
                    onDrop()
                    return@awaitEachGesture
                }
                dragState.moveTo(column, change.position.x)
                change.consume()
            }
        } catch (cancellation: CancellationException) {
            dragState.reset()
            throw cancellation
        }
    }
}
