package com.emberr.presentation.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberr.ui.theme.LocalAppIsDark
import com.emberr.ui.theme.LocalEmberrFontStyle
import com.emberr.ui.theme.fontFamilyFor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal val OnboardingGlideEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
internal const val OnboardingGlideDurationMillis = 2200
private const val FadeDurationMillis = 1500
private val IconBadgeSize = 48.dp
private val IconSize = 21.dp
private const val VibrateCycleMillis = 2600
private const val VibrateBurstFraction = 0.14f
private const val VibrateSwingCount = 3f
private const val VibrateAngleDegrees = 7f
private val VibrateShift = 1.5.dp
private val HeadlineBlueOnDark = Color(0xFF4F5B8A)
private val HeadlineFadeOnDark = Color(0xFFD5DCF8)
private val HeadlineBlueOnLight = Color(0xFF4F5B8A)
private val HeadlineFadeOnLight = Color(0xFF151C3D)

private const val WaveDurationMillis = 1500
private const val WaveTravelTurns = 2f
private const val WaveCount = 1f
private const val MaxWaveSlices = 140
private val WaveAmplitude = 3.dp
private const val RainbowDriftMillis = 7000
private val RainbowTileWidth = 280.dp

private val RainbowOnDark = listOf(
    Color(0xFFFF8787),
    Color(0xFFFFA94D),
    Color(0xFFFFD43B),
    Color(0xFF69DB7C),
    Color(0xFF3BC9DB),
    Color(0xFF748FFC),
    Color(0xFFDA77F2)
)

private val RainbowOnLight = listOf(
    Color(0xFFE03131),
    Color(0xFFE8590C),
    Color(0xFFF08C00),
    Color(0xFF2F9E44),
    Color(0xFF0C8599),
    Color(0xFF3B5BDB),
    Color(0xFF9C36B5)
)

@Composable
fun OnboardingStatementStack(
    steps: List<OnboardingStep>,
    currentStepIndex: Int,
    spaceBetweenStatements: Dp,
    topFadeHeight: Dp,
    bottomFadeHeight: Dp,
    isWideLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { spaceBetweenStatements.roundToPx() }
    val bottomFadePx = with(density) { bottomFadeHeight.roundToPx() }

    val statementHeights = remember(steps.size) {
        mutableStateListOf<Int>().apply { repeat(steps.size) { add(0) } }
    }
    val stackAreaHeight = remember { mutableIntStateOf(0) }

    var bottomOfActiveStatement = 0
    for (index in 0..currentStepIndex) {
        if (index > 0) bottomOfActiveStatement += spacingPx
        bottomOfActiveStatement += statementHeights[index]
    }

    val everyStatementIsMeasured = statementHeights.none { it == 0 }

    val activeStatementBottom = remember { Animatable(0f) }
    var hasTakenFirstPosition by remember { mutableStateOf(false) }

    LaunchedEffect(bottomOfActiveStatement, everyStatementIsMeasured) {
        if (!everyStatementIsMeasured) return@LaunchedEffect

        if (hasTakenFirstPosition) {
            activeStatementBottom.animateTo(
                targetValue = bottomOfActiveStatement.toFloat(),
                animationSpec = tween(OnboardingGlideDurationMillis, easing = OnboardingGlideEasing)
            )
        } else {
            activeStatementBottom.snapTo(bottomOfActiveStatement.toFloat())
            hasTakenFirstPosition = true
        }
    }

    val stackAlpha by animateFloatAsState(
        targetValue = if (hasTakenFirstPosition) 1f else 0f,
        animationSpec = tween(FadeDurationMillis),
        label = "onboarding-stack-alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .fadingEdges(topFade = topFadeHeight, bottomFade = bottomFadeHeight)
            .onSizeChanged { stackArea -> stackAreaHeight.intValue = stackArea.height }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = stackAlpha }
                .offset {
                    IntOffset(
                        x = 0,
                        y = stackAreaHeight.intValue -
                            bottomFadePx -
                            activeStatementBottom.value.roundToInt()
                    )
                }
                .wrapContentHeight(align = Alignment.Top, unbounded = true),
            horizontalAlignment = if (isWideLayout) {
                Alignment.CenterHorizontally
            } else {
                Alignment.Start
            }
        ) {
            steps.forEachIndexed { index, step ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(spaceBetweenStatements))
                }

                OnboardingStatement(
                    step = step,
                    stepsBehind = currentStepIndex - index,
                    isWideLayout = isWideLayout,
                    modifier = Modifier.onSizeChanged { statement ->
                        statementHeights[index] = statement.height
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingStatement(
    step: OnboardingStep,
    stepsBehind: Int,
    isWideLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val statementAlpha by animateFloatAsState(
        targetValue = alphaForDistance(stepsBehind),
        animationSpec = tween(FadeDurationMillis),
        label = "onboarding-statement-alpha"
    )
    val statementBlur by animateDpAsState(
        targetValue = blurForDistance(stepsBehind),
        animationSpec = tween(FadeDurationMillis),
        label = "onboarding-statement-blur"
    )

    val textAlignment = if (isWideLayout) TextAlign.Center else TextAlign.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = statementAlpha }
            .blur(statementBlur, BlurredEdgeTreatment.Unbounded),
        horizontalAlignment = if (isWideLayout) Alignment.CenterHorizontally else Alignment.Start
    ) {
        StatementIconBadge(icon = step.icon, isActive = stepsBehind == 0)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = step.lead,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = textAlignment,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        StatementHeadline(
            headline = step.headline,
            isActive = stepsBehind == 0,
            isRainbow = step.hasRainbowHeadline,
            isWideLayout = isWideLayout,
            textAlignment = textAlignment
        )

        if (step.detail != null) {
            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                textAlign = textAlignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (isWideLayout) 0.dp else 8.dp)
            )
        }
    }
}

