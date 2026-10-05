package com.emberr.presentation.shared.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.sin

private const val BLINK_INTERVAL_SECONDS = 3.7f
private const val BLINK_DURATION_SECONDS = 0.16f

private class EmberrGhostEye(val center: Offset, val radiusX: Float, val radiusY: Float)

private val EmberrGhostEyes = listOf(
    EmberrGhostEye(center = Offset(0.853f, 0.345f), radiusX = 0.063f, radiusY = 0.075f),
    EmberrGhostEye(center = Offset(0.620f, 0.383f), radiusX = 0.073f, radiusY = 0.079f)
)

private val EmberrGhostOutline = listOf(
    Offset(0.613f, 0.027f), Offset(0.686f, 0.027f), Offset(0.754f, 0.039f), Offset(0.816f, 0.065f),
    Offset(0.872f, 0.106f), Offset(0.919f, 0.157f), Offset(0.955f, 0.215f), Offset(0.979f, 0.278f),
    Offset(0.996f, 0.344f), Offset(1.000f, 0.416f), Offset(0.996f, 0.487f), Offset(0.986f, 0.555f),
    Offset(0.966f, 0.620f), Offset(0.940f, 0.682f), Offset(0.908f, 0.742f), Offset(0.870f, 0.799f),
    Offset(0.824f, 0.853f), Offset(0.773f, 0.903f), Offset(0.717f, 0.944f), Offset(0.656f, 0.973f),
    Offset(0.591f, 0.969f), Offset(0.591f, 0.902f), Offset(0.553f, 0.855f), Offset(0.486f, 0.869f),
    Offset(0.425f, 0.896f), Offset(0.361f, 0.919f), Offset(0.293f, 0.931f), Offset(0.226f, 0.923f),
    Offset(0.179f, 0.876f), Offset(0.189f, 0.810f), Offset(0.190f, 0.743f), Offset(0.130f, 0.711f),
    Offset(0.057f, 0.711f), Offset(0.000f, 0.687f), Offset(0.048f, 0.642f), Offset(0.113f, 0.625f),
    Offset(0.183f, 0.618f), Offset(0.252f, 0.608f), Offset(0.314f, 0.582f), Offset(0.362f, 0.532f),
    Offset(0.383f, 0.468f), Offset(0.384f, 0.395f), Offset(0.380f, 0.324f), Offset(0.384f, 0.252f),
    Offset(0.405f, 0.188f), Offset(0.439f, 0.129f), Offset(0.489f, 0.079f), Offset(0.548f, 0.045f)
)

internal fun DrawScope.drawEmberrGhostEyes(
    phase: Float,
    energy: Float,
    emberrGhostTopLeft: Offset,
    emberrGhostSize: Float,
    eyeColor: Color
) {
    val openness = blinkOpenness(phase)
    val eyeGrowth = 1f + 0.1f * energy
    for (eye in EmberrGhostEyes) {
        val eyeCenter = emberrGhostTopLeft + Offset(eye.center.x + 0.01f * energy, eye.center.y) * emberrGhostSize
        val eyeSize = Size(
            width = eye.radiusX * 2f * emberrGhostSize * eyeGrowth,
            height = eye.radiusY * 2f * emberrGhostSize * eyeGrowth * openness
        )
        drawOval(
            color = eyeColor,
            topLeft = Offset(eyeCenter.x - eyeSize.width / 2f, eyeCenter.y - eyeSize.height / 2f),
            size = eyeSize
        )
    }
}

private fun blinkOpenness(phase: Float): Float {
    val timeInBlinkCycle = phase % BLINK_INTERVAL_SECONDS
    if (timeInBlinkCycle > BLINK_DURATION_SECONDS) return 1f
    val halfBlink = BLINK_DURATION_SECONDS / 2f
    return (abs(timeInBlinkCycle - halfBlink) / halfBlink).coerceAtLeast(0.12f)
}

private fun flutteredEmberrGhostPoint(point: Offset, phase: Float, energy: Float): Offset {
    val tailWeight = if (point.y > 0.55f) ((0.45f - point.x) / 0.45f).coerceIn(0f, 1f) else 0f
    val hemWeight = ((point.y - 0.8f) / 0.17f).coerceIn(0f, 1f)
    val flutterX = 0.015f * tailWeight * sin(phase * 2.6f + point.x * 7f)
    val flutterY = (0.02f + 0.03f * energy) * tailWeight * sin(phase * 3.2f + point.x * 9f) +
        0.012f * hemWeight * sin(phase * 4f + point.x * 14f)
    return Offset(point.x + flutterX, point.y + flutterY)
}

internal fun Path.traceEmberrGhost(phase: Float, energy: Float, emberrGhostTopLeft: Offset, emberrGhostSize: Float) {
    reset()
    val points = EmberrGhostOutline.map { point -> emberrGhostTopLeft + flutteredEmberrGhostPoint(point, phase, energy) * emberrGhostSize }
    moveTo(points[0].x, points[0].y)
    for (index in points.indices) {
        val before = points[(index - 1 + points.size) % points.size]
        val start = points[index]
        val end = points[(index + 1) % points.size]
        val after = points[(index + 2) % points.size]
        val firstControl = start + (end - before) / 6f
        val secondControl = end - (after - start) / 6f
        cubicTo(firstControl.x, firstControl.y, secondControl.x, secondControl.y, end.x, end.y)
    }
    close()
}
