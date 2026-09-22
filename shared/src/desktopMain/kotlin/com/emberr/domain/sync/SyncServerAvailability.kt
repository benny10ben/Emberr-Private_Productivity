package com.emberr.domain.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.BindException

class SyncServerAvailability {

    private val _status = MutableStateFlow<SyncServerStatus>(SyncServerStatus.Idle)
    val status = _status.asStateFlow()

    fun markRunning(port: Int) {
        _status.value = SyncServerStatus.Running(port)
    }

    fun markIdle() {
        _status.value = SyncServerStatus.Idle
    }

    fun markUnavailable(port: Int, cause: Throwable) {
        _status.value = SyncServerStatus.Unavailable(port, describeFailure(port, cause))
    }

    private fun describeFailure(port: Int, cause: Throwable): String {
        val isPortAlreadyTaken = generateSequence(cause) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .any { it is BindException }

        return if (isPortAlreadyTaken) {
            "Port $port is already in use by another program. Pick a different sync port in Settings to turn LAN sync back on."
        } else {
            cause.message ?: "LAN sync could not start on port $port."
        }
    }

    private companion object {
        const val MAX_CAUSE_DEPTH = 5
    }
}
