package com.emberr.domain.reminders

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

object ReminderClickBus {

    private val _pendingBlockId = MutableStateFlow<String?>(null)
    val pendingBlockId: StateFlow<String?> = _pendingBlockId.asStateFlow()

    fun requestOpen(blockId: String) {
        _pendingBlockId.value = blockId
    }

    fun consumePendingBlockId(): String? = _pendingBlockId.getAndUpdate { null }
}
