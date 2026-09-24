package com.emberr.domain.update

import java.io.File

object RunningAppImage {

    fun fileOrNull(): File? {
        val appImagePath = System.getenv("APPIMAGE") ?: return null
        val mountDirectory = System.getenv("APPDIR") ?: return null
        val runtimeDirectory = System.getProperty("java.home") ?: return null

        val isRunningFromThisMount = File(runtimeDirectory).canonicalPath
            .startsWith(File(mountDirectory).canonicalPath + File.separator)

        return File(appImagePath).takeIf { isRunningFromThisMount && it.isFile }
    }
}
