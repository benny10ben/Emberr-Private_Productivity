package com.emberr.presentation.shared.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val MENU_WIDTH = 220.dp
private val MENU_VERTICAL_PADDING = 4.dp
private val ROW_OUTER_HORIZONTAL_PADDING = 8.dp
private val ROW_OUTER_VERTICAL_PADDING = 2.dp
private val ROW_INNER_HORIZONTAL_PADDING = 12.dp
private val ROW_INNER_VERTICAL_PADDING = 10.dp
private val ROW_SHAPE = RoundedCornerShape(12.dp)
private val ROW_ICON_SIZE = 20.dp
private val ROW_ICON_GAP = 12.dp
private const val DISABLED_ALPHA = 0.38f

@Composable
fun EmberrDesktopMenuItems(
    width: Dp = MENU_WIDTH,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.width(width).padding(vertical = MENU_VERTICAL_PADDING),
        content = content
    )
}

@Composable
fun EmberrDesktopMenuItem(
    text: String,
    icon: Painter,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DesktopMenuItemRow(text = text, isDestructive = isDestructive, enabled = enabled, onClick = onClick) { tint ->
        Icon(painter = icon, contentDescription = null, tint = tint, modifier = Modifier.size(ROW_ICON_SIZE))
    }
}

@Composable
fun EmberrDesktopMenuItem(
    text: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DesktopMenuItemRow(text = text, isDestructive = isDestructive, enabled = enabled, onClick = onClick) { tint ->
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(ROW_ICON_SIZE))
    }
}

@Composable
private fun DesktopMenuItemRow(
    text: String,
    isDestructive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val baseColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val contentColor = if (enabled) baseColor else baseColor.copy(alpha = DISABLED_ALPHA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = ROW_OUTER_HORIZONTAL_PADDING,
                vertical = ROW_OUTER_VERTICAL_PADDING
            )
            .clip(ROW_SHAPE)
            .clickable(enabled = enabled) { onClick() }
            .padding(
                horizontal = ROW_INNER_HORIZONTAL_PADDING,
                vertical = ROW_INNER_VERTICAL_PADDING
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(contentColor)
        Spacer(modifier = Modifier.width(ROW_ICON_GAP))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}
