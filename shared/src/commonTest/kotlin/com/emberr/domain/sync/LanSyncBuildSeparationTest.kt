package com.emberr.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class LanSyncBuildSeparationTest {

    @Test
    fun releaseBuildKeepsTheOriginalPortAndServiceType() {
        assertEquals(8080, defaultSyncPortFor(isDebugBuild = false))
        assertEquals("_emberrsync._tcp.", discoveryServiceTypeFor(isDebugBuild = false))
    }

    @Test
    fun debugBuildUsesItsOwnPortAndServiceType() {
        assertEquals(8081, defaultSyncPortFor(isDebugBuild = true))
        assertEquals("_emberrdebug._tcp.", discoveryServiceTypeFor(isDebugBuild = true))
    }
}
