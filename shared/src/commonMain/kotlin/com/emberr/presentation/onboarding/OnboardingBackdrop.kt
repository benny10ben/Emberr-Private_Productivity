package com.emberr.presentation.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositeShader
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.emberr.ui.theme.LocalAppIsDark

private val DarkSilkColor = Color(0xFF1C1C1C)
private const val DarkSilkContrast = 1f
private val LightSilkColor = Color.White
private const val LightSilkContrast = 0.09f
private const val SilkWaveSpeed = 0.15f
private const val SilkWaveScale = 0.6f
private const val NanosPerSecond = 1_000_000_000f

class OnboardingSilk(
    private val silkShader: SilkShader,
    private val waveTime: MutableFloatState,
    private val baseColor: Color,
    private val contrast: Float
) {
    fun currentShader(surfaceSize: Size): Shader? =
        silkShader.shaderFor(surfaceSize, waveTime.floatValue, SilkWaveScale, baseColor, contrast)
}

@Composable
fun rememberOnboardingSilk(): OnboardingSilk {
    val silkShader = remember { SilkShader() }
    val waveTime = remember { mutableFloatStateOf(0f) }
    val isDark = LocalAppIsDark.current

    LaunchedEffect(silkShader) {
        if (!silkShader.isSupported) return@LaunchedEffect

        val startNanos = withFrameNanos { frameNanos -> frameNanos }
        while (true) {
            withFrameNanos { frameNanos ->
                waveTime.floatValue = (frameNanos - startNanos) / NanosPerSecond * SilkWaveSpeed
            }
        }
    }

    return remember(silkShader, isDark) {
        OnboardingSilk(
            silkShader = silkShader,
            waveTime = waveTime,
            baseColor = if (isDark) DarkSilkColor else LightSilkColor,
            contrast = if (isDark) DarkSilkContrast else LightSilkContrast
        )
    }
}

@Composable
fun SilkBackdrop(silk: OnboardingSilk, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer()
            .drawBehind {
                val silkShader = silk.currentShader(size) ?: return@drawBehind
                drawRect(ShaderBrush(silkShader))
            }
    )
}

@Composable
fun StatementEdgeFades(
    silk: OnboardingSilk?,
    statementBounds: () -> Rect,
    topFade: Dp,
    bottomFade: Dp,
    fadeColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .graphicsLayer()
            .drawBehind {
                val bounds = statementBounds()
                if (bounds.height <= 0f) return@drawBehind

                val firstClearStop = (topFade.toPx() / bounds.height).coerceIn(0f, 0.5f)
                val lastClearStop = (1f - bottomFade.toPx() / bounds.height)
                    .coerceIn(firstClearStop, 1f)

                val silkShader = silk?.currentShader(size)
                val fadeBrush = if (silkShader != null) {
                    val alphaMask = LinearGradientShader(
                        from = Offset(0f, bounds.top),
                        to = Offset(0f, bounds.bottom),
                        colors = listOf(Color.Black, Color.Transparent, Color.Transparent, Color.Black),
                        colorStops = listOf(0f, firstClearStop, lastClearStop, 1f)
                    )
                    ShaderBrush(
                        CompositeShader(
                            dst = silkShader,
                            src = alphaMask,
                            blendMode = BlendMode.DstIn
                        )
                    )
                } else {
                    val clearFadeColor = fadeColor.copy(alpha = 0f)
                    Brush.verticalGradient(
                        0f to fadeColor,
                        firstClearStop to clearFadeColor,
                        lastClearStop to clearFadeColor,
                        1f to fadeColor,
                        startY = bounds.top,
                        endY = bounds.bottom
                    )
                }

                drawRect(brush = fadeBrush, topLeft = bounds.topLeft, size = bounds.size)
            }
    )
}
