package com.emberr.presentation.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle

object NoRippleIndicationNodeFactory : IndicationNodeFactory {
    private class NoRippleIndicationNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() {
            drawContent()
        }
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode = NoRippleIndicationNode()

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -1
}

@Composable
fun TopBarPillButton(
    text: String,
    bgColor: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    height: Dp = 52.dp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val resolvedStyle = hazeStyle ?: EmberrBlur.Regular
    Surface(
        shape = shape,
        color = bgColor,
        contentColor = tint,
        modifier = modifier
            .height(height)
            .customEmberrShadow(shape)
            .clip(shape)
            .emberrBlur(hazeState, resolvedStyle)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                onClick = onClick
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = shape
            )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TopBarIconButton(
    icon: Painter,
    contentDescription: String,
    bgColor: Color,
    tint: Color,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shadowElevation: Dp = EmberrShadowElevation.Standard,
    onClick: () -> Unit
) {
    val resolvedStyle = hazeStyle ?: EmberrBlur.Regular
    Surface(
        shape = CircleShape,
        color = bgColor,
        contentColor = tint,
        modifier = Modifier
            .size(size)
            .customEmberrShadow(CircleShape, shadowElevation)
            .clip(CircleShape)
            .emberrBlur(hazeState, resolvedStyle)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                onClick = onClick
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    bgColor: Color,
    tint: Color,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shadowElevation: Dp = EmberrShadowElevation.Standard,
    onClick: () -> Unit
) {
    TopBarIconButton(
        icon = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        bgColor = bgColor,
        tint = tint,
        hazeState = hazeState,
        hazeStyle = hazeStyle,
        size = size,
        iconSize = iconSize,
        shadowElevation = shadowElevation,
        onClick = onClick
    )
}

data class TopBarIconButtonItem(
    val icon: Painter,
    val contentDescription: String,
    val onClick: () -> Unit,
    val isSelected: Boolean = false,
    val iconSize: Dp? = null
)

// Wraps multiple icons in a single pill Surface - shares one bg/shadow/border/haze instead of
// each icon getting its own circle, while each icon keeps its own independent click target.
@Composable
fun TopBarIconButtonGroup(
    items: List<TopBarIconButtonItem>,
    bgColor: Color,
    tint: Color,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    horizontalPadding: Dp = 6.dp,
    iconSize: Dp = 22.dp,
    shadowElevation: Dp = EmberrShadowElevation.Standard,
    shadowSpotColor: Color = EmberrShadowSpotColor,
    shadowAmbientColor: Color = EmberrShadowAmbientColor,
    isVertical: Boolean = false,
    horizontalItemSpacing: Dp = 0.dp
) {
    val resolvedStyle = hazeStyle ?: EmberrBlur.Regular
    Surface(
        shape = CircleShape,
        color = bgColor,
        contentColor = tint,
        modifier = Modifier
            .then(if (isVertical) Modifier.width(44.dp) else Modifier.height(44.dp))
            .customEmberrShadow(CircleShape, shadowElevation, shadowSpotColor, shadowAmbientColor)
            .clip(CircleShape)
            .emberrBlur(hazeState, resolvedStyle)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        if (isVertical) {
            Column(
                modifier = Modifier.padding(vertical = horizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEach { item ->
                    TopBarIconButtonGroupItem(item, tint, iconSize, selectedBackgroundWidth = 36.dp)
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(horizontalItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    TopBarIconButtonGroupItem(item, tint, iconSize, selectedBackgroundWidth = 36.dp + horizontalPadding * 2)
                }
            }
        }
    }
}

@Composable
private fun TopBarIconButtonGroupItem(
    item: TopBarIconButtonItem,
    tint: Color,
    iconSize: Dp,
    selectedBackgroundWidth: Dp
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = NoRippleIndicationNodeFactory,
                onClick = item.onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (item.isSelected) {
            Box(Modifier.requiredSize(width = selectedBackgroundWidth, height = 36.dp).background(tint.copy(alpha = 0.15f), CircleShape))
        }
        Icon(
            painter = item.icon,
            contentDescription = item.contentDescription,
            tint = tint,
            modifier = Modifier.size(item.iconSize ?: iconSize)
        )
    }
}