package com.emberr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrTextContextMenu

// Base color palette

val CharcoalNoir   = Color(0xFF0d0d0d)
val IroncladGrey   = Color(0xFF1E1E1E)
val UrbanFog       = Color(0xFF848484)
val CloudVeil      = Color(0xFFEFEFEF)
val HighlightLime      = Color(0xFFFFF176)
val HighlightLimeDark  = Color(0xFF6B5F10)
const val HighlightCellBackgroundHex = "#FDFFB6"

private val LightColorScheme = lightColorScheme(
    primary          = CharcoalNoir,
    onPrimary        = CloudVeil,
    background       = Color.White,
    onBackground     = Color.Black,
    surface          = CloudVeil,
    onSurface        = CharcoalNoir,
    outline          = UrbanFog,
    surfaceVariant = CloudVeil
)

private val DarkColorScheme = darkColorScheme(
    primary          = CloudVeil,
    onPrimary        = CharcoalNoir,
    background       = Color.Black,
    onBackground     = Color.White,
    surface          = IroncladGrey,
    onSurface        = CloudVeil,
    outline          = UrbanFog,
    surfaceVariant = Color(0xFF363636)
)

val LocalAppIsDark = staticCompositionLocalOf { false }

val highlightBackgroundColor: Color
    @Composable get() = if (LocalAppIsDark.current) HighlightLimeDark else HighlightLime

enum class FontSizePreference { SMALL, DEFAULT, LARGE }
enum class ThemePreference(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

@Composable
fun EmberrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontSizePreference: FontSizePreference = FontSizePreference.DEFAULT,
    fontStylePreference: FontStylePreference = FontStylePreference.POPPINS,
    content: @Composable () -> Unit
) {
    val baseColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val colorScheme = if (darkTheme && isDesktopPlatform) {
        baseColorScheme.copy(background = Color.Black)
    } else {
        baseColorScheme
    }

    val currentFontSizes = when {
        isDesktopPlatform -> when (fontSizePreference) {
            FontSizePreference.SMALL -> DesktopFontSizesSmall
            FontSizePreference.DEFAULT -> DesktopFontSizesDefault
            FontSizePreference.LARGE -> DesktopFontSizesLarge
        }
        else -> when (fontSizePreference) {
            FontSizePreference.SMALL -> MobileFontSizesSmall
            FontSizePreference.DEFAULT -> MobileFontSizesDefault
            FontSizePreference.LARGE -> MobileFontSizesLarge
        }
    }

    SetSystemBars(
        statusBarColor = Color.Transparent,
        darkIcons = !darkTheme
    )

    CompositionLocalProvider(
        LocalAppIsDark provides darkTheme,
        LocalEmberrFontSizes provides currentFontSizes,
        LocalEmberrFontStyle provides fontStylePreference
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography
        ) {
            EmberrTextContextMenu(content)
        }
    }
}