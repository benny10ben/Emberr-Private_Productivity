package com.emberr.presentation.desktop.window

import java.awt.Frame

private val isRunningOnWindows = System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)

fun raiseWindowToFront(window: Frame) {
    window.toFront()
    window.requestFocus()

    if (!isRunningOnWindows || !window.isAlwaysOnTopSupported) return

    val wasAlwaysOnTop = window.isAlwaysOnTop
    window.isAlwaysOnTop = true
    window.toFront()
    window.isAlwaysOnTop = wasAlwaysOnTop
}
