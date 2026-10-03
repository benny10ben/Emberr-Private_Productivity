package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val DragAutoScrollEdgeHeight = 64.dp
private val DragAutoScrollMaxStep = 16.dp

@Stable
internal class VerticalDragAutoScroll {
    val dispatcher = NestedScrollDispatcher()
    val connection = object : NestedScrollConnection {}
    var coordinates: LayoutCoordinates? = null
}

internal fun Modifier.verticalDragAutoScroll(autoScroll: VerticalDragAutoScroll): Modifier =
    nestedScroll(autoScroll.connection, autoScroll.dispatcher)
        .onGloballyPositioned { autoScroll.coordinates = it }

@Composable
internal fun rememberVerticalDragAutoScroll(isDragging: Boolean, pointerInRoot: () -> Offset): VerticalDragAutoScroll {
    val autoScroll = remember { VerticalDragAutoScroll() }
    val density = LocalDensity.current
    val latestPointerInRoot by rememberUpdatedState(pointerInRoot)

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        val edgeHeightPx = with(density) { DragAutoScrollEdgeHeight.toPx() }
        val maxStepPx = with(density) { DragAutoScrollMaxStep.toPx() }
        while (true) {
            withFrameNanos { }
            val coordinates = autoScroll.coordinates?.takeIf { it.isAttached } ?: continue
            val visibleBounds = coordinates.boundsInRoot()
            val contentTop = coordinates.positionInRoot().y
            val contentBottom = contentTop + coordinates.size.height
            val step = columnAutoScrollStep(latestPointerInRoot().y - visibleBounds.top, visibleBounds.height, edgeHeightPx, maxStepPx)
            val contentContinuesThatWay = (step < 0f && contentTop < visibleBounds.top - 1f) ||
                (step > 0f && contentBottom > visibleBounds.bottom + 1f)
            if (contentContinuesThatWay) {
                autoScroll.dispatcher.dispatchPostScroll(Offset.Zero, Offset(0f, -step), NestedScrollSource.UserInput)
            }
        }
    }

    return autoScroll
}
