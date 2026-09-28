package com.emberr.presentation.shared.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round

enum class MenuCascadeDirection { RIGHT, LEFT }

internal val LocalMenuCascadeDirection = compositionLocalOf { MenuCascadeDirection.RIGHT }

internal class MenuAtTapPlacement(
    val opensLeftward: Boolean,
    val menuWidth: Dp,
    val direction: MenuCascadeDirection,
    val onMenuPositioned: (menuLeftInWindow: Float) -> Unit
)

internal val LocalMenuAtTapPlacement = compositionLocalOf<MenuAtTapPlacement?> { null }

@Stable
class MenuTapAnchor {
    var tapPosition by mutableStateOf(Offset.Zero)
    internal var tapXInWindow: Float = 0f
}

@Composable
fun rememberMenuTapAnchor(): MenuTapAnchor = remember { MenuTapAnchor() }

fun Modifier.menuTapAnchor(anchor: MenuTapAnchor): Modifier = pointerInput(anchor) {
    awaitEachGesture {
        anchor.tapPosition = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
    }
}

@Composable
fun MenuAtTap(anchor: MenuTapAnchor, menuWidth: Dp, menu: @Composable () -> Unit) {
    val parentDirection = LocalMenuCascadeDirection.current
    var direction by remember { mutableStateOf(parentDirection) }
    val placement = MenuAtTapPlacement(
        opensLeftward = parentDirection == MenuCascadeDirection.LEFT,
        menuWidth = menuWidth,
        direction = direction,
        onMenuPositioned = { menuLeftInWindow ->
            direction = if (menuLeftInWindow + 1f < anchor.tapXInWindow) MenuCascadeDirection.LEFT else MenuCascadeDirection.RIGHT
        }
    )

    Box(
        modifier = Modifier
            .offset { anchor.tapPosition.round() }
            .size(0.dp)
            .onGloballyPositioned { anchor.tapXInWindow = it.positionInWindow().x }
    ) {
        CompositionLocalProvider(LocalMenuAtTapPlacement provides placement) {
            menu()
        }
    }
}
