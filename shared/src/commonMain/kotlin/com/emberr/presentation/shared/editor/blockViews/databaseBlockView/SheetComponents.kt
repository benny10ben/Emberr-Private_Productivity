@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.SelectedOptionBackground

internal object NoRippleIndicationNodeFactory : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode(): Int = -1
}

internal val sheetSideInset get() = if (isDesktopPlatform) 12.dp else 0.dp

internal fun Modifier.sheetSidePadding(): Modifier = padding(horizontal = sheetSideInset)

@Composable
internal fun MuteRippleOnMobile(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIndication provides if (isDesktopPlatform) ripple() else NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides if (isDesktopPlatform) LocalRippleConfiguration.current else null,
        content = content
    )
}

@Composable
internal fun SheetDivider(verticalPadding: Dp = 12.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = verticalPadding),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    )
}

@Composable
internal fun parseTagColor(colorHex: String): Color = try {
    Color(colorHex.removePrefix("#").toLong(16) or 0xFF000000)
} catch (_: Exception) {
    MaterialTheme.colorScheme.primary
}

@Composable
internal fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = modifier
    )
}

@Composable
fun SheetMenuRow(
    icon: Painter,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SelectedOptionBackground else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
    }
}
