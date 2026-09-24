package com.emberr.domain.util.system

expect val isDesktopPlatform: Boolean
expect val appVersionName: String?
expect fun showFeedback(message: String)
expect fun triggerHapticFeedback()
expect fun restartApplication()
