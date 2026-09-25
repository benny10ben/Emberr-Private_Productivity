package com.emberr.presentation.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.emberr.presentation.home.DropInsertPosition
import com.emberr.presentation.home.HomeItemKey
import com.emberr.presentation.shared.components.EmberrShadowElevation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal const val DRAG_PREFIX_NOTE   = "note:"
internal const val DRAG_PREFIX_FOLDER = "folder:"

private val WHEEL_SETTLE_TIME = 150.milliseconds
private const val MAX_DRAG_START_JUMP_PX = 55f

internal object ListScrollActivity {
    private var lastWheelScrollAt: TimeMark? = null

    fun recordWheelScroll() {
        lastWheelScrollAt = TimeSource.Monotonic.markNow()
    }

    fun isWheelScrollingRightNow(): Boolean {
        val lastScroll = lastWheelScrollAt ?: return false
        return lastScroll.elapsedNow() < WHEEL_SETTLE_TIME
    }
}

private const val AUTO_SCROLL_EDGE_ZONE_IN_ROWS = 1.5f
private const val AUTO_SCROLL_ROWS_PER_SECOND   = 10f

class DesktopListDragState {
    var dragging     by mutableStateOf(false)
    var payload      by mutableStateOf<String?>(null)
    var dropTargetId by mutableStateOf<String?>(null)
    var dropPosition by mutableStateOf(DropInsertPosition.BEFORE)
    var cursorY      by mutableStateOf(0f)

    fun startDrag(p: String) { payload = p; dragging = true }
    fun endDrag()            { dragging = false; payload = null; dropTargetId = null; cursorY = 0f; dropPosition = DropInsertPosition.BEFORE }
}

@Composable
fun rememberDesktopListDragState() = remember { DesktopListDragState() }

// Drag chip

