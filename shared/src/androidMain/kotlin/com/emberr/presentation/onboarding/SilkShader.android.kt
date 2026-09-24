package com.emberr.presentation.onboarding

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader

actual class SilkShader actual constructor() {
    private val runtimeShader: RuntimeShader? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(SilkShaderSource)
        } else {
            null
        }

    actual val isSupported: Boolean = runtimeShader != null

    actual fun shaderFor(
        surfaceSize: Size,
        waveTime: Float,
        waveScale: Float,
        baseColor: Color,
        contrast: Float
    ): Shader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val shader = runtimeShader ?: return null

        shader.setFloatUniform("surfaceSize", surfaceSize.width, surfaceSize.height)
        shader.setFloatUniform("waveTime", waveTime)
        shader.setFloatUniform("waveScale", waveScale)
        shader.setFloatUniform("baseColor", baseColor.red, baseColor.green, baseColor.blue)
        shader.setFloatUniform("contrast", contrast)

        return shader
    }
}
