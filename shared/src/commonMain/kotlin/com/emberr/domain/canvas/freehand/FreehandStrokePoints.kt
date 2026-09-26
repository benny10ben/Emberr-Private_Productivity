package com.emberr.domain.canvas.freehand

private const val MINIMUM_STREAMLINE_FRACTION = 0.15
private const val STREAMLINE_FRACTION_RANGE = 0.85
private const val DEFAULT_FIRST_PRESSURE = 0.25
private const val DEFAULT_PRESSURE = 0.5
private val ONE_UNIT_DIAGONAL = FreehandPoint(1.0, 1.0)

fun freehandStroke(
    inputPoints: List<FreehandInputPoint>,
    options: FreehandStrokeOptions = FreehandStrokeOptions()
): List<FreehandPoint> = freehandOutlineOf(freehandStrokePoints(inputPoints, options), options)

fun freehandStrokePoints(
    inputPoints: List<FreehandInputPoint>,
    options: FreehandStrokeOptions = FreehandStrokeOptions()
): List<FreehandStrokePoint> {
    if (inputPoints.isEmpty()) return emptyList()

    val streamlineFraction = MINIMUM_STREAMLINE_FRACTION + (1 - options.streamline) * STREAMLINE_FRACTION_RANGE
    val points = withEnoughPointsToTaper(inputPoints)

    val strokePoints = mutableListOf(
        FreehandStrokePoint(
            point = FreehandPoint(points[0].x, points[0].y),
            pressure = points[0].pressure.validOr(DEFAULT_FIRST_PRESSURE),
            vector = ONE_UNIT_DIAGONAL,
            distance = 0.0,
            runningLength = 0.0
        )
    )
    var hasReachedMinimumLength = false
    var runningLength = 0.0
    var previous = strokePoints[0]
    val lastIndex = points.lastIndex

    for (index in 1..lastIndex) {
        val inputPoint = FreehandPoint(points[index].x, points[index].y)
        val point = if (options.isComplete && index == lastIndex) {
            inputPoint
        } else {
            previous.point.lerpTo(inputPoint, streamlineFraction)
        }
        if (previous.point.isAtSamePlaceAs(point)) continue

        val distance = point.distanceTo(previous.point)
        runningLength += distance

        if (index < lastIndex && !hasReachedMinimumLength) {
            if (runningLength < options.size) continue
            hasReachedMinimumLength = true
        }

        previous = FreehandStrokePoint(
            point = point,
            pressure = points[index].pressure.validOr(DEFAULT_PRESSURE),
            vector = (previous.point - point).unit(),
            distance = distance,
            runningLength = runningLength
        )
        strokePoints.add(previous)
    }

    strokePoints[0] = strokePoints[0].copy(vector = strokePoints.getOrNull(1)?.vector ?: FreehandPoint(0.0, 0.0))
    return strokePoints
}

private fun withEnoughPointsToTaper(inputPoints: List<FreehandInputPoint>): List<FreehandInputPoint> {
    val first = inputPoints[0]
    return when (inputPoints.size) {
        1 -> listOf(first, first.copy(x = first.x + ONE_UNIT_DIAGONAL.x, y = first.y + ONE_UNIT_DIAGONAL.y))
        2 -> {
            val start = FreehandPoint(first.x, first.y)
            val end = FreehandPoint(inputPoints[1].x, inputPoints[1].y)
            listOf(first) + (1..4).map { step ->
                val between = start.lerpTo(end, step / 4.0)
                FreehandInputPoint(between.x, between.y)
            }
        }
        else -> inputPoints
    }
}

private fun Double?.validOr(fallback: Double): Double = if (this != null && this >= 0.0) this else fallback
