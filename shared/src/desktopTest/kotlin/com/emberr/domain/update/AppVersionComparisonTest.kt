package com.emberr.domain.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppVersionComparisonTest {

    @Test
    fun higherPatchMinorOrMajorIsNewer() {
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun partsAreComparedAsNumbersNotText() {
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
    }

    @Test
    fun sameOrOlderVersionIsNotNewer() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun missingPartsCountAsZero() {
        assertFalse(isNewerVersion("1.1", "1.1.0"))
        assertTrue(isNewerVersion("1.1.1", "1.1"))
    }

    @Test
    fun malformedVersionIsNeverNewer() {
        assertFalse(isNewerVersion("", "1.0.0"))
        assertFalse(isNewerVersion("1.1.0-beta", "1.0.0"))
        assertFalse(isNewerVersion("1.1.0", "unknown"))
    }
}
