package com.emberr.presentation.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.emberr.presentation.shared.components.drawEmberrGhostEyes
import com.emberr.presentation.shared.components.traceEmberrGhost
import com.emberr.ui.theme.LocalAppIsDark
import kotlin.math.cos
import kotlin.math.sin

private val FloatingEmberrGhostSize = 44.dp
private val FloatingSpot = Offset(0.15f, 0.12f)

@Composable
internal fun FloatingEmberrGhost(modifier: Modifier = Modifier) {
    val emberrGhostColor = if (LocalAppIsDark.current) Color.White else Color.Black
    val eyeColor = MaterialTheme.colorScheme.background
    val emberrGhostPath = remember { Path() }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var previousFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                phase += (frameNanos - previousFrameNanos) / 1_000_000_000f
                previousFrameNanos = frameNanos
            }
        }
    }

    Canvas(modifier = modifier) {
        val emberrGhostSize = FloatingEmberrGhostSize.toPx()
        val emberrGhostCenter = Offset(
            x = (FloatingSpot.x + sin(phase * 0.7f) * 0.015f) * size.width,
            y = (FloatingSpot.y + cos(phase * 0.9f) * 0.02f) * size.height + sin(phase * 1.8f) * emberrGhostSize * 0.08f
        )
        val emberrGhostTopLeft = Offset(emberrGhostCenter.x - emberrGhostSize / 2f, emberrGhostCenter.y - emberrGhostSize / 2f)
        val swayDegrees = sin(phase * 1.3f) * 3f
        val stretch = 1f + 0.03f * sin(phase * 2.2f)

        rotate(degrees = swayDegrees, pivot = emberrGhostCenter) {
            scale(scaleX = 1f, scaleY = stretch, pivot = emberrGhostCenter) {
                emberrGhostPath.traceEmberrGhost(phase, 0f, emberrGhostTopLeft, emberrGhostSize)
                drawPath(path = emberrGhostPath, color = emberrGhostColor)
                drawEmberrGhostEyes(phase, 0f, emberrGhostTopLeft, emberrGhostSize, eyeColor)
            }
        }
    }
}
