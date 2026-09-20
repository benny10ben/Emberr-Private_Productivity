package com.emberr.presentation.desktop.window

import java.util.concurrent.TimeUnit

enum class WindowButtonKind {
    Minimize,
    Maximize,
    Close
}

data class WindowButtonLayout(
    val leadingButtons: List<WindowButtonKind>,
    val trailingButtons: List<WindowButtonKind>
) {
    companion object {
        val Fallback = WindowButtonLayout(
            leadingButtons = emptyList(),
            trailingButtons = listOf(
                WindowButtonKind.Minimize,
                WindowButtonKind.Maximize,
                WindowButtonKind.Close
            )
        )
    }
}

object DesktopWindowButtonLayout {

    val current: WindowButtonLayout by lazy {
        val setting = readDesktopSetting() ?: return@lazy WindowButtonLayout.Fallback
        parseButtonLayout(setting) ?: WindowButtonLayout.Fallback
    }

    private fun readDesktopSetting(): String? = runCatching {
        val process = ProcessBuilder(
            "gsettings", "get", "org.gnome.desktop.wm.preferences", "button-layout"
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(2, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching null
        }
        if (process.exitValue() != 0) return@runCatching null
        output.trim().trim('\'', '"').ifBlank { null }
    }.getOrNull()

    private fun parseButtonLayout(setting: String): WindowButtonLayout? {
        val sides = setting.split(':')
        if (sides.size != 2) return null
        val leading = parseSide(sides[0])
        val trailing = parseSide(sides[1])
        if (leading.isEmpty() && trailing.isEmpty()) return null
        return WindowButtonLayout(leadingButtons = leading, trailingButtons = trailing)
    }

    private fun parseSide(side: String): List<WindowButtonKind> =
        side.split(',')
            .mapNotNull { name ->
                when (name.trim().lowercase()) {
                    "minimize" -> WindowButtonKind.Minimize
                    "maximize" -> WindowButtonKind.Maximize
                    "close" -> WindowButtonKind.Close
                    else -> null
                }
            }
            .distinct()
}
