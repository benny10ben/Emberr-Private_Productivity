package com.emberr.presentation.home

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

private const val TREE_ROW_MOVE_DELAY_MILLIS = 40
private const val TREE_ROW_MOVE_DURATION_MILLIS = 240
private const val TREE_ROW_FADE_OUT_DURATION_MILLIS = 90
private const val TREE_ROW_FADE_IN_DURATION_MILLIS = 160
private const val TREE_ROW_FADE_IN_DELAY_MILLIS = 140

internal val TreeRowFadeOutSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = TREE_ROW_FADE_OUT_DURATION_MILLIS,
    easing = FastOutLinearInEasing
)

internal val TreeRowPlacementSpec: FiniteAnimationSpec<IntOffset> = tween(
    durationMillis = TREE_ROW_MOVE_DURATION_MILLIS,
    delayMillis = TREE_ROW_MOVE_DELAY_MILLIS,
    easing = FastOutSlowInEasing
)

internal val TreeRowFadeInSpec: FiniteAnimationSpec<Float> = tween(
    durationMillis = TREE_ROW_FADE_IN_DURATION_MILLIS,
    delayMillis = TREE_ROW_FADE_IN_DELAY_MILLIS,
    easing = LinearOutSlowInEasing
)
