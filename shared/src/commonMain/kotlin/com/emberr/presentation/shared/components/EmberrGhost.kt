package com.emberr.presentation.shared.components

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
import com.emberr.ui.theme.LocalAppIsDark
import kotlin.math.sin

private const val EMBERR_GHOST_FILL_FRACTION = 0.8f

@Composable
fun EmberrGhost(modifier: Modifier = Modifier) {
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
        val emberrGhostSize = size.minDimension * EMBERR_GHOST_FILL_FRACTION
        val emberrGhostCenter = Offset(center.x, center.y + sin(phase * 1.8f) * emberrGhostSize * 0.08f)
        val emberrGhostTopLeft = Offset(
            x = emberrGhostCenter.x - emberrGhostSize / 2f,
            y = emberrGhostCenter.y - emberrGhostSize / 2f
        )
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
