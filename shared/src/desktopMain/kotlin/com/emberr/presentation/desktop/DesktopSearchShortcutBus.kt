package com.emberr.presentation.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Signals a Ctrl/Cmd+F press from the `Window`-level AWT key hook (DesktopMain.kt) down to
 * DesktopMainScreen, which reveals the sidebar and focuses its search field. A shared flow is used instead of a
 * direct callback because the Window and the screen composable sit on opposite ends of the
 * composition (Window wraps EmberrApp as content), the same cross-cutting-signal shape as AiEventBus.
 */
object DesktopSearchShortcutBus {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests = _requests.asSharedFlow()

    fun requestOpen() {
        _requests.tryEmit(Unit)
    }
}
