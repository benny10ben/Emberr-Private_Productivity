package com.emberr.domain.selfhost.sync

import com.emberr.data.local.prefs.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelfHostConnectionState(private val settingsManager: SettingsManager) {
    private val _isConnected = MutableStateFlow(settingsManager.isSelfHostConnected())
    val isConnected = _isConnected.asStateFlow()

    fun markConnected() {
        settingsManager.saveSelfHostConnected(true)
        _isConnected.value = true
    }

    fun markDisconnected() {
        settingsManager.saveSelfHostConnected(false)
        _isConnected.value = false
    }
}
