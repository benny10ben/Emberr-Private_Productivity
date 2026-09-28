package com.emberr.presentation.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

private val DefaultMenuShape = RoundedCornerShape(18.dp)
private val MenuEdgeWidth = 0.5.dp
private const val MenuEdgeAlpha = 0.2f
private val MenuHorizontalPadding = 6.dp

@Composable
fun EmberrDesktopMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    val blurSource = LocalEmberrBlurSource.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val edgeColor = MaterialTheme.colorScheme.outline.copy(alpha = MenuEdgeAlpha)
    val atTapPlacement = LocalMenuAtTapPlacement.current
    val density = LocalDensity.current
    var measuredMenuWidth by remember { mutableStateOf<Dp?>(null) }
    val placementModifier = if (atTapPlacement == null) {
        Modifier
    } else {
        Modifier
            .onSizeChanged { measuredMenuWidth = with(density) { it.width.toDp() } }
            .onGloballyPositioned { atTapPlacement.onMenuPositioned(it.positionInWindow().x) }
    }
    val menuOffset = if (atTapPlacement?.opensLeftward == true) {
        DpOffset(x = -(measuredMenuWidth ?: atTapPlacement.menuWidth), y = 0.dp)
    } else {
        offset
    }
    val menuContent: @Composable ColumnScope.() -> Unit = {
        CompositionLocalProvider(
            LocalMenuCascadeDirection provides (atTapPlacement?.direction ?: MenuCascadeDirection.RIGHT),
            LocalMenuAtTapPlacement provides null
        ) {
            content()
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = menuOffset,
        properties = properties,
        shape = DefaultMenuShape,
        containerColor = if (blurSource != null) Color.Transparent else surfaceColor,
        shadowElevation = if (blurSource != null) EmberrShadowElevation.None else MenuDefaults.ShadowElevation,
        modifier = if (blurSource != null) {
            modifier
                .then(placementModifier)
                .emberrBlur(blurSource, EmberrBlur.Thick)
                .border(width = MenuEdgeWidth, color = edgeColor, shape = DefaultMenuShape)
                .padding(horizontal = MenuHorizontalPadding)
        } else {
            modifier
                .then(placementModifier)
                .background(color = surfaceColor, shape = DefaultMenuShape)
                .padding(horizontal = MenuHorizontalPadding)
        },
        content = menuContent
    )
}