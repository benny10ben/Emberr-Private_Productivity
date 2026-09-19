package com.emberr.ui.theme

import androidx.compose.ui.graphics.Color
import com.emberr.domain.model.defaultHighlightColorName

enum class HighlightColor(
    val storageName: String,
    val displayName: String,
    val lightBackground: Color,
    val darkBackground: Color,
    val cellBackgroundHex: String
) {
    YELLOW(defaultHighlightColorName, "Yellow", Color(0xFFFFF176), Color(0xFF6B5F10), HighlightCellBackgroundHex),
    LIME("lime", "Lime", Color(0xFFDCE775), Color(0xFF55611A), "#DCE775"),
    GREY("grey", "Grey", Color(0xFFD9D9D9), Color(0xFF4A4A4A), "#D9D9D9"),
    RED("red", "Red", Color(0xFFFFAB91), Color(0xFF6E2C1A), "#FFAB91"),
    BLUE("blue", "Blue", Color(0xFF90CAF9), Color(0xFF1E3E5F), "#90CAF9"),
    GREEN("green", "Green", Color(0xFFA5D6A7), Color(0xFF1F4A26), "#A5D6A7"),
    ORANGE("orange", "Orange", Color(0xFFFFCC80), Color(0xFF6B4415), "#FFCC80"),
    PINK("pink", "Pink", Color(0xFFF8BBD0), Color(0xFF5E2740), "#F8BBD0"),
    PURPLE("purple", "Purple", Color(0xFFD1C4E9), Color(0xFF3E2C5A), "#D1C4E9");

    fun backgroundFor(isDarkTheme: Boolean): Color = if (isDarkTheme) darkBackground else lightBackground

    companion object {
        val defaultColor = YELLOW

        fun named(storageName: String?): HighlightColor =
            entries.firstOrNull { color -> color.storageName == storageName } ?: defaultColor
    }
}

fun highlightBackgroundFor(storageName: String?, isDarkTheme: Boolean): Color =
    HighlightColor.named(storageName).backgroundFor(isDarkTheme)
