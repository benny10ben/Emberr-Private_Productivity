package com.emberr.domain.canvas.freehand

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class FreehandPoint(val x: Double, val y: Double) {
    operator fun plus(other: FreehandPoint) = FreehandPoint(x + other.x, y + other.y)
    operator fun minus(other: FreehandPoint) = FreehandPoint(x - other.x, y - other.y)
    operator fun times(scale: Double) = FreehandPoint(x * scale, y * scale)
    operator fun unaryMinus() = FreehandPoint(-x, -y)

    fun perpendicular() = FreehandPoint(y, -x)
    fun dot(other: FreehandPoint) = x * other.x + y * other.y
    fun length() = hypot(x, y)
    fun unit(): FreehandPoint {
        val length = length()
        return FreehandPoint(x / length, y / length)
    }

    fun distanceTo(other: FreehandPoint) = hypot(y - other.y, x - other.x)
    fun squaredDistanceTo(other: FreehandPoint): Double {
        val deltaX = x - other.x
        val deltaY = y - other.y
        return deltaX * deltaX + deltaY * deltaY
    }

    fun isAtSamePlaceAs(other: FreehandPoint) = x == other.x && y == other.y
    fun lerpTo(target: FreehandPoint, fraction: Double) = this + (target - this) * fraction
    fun movedAlong(direction: FreehandPoint, distance: Double) = this + direction * distance

    fun rotatedAround(center: FreehandPoint, radians: Double): FreehandPoint {
        val sine = sin(radians)
        val cosine = cos(radians)
        val relativeX = x - center.x
        val relativeY = y - center.y
        return FreehandPoint(
            relativeX * cosine - relativeY * sine + center.x,
            relativeX * sine + relativeY * cosine + center.y
        )
    }
}

data class FreehandInputPoint(val x: Double, val y: Double, val pressure: Double? = null)

data class FreehandStrokePoint(
    val point: FreehandPoint,
    val pressure: Double,
    val vector: FreehandPoint,
    val distance: Double,
    val runningLength: Double
)

sealed interface FreehandTaper {
    data object None : FreehandTaper
    data object WholeStroke : FreehandTaper
    data class Distance(val length: Double) : FreehandTaper
}

data class FreehandStrokeEnd(
    val hasCap: Boolean = true,
    val taper: FreehandTaper = FreehandTaper.None,
    val taperEasing: ((Double) -> Double)? = null
)

data class FreehandStrokeOptions(
    val size: Double = 16.0,
    val thinning: Double = 0.5,
    val smoothing: Double = 0.5,
    val streamline: Double = 0.5,
    val pressureEasing: (Double) -> Double = { pressure -> pressure },
    val simulatesPressure: Boolean = true,
    val start: FreehandStrokeEnd = FreehandStrokeEnd(),
    val end: FreehandStrokeEnd = FreehandStrokeEnd(),
    val isComplete: Boolean = false
)
