package com.emberr.domain.util.system

import com.emberr.presentation.desktop.DesktopRestartBus

actual val isDesktopPlatform = true
actual val appVersionName: String?
    get() = System.getProperty("jpackage.app-version")

actual fun showFeedback(message: String) {
    println("Feedback: $message")
}

actual fun triggerHapticFeedback() {}

actual fun restartApplication() {
    DesktopRestartBus.requestRestart()
}
