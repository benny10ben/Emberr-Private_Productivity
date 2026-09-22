package com.emberr.presentation.desktop.window

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.emberr.ui.theme.LocalAppIsDark
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import kotlinx.coroutines.delay
import java.awt.Window
import javax.swing.RootPaneContainer

private const val DARK_TITLE_BAR_ATTRIBUTE = 20
private const val DARK_TITLE_BAR_ATTRIBUTE_ON_OLDER_WINDOWS = 19
private const val TITLE_BAR_COLOR_ATTRIBUTE = 35
private const val TITLE_TEXT_COLOR_ATTRIBUTE = 36
private const val WINDOW_BORDER_COLOR_ATTRIBUTE = 34
private const val ATTRIBUTE_WAS_ACCEPTED = 0
private const val KEEP_CURRENT_SIZE = 0x0001
private const val KEEP_CURRENT_POSITION = 0x0002
private const val KEEP_CURRENT_STACKING = 0x0004
private const val KEEP_CURRENT_FOCUS = 0x0010
private const val REDRAW_THE_WINDOW_FRAME = 0x0020
private const val WINDOW_HANDLE_ATTEMPTS = 20
private const val DELAY_BETWEEN_ATTEMPTS_MILLIS = 50L

private interface DesktopWindowManagerLibrary : Library {
    fun DwmSetWindowAttribute(
        windowHandle: WinDef.HWND,
        attribute: Int,
        value: Pointer,
        valueSizeInBytes: Int
    ): Int
}

@Composable
fun MatchWindowsTitleBarToAppTheme(window: Window) {
    val isDarkTheme = LocalAppIsDark.current
    val appBackgroundColor = MaterialTheme.colorScheme.background
    val appTextColor = MaterialTheme.colorScheme.onBackground

    LaunchedEffect(window, isDarkTheme, appBackgroundColor, appTextColor) {
        if (!WindowsWindowDecorations.isRunningOnWindows) return@LaunchedEffect
        repeat(WINDOW_HANDLE_ATTEMPTS) {
            val wasApplied = WindowsWindowDecorations.paintWindowFrameWithAppColors(
                window = window,
                isDarkTheme = isDarkTheme,
                backgroundColor = appBackgroundColor,
                textColor = appTextColor
            )
            if (wasApplied) return@LaunchedEffect
            delay(DELAY_BETWEEN_ATTEMPTS_MILLIS)
        }
    }
}

object WindowsWindowDecorations {

    val isRunningOnWindows: Boolean by lazy {
        System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)
    }

    fun paintWindowFrameWithAppColors(
        window: Window,
        isDarkTheme: Boolean,
        backgroundColor: Color,
        textColor: Color
    ): Boolean {
        if (!isRunningOnWindows) return true

        val opaqueBackground = java.awt.Color(backgroundColor.toArgb())
        runCatching {
            window.background = opaqueBackground
            (window as? RootPaneContainer)?.contentPane?.background = opaqueBackground
        }

        val desktopWindowManager = desktopWindowManagerLibrary ?: return true
        val windowHandle = readNativeWindowHandle(window) ?: return false

        val useDarkTitleBar = if (isDarkTheme) 1 else 0
        val acceptedNewerAttribute = writeWindowAttribute(
            desktopWindowManager,
            windowHandle,
            DARK_TITLE_BAR_ATTRIBUTE,
            useDarkTitleBar
        )
        if (!acceptedNewerAttribute) {
            writeWindowAttribute(
                desktopWindowManager,
                windowHandle,
                DARK_TITLE_BAR_ATTRIBUTE_ON_OLDER_WINDOWS,
                useDarkTitleBar
            )
        }

        val frameColorValue = toWindowsColorValue(backgroundColor)
        writeWindowAttribute(desktopWindowManager, windowHandle, TITLE_BAR_COLOR_ATTRIBUTE, frameColorValue)
        writeWindowAttribute(desktopWindowManager, windowHandle, WINDOW_BORDER_COLOR_ATTRIBUTE, frameColorValue)
        writeWindowAttribute(
            desktopWindowManager,
            windowHandle,
            TITLE_TEXT_COLOR_ATTRIBUTE,
            toWindowsColorValue(textColor)
        )

        redrawWindowFrame(windowHandle)
        return true
    }

    private val desktopWindowManagerLibrary: DesktopWindowManagerLibrary? by lazy {
        if (!isRunningOnWindows) return@lazy null
        runCatching { Native.load("dwmapi", DesktopWindowManagerLibrary::class.java) }.getOrNull()
    }

    private fun readNativeWindowHandle(window: Window): WinDef.HWND? = runCatching {
        if (!window.isDisplayable) return@runCatching null
        WinDef.HWND(Native.getWindowPointer(window))
    }.getOrNull()

    private fun writeWindowAttribute(
        desktopWindowManager: DesktopWindowManagerLibrary,
        windowHandle: WinDef.HWND,
        attribute: Int,
        value: Int
    ): Boolean = runCatching {
        val valueBuffer = Memory(Integer.BYTES.toLong())
        valueBuffer.setInt(0, value)
        val result = desktopWindowManager.DwmSetWindowAttribute(
            windowHandle,
            attribute,
            valueBuffer,
            Integer.BYTES
        )
        result == ATTRIBUTE_WAS_ACCEPTED
    }.getOrDefault(false)

    private fun redrawWindowFrame(windowHandle: WinDef.HWND) {
        runCatching {
            User32.INSTANCE.SetWindowPos(
                windowHandle,
                null,
                0,
                0,
                0,
                0,
                KEEP_CURRENT_SIZE or
                    KEEP_CURRENT_POSITION or
                    KEEP_CURRENT_STACKING or
                    KEEP_CURRENT_FOCUS or
                    REDRAW_THE_WINDOW_FRAME
            )
        }
    }

    private fun toWindowsColorValue(color: Color): Int {
        val argbValue = color.toArgb()
        val red = (argbValue shr 16) and 0xFF
        val green = (argbValue shr 8) and 0xFF
        val blue = argbValue and 0xFF
        return (blue shl 16) or (green shl 8) or red
    }
}
