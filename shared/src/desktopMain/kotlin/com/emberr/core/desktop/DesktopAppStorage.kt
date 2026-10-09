package com.emberr.core.desktop

import java.io.File

object DesktopAppStorage {

    const val DEBUG_BUILD_PROPERTY = "emberr.debugBuild"

    val isDebugBuild: Boolean = System.getProperty(DEBUG_BUILD_PROPERTY).toBoolean()

    val emberrDirectory: File = emberrDirectoryFor(File(System.getProperty("user.home")), isDebugBuild)

    val mediaDirectory: File
        get() = File(emberrDirectory, "media")

    fun emberrDirectoryFor(homeDirectory: File, isDebugBuild: Boolean): File =
        File(homeDirectory, if (isDebugBuild) ".emberr_debug" else ".emberr")
}
