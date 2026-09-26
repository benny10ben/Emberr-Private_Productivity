package com.emberr.domain.canvas.freehand

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

private const val RATE_OF_PRESSURE_CHANGE = 0.275
private const val PI_WITH_RENDERING_OFFSET = PI + 0.0001
private const val START_CAP_SEGMENTS = 13
private const val END_CAP_SEGMENTS = 29
private const val CORNER_CAP_SEGMENTS = 13
private const val END_NOISE_LENGTH = 3.0
private const val MINIMUM_RADIUS = 0.01
private const val POINTS_AVERAGED_FOR_STARTING_PRESSURE = 10

fun freehandOutlineOf(
    strokePoints: List<FreehandStrokePoint>,
    options: FreehandStrokeOptions = FreehandStrokeOptions()
): List<FreehandPoint> {
    val size = options.size
    if (strokePoints.isEmpty() || size <= 0) return emptyList()

    val startTaperEasing = options.start.taperEasing ?: { distance -> distance * (2 - distance) }
    val endTaperEasing = options.end.taperEasing ?: { distance ->
        val shifted = distance - 1
        shifted * shifted * shifted + 1
    }
    val totalLength = strokePoints.last().runningLength
    val startTaperLength = options.start.taper.lengthFor(size, totalLength)
    val endTaperLength = options.end.taper.lengthFor(size, totalLength)
    val minimumSpacing = size * options.smoothing
    val minimumSquaredDistance = minimumSpacing * minimumSpacing

    val leftPoints = mutableListOf<FreehandPoint>()
    val rightPoints = mutableListOf<FreehandPoint>()
    var previousPressure = startingPressure(strokePoints, options.simulatesPressure, size)
    var radius = strokeRadius(size, options.thinning, strokePoints.last().pressure, options.pressureEasing)
    var firstRadius: Double? = null
    var previousVector = strokePoints[0].vector
    var previousLeftPoint = strokePoints[0].point
    var previousRightPoint = previousLeftPoint
    var latestLeftPoint = previousLeftPoint
    var latestRightPoint = previousRightPoint
    var wasPreviousPointSharpCorner = false

    for (index in strokePoints.indices) {
        val strokePoint = strokePoints[index]
        val point = strokePoint.point
        val vector = strokePoint.vector
        val isLastPoint = index == strokePoints.lastIndex
        var pressure = strokePoint.pressure

        if (!isLastPoint && totalLength - strokePoint.runningLength < END_NOISE_LENGTH) continue

        if (options.thinning != 0.0) {
            if (options.simulatesPressure) {
                pressure = simulatedPressure(previousPressure, strokePoint.distance, size)
            }
            radius = strokeRadius(size, options.thinning, pressure, options.pressureEasing)
        } else {
            radius = size / 2
        }
        if (firstRadius == null) firstRadius = radius

        val lengthFromEnd = totalLength - strokePoint.runningLength
        val startTaperStrength = if (strokePoint.runningLength < startTaperLength) {
            startTaperEasing(strokePoint.runningLength / startTaperLength)
        } else {
            1.0
        }
        val endTaperStrength = if (lengthFromEnd < endTaperLength) endTaperEasing(lengthFromEnd / endTaperLength) else 1.0
        radius = max(MINIMUM_RADIUS, radius * min(startTaperStrength, endTaperStrength))

        val nextVector = (if (isLastPoint) strokePoint else strokePoints[index + 1]).vector
        val nextTurn = if (isLastPoint) 1.0 else vector.dot(nextVector)
        val previousTurn = vector.dot(previousVector)
        val isPointSharpCorner = previousTurn < 0 && !wasPreviousPointSharpCorner
        val isNextPointSharpCorner = nextTurn < 0

        if (isPointSharpCorner || isNextPointSharpCorner) {
            val offset = previousVector.perpendicular() * radius
            val step = 1.0 / CORNER_CAP_SEGMENTS
            var fraction = 0.0
            while (fraction <= 1) {
                latestLeftPoint = (point - offset).rotatedAround(point, PI_WITH_RENDERING_OFFSET * fraction)
                leftPoints.add(latestLeftPoint)
                latestRightPoint = (point + offset).rotatedAround(point, PI_WITH_RENDERING_OFFSET * -fraction)
                rightPoints.add(latestRightPoint)
                fraction += step
            }
            previousLeftPoint = latestLeftPoint
            previousRightPoint = latestRightPoint
            if (isNextPointSharpCorner) wasPreviousPointSharpCorner = true
            continue
        }

        wasPreviousPointSharpCorner = false

        if (isLastPoint) {
            val offset = vector.perpendicular() * radius
            leftPoints.add(point - offset)
            rightPoints.add(point + offset)
            continue
        }

        val offset = nextVector.lerpTo(vector, nextTurn).perpendicular() * radius

        latestLeftPoint = point - offset
        if (index <= 1 || previousLeftPoint.squaredDistanceTo(latestLeftPoint) > minimumSquaredDistance) {
            leftPoints.add(latestLeftPoint)
            previousLeftPoint = latestLeftPoint
        }

        latestRightPoint = point + offset
        if (index <= 1 || previousRightPoint.squaredDistanceTo(latestRightPoint) > minimumSquaredDistance) {
            rightPoints.add(latestRightPoint)
            previousRightPoint = latestRightPoint
        }

        previousPressure = pressure
        previousVector = vector
    }

    val firstPoint = strokePoints[0].point
    val lastPoint = if (strokePoints.size > 1) strokePoints.last().point else firstPoint + FreehandPoint(1.0, 1.0)
    val isTapered = startTaperLength != 0.0 || endTaperLength != 0.0
    val startCap = mutableListOf<FreehandPoint>()
    val endCap = mutableListOf<FreehandPoint>()

    if (strokePoints.size == 1) {
        if (!isTapered || options.isComplete) {
            return dotOutline(firstPoint, firstRadius?.takeIf { it != 0.0 } ?: radius)
        }
    } else {
        if (startTaperLength == 0.0) {
            startCap += if (options.start.hasCap) {
                roundStartCap(firstPoint, rightPoints[0])
            } else {
                flatStartCap(firstPoint, leftPoints[0], rightPoints[0])
            }
        }

        val endDirection = (-strokePoints.last().vector).perpendicular()
        endCap += when {
            endTaperLength != 0.0 -> listOf(lastPoint)
            options.end.hasCap -> roundEndCap(lastPoint, endDirection, radius)
            else -> flatEndCap(lastPoint, endDirection, radius)
        }
    }

    return leftPoints + endCap + rightPoints.reversed() + startCap
}