@Composable
fun DesktopListDragChip(
    dragState: DesktopListDragState,
    labelForPayload: (String) -> String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = dragState.dragging,
        enter = fadeIn(tween(120)) + expandVertically(tween(120)),
        exit  = fadeOut(tween(80)) + shrinkVertically(tween(80)),
        modifier = modifier.zIndex(100f)
    ) {
        val density     = LocalDensity.current
        val label       = dragState.payload?.let { labelForPayload(it) } ?: ""
        val isFolder    = dragState.payload?.startsWith(DRAG_PREFIX_FOLDER) == true
        val chipOffsetY = with(density) { dragState.cursorY.toDp() - 16.dp }

        Row(
            modifier = Modifier
                .offset(y = chipOffsetY.coerceAtLeast(0.dp))
                .padding(start = 12.dp)
                .shadow(EmberrShadowElevation.Standard, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isFolder) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun Modifier.desktopListDragTracker(
    dragState: DesktopListDragState,
    listState: LazyListState,
    rowKeys: List<String?>,
    payloadForKey: (String?) -> String?,
    isDropTarget: (key: String?, payload: String) -> Boolean,
    onDrop: (payload: String, targetKey: String, insertBefore: Boolean) -> Unit,
    rowHeightPx: Float,
    dragThresholdPx: Float = 8f
): Modifier {
    val currentRowKeys       by rememberUpdatedState(rowKeys)
    val currentPayloadForKey by rememberUpdatedState(payloadForKey)
    val currentIsDropTarget  by rememberUpdatedState(isDropTarget)
    val currentOnDrop        by rememberUpdatedState(onDrop)

    val isDragging = dragState.dragging

    LaunchedEffect(isDragging, listState, rowHeightPx) {
        if (!isDragging) return@LaunchedEffect

        var lastFrameTimeNanos = withFrameNanos { it }
        while (true) {
            val frameTimeNanos = withFrameNanos { it }
            val secondsSinceLastFrame = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            lastFrameTimeNanos = frameTimeNanos

            val scrollDistance = autoScrollDistanceForFrame(
                cursorY               = dragState.cursorY,
                listState             = listState,
                rowHeightPx           = rowHeightPx,
                secondsSinceLastFrame = secondsSinceLastFrame
            )
            if (scrollDistance == 0f) continue

            val scrolledDistance = try {
                listState.scrollBy(scrollDistance)
            } catch (scrollTakenOverBySomethingElse: CancellationException) {
                ensureActive()
                0f
            }
            if (scrolledDistance == 0f) continue

            resolveDropTarget(
                cursorY      = dragState.cursorY,
                listState    = listState,
                rowKeys      = currentRowKeys,
                payload      = dragState.payload ?: "",
                isDropTarget = currentIsDropTarget,
                dragState    = dragState
            )
        }
    }

    return this.pointerInput(dragState, listState) {
        awaitPointerEventScope {
            while (true) {
                val press = awaitPointerEvent(PointerEventPass.Initial)
                if (press.type == PointerEventType.Scroll) {
                    ListScrollActivity.recordWheelScroll()
                    continue
                }
                if (press.type != PointerEventType.Press) continue
                if (press.buttons.isSecondaryPressed) continue
                if (!press.buttons.isPrimaryPressed) continue
                if (ListScrollActivity.isWheelScrollingRightNow()) continue
                if (listState.isScrollInProgress) continue
                val pressChange = press.changes.firstOrNull() ?: continue
                if (pressChange.isConsumed) continue
                val pressPos = pressChange.position

                var pressedKey: String? = keyAtY(pressPos.y, listState, currentRowKeys)
                var dragStarted = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: break

                    val pointerIsScrolling = event.type == PointerEventType.Scroll
                    val primaryButtonLetGo = event.type != PointerEventType.Release &&
                            !event.buttons.isPrimaryPressed

                    if (pointerIsScrolling) ListScrollActivity.recordWheelScroll()

                    if (pointerIsScrolling || primaryButtonLetGo) {
                        if (dragStarted) dragState.endDrag()
                        break
                    }

                    when (event.type) {
                        PointerEventType.Move -> {
                            val dist = (change.position - pressPos).getDistance()

                            if (!dragStarted && change.isConsumed) {
                                pressedKey = null
                            }

                            val jumpSinceLastEvent = change.positionChange().getDistance()

                            if (!dragStarted && dist > dragThresholdPx && pressedKey != null &&
                                jumpSinceLastEvent <= MAX_DRAG_START_JUMP_PX
                            ) {
                                val payload = currentPayloadForKey(pressedKey)
                                if (payload != null) {
                                    dragState.startDrag(payload)
                                    dragStarted = true
                                }
                            }

                            if (dragStarted) {
                                change.consume()
                                val cursorY = change.position.y
                                dragState.cursorY = cursorY
                                val payload = dragState.payload ?: ""

                                resolveDropTarget(
                                    cursorY      = cursorY,
                                    listState    = listState,
                                    rowKeys      = currentRowKeys,
                                    payload      = payload,
                                    isDropTarget = currentIsDropTarget,
                                    dragState    = dragState
                                )
                            }
                        }

                        PointerEventType.Release -> {
                            if (dragStarted) {
                                change.consume()
                                val target  = dragState.dropTargetId
                                val payload = dragState.payload
                                if (target != null && payload != null) {
                                    currentOnDrop(
                                        payload,
                                        target,
                                        dragState.dropPosition == DropInsertPosition.BEFORE
                                    )
                                }
                            }
                            dragState.endDrag()
                            pressedKey  = null
                            dragStarted = false
                            break
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

private fun autoScrollDistanceForFrame(
    cursorY: Float,
    listState: LazyListState,
    rowHeightPx: Float,
    secondsSinceLastFrame: Float
): Float {
    val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
    if (viewportHeight <= 0f || rowHeightPx <= 0f) return 0f

    val edgeZone = (rowHeightPx * AUTO_SCROLL_EDGE_ZONE_IN_ROWS).coerceAtMost(viewportHeight / 3f)
    val depthIntoTopEdge    = edgeZone - cursorY
    val depthIntoBottomEdge = cursorY - (viewportHeight - edgeZone)

    val speedFraction = when {
        depthIntoTopEdge    > 0f -> -(depthIntoTopEdge / edgeZone).coerceAtMost(1f)
        depthIntoBottomEdge > 0f ->  (depthIntoBottomEdge / edgeZone).coerceAtMost(1f)
        else                     -> return 0f
    }

    return speedFraction * rowHeightPx * AUTO_SCROLL_ROWS_PER_SECOND * secondsSinceLastFrame
}

private fun resolveDropTarget(
    cursorY: Float,
    listState: LazyListState,
    rowKeys: List<String?>,
    payload: String,
    isDropTarget: (key: String?, payload: String) -> Boolean,
    dragState: DesktopListDragState
) {
    val layoutItems = listState.layoutInfo.visibleItemsInfo
    if (layoutItems.isEmpty()) return

    data class DragItem(
        val key: String,
        val top: Float,
        val bottom: Float,
        val isFolder: Boolean
    ) {
        val center get() = (top + bottom) / 2f
        val height get() = bottom - top
    }

    val draggable = layoutItems
        .mapNotNull { item ->
            val key = rowKeys.getOrNull(item.index) ?: return@mapNotNull null
            if (!isDropTarget(key, payload)) return@mapNotNull null
            val top    = item.offset.toFloat()
            val bottom = top + item.size.toFloat()
            DragItem(key, top, bottom, HomeItemKey.isFolder(key))
        }
        .sortedBy { it.top }

    if (draggable.isEmpty()) return
    if (cursorY < draggable.first().top) {
        dragState.dropTargetId = draggable.first().key
        dragState.dropPosition = DropInsertPosition.BEFORE
        return
    }
    if (cursorY >= draggable.last().bottom) {
        dragState.dropTargetId = draggable.last().key
        dragState.dropPosition = DropInsertPosition.AFTER
        return
    }

    val hit = draggable.firstOrNull { cursorY >= it.top && cursorY < it.bottom }

    if (hit != null) {
        if (hit.isFolder) {
            // BEFORE: top 10% of the row
            // INTO:   middle 80% of the row
            // AFTER:  bottom 10% of the row
            val edgeZone = hit.height * 0.20f
            dragState.dropTargetId = hit.key
            dragState.dropPosition = when {
                cursorY < hit.top    + edgeZone -> DropInsertPosition.BEFORE
                cursorY > hit.bottom - edgeZone -> DropInsertPosition.AFTER
                else                            -> DropInsertPosition.INTO
            }
        } else {
            dragState.dropTargetId = hit.key
            dragState.dropPosition = if (cursorY < hit.center) DropInsertPosition.BEFORE
            else                      DropInsertPosition.AFTER
        }
        return
    }

    var bestItem = draggable.first()
    var bestDist = Float.MAX_VALUE
    for (item in draggable) {
        val d1 = kotlin.math.abs(cursorY - item.top)
        val d2 = kotlin.math.abs(cursorY - item.bottom)
        if (d1 < bestDist) { bestDist = d1; bestItem = item }
        if (d2 < bestDist) { bestDist = d2; bestItem = item }
    }
    dragState.dropTargetId = bestItem.key
    dragState.dropPosition = if (cursorY < bestItem.center) DropInsertPosition.BEFORE
    else                           DropInsertPosition.AFTER
}

private fun keyAtY(y: Float, listState: LazyListState, rowKeys: List<String?>): String? {
    for (item in listState.layoutInfo.visibleItemsInfo) {
        val top    = item.offset.toFloat()
        val bottom = top + item.size.toFloat()
        if (y >= top && y < bottom) return rowKeys.getOrNull(item.index)
    }
    return null
}