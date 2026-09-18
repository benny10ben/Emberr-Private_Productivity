package com.emberr.presentation.shared.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

private val ROW_VERTICAL_PADDING = 12.dp
private val ROW_ICON_SIZE = 20.dp
private val ROW_ICON_GAP = 12.dp
private const val DISABLED_ALPHA = 0.38f

@Composable
fun EmberrBottomSheetItem(
    text: String,
    icon: Painter,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val baseColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val contentColor = if (enabled) baseColor else baseColor.copy(alpha = DISABLED_ALPHA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(ROW_ICON_SIZE)
        )
        Spacer(modifier = Modifier.width(ROW_ICON_GAP))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}
