package com.emberr.domain.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasStrokePointsTest {

    @Test
    fun pointsWithoutPressureSurviveTheTripThroughText() {
        val points = listOf(CanvasStrokePoint(0f, 0f), CanvasStrokePoint(12.5f, -3.2f), CanvasStrokePoint(40.1f, 7f))

        assertEquals(points, CanvasStrokePoints.decode(CanvasStrokePoints.encode(points)))
    }

    @Test
    fun pointsWithPenPressureKeepTheirPressure() {
        val points = listOf(CanvasStrokePoint(0f, 0f, pressure = 0.25f), CanvasStrokePoint(3f, 4f, pressure = 0.8f))

        assertEquals(points, CanvasStrokePoints.decode(CanvasStrokePoints.encode(points)))
    }

    @Test
    fun positionsAreKeptToATenthOfAUnitAndPressureToAHundredth() {
        val encoded = CanvasStrokePoints.encode(listOf(CanvasStrokePoint(1.234f, 5.678f, pressure = 0.4567f)))

        assertEquals("12,57,46", encoded)
        assertEquals(listOf(CanvasStrokePoint(1.2f, 5.7f, pressure = 0.46f)), CanvasStrokePoints.decode(encoded))
    }

    @Test
    fun pressureOutsideZeroToOneIsClamped() {
        val encoded = CanvasStrokePoints.encode(listOf(CanvasStrokePoint(0f, 0f, pressure = 1.7f), CanvasStrokePoint(1f, 1f, pressure = -0.2f)))

        assertEquals(listOf(1f, 0f), CanvasStrokePoints.decode(encoded).map { it.pressure })
    }

    @Test
    fun damagedPointsAreSkippedInsteadOfBreakingTheStroke() {
        val decoded = CanvasStrokePoints.decode("10,20 oops 30 1,2,3,4 40,50,60")

        assertEquals(listOf(CanvasStrokePoint(1f, 2f), CanvasStrokePoint(4f, 5f, pressure = 0.6f)), decoded)
    }

    @Test
    fun emptyTextHasNoPoints() {
        assertTrue(CanvasStrokePoints.decode("").isEmpty())
        assertEquals("", CanvasStrokePoints.encode(emptyList()))
    }
}
