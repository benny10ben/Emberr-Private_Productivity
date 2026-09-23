package com.emberr.presentation.shared.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.ui.theme.LocalAppIsDark

object EmberrShadowElevation {
    val None = 0.dp
    val Standard = 14.dp
    const val StandardPx = 14f
}

val EmberrShadowSpotColor = Color.Black.copy(alpha = 0.20f)
val EmberrShadowAmbientColor = Color.Black.copy(alpha = 0.10f)
val EmberrShadowSpotColorSoft = Color.Black.copy(alpha = 0.14f)

val EmberrPillShadowSpotColor: Color
    @Composable get() =
        if (LocalAppIsDark.current) EmberrShadowSpotColor
        else Color.Black.copy(alpha = 0.10f)

val EmberrPillShadowAmbientColor: Color
    @Composable get() =
        if (LocalAppIsDark.current) EmberrShadowAmbientColor
        else Color.Transparent

fun Modifier.customEmberrShadow(
    shape: Shape,
    elevation: Dp = EmberrShadowElevation.Standard,
    spotColor: Color = EmberrShadowSpotColor,
    ambientColor: Color = EmberrShadowAmbientColor
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    spotColor = spotColor,
    ambientColor = ambientColor
)

object EmberrWindowShadow {
    val AmbientBlur = 7.dp
    val AmbientDrop = 4.dp
    val ContactBlur = 2.dp
    val ContactDrop = 1.dp
    const val AmbientAlpha = 0.30f
    const val ContactAlpha = 0.22f
    const val UnfocusedStrength = 0.45f
}
