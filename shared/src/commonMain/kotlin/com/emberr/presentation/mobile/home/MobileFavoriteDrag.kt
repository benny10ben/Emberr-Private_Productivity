package com.emberr.presentation.mobile.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.emberr.presentation.shared.components.EmberrShadowElevation
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

const val FAVORITE_CARD_KEY_PREFIX = "fav_"

private val InsertLineSpec = tween<Float>(durationMillis = 160, easing = FastOutSlowInEasing)

class MobileFavoriteDragState {
    var draggedNoteId by mutableStateOf<String?>(null)
        internal set
    var targetNoteId by mutableStateOf<String?>(null)
        internal set
    var insertBefore by mutableStateOf(true)
        internal set
    var travelX by mutableStateOf(0f)
        internal set
    var pointerXInRow by mutableStateOf(0f)
        internal set

    val isDragging: Boolean get() = draggedNoteId != null

    fun isDragged(noteId: String) = draggedNoteId == noteId

    fun isInsertBefore(noteId: String) =
        isDragging && targetNoteId == noteId && insertBefore

    fun isInsertAfter(noteId: String) =
        isDragging && targetNoteId == noteId && !insertBefore

    internal fun reset() {
        draggedNoteId = null
        targetNoteId = null
        insertBefore = true
        travelX = 0f
        pointerXInRow = 0f
    }
}

@Composable
fun rememberMobileFavoriteDragState() = remember { MobileFavoriteDragState() }

@Composable
fun Modifier.mobileFavoriteDragSource(
    noteId: String,
    dragState: MobileFavoriteDragState,
    rowState: LazyListState,
    dragEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDrop: (targetNoteId: String?, insertBefore: Boolean) -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentDragEnabled by rememberUpdatedState(dragEnabled)
    val haptics = LocalHapticFeedback.current

    return this.pointerInput(noteId) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            var releasedEarly = false
            val tap = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation().also { if (it == null) releasedEarly = true }
            }
            if (tap != null) {
                currentOnClick()
                return@awaitEachGesture
            }
            if (releasedEarly) return@awaitEachGesture

            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

            if (!currentDragEnabled) {
                waitForUpOrCancellation()
                currentOnLongPress()
                return@awaitEachGesture
            }

            currentEvent.changes.forEach { it.consume() }

            val grabOffsetInsideCard = down.position
            var pointerId = down.id

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                    ?: event.changes.firstOrNull { it.pressed }
                    ?: break
                pointerId = change.id
                if (!change.pressed) break

                change.consume()

                if (!dragState.isDragging &&
                    (change.position - grabOffsetInsideCard).getDistance() > viewConfiguration.touchSlop
                ) {
                    dragState.draggedNoteId = noteId
                }

                if (dragState.isDragged(noteId)) {
                    dragState.travelX = change.position.x - grabOffsetInsideCard.x
                    val cardStart = cardStartInRow(noteId, rowState)
                    if (cardStart != null) {
                        dragState.pointerXInRow = cardStart + change.position.x
                    }
                    resolveFavoriteDropTarget(
                        pointerXInRow = dragState.pointerXInRow,
                        rowState      = rowState,
                        draggedNoteId = noteId,
                        dragState     = dragState
                    )
                }
            }

            val startedDragHere = dragState.isDragged(noteId)
            val targetNoteId = dragState.targetNoteId
            val insertBefore = dragState.insertBefore
            if (startedDragHere) dragState.reset()

            if (startedDragHere) currentOnDrop(targetNoteId, insertBefore) else currentOnLongPress()
        }
    }
}

fun MobileFavoriteDragState.refreshDropTarget(rowState: LazyListState) {
    val noteId = draggedNoteId ?: return
    resolveFavoriteDropTarget(pointerXInRow, rowState, noteId, this)
}

fun MobileFavoriteDragState.edgeScrollDelta(
    rowState: LazyListState,
    edgeSizePx: Float,
    maxStepPx: Float
): Float {
    if (!isDragging || edgeSizePx <= 0f) return 0f
    val viewportWidth = (rowState.layoutInfo.viewportEndOffset - rowState.layoutInfo.viewportStartOffset).toFloat()
    if (viewportWidth <= 0f) return 0f

    val x = pointerXInRow
    val fraction = when {
        x < edgeSizePx -> -(1f - x / edgeSizePx)
        x > viewportWidth - edgeSizePx -> (x - (viewportWidth - edgeSizePx)) / edgeSizePx
        else -> 0f
    }
    return fraction.coerceIn(-1f, 1f) * maxStepPx
}

@Composable
fun Modifier.mobileFavoriteDraggedCard(
    dragState: MobileFavoriteDragState,
    noteId: String
): Modifier = this.graphicsLayer {
    if (!dragState.isDragged(noteId)) return@graphicsLayer
    translationX = dragState.travelX
    scaleX = 1.04f
    scaleY = 1.04f
    alpha = 0.95f
    shadowElevation = EmberrShadowElevation.StandardPx
}

@Composable
fun BoxScope.MobileFavoriteInsertLine(visible: Boolean, atStart: Boolean) {
    val lineAlpha by animateFloatAsState(
        if (visible) 1f else 0f,
        InsertLineSpec,
        label = "favorite_insert_line"
    )
    if (lineAlpha <= 0f) return

    Box(
        modifier = Modifier.matchParentSize(),
        contentAlignment = if (atStart) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = lineAlpha))
        )
    }
}

private fun cardStartInRow(noteId: String, rowState: LazyListState): Float? =
    rowState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.key == "$FAVORITE_CARD_KEY_PREFIX$noteId" }
        ?.offset
        ?.toFloat()

private data class FavoriteCardBounds(val noteId: String, val start: Float, val end: Float) {
    val center get() = (start + end) / 2f
}

private fun resolveFavoriteDropTarget(
    pointerXInRow: Float,
    rowState: LazyListState,
    draggedNoteId: String,
    dragState: MobileFavoriteDragState
) {
    val cards = rowState.layoutInfo.visibleItemsInfo
        .mapNotNull { info ->
            val key = info.key as? String ?: return@mapNotNull null
            if (!key.startsWith(FAVORITE_CARD_KEY_PREFIX)) return@mapNotNull null
            val cardNoteId = key.removePrefix(FAVORITE_CARD_KEY_PREFIX)
            if (cardNoteId == draggedNoteId) return@mapNotNull null
            FavoriteCardBounds(
                noteId = cardNoteId,
                start = info.offset.toFloat(),
                end = (info.offset + info.size).toFloat()
            )
        }
        .sortedBy { it.start }

    if (cards.isEmpty()) {
        dragState.targetNoteId = null
        return
    }

    val hovered = cards.firstOrNull { pointerXInRow >= it.start && pointerXInRow < it.end }
        ?: if (pointerXInRow < cards.first().start) cards.first() else cards.last()

    dragState.targetNoteId = hovered.noteId
    dragState.insertBefore = pointerXInRow < hovered.center
}
