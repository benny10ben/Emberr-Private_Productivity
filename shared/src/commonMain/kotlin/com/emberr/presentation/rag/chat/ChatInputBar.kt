package com.emberr.presentation.rag.chat

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.BOTTOM_BAR_BOTTOM_PADDING
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.rag.settings.AiSettingsMenuContent
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrShadowElevation
import com.emberr.presentation.shared.components.customEmberrShadow
import com.emberr.presentation.shared.components.emberrBlur
import dev.chrisbanes.haze.HazeState
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.cog
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean,
    isGenerating: Boolean,
    onStopGeneration: () -> Unit,
    hazeState: HazeState,
    viewModel: RagViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    chatAnimatedVisibilityScope: AnimatedVisibilityScope?,
    onSettingsClick: () -> Unit,
    showAiSettingsMenu: Boolean,
    onAiSettingsMenuDismiss: () -> Unit,
    onLocalAiClick: () -> Unit,
    onExternalAiClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = value.isNotBlank() && enabled

    val sendColor by animateColorAsState(
        targetValue = if (canSend) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "sendColor"
    )

    val barShape = RoundedCornerShape(28.dp)

    val minHeight = 96.dp
    val horizontalInset = 16.dp

    val isMorphing = chatAnimatedVisibilityScope?.transition?.isRunning == true
    val shadowElevation by animateDpAsState(
        targetValue = if (isMorphing) EmberrShadowElevation.None else EmberrShadowElevation.Standard,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
    )

    val sharedPillModifier = if (sharedTransitionScope != null && chatAnimatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "calendarBottomBarPill"),
                animatedVisibilityScope = chatAnimatedVisibilityScope,
                boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    val innerColumnModifier = if (sharedTransitionScope != null) {
        with(sharedTransitionScope) {
            Modifier
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .skipToLookaheadSize()
        }
    } else {
        Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
    }

    val aiIconModifier = if (sharedTransitionScope != null && chatAnimatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "aiIcon"),
                animatedVisibilityScope = chatAnimatedVisibilityScope,
                boundsTransform = { _, _ -> tween(durationMillis = 300, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(bottom = BOTTOM_BAR_BOTTOM_PADDING)
            .padding(horizontal = horizontalInset)
            .heightIn(min = minHeight)
            .then(sharedPillModifier)
            .customEmberrShadow(barShape, elevation = shadowElevation)
            .animateContentSize(animationSpec = tween(150))
            .clip(barShape)
            .emberrBlur(hazeState, EmberrBlur.Regular)
            .background(Color.Transparent)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = barShape
            )
    ) {
        Column(modifier = innerColumnModifier) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "Ask anything..",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isDesktopPlatform) {
                                Modifier.onPreviewKeyEvent { keyEvent ->
                                    val isEnter = keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter
                                    if (keyEvent.type == KeyEventType.KeyDown && isEnter && !keyEvent.isShiftPressed) {
                                        onSubmit()
                                        true
                                    } else {
                                        false
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable(enabled = true, onClick = onSettingsClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(Res.drawable.cog),
                                contentDescription = "AI settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(18.dp)
                                    .then(aiIconModifier)
                            )
                        }

                        if (isDesktopPlatform) {
                            EmberrDesktopMenu(
                                expanded = showAiSettingsMenu,
                                onDismissRequest = onAiSettingsMenuDismiss
                            ) {
                                AiSettingsMenuContent(
                                    viewModel = viewModel,
                                    onLocalAiClick = { onAiSettingsMenuDismiss(); onLocalAiClick() },
                                    onExternalAiClick = { onAiSettingsMenuDismiss(); onExternalAiClick() },
                                    onDismiss = onAiSettingsMenuDismiss
                                )
                            }
                        }
                    }
                    ModelPickerPill(viewModel = viewModel)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isGenerating) MaterialTheme.colorScheme.primary else sendColor)
                        .clickable(
                            enabled = isGenerating || canSend,
                            onClick = if (isGenerating) onStopGeneration else onSubmit
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGenerating) Icons.Default.Pause else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = if (isGenerating) "Stop generating" else "Send",
                        tint = if (isGenerating || canSend) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
