package com.emberr.domain.ai.tools

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

data class NoteToolCallSummary(val toolName: String, val description: String)

class NoteToolCallEvents {

    private val _calls = MutableSharedFlow<NoteToolCallSummary>(extraBufferCapacity = 16)
    val calls: SharedFlow<NoteToolCallSummary> = _calls

    suspend fun notifyCalled(summary: NoteToolCallSummary) {
        _calls.emit(summary)
    }
}
