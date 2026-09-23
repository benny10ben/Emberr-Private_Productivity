package com.emberr.presentation.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties

private val DefaultMenuShape = RoundedCornerShape(18.dp)
private val MenuEdgeWidth = 0.5.dp
private const val MenuEdgeAlpha = 0.2f

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

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        properties = properties,
        shape = DefaultMenuShape,
        containerColor = if (blurSource != null) Color.Transparent else surfaceColor,
        shadowElevation = if (blurSource != null) EmberrShadowElevation.None else MenuDefaults.ShadowElevation,
        modifier = if (blurSource != null) {
            modifier
                .emberrBlur(blurSource, EmberrBlur.Thick)
                .border(width = MenuEdgeWidth, color = edgeColor, shape = DefaultMenuShape)
        } else {
            modifier.background(color = surfaceColor, shape = DefaultMenuShape)
        },
        content = content
    )
}