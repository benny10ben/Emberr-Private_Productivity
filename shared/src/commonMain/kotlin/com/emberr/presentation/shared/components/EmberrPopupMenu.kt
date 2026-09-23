package com.emberr.presentation.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

private val PopupMenuShape = RoundedCornerShape(10.dp)
private val PopupMenuRowShape = RoundedCornerShape(7.dp)

@Composable
fun EmberrPopupMenuSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = PopupMenuShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = EmberrShadowElevation.Standard,
        modifier = modifier
            .widthIn(min = 112.dp, max = 240.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), PopupMenuShape)
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Max).padding(4.dp),
            content = content
        )
    }
}

@Composable
fun EmberrPopupMenuRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val highlight = if (isHovered) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .clip(PopupMenuRowShape)
            .background(highlight)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}

class MenuAtClickPositionProvider(private val clickPosition: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val edge = 8

        var x = anchorBounds.left + clickPosition.x
        if (x + popupContentSize.width > windowSize.width - edge) {
            x = windowSize.width - popupContentSize.width - edge
        }

        var y = anchorBounds.top + clickPosition.y
        if (y + popupContentSize.height > windowSize.height - edge) {
            y -= popupContentSize.height
        }

        return IntOffset(x.coerceAtLeast(edge), y.coerceAtLeast(edge))
    }
}
