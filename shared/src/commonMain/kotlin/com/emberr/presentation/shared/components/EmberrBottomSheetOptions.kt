package com.emberr.presentation.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val OPTION_SHAPE = RoundedCornerShape(14.dp)
private val OPTION_VERTICAL_SPACING = 2.dp
private val OPTION_OUTER_HORIZONTAL_PADDING = 20.dp
private val OPTION_INNER_HORIZONTAL_PADDING = 14.dp
private val OPTION_INNER_VERTICAL_PADDING = 14.dp
private val ICON_GAP = 14.dp
private val TRAILING_GAP = 12.dp
private val SELECTED_DOT_SIZE = 8.dp

@Composable
fun EmberrBottomSheetOption(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    labelFontFamily: FontFamily? = null,
    labelMaxLines: Int = Int.MAX_VALUE,
    subtitleMaxLines: Int = Int.MAX_VALUE,
    outerHorizontalPadding: Dp = OPTION_OUTER_HORIZONTAL_PADDING
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = outerHorizontalPadding,
                vertical = OPTION_VERTICAL_SPACING
            )
            .clip(OPTION_SHAPE)
            .background(if (isSelected) SelectedOptionBackground else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                onClick = onClick
            )
            .padding(
                horizontal = OPTION_INNER_HORIZONTAL_PADDING,
                vertical = OPTION_INNER_VERTICAL_PADDING
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(ICON_GAP))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = labelFontFamily,
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
            Spacer(modifier = Modifier.width(TRAILING_GAP))
            Box(
                modifier = Modifier
                    .size(SELECTED_DOT_SIZE)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }

        if (trailing != null) {
            Spacer(modifier = Modifier.width(TRAILING_GAP))
            trailing()
        }
    }
}
