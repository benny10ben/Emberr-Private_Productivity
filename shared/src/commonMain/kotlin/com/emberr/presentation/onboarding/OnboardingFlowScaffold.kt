package com.emberr.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.stableStatusBarsPadding
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random

private val WideLayoutMinWidth = 660.dp
private val WidePaneMaxWidth = 720.dp
private val CompactPaneMaxWidth = 520.dp
private val WideSidePadding = 56.dp
private val CompactSidePadding = 28.dp
private val WidePanelMaxWidth = 560.dp
private val WideButtonWidth = 300.dp
private val WideGapBelowStatements = 12.dp
private val CompactGapBelowStatements = 8.dp
private val WideGapAboveButton = 48.dp
private val CompactGapAboveButton = 40.dp
private val WideButtonGap = 16.dp
private val WideBottomMargin = 68.dp
private val DesktopCompactBottomMargin = 40.dp
private val MobileBottomMargin = 30.dp
private val WideSpaceBetweenStatements = 76.dp
private val CompactSpaceBetweenStatements = 54.dp
private val ControlHeight = 54.dp
private val SkipTapPadding = 12.dp
private val DesktopSkipInset = 48.dp
private val MobileSkipInset = 14.dp
private val SkipLabelReserve = 22.dp
private val MobileSystemBarsReserve = 48.dp
private val WideMinStatementSpace = 160.dp
private val CompactMinStatementSpace = 175.dp
private val WideTopFadeHeight = 120.dp
private val WideBottomFadeHeight = 32.dp
private val CompactTopFadeHeight = 96.dp
private val CompactBottomFadeHeight = 28.dp
private const val ShowTexturedBackground = false
private val GrainCellSize = 1.dp
private const val GrainTileCells = 128
private const val GrainDensity = 0.20f
private const val GrainMinAlpha = 0.02f
private const val GrainMaxAlpha = 0.06f
private const val GrainSeed = 7

internal val LocalOnboardingWideLayout = compositionLocalOf { false }

