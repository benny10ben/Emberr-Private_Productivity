package com.emberr.core.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopAppStorageTest {

    private val homeDirectory = File("/home/someone")

    @Test
    fun releaseBuildUsesTheEmberrFolder() {
        assertEquals(
            File(homeDirectory, ".emberr"),
            DesktopAppStorage.emberrDirectoryFor(homeDirectory, isDebugBuild = false)
        )
    }

    @Test
    fun debugBuildUsesTheEmberrDebugFolder() {
        assertEquals(
            File(homeDirectory, ".emberr_debug"),
            DesktopAppStorage.emberrDirectoryFor(homeDirectory, isDebugBuild = true)
        )
    }

    @Test
    fun appIsTreatedAsReleaseWhenTheDebugFlagIsMissing() {
        assertFalse(DesktopAppStorage.isDebugBuild)
        assertEquals(".emberr", DesktopAppStorage.emberrDirectory.name)
        assertEquals(File(DesktopAppStorage.emberrDirectory, "media"), DesktopAppStorage.mediaDirectory)
    }
}
