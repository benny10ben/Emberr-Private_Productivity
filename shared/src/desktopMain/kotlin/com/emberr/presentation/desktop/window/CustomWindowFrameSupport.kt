package com.emberr.presentation.desktop.window

import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment

object CustomWindowFrameSupport {

    private const val OVERRIDE_PROPERTY = "emberr.customWindowFrame"

    val isSupportedByDesktop: Boolean by lazy { resolveAvailability() }

    private fun resolveAvailability(): Boolean {
        val override = System.getProperty(OVERRIDE_PROPERTY)?.trim()?.lowercase()
        if (override == "false" || override == "off" || override == "0") return false
        if (override == "true" || override == "on" || override == "1") return true

        val canDrawTranslucentWindow = runCatching {
            if (GraphicsEnvironment.isHeadless()) return@runCatching false
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)
        }.getOrDefault(false)

        return canDrawTranslucentWindow && NativeWindowActions.isAvailable
    }
}