private fun FreehandTaper.lengthFor(size: Double, totalLength: Double): Double = when (this) {
    FreehandTaper.None -> 0.0
    FreehandTaper.WholeStroke -> max(size, totalLength)
    is FreehandTaper.Distance -> length
}

private fun startingPressure(strokePoints: List<FreehandStrokePoint>, simulatesPressure: Boolean, size: Double): Double =
    strokePoints.take(POINTS_AVERAGED_FOR_STARTING_PRESSURE).fold(strokePoints[0].pressure) { averagePressure, strokePoint ->
        val pressure = if (simulatesPressure) {
            simulatedPressure(averagePressure, strokePoint.distance, size)
        } else {
            strokePoint.pressure
        }
        (averagePressure + pressure) / 2
    }

private fun simulatedPressure(previousPressure: Double, distance: Double, size: Double): Double {
    val speed = min(1.0, distance / size)
    val slowness = min(1.0, 1 - speed)
    return min(1.0, previousPressure + (slowness - previousPressure) * (speed * RATE_OF_PRESSURE_CHANGE))
}

private fun strokeRadius(size: Double, thinning: Double, pressure: Double, easing: (Double) -> Double): Double =
    size * easing(0.5 - thinning * (0.5 - pressure))

private fun dotOutline(center: FreehandPoint, radius: Double): List<FreehandPoint> {
    val offsetPoint = center + FreehandPoint(1.0, 1.0)
    val start = center.movedAlong((center - offsetPoint).perpendicular().unit(), -radius)
    val outline = mutableListOf<FreehandPoint>()
    val step = 1.0 / START_CAP_SEGMENTS
    var fraction = step
    while (fraction <= 1) {
        outline.add(start.rotatedAround(center, PI_WITH_RENDERING_OFFSET * 2 * fraction))
        fraction += step
    }
    return outline
}

private fun roundStartCap(center: FreehandPoint, rightPoint: FreehandPoint): List<FreehandPoint> {
    val cap = mutableListOf<FreehandPoint>()
    val step = 1.0 / START_CAP_SEGMENTS
    var fraction = step
    while (fraction <= 1) {
        cap.add(rightPoint.rotatedAround(center, PI_WITH_RENDERING_OFFSET * fraction))
        fraction += step
    }
    return cap
}

private fun flatStartCap(center: FreehandPoint, leftPoint: FreehandPoint, rightPoint: FreehandPoint): List<FreehandPoint> {
    val cornersVector = leftPoint - rightPoint
    val halfWidth = cornersVector * 0.5
    val slightlyWiderHalfWidth = cornersVector * 0.51
    return listOf(center - halfWidth, center - slightlyWiderHalfWidth, center + slightlyWiderHalfWidth, center + halfWidth)
}

private fun roundEndCap(center: FreehandPoint, direction: FreehandPoint, radius: Double): List<FreehandPoint> {
    val cap = mutableListOf<FreehandPoint>()
    val start = center.movedAlong(direction, radius)
    val step = 1.0 / END_CAP_SEGMENTS
    var fraction = step
    while (fraction < 1) {
        cap.add(start.rotatedAround(center, PI_WITH_RENDERING_OFFSET * 3 * fraction))
        fraction += step
    }
    return cap
}

private fun flatEndCap(center: FreehandPoint, direction: FreehandPoint, radius: Double): List<FreehandPoint> = listOf(
    center + direction * radius,
    center + direction * (radius * 0.99),
    center - direction * (radius * 0.99),
    center - direction * radius
)
