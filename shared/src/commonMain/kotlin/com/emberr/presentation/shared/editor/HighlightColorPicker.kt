package com.emberr.presentation.shared.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.emberr.ui.theme.HighlightColor
import com.emberr.ui.theme.LocalAppIsDark

val HighlightColorMenuWidth = 380.dp
private val HighlightCircleSize = 24.dp
private val RemoveHighlightMarkColor = Color(0xFFE53935)

fun Modifier.removeHighlightMark(): Modifier = this.drawBehind {
    val inset = size.minDimension * 0.24f
    drawLine(
        color = RemoveHighlightMarkColor,
        start = Offset(inset, size.height - inset),
        end = Offset(size.width - inset, inset),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round
    )
}

@Composable
fun HighlightColorCircles(onColorChosen: (String) -> Unit, onClose: () -> Unit) {
    val isDarkTheme = LocalAppIsDark.current

    Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HighlightCircle(
            fillColor = MaterialTheme.colorScheme.background,
            onClick = onClose
        ) {
            Icon(
                Icons.Default.Close,
                "Close colours",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(HighlightCircleSize / 2)
            )
        }

        HighlightCircle(
            fillColor = Color.Transparent,
            showsRemoveMark = true,
            onClick = { onColorChosen("highlight:none") }
        )

        HighlightColor.entries.forEach { highlightColor ->
            HighlightCircle(
                fillColor = highlightColor.backgroundFor(isDarkTheme),
                onClick = { onColorChosen("highlight:${highlightColor.storageName}") }
            )
        }
    }
}

@Composable
private fun HighlightCircle(
    fillColor: Color,
    onClick: () -> Unit,
    showsRemoveMark: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(HighlightCircleSize)
            .clip(CircleShape)
            .background(fillColor)
            .then(if (showsRemoveMark) Modifier.removeHighlightMark() else Modifier)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
