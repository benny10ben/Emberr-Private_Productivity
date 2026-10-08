package com.emberr.domain.selfhost.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SelfHostUpdateRequiredSignal {
    private val _isUpdateRequired = MutableStateFlow(false)
    val isUpdateRequired = _isUpdateRequired.asStateFlow()

    private val _shouldShowDialog = MutableStateFlow(false)
    val shouldShowDialog = _shouldShowDialog.asStateFlow()

    fun markUpdateRequired() {
        if (_isUpdateRequired.compareAndSet(expect = false, update = true)) {
            _shouldShowDialog.value = true
        }
    }

    fun dismissDialog() {
        _shouldShowDialog.value = false
    }
}
