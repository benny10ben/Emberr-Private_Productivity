package com.emberr.domain.util.sync

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class NoteSyncEvent {
    // A note's persisted content changed (incoming remote sync, an import, or "global_pinned") -
    // listeners should reload it, but only if they don't have unsaved local edits in flight
    data class NoteChanged(val entityId: String, val spaceId: String? = null) : NoteSyncEvent()

    // A specific block moved to a different daily note - safe to apply even mid-autosave since it
    // mutates only that one block id rather than reconciling a whole note's content
    data class BlockMoved(val blockId: String, val fromDateString: String?, val toDateString: String) : NoteSyncEvent()

    data class BlockRemoved(val blockId: String, val dateString: String) : NoteSyncEvent()
}

object SyncEventBus {
    // No replay (a listener that starts up later doesn't need history) but a real buffer with
    // DROP_OLDEST: emit() must never suspend waiting for a collector. A background sync can run with
    // no editor screen open at all, and every self-host sync call holds SyncCoordinator.mutex for its
    // full duration - an emit() that blocked on a collector would hang that mutex (and therefore every
    // other save/sync in the app) indefinitely instead of just being a missed UI refresh.
    private val _events = MutableSharedFlow<NoteSyncEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<NoteSyncEvent> = _events.asSharedFlow()

    suspend fun emitSyncCompleted(entityId: String, spaceId: String? = null) {
        _events.emit(NoteSyncEvent.NoteChanged(entityId, spaceId))
    }

    suspend fun emitBlockMoved(blockId: String, fromDateString: String?, toDateString: String) {
        _events.emit(NoteSyncEvent.BlockMoved(blockId, fromDateString, toDateString))
    }

    suspend fun emitBlockRemoved(blockId: String, dateString: String) {
        _events.emit(NoteSyncEvent.BlockRemoved(blockId, dateString))
    }
}
