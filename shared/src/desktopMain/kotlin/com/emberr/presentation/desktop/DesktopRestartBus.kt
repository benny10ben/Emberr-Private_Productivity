package com.emberr.presentation.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object DesktopRestartBus {

    var isRestartRequested: Boolean by mutableStateOf(false)
        private set

    fun requestRestart() {
        isRestartRequested = true
    }
}
