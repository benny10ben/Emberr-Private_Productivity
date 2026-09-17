package com.emberr.presentation.shared.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Sequences "dismiss the keyboard" and "show a sheet" into two consecutive animations
 * instead of one collision. Clears focus first so the IME can't bounce back, then waits
 * for the real inset animation rather than a guessed duration.
 */
@Stable
class KeyboardHandoff internal constructor(
    private val scope: CoroutineScope,
    private val imeInsets: WindowInsets,
    private val density: Density,
    private val focusManager: FocusManager,
    private val keyboard: SoftwareKeyboardController?
) {
    /** True from tap until the action fires. Feed into any visibility condition that would
     *  otherwise collapse when focus clears. Not needed for stable triggers. */
    var isPending by mutableStateOf(false)
        private set

    val isKeyboardOpen: Boolean
        get() = imeInsets.getBottom(density) > 0

    private var job: Job? = null

    suspend fun awaitKeyboardClosed() {
        if (!isKeyboardOpen) return
        focusManager.clearFocus()
        keyboard?.hide()
        withTimeoutOrNull(600.milliseconds) {
            snapshotFlow { imeInsets.getBottom(density) }.first { it == 0 }
        }
        delay(50.milliseconds)
    }

    fun run(action: () -> Unit) {
        if (isPending) return
        if (!isKeyboardOpen) {
            action()
            return
        }
        isPending = true
        job?.cancel()
        job = scope.launch {
            try {
                awaitKeyboardClosed()
                action()
            } finally {
                isPending = false
            }
        }
    }
}

@Composable
fun rememberKeyboardHandoff(): KeyboardHandoff {
    val scope = rememberCoroutineScope()
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(scope, imeInsets, density, focusManager, keyboard) {
        KeyboardHandoff(scope, imeInsets, density, focusManager, keyboard)
    }
}

@Composable
fun rememberShowAfterKeyboardCloses(isRequested: Boolean): Boolean {
    val handoff = rememberKeyboardHandoff()
    var isReadyToShow by remember { mutableStateOf(false) }

    LaunchedEffect(isRequested) {
        if (!isRequested) {
            isReadyToShow = false
            return@LaunchedEffect
        }
        handoff.awaitKeyboardClosed()
        isReadyToShow = true
    }

    return isReadyToShow
}