@Composable
fun OnboardingFlowScaffold(
    statements: List<OnboardingStep>,
    currentStepIndex: Int,
    showSkip: Boolean,
    onSkip: () -> Unit,
    onSwipeForward: (() -> Unit)?,
    onSwipeBackward: (() -> Unit)?,
    panel: @Composable ColumnScope.() -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val grainBrush = rememberGrainBrush(speckColor = MaterialTheme.colorScheme.onBackground)
    val silk = if (ShowTexturedBackground) rememberOnboardingSilk() else null
    var scaffoldOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var statementStackBoundsInRoot by remember { mutableStateOf(Rect.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { scaffold -> scaffoldOriginInRoot = scaffold.positionInRoot() }
            .drawWithContent {
                drawContent()
                if (ShowTexturedBackground) drawRect(grainBrush)
            }
    ) {
        val isWideLayout = maxWidth >= WideLayoutMinWidth

        val paneMaxWidth = if (isWideLayout) WidePaneMaxWidth else CompactPaneMaxWidth
        val sidePadding = if (isWideLayout) WideSidePadding else CompactSidePadding
        val panelMaxWidth = if (isWideLayout) WidePanelMaxWidth else CompactPaneMaxWidth
        val panelSidePadding = if (isWideLayout) 0.dp else sidePadding
        val gapAboveButton = if (isWideLayout) WideGapAboveButton else CompactGapAboveButton
        val skipInset = if (isDesktopPlatform) DesktopSkipInset else MobileSkipInset
        val systemBarsReserve = if (isDesktopPlatform) 0.dp else MobileSystemBarsReserve
        val gapBelowStatements = if (isWideLayout) {
            WideGapBelowStatements
        } else {
            CompactGapBelowStatements
        }
        val bottomMargin = when {
            isWideLayout -> WideBottomMargin
            isDesktopPlatform -> DesktopCompactBottomMargin
            else -> MobileBottomMargin
        }
        val spaceBetweenStatements = if (isWideLayout) {
            WideSpaceBetweenStatements
        } else {
            CompactSpaceBetweenStatements
        }
        val topFadeHeight = if (isWideLayout) WideTopFadeHeight else CompactTopFadeHeight
        val bottomFadeHeight = if (isWideLayout) {
            WideBottomFadeHeight
        } else {
            CompactBottomFadeHeight
        }
        val minStatementSpace = if (isWideLayout) {
            WideMinStatementSpace
        } else {
            CompactMinStatementSpace
        }

        val topRowReserve = skipInset + SkipTapPadding * 2 + SkipLabelReserve
        val bottomBarReserve = gapAboveButton + ControlHeight + bottomMargin
        val panelMaxHeight = (
            maxHeight - topRowReserve - bottomBarReserve - gapBelowStatements -
                systemBarsReserve - minStatementSpace
            ).coerceAtLeast(0.dp)

        CompositionLocalProvider(LocalOnboardingWideLayout provides isWideLayout) {
            if (silk != null) {
                SilkBackdrop(silk = silk, modifier = Modifier.fillMaxSize())
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (onSwipeForward != null && onSwipeBackward != null) {
                            Modifier.swipeBetweenSteps(
                                stepKey = currentStepIndex,
                                onSwipeForward = onSwipeForward,
                                onSwipeBackward = onSwipeBackward
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                OnboardingTopRow(showSkip = showSkip, onSkip = onSkip, skipInset = skipInset)

                Column(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = paneMaxWidth)
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    OnboardingStatementStack(
                        steps = statements,
                        currentStepIndex = currentStepIndex,
                        spaceBetweenStatements = spaceBetweenStatements,
                        bottomFadeHeight = bottomFadeHeight,
                        isWideLayout = isWideLayout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = sidePadding)
                            .onGloballyPositioned { statementStack ->
                                statementStackBoundsInRoot = statementStack.boundsInRoot()
                            }
                    )

                    Spacer(modifier = Modifier.height(gapBelowStatements))

                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = panelMaxWidth)
                            .fillMaxWidth()
                            .heightIn(max = panelMaxHeight)
                            .padding(horizontal = panelSidePadding),
                        content = panel
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isDesktopPlatform) Modifier else Modifier.navigationBarsPadding()
                            )
                            .padding(
                                start = sidePadding,
                                end = sidePadding,
                                top = gapAboveButton,
                                bottom = bottomMargin
                            )
                    ) {
                        bottomBar()
                    }
                }
            }

            StatementEdgeFades(
                silk = silk,
                statementBounds = { statementStackBoundsInRoot.translate(-scaffoldOriginInRoot) },
                topFade = topFadeHeight,
                bottomFade = bottomFadeHeight,
                fadeColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun rememberGrainBrush(speckColor: Color): ShaderBrush {
    val cellSizePx = with(LocalDensity.current) { GrainCellSize.roundToPx() }.coerceAtLeast(1)

    return remember(cellSizePx, speckColor) {
        ShaderBrush(
            ImageShader(
                image = createGrainTile(cellSizePx, speckColor),
                tileModeX = TileMode.Repeated,
                tileModeY = TileMode.Repeated
            )
        )
    }
}

private fun createGrainTile(cellSizePx: Int, speckColor: Color): ImageBitmap {
    val tileSizePx = GrainTileCells * cellSizePx
    val tile = ImageBitmap(tileSizePx, tileSizePx)
    val canvas = Canvas(tile)
    val paint = Paint()
    val random = Random(GrainSeed)

    for (row in 0 until GrainTileCells) {
        for (column in 0 until GrainTileCells) {
            if (random.nextFloat() >= GrainDensity) continue

            val speckAlpha = GrainMinAlpha + random.nextFloat() * (GrainMaxAlpha - GrainMinAlpha)
            paint.color = speckColor.copy(alpha = speckAlpha)

            val left = (column * cellSizePx).toFloat()
            val top = (row * cellSizePx).toFloat()
            canvas.drawRect(left, top, left + cellSizePx, top + cellSizePx, paint)
        }
    }

    return tile
}

@Composable
fun OnboardingFlowBottomBar(
    label: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val isWideLayout = LocalOnboardingWideLayout.current

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isWideLayout) {
            val backButtonOffset =
                -(WideButtonWidth / 2 + WideButtonGap + ControlHeight / 2)

            OnboardingPillButton(
                label = label,
                onClick = onNext,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(WideButtonWidth)
            )

            AnimatedVisibility(
                visible = showBack,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = backButtonOffset),
                enter = fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.8f),
                exit = fadeOut(tween(170)) + scaleOut(tween(170), targetScale = 0.8f)
            ) {
                OnboardingStepBackButton(onClick = onBack)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = showBack,
                    enter = fadeIn(tween(300, delayMillis = 160)) + expandHorizontally(
                        animationSpec = tween(400, easing = OnboardingGlideEasing),
                        expandFrom = Alignment.Start
                    ),
                    exit = fadeOut(tween(140)) + shrinkHorizontally(
                        animationSpec = tween(340, easing = OnboardingGlideEasing),
                        shrinkTowards = Alignment.Start
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OnboardingStepBackButton(onClick = onBack)

                        Spacer(modifier = Modifier.width(14.dp))
                    }
                }

                OnboardingPillButton(
                    label = label,
                    onClick = onNext,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OnboardingTopRow(showSkip: Boolean, onSkip: () -> Unit, skipInset: Dp) {
    val skipAlpha by animateFloatAsState(
        targetValue = if (showSkip) 1f else 0f,
        animationSpec = tween(260),
        label = "onboarding-skip-alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            .padding(end = skipInset, top = skipInset),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = "Skip",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier
                .graphicsLayer { alpha = skipAlpha }
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = showSkip, onClick = onSkip)
                .padding(SkipTapPadding)
        )
    }
}

@Composable
private fun OnboardingPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(ControlHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = label,
            animationSpec = tween(450),
            label = "onboarding-button-label"
        ) { visibleLabel ->
            Text(
                text = visibleLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OnboardingStepBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(ControlHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(Res.drawable.chevron_left),
            contentDescription = "Go back",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun Modifier.swipeBetweenSteps(
    stepKey: Int,
    onSwipeForward: () -> Unit,
    onSwipeBackward: () -> Unit
): Modifier = pointerInput(stepKey) {
    val swipeThreshold = 72.dp.toPx()
    var horizontalDragTotal = 0f

    detectHorizontalDragGestures(
        onDragStart = { horizontalDragTotal = 0f },
        onDragCancel = { horizontalDragTotal = 0f },
        onDragEnd = {
            if (horizontalDragTotal <= -swipeThreshold) onSwipeForward()
            if (horizontalDragTotal >= swipeThreshold) onSwipeBackward()
            horizontalDragTotal = 0f
        },
        onHorizontalDrag = { _, dragAmount -> horizontalDragTotal += dragAmount }
    )
}

@Composable
fun <T> OnboardingPanelTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val crossFade = fadeIn(tween(600, delayMillis = 1200)) togetherWith fadeOut(tween(300))

            crossFade using SizeTransform { _, _ ->
                tween(OnboardingGlideDurationMillis, easing = OnboardingGlideEasing)
            }
        },
        label = "onboarding-panel"
    ) { visibleState ->
        content(visibleState)
    }
}
