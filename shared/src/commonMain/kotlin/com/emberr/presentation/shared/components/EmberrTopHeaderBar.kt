package com.emberr.presentation.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.stableStatusBarsPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import org.jetbrains.compose.resources.painterResource

val TopHeaderBarButtonSize = 44.dp

private val DefaultTitleSideInset = 56.dp

enum class TopHeaderTitlePlacement { Center, Start }

fun topHeaderBarPadding(
    top: Dp = if (isDesktopPlatform) 16.dp else 10.dp,
    horizontal: Dp = 16.dp,
    bottom: Dp = 0.dp
): PaddingValues = PaddingValues(start = horizontal, end = horizontal, top = top, bottom = bottom)

@Composable
fun EmberrTopHeaderBar(
    modifier: Modifier = Modifier,
    title: String = "",
    titlePlacement: TopHeaderTitlePlacement = TopHeaderTitlePlacement.Center,
    titleVisibility: Float = 1f,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    titleFontWeight: FontWeight = FontWeight.Bold,
    titleSideInset: Dp = DefaultTitleSideInset,
    titlePadding: PaddingValues = PaddingValues(0.dp),
    titleLeadingIcon: (@Composable () -> Unit)? = null,
    titleTrailingIcon: (@Composable () -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    showBackButton: Boolean = true,
    reserveBackButtonSpace: Boolean = true,
    backIcon: Painter = painterResource(Res.drawable.chevron_left),
    backContentDescription: String = "Back",
    backButtonTint: Color = MaterialTheme.colorScheme.primary,
    backButtonBackground: Color = Color.Transparent,
    onBackClick: () -> Unit = {},
    background: Color = Color.Transparent,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle = EmberrBlur.Regular,
    applyStatusBarPadding: Boolean = !isDesktopPlatform,
    contentPadding: PaddingValues = topHeaderBarPadding(),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onPositioned: (LayoutCoordinates) -> Unit = {},
    leadingContent: @Composable RowScope.() -> Unit = {},
    centerContent: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    overlayContent: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .then(if (applyStatusBarPadding) Modifier.stableStatusBarsPadding() else Modifier)
            .padding(contentPadding)
            .onGloballyPositioned(onPositioned)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = verticalAlignment
        ) {
            when {
                showBackButton -> TopBarIconButton(
                    icon = backIcon,
                    contentDescription = backContentDescription,
                    bgColor = backButtonBackground,
                    tint = backButtonTint,
                    hazeState = hazeState,
                    hazeStyle = hazeStyle,
                    size = TopHeaderBarButtonSize,
                    onClick = onBackClick
                )

                reserveBackButtonSpace -> Spacer(Modifier.size(TopHeaderBarButtonSize))
            }

            leadingContent()

            if (titlePlacement == TopHeaderTitlePlacement.Start) {
                TopHeaderBarTitle(
                    text = title,
                    style = titleStyle,
                    color = titleColor,
                    fontWeight = titleFontWeight,
                    visibility = titleVisibility,
                    padding = titlePadding,
                    leadingIcon = titleLeadingIcon,
                    trailingIcon = titleTrailingIcon,
                    onClick = onTitleClick
                )
            }

            if (centerContent != null) {
                centerContent()
            } else {
                Spacer(Modifier.weight(1f))
            }

            actions()
        }

        if (titlePlacement == TopHeaderTitlePlacement.Center) {
            TopHeaderBarTitle(
                text = title,
                style = titleStyle,
                color = titleColor,
                fontWeight = titleFontWeight,
                visibility = titleVisibility,
                padding = titlePadding,
                leadingIcon = titleLeadingIcon,
                trailingIcon = titleTrailingIcon,
                onClick = onTitleClick,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = titleSideInset)
            )
        }

        overlayContent()
    }
}

@Composable
private fun TopHeaderBarTitle(
    text: String,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight,
    visibility: Float,
    padding: PaddingValues,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty() && leadingIcon == null && trailingIcon == null) return

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .graphicsLayer { alpha = visibility }
            .clickable(
                enabled = onClick != null && visibility > 0f,
                interactionSource = interactionSource,
                indication = NoRippleIndicationNodeFactory,
                onClick = { onClick?.invoke() }
            )
            .padding(padding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            trailingIcon()
        }
    }
}
