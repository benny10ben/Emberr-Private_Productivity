package com.emberr.presentation.onboarding

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader

internal const val SilkShaderSource = """
uniform float2 surfaceSize;
uniform float waveTime;
uniform float3 baseColor;
uniform float contrast;
uniform float waveScale;

half4 main(float2 fragCoord) {
    float2 tex = float2(fragCoord.x / surfaceSize.x, 1.0 - fragCoord.y / surfaceSize.y) * waveScale;
    tex.y += 0.03 * sin(8.0 * tex.x - waveTime);

    float pattern = 0.6 + 0.4 * sin(
        5.0 * (tex.x + tex.y + cos(3.0 * tex.x + 5.0 * tex.y) + 0.02 * waveTime) +
        sin(20.0 * (tex.x + tex.y - 0.1 * waveTime))
    );

    float shade = mix(1.0, pattern, contrast);
    return half4(half3(baseColor * shade), 1.0);
}
"""

expect class SilkShader() {
    val isSupported: Boolean

    fun shaderFor(
        surfaceSize: Size,
        waveTime: Float,
        waveScale: Float,
        baseColor: Color,
        contrast: Float
    ): Shader?
}
