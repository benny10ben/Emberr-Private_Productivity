package com.emberr.presentation.onboarding

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeShader
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

actual class SilkShader actual constructor() {
    private val shaderBuilder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(SilkShaderSource))

    actual val isSupported: Boolean = true

    actual fun shaderFor(
        surfaceSize: Size,
        waveTime: Float,
        waveScale: Float,
        baseColor: Color,
        contrast: Float
    ): Shader? {
        shaderBuilder.uniform("surfaceSize", surfaceSize.width, surfaceSize.height)
        shaderBuilder.uniform("waveTime", waveTime)
        shaderBuilder.uniform("waveScale", waveScale)
        shaderBuilder.uniform("baseColor", baseColor.red, baseColor.green, baseColor.blue)
        shaderBuilder.uniform("contrast", contrast)

        return shaderBuilder.makeShader().asComposeShader()
    }
}
