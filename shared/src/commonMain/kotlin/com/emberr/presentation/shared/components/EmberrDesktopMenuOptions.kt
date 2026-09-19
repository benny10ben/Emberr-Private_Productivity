package com.emberr.presentation.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DESKTOP_OPTION_SHAPE = RoundedCornerShape(12.dp)
private val DESKTOP_OPTION_VERTICAL_SPACING = 2.dp
private val DESKTOP_OPTION_OUTER_HORIZONTAL_PADDING = 8.dp
private val DESKTOP_OPTION_INNER_HORIZONTAL_PADDING = 12.dp
private val DESKTOP_OPTION_INNER_VERTICAL_PADDING = 10.dp
private val DESKTOP_TRAILING_GAP = 12.dp
private val DESKTOP_SELECTED_DOT_SIZE = 8.dp

@Composable
fun EmberrDesktopMenuOption(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    labelMaxLines: Int = 1,
    subtitleMaxLines: Int = 1,
    outerHorizontalPadding: Dp = DESKTOP_OPTION_OUTER_HORIZONTAL_PADDING
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = outerHorizontalPadding,
                vertical = DESKTOP_OPTION_VERTICAL_SPACING
            )
            .clip(DESKTOP_OPTION_SHAPE)
            .background(if (isSelected) SelectedOptionBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(
                horizontal = DESKTOP_OPTION_INNER_HORIZONTAL_PADDING,
                vertical = DESKTOP_OPTION_INNER_VERTICAL_PADDING
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = labelMaxLines,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isSelected) {
            Spacer(modifier = Modifier.width(DESKTOP_TRAILING_GAP))
            Box(
                modifier = Modifier
                    .size(DESKTOP_SELECTED_DOT_SIZE)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(DESKTOP_TRAILING_GAP))
            trailing()
        }
    }
}
