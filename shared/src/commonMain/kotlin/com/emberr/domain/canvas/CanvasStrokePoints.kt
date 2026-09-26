package com.emberr.domain.canvas

import kotlin.math.roundToInt

data class CanvasStrokePoint(val x: Float, val y: Float, val pressure: Float? = null)

object CanvasStrokePoints {

    private const val POSITION_STEPS_PER_UNIT = 10f
    private const val PRESSURE_STEPS = 100f

    fun encode(points: List<CanvasStrokePoint>): String = points.joinToString(" ") { point ->
        val x = (point.x * POSITION_STEPS_PER_UNIT).roundToInt()
        val y = (point.y * POSITION_STEPS_PER_UNIT).roundToInt()
        val pressure = point.pressure?.let { (it.coerceIn(0f, 1f) * PRESSURE_STEPS).roundToInt() }
        if (pressure == null) "$x,$y" else "$x,$y,$pressure"
    }

    fun decode(text: String): List<CanvasStrokePoint> = text.split(' ').mapNotNull { point ->
        val values = point.split(',').map { it.toIntOrNull() ?: return@mapNotNull null }
        if (values.size !in 2..3) return@mapNotNull null
        CanvasStrokePoint(
            x = values[0] / POSITION_STEPS_PER_UNIT,
            y = values[1] / POSITION_STEPS_PER_UNIT,
            pressure = values.getOrNull(2)?.let { it / PRESSURE_STEPS }
        )
    }
}
