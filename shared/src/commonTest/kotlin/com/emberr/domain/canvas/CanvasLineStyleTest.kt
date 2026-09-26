package com.emberr.domain.canvas

import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasLineStyleTest {

    @Test
    fun everyLineStyleIsADifferentMixOfPatternAndArrowHead() {
        val combinations = CanvasLineStyle.entries.map { it.pattern to it.hasArrowHead }.toSet()

        assertEquals(6, combinations.size)
        assertEquals(setOf(null, LINE_PATTERN_DOTTED, LINE_PATTERN_DASHED), combinations.map { it.first }.toSet())
    }
}
