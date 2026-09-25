package com.emberr.presentation.ai.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.emberr.domain.util.system.isDesktopPlatform

@Composable
internal fun Modifier.clickableWithoutMobileRipple(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = if (isDesktopPlatform) LocalIndication.current else null,
    onClick = onClick
)
