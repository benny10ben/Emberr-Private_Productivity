@file:OptIn(ExperimentalFoundationApi::class)

package com.emberr.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import com.emberr.presentation.shared.components.EmberrShadowElevation
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

private const val FOLDER_EDGE_ZONE_FRACTION = 0.25f

class MobileTreeDragState {
    var draggedKey by mutableStateOf<String?>(null)
        internal set
    var dropTargetKey by mutableStateOf<String?>(null)
        internal set
    var dropPosition by mutableStateOf(DropInsertPosition.BEFORE)
        internal set
    var floatingTopLeftInRoot by mutableStateOf(Offset.Zero)
        internal set
    var floatingSize by mutableStateOf(IntSize.Zero)
        internal set
    var pointerInList by mutableStateOf(Offset.Zero)
        internal set

    val isDragging: Boolean get() = draggedKey != null

    fun isDragged(itemKey: String) = draggedKey == itemKey

    fun isInsertBefore(itemKey: String) =
        isDragging && dropTargetKey == itemKey && dropPosition == DropInsertPosition.BEFORE

    fun isInsertAfter(itemKey: String) =
        isDragging && dropTargetKey == itemKey && dropPosition == DropInsertPosition.AFTER

    fun isIntoTarget(itemKey: String) =
        isDragging && dropTargetKey == itemKey && dropPosition == DropInsertPosition.INTO

    internal fun reset() {
        draggedKey = null
        dropTargetKey = null
        dropPosition = DropInsertPosition.BEFORE
        floatingSize = IntSize.Zero
    }
}

@Composable
fun rememberMobileTreeDragState() = remember { MobileTreeDragState() }

val IdleTreeDragState = MobileTreeDragState()

@Composable
fun Modifier.mobileTreeDragSource(
    itemKey: String,
    dragState: MobileTreeDragState,
    gridState: LazyStaggeredGridState,
    blockedTargetKeys: Set<String>,
    dragEnabled: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDrop: (targetKey: String?, position: DropInsertPosition) -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentDragEnabled by rememberUpdatedState(dragEnabled)
    val currentBlockedKeys by rememberUpdatedState(blockedTargetKeys)
    val haptics = LocalHapticFeedback.current

    var rowPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }

    return this
        .onGloballyPositioned {
            rowPositionInRoot = it.positionInRoot()
            rowSize = it.size
        }
        .pointerInput(itemKey) {
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

                val grabOffsetInsideRow = down.position
                var pointerId = down.id

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                        ?: event.changes.firstOrNull { it.pressed }
                        ?: break
                    pointerId = change.id
                    if (!change.pressed) break

                    val fingerInRoot = rowPositionInRoot + change.position
                    change.consume()

                    if (!dragState.isDragging &&
                        (change.position - grabOffsetInsideRow).getDistance() > viewConfiguration.touchSlop
                    ) {
                        dragState.draggedKey = itemKey
                        dragState.floatingSize = rowSize
                    }

                    if (dragState.isDragged(itemKey)) {
                        dragState.floatingTopLeftInRoot = fingerInRoot - grabOffsetInsideRow

                        val rowTopInGrid = rowTopInGrid(itemKey, gridState)
                        if (rowTopInGrid != null) {
                            dragState.pointerInList = Offset(
                                x = change.position.x,
                                y = rowTopInGrid + change.position.y
                            )
                        }
                        resolveTreeDropTarget(
                            pointerInList     = dragState.pointerInList,
                            gridState         = gridState,
                            blockedTargetKeys = currentBlockedKeys,
                            dragState         = dragState
                        )
                    }
                }

                val startedDragHere = dragState.isDragged(itemKey)
                val targetKey = dragState.dropTargetKey
                val position = dragState.dropPosition
                if (startedDragHere) dragState.reset()

                if (startedDragHere) currentOnDrop(targetKey, position) else currentOnLongPress()
            }
        }
}

fun MobileTreeDragState.refreshDropTarget(
    gridState: LazyStaggeredGridState,
    blockedTargetKeys: Set<String>
) {
    if (!isDragging) return
    resolveTreeDropTarget(pointerInList, gridState, blockedTargetKeys, this)
}

fun MobileTreeDragState.edgeScrollDelta(
    gridState: LazyStaggeredGridState,
    edgeSizePx: Float,
    maxStepPx: Float
): Float {
    if (!isDragging || edgeSizePx <= 0f) return 0f
    val viewportHeight = gridState.layoutInfo.viewportSize.height.toFloat()
    if (viewportHeight <= 0f) return 0f

    val y = pointerInList.y
    val fraction = when {
        y < edgeSizePx -> -(1f - y / edgeSizePx)
        y > viewportHeight - edgeSizePx -> (y - (viewportHeight - edgeSizePx)) / edgeSizePx
        else -> 0f
    }
    return fraction.coerceIn(-1f, 1f) * maxStepPx
}

private fun rowTopInGrid(itemKey: String, gridState: LazyStaggeredGridState): Float? =
    gridState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.key == itemKey }
        ?.offset?.y?.toFloat()

private data class TreeRowBounds(val key: String, val top: Float, val bottom: Float) {
    val center get() = (top + bottom) / 2f
    val height get() = bottom - top
}

private fun resolveTreeDropTarget(
    pointerInList: Offset,
    gridState: LazyStaggeredGridState,
    blockedTargetKeys: Set<String>,
    dragState: MobileTreeDragState
) {
    val rows = gridState.layoutInfo.visibleItemsInfo
        .mapNotNull { info ->
            val key = info.key as? String ?: return@mapNotNull null
            if (!HomeItemKey.isFolder(key) && !HomeItemKey.isNote(key)) return@mapNotNull null
            if (key in blockedTargetKeys) return@mapNotNull null
            TreeRowBounds(
                key = key,
                top = info.offset.y.toFloat(),
                bottom = (info.offset.y + info.size.height).toFloat()
            )
        }
        .sortedBy { it.top }

    if (rows.isEmpty()) {
        dragState.dropTargetKey = null
        return
    }

    val pointerY = pointerInList.y

    if (pointerY < rows.first().top) {
        dragState.dropTargetKey = rows.first().key
        dragState.dropPosition = DropInsertPosition.BEFORE
        return
    }
    if (pointerY >= rows.last().bottom) {
        dragState.dropTargetKey = rows.last().key
        dragState.dropPosition = DropInsertPosition.AFTER
        return
    }

    val hovered = rows.firstOrNull { pointerY >= it.top && pointerY < it.bottom }
        ?: rows.minByOrNull { min(abs(pointerY - it.top), abs(pointerY - it.bottom)) }
        ?: return

    val edgeZone = hovered.height * FOLDER_EDGE_ZONE_FRACTION
    dragState.dropTargetKey = hovered.key
    dragState.dropPosition = when {
        !HomeItemKey.isFolder(hovered.key) ->
            if (pointerY < hovered.center) DropInsertPosition.BEFORE else DropInsertPosition.AFTER

        pointerY < hovered.top + edgeZone -> DropInsertPosition.BEFORE
        pointerY > hovered.bottom - edgeZone -> DropInsertPosition.AFTER
        else -> DropInsertPosition.INTO
    }
}

@Composable
fun Modifier.mobileTreeFloatingRow(
    dragState: MobileTreeDragState,
    listOriginInRoot: Offset
): Modifier =
    this.graphicsLayer {
        val topLeft = dragState.floatingTopLeftInRoot - listOriginInRoot
        translationX = topLeft.x
        translationY = topLeft.y
        alpha = 0.95f
        scaleX = 1.02f
        scaleY = 1.02f
        shadowElevation = EmberrShadowElevation.StandardPx
    }
