package com.emberr.domain.canvas.freehand

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class FreehandStrokeTest {

    private val tolerance = 1e-6

    private fun inputPointsOf(text: String): List<FreehandInputPoint> =
        text.split(' ').filter { it.isNotEmpty() }.map { point ->
            val values = point.split(',').map { it.toDouble() }
            FreehandInputPoint(values[0], values[1], values.getOrNull(2))
        }

    private fun outlineOf(text: String): List<FreehandPoint> =
        text.split(' ').filter { it.isNotEmpty() }.map { point ->
            val values = point.split(',').map { it.toDouble() }
            FreehandPoint(values[0], values[1])
        }

    private fun strokePointsOf(text: String): List<FreehandStrokePoint> =
        text.split(' ').filter { it.isNotEmpty() }.map { point ->
            val values = point.split(',').map { it.toDouble() }
            FreehandStrokePoint(
                point = FreehandPoint(values[0], values[1]),
                pressure = values[2],
                vector = FreehandPoint(values[3], values[4]),
                distance = values[5],
                runningLength = values[6]
            )
        }

    private fun assertClose(expected: Double, actual: Double, description: String) {
        if (abs(expected - actual) > tolerance) fail("$description: expected $expected but was $actual")
    }

    private fun assertOutlinesMatch(expected: List<FreehandPoint>, actual: List<FreehandPoint>, name: String) {
        assertEquals(expected.size, actual.size, "$name: number of outline points")
        expected.zip(actual).forEachIndexed { index, (expectedPoint, actualPoint) ->
            assertClose(expectedPoint.x, actualPoint.x, "$name: x of point $index")
            assertClose(expectedPoint.y, actualPoint.y, "$name: y of point $index")
        }
    }

    private fun List<FreehandPoint>.hasNoInvalidNumbers() = all { it.x.isFinite() && it.y.isFinite() }

    @Test
    fun everyUpstreamStrokeMatchesTheUpstreamOutline() {
        FreehandStrokeFixtures.inputs.forEach { (name, inputText) ->
            val expected = outlineOf(FreehandStrokeFixtures.outlinesWithDefaultOptions.getValue(name))
            assertOutlinesMatch(expected, freehandStroke(inputPointsOf(inputText)), name)
        }
    }

    @Test
    fun everyUpstreamStrokeMatchesTheUpstreamStrokePoints() {
        FreehandStrokeFixtures.strokePointsWithDefaultOptions.forEach { (name, expectedText) ->
            val expected = strokePointsOf(expectedText)
            val actual = freehandStrokePoints(inputPointsOf(FreehandStrokeFixtures.inputs.getValue(name)))
            assertEquals(expected.size, actual.size, "$name: number of stroke points")
            expected.zip(actual).forEachIndexed { index, (expectedPoint, actualPoint) ->
                assertClose(expectedPoint.point.x, actualPoint.point.x, "$name: x of point $index")
                assertClose(expectedPoint.point.y, actualPoint.point.y, "$name: y of point $index")
                assertClose(expectedPoint.pressure, actualPoint.pressure, "$name: pressure of point $index")
                assertClose(expectedPoint.vector.x, actualPoint.vector.x, "$name: vector x of point $index")
                assertClose(expectedPoint.vector.y, actualPoint.vector.y, "$name: vector y of point $index")
                assertClose(expectedPoint.distance, actualPoint.distance, "$name: distance of point $index")
                assertClose(expectedPoint.runningLength, actualPoint.runningLength, "$name: running length of point $index")
            }
        }
    }

    @Test
    fun aSinglePointWithTrickyOptionsMatchesTheUpstreamOutline() {
        val options = FreehandStrokeOptions(size = 1.0, thinning = 0.6, smoothing = 0.5, streamline = 0.5, simulatesPressure = true, isComplete = false)
        val actual = freehandStroke(inputPointsOf(FreehandStrokeFixtures.inputs.getValue("onePoint")), options)
        assertOutlinesMatch(outlineOf(FreehandStrokeFixtures.onePointOutlineWithTrickyOptions), actual, "onePoint with tricky options")
    }

    @Test
    fun noPointsGiveAnEmptyOutline() {
        assertTrue(freehandStroke(emptyList()).isEmpty())
        assertTrue(freehandStrokePoints(emptyList()).isEmpty())
    }

    @Test
    fun randomOptionsNeverProduceInvalidNumbers() {
        val random = Random(seed = 20260926)
        FreehandStrokeFixtures.inputs.forEach { (name, inputText) ->
            val inputPoints = inputPointsOf(inputText)
            repeat(500) {
                val options = FreehandStrokeOptions(
                    size = random.nextDouble() * 100,
                    thinning = random.nextDouble(),
                    smoothing = random.nextDouble(),
                    streamline = random.nextDouble(),
                    simulatesPressure = random.nextBoolean(),
                    isComplete = random.nextBoolean(),
                    start = FreehandStrokeEnd(hasCap = random.nextBoolean(), taper = FreehandTaper.Distance(random.nextDouble() * 100)),
                    end = FreehandStrokeEnd(hasCap = random.nextBoolean(), taper = FreehandTaper.Distance(random.nextDouble() * 100))
                )
                assertTrue(freehandStroke(inputPoints, options).hasNoInvalidNumbers(), "$name with $options")
            }
        }
    }

    @Test
    fun wholeStrokeTaperAndNoThinningNeverProduceInvalidNumbers() {
        val options = FreehandStrokeOptions(
            thinning = 0.0,
            start = FreehandStrokeEnd(taper = FreehandTaper.WholeStroke),
            end = FreehandStrokeEnd(hasCap = false, taper = FreehandTaper.WholeStroke)
        )
        FreehandStrokeFixtures.inputs.forEach { (name, inputText) ->
            assertTrue(freehandStroke(inputPointsOf(inputText), options).hasNoInvalidNumbers(), name)
        }
    }
}
