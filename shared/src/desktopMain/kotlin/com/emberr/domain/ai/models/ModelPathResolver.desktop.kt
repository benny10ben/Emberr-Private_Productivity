package com.emberr.domain.ai.models

import com.emberr.core.desktop.DesktopAppStorage
import java.io.File

actual fun resolveModelPath(fileName: String): String {
    val modelsDir = File(DesktopAppStorage.emberrDirectory, "models")
    modelsDir.mkdirs()
    return File(modelsDir, fileName).absolutePath
}
actual fun modelFileExists(path: String): Boolean {
    val f = File(path)
    return f.exists() && f.length() > 0
}