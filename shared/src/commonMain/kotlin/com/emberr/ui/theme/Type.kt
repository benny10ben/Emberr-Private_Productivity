package com.emberr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.poppins_bold
import emberr.shared.generated.resources.poppins_medium
import emberr.shared.generated.resources.poppins_regular
import emberr.shared.generated.resources.inter_bold
import emberr.shared.generated.resources.inter_medium
import emberr.shared.generated.resources.inter_regular
import emberr.shared.generated.resources.lora_bold
import emberr.shared.generated.resources.lora_medium
import emberr.shared.generated.resources.lora_regular
import emberr.shared.generated.resources.merriweather_bold
import emberr.shared.generated.resources.merriweather_medium
import emberr.shared.generated.resources.merriweather_regular
import emberr.shared.generated.resources.opensans_bold
import emberr.shared.generated.resources.opensans_medium
import emberr.shared.generated.resources.opensans_regular
import emberr.shared.generated.resources.jetbrainsmono_bold
import emberr.shared.generated.resources.jetbrainsmono_medium
import emberr.shared.generated.resources.jetbrainsmono_regular
import org.jetbrains.compose.resources.Font

// --- Font Size Definitions ---

data class EmberrFontSizes(
    val bodyLarge: TextUnit,
    val titleLarge: TextUnit,
    val labelSmall: TextUnit
)

// Mobile Profiles
val MobileFontSizesExtraSmall = EmberrFontSizes(bodyLarge = 13.sp, titleLarge = 19.sp, labelSmall = 11.sp)
val MobileFontSizesSmall = EmberrFontSizes(bodyLarge = 14.sp, titleLarge = 20.sp, labelSmall = 12.sp)
val MobileFontSizesDefault = EmberrFontSizes(bodyLarge = 15.sp, titleLarge = 21.sp, labelSmall = 13.sp)
val MobileFontSizesLarge = EmberrFontSizes(bodyLarge = 16.sp, titleLarge = 22.sp, labelSmall = 14.sp)
val MobileFontSizesExtraLarge = EmberrFontSizes(bodyLarge = 17.sp, titleLarge = 23.sp, labelSmall = 15.sp)

// Desktop Profiles (Scaled up slightly)
val DesktopFontSizesExtraSmall = EmberrFontSizes(bodyLarge = 14.sp, titleLarge = 20.sp, labelSmall = 12.sp)
val DesktopFontSizesSmall = EmberrFontSizes(bodyLarge = 15.sp, titleLarge = 21.sp, labelSmall = 13.sp)
val DesktopFontSizesDefault = EmberrFontSizes(bodyLarge = 16.sp, titleLarge = 22.sp, labelSmall = 14.sp)
val DesktopFontSizesLarge = EmberrFontSizes(bodyLarge = 17.sp, titleLarge = 23.sp, labelSmall = 15.sp)
val DesktopFontSizesExtraLarge = EmberrFontSizes(bodyLarge = 18.sp, titleLarge = 24.sp, labelSmall = 16.sp)

val LocalEmberrFontSizes = staticCompositionLocalOf { MobileFontSizesDefault }

// --- Font Style (Family) Definitions ---

enum class FontStylePreference(val displayName: String) {
    POPPINS("Poppins"),
    INTER("Inter"),
    LORA("Lora"),
    MERRIWEATHER("Merriweather"),
    OPEN_SANS("Open Sans"),
    JETBRAINS_MONO("JetBrains Mono")
}

val LocalEmberrFontStyle = staticCompositionLocalOf { FontStylePreference.POPPINS }

@Composable
fun fontFamilyFor(preference: FontStylePreference): FontFamily = when (preference) {
    FontStylePreference.POPPINS -> FontFamily(
        Font(Res.font.poppins_regular, FontWeight.Normal),
        Font(Res.font.poppins_medium, FontWeight.Medium),
        Font(Res.font.poppins_bold, FontWeight.Bold)
    )
    FontStylePreference.INTER -> FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_bold, FontWeight.Bold)
    )
    FontStylePreference.LORA -> FontFamily(
        Font(Res.font.lora_regular, FontWeight.Normal),
        Font(Res.font.lora_medium, FontWeight.Medium),
        Font(Res.font.lora_bold, FontWeight.Bold)
    )
    FontStylePreference.MERRIWEATHER -> FontFamily(
        Font(Res.font.merriweather_regular, FontWeight.Normal),
        Font(Res.font.merriweather_medium, FontWeight.Medium),
        Font(Res.font.merriweather_bold, FontWeight.Bold)
    )
    FontStylePreference.OPEN_SANS -> FontFamily(
        Font(Res.font.opensans_regular, FontWeight.Normal),
        Font(Res.font.opensans_medium, FontWeight.Medium),
        Font(Res.font.opensans_bold, FontWeight.Bold)
    )
    FontStylePreference.JETBRAINS_MONO -> FontFamily(
        Font(Res.font.jetbrainsmono_regular, FontWeight.Normal),
        Font(Res.font.jetbrainsmono_medium, FontWeight.Medium),
        Font(Res.font.jetbrainsmono_bold, FontWeight.Bold)
    )
}

// --- Typography ---

val AppTypography: Typography
    @Composable
    get() {
        val family = fontFamilyFor(LocalEmberrFontStyle.current)
        val sizes = LocalEmberrFontSizes.current

        return Typography(
            bodyLarge = TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Normal,
                fontSize = sizes.bodyLarge,
                lineHeight = (sizes.bodyLarge.value * 1.5f).sp,
                letterSpacing = 0.5.sp
            ),
            titleLarge = TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = sizes.titleLarge,
                lineHeight = (sizes.titleLarge.value * 1.25f).sp,
                letterSpacing = 0.sp
            ),
            labelSmall = TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Medium,
                fontSize = sizes.labelSmall,
                lineHeight = (sizes.labelSmall.value * 1.5f).sp,
                letterSpacing = 0.5.sp
            )
        )
    }