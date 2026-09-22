package com.emberr.presentation.shared.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs

private const val SETTLE_FRACTION = 0.16f
private const val SETTLE_THRESHOLD_PX = 0.5f
private const val MIN_STEP_PX = 1f
private const val SMALLEST_WHOLE_NOTCH = 1f

val DefaultTrackpadNotch: Dp = 34.dp
val DefaultMouseWheelNotch: Dp = 90.dp

@Composable
fun Modifier.smoothWheelScroll(
    state: ScrollableState,
    pixelsPerTrackpadNotch: Dp = DefaultTrackpadNotch,
    pixelsPerMouseWheelNotch: Dp = DefaultMouseWheelNotch,
    horizontal: Boolean = false
): Modifier {
    if (!isDesktopPlatform) return this

    val density = LocalDensity.current
    val trackpadDistancePx = with(density) { pixelsPerTrackpadNotch.toPx() }
    val mouseWheelDistancePx = with(density) { pixelsPerMouseWheelNotch.toPx() }
    val glide = remember(state) { WheelGlide() }

    LaunchedEffect(state) {
        while (true) {
            glide.wakeups.receive()
            state.scroll {
                while (true) {
                    withFrameNanos {}
                    val remaining = glide.pendingPx
                    if (abs(remaining) < SETTLE_THRESHOLD_PX) {
                        glide.pendingPx = 0f
                        break
                    }
                    val requested = remaining * SETTLE_FRACTION
                    val consumed = scrollBy(if (abs(requested) < MIN_STEP_PX) remaining else requested)
                    glide.pendingPx -= consumed
                    if (abs(consumed) < 0.01f) {
                        glide.pendingPx = 0f
                        break
                    }
                }
            }
        }
    }

    return this.pointerInput(state, trackpadDistancePx, mouseWheelDistancePx, horizontal) {
        awaitPointerEventScope {
            while (true) {
                var claimedDistancePx = 0f

                val initial = awaitPointerEvent(PointerEventPass.Initial)
                if (initial.type == PointerEventType.Scroll) {
                    val change = initial.changes.firstOrNull()
                    val rawDelta = change?.let {
                        with(it.scrollDelta) { if (horizontal && y == 0f) x else y }
                    } ?: 0f
                    val isWholeWheelNotch = abs(rawDelta) >= SMALLEST_WHOLE_NOTCH
                    val distancePerNotch =
                        if (isWholeWheelNotch) mouseWheelDistancePx else trackpadDistancePx
                    val distance = rawDelta.coerceIn(-1f, 1f) * distancePerNotch
                    val canTravel =
                        if (distance > 0f) state.canScrollForward else state.canScrollBackward

                    if (distance != 0f && canTravel) {
                        change?.consume()
                        claimedDistancePx = distance
                        WheelDispatch.innermostClaimant = glide
                    }
                }

                awaitPointerEvent(PointerEventPass.Final)
                if (claimedDistancePx != 0f && WheelDispatch.innermostClaimant === glide) {
                    glide.pendingPx += claimedDistancePx
                    glide.wakeups.trySend(Unit)
                }
            }
        }
    }
}

private class WheelGlide {
    var pendingPx = 0f
    val wakeups = Channel<Unit>(Channel.CONFLATED)
}

private object WheelDispatch {
    var innermostClaimant: Any? = null
}
