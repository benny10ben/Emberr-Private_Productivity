// The colours every home screen widget shares so they all match the app's look.
package com.emberr.presentation.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.emberr.ui.theme.CharcoalNoir
import com.emberr.ui.theme.CloudVeil
import com.emberr.ui.theme.HighlightColor
import com.emberr.ui.theme.IroncladGrey
import com.emberr.ui.theme.UrbanFog

internal val surfaceColor = ColorProvider(day = Color.White, night = Color.Black)
internal val primaryTextColor = ColorProvider(day = Color.Black, night = Color.White)
internal val secondaryTextColor = ColorProvider(day = UrbanFog, night = UrbanFog)
internal val separatorColor = ColorProvider(day = Color(0xFFD4D4D4), night = Color(0xFF3A3A3A))
internal val elevatedSurfaceColor = ColorProvider(day = CloudVeil, night = IroncladGrey)
internal val highlightColor = ColorProvider(day = CharcoalNoir, night = CloudVeil)
internal val onHighlightColor = ColorProvider(day = CloudVeil, night = CharcoalNoir)
internal fun highlightedTextBackgroundColor(colorName: String?): ColorProvider {
    val highlightColor = HighlightColor.named(colorName)
    return ColorProvider(day = highlightColor.lightBackground, night = highlightColor.darkBackground)
}