@Composable
private fun StatementIconBadge(icon: DrawableResource, isActive: Boolean) {
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Box(
        modifier = Modifier.size(IconBadgeSize),
        contentAlignment = Alignment.Center
    ) {
        BadgeRing(color = ringColor)

        if (isActive) {
            val vibration = rememberInfiniteTransition(label = "onboarding-icon-vibration")

            val cycleProgress = vibration.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(VibrateCycleMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "onboarding-icon-vibration-cycle"
            )

            BadgeIcon(
                icon = icon,
                modifier = Modifier.graphicsLayer {
                    val swing = vibrationSwingAt(cycleProgress.value)
                    rotationZ = swing * VibrateAngleDegrees
                    translationX = swing * VibrateShift.toPx()
                }
            )
        } else {
            BadgeIcon(icon = icon)
        }
    }
}

private fun vibrationSwingAt(cycleProgress: Float): Float {
    if (cycleProgress >= VibrateBurstFraction) return 0f

    val burstProgress = cycleProgress / VibrateBurstFraction
    val settling = 1f - burstProgress

    return sin(burstProgress * VibrateSwingCount * 2f * PI.toFloat()) * settling
}

@Composable
private fun BadgeRing(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .border(width = 1.dp, color = color, shape = CircleShape)
    )
}

@Composable
private fun BadgeIcon(icon: DrawableResource, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        modifier = modifier.size(IconSize)
    )
}

@Composable
private fun StatementHeadline(
    headline: String,
    isActive: Boolean,
    isRainbow: Boolean,
    isWideLayout: Boolean,
    textAlignment: TextAlign
) {
    val headlineStyle = statementHeadlineStyle(isWideLayout)
    val sideSlack = if (isWideLayout) 0.dp else -WaveAmplitude

    val waveProgress = remember { Animatable(1f) }
    var hasBeenActive by remember { mutableStateOf(false) }
    var isWaving by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (!isActive && !hasBeenActive) return@LaunchedEffect
        if (isActive) hasBeenActive = true

        isWaving = true
        waveProgress.snapTo(0f)
        waveProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(WaveDurationMillis, easing = LinearEasing)
        )
        isWaving = false
    }

    if (!isWaving && !(isRainbow && isActive)) {
        Text(
            text = headline,
            style = headlineStyle.copy(brush = blueHeadlineBrush()),
            textAlign = textAlignment,
            modifier = Modifier
                .offset(x = sideSlack)
                .padding(horizontal = WaveAmplitude)
        )
        return
    }

    val textLayer = rememberGraphicsLayer()
    val steadyBrush = blueHeadlineBrush()
    val rainbowColors = if (LocalAppIsDark.current) RainbowOnDark else RainbowOnLight
    val loopingColors = remember(rainbowColors) { rainbowColors + rainbowColors.first() }

    val colorDrift = rememberInfiniteTransition(label = "onboarding-headline-color")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(RainbowDriftMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "onboarding-headline-color-drift"
        )

    Text(
        text = headline,
        style = headlineStyle.copy(color = MaterialTheme.colorScheme.onBackground),
        textAlign = textAlignment,
        modifier = Modifier
            .offset(x = sideSlack)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                if (size.width <= 0f || size.height <= 0f) return@drawWithContent

                val progress = waveProgress.value
                val energy = sin(progress * PI.toFloat())

                if (energy <= 0.01f) {
                    drawContent()
                } else {
                    textLayer.record {
                        this@drawWithContent.drawContent()
                    }

                    val amplitude = WaveAmplitude.toPx() * energy
                    val sliceCount = size.height.roundToInt().coerceIn(8, MaxWaveSlices)
                    val sliceHeight = size.height / sliceCount
                    val travel = progress * WaveTravelTurns * 2f * PI.toFloat()

                    for (sliceIndex in 0 until sliceCount) {
                        val sliceTop = sliceIndex * sliceHeight
                        val downText = sliceIndex / sliceCount.toFloat()
                        val shift = sin(
                            travel + downText * WaveCount * 2f * PI.toFloat()
                        ) * amplitude

                        clipRect(top = sliceTop, bottom = sliceTop + sliceHeight + 1f) {
                            translate(left = shift) {
                                drawLayer(textLayer)
                            }
                        }
                    }
                }

                drawRect(
                    brush = if (isRainbow) {
                        val tileWidth = RainbowTileWidth.toPx()
                        val colorShift = colorDrift.value * tileWidth

                        Brush.horizontalGradient(
                            colors = loopingColors,
                            startX = colorShift - tileWidth,
                            endX = colorShift,
                            tileMode = TileMode.Repeated
                        )
                    } else {
                        steadyBrush
                    },
                    blendMode = BlendMode.SrcIn
                )
            }
            .padding(horizontal = WaveAmplitude)
    )
}

@Composable
private fun blueHeadlineBrush(): Brush {
    val isDark = LocalAppIsDark.current
    val startColor = if (isDark) HeadlineBlueOnDark else HeadlineBlueOnLight
    val endColor = if (isDark) HeadlineFadeOnDark else HeadlineFadeOnLight

    return Brush.horizontalGradient(listOf(startColor, endColor))
}

@Composable
private fun statementHeadlineStyle(isWideLayout: Boolean): TextStyle {
    val fontSize = if (isWideLayout) 42.sp else 31.sp

    return TextStyle(
        fontFamily = fontFamilyFor(LocalEmberrFontStyle.current),
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.18f).sp,
        letterSpacing = (-0.4).sp
    )
}

private fun Modifier.fadingEdges(topFade: Dp, bottomFade: Dp): Modifier {
    if (topFade <= 0.dp && bottomFade <= 0.dp) return this

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()

            if (size.height <= 0f) return@drawWithContent

            val firstOpaqueStop = (topFade.toPx() / size.height).coerceIn(0f, 0.5f)
            val lastOpaqueStop = (1f - bottomFade.toPx() / size.height)
                .coerceIn(firstOpaqueStop, 1f)

            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    firstOpaqueStop to Color.Black,
                    lastOpaqueStop to Color.Black,
                    1f to Color.Transparent
                ),
                blendMode = BlendMode.DstIn
            )
        }
}

private fun alphaForDistance(stepsBehind: Int): Float = when (stepsBehind) {
    0 -> 1f
    1 -> 0.3f
    2 -> 0.13f
    3 -> 0.05f
    else -> 0f
}

private fun blurForDistance(stepsBehind: Int): Dp = when (stepsBehind) {
    1 -> 3.dp
    2 -> 6.dp
    else -> 0.dp
}
