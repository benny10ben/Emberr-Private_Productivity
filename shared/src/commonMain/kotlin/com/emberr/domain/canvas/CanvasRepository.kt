package com.emberr.domain.canvas

import com.emberr.data.local.room.dao.CanvasDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.domain.sync.AutoSyncTrigger
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.domain.vault.VaultMirrorTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CanvasRepository(
    private val canvasDao: CanvasDao,
    private val noteDao: NoteDao
) {

    private val _locallySavedNoteIds = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val locallySavedNoteIds: SharedFlow<String> = _locallySavedNoteIds.asSharedFlow()

    suspend fun loadCanvasIncludingDeleted(noteId: String): CanvasContent = withContext(Dispatchers.IO) {
        CanvasContent(
            nodes = canvasDao.getAllNodesForNoteIncludingDeleted(noteId),
            edges = canvasDao.getAllEdgesForNoteIncludingDeleted(noteId)
        )
    }

    suspend fun saveChanges(noteId: String, changes: CanvasContent) {
        if (changes.isEmpty()) return
        withContext(Dispatchers.IO) {
            SyncCoordinator.mutex.withLock {
                if (noteDao.getNoteById(noteId) == null) return@withLock
                val ownedChanges = changes.withNoteId(noteId)
                canvasDao.upsertNodes(ownedChanges.nodes)
                canvasDao.upsertEdges(ownedChanges.edges)
                noteDao.updateNoteUpdatedAt(noteId, System.currentTimeMillis())
            }
            _locallySavedNoteIds.tryEmit(noteId)
            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestNoteRefresh(noteId)
        }
    }

    suspend fun applyRemoteCanvasWhileSyncLockHeld(
        noteId: String,
        remote: CanvasContent,
        remoteNoteUpdatedAt: Long? = null
    ): Boolean =
        withContext(Dispatchers.IO) {
            val local = loadCanvasIncludingDeleted(noteId)
            val newerRemoteItems = CanvasMerge.remoteItemsNewerThanLocal(local, remote.withNoteId(noteId))
            if (newerRemoteItems.isEmpty()) return@withContext false
            canvasDao.upsertNodes(newerRemoteItems.nodes)
            canvasDao.upsertEdges(newerRemoteItems.edges)
            val localNoteUpdatedAt = noteDao.getNoteById(noteId)?.updatedAt
            if (remoteNoteUpdatedAt != null && localNoteUpdatedAt != null && remoteNoteUpdatedAt > localNoteUpdatedAt) {
                noteDao.updateNoteUpdatedAt(noteId, remoteNoteUpdatedAt)
            }
            VaultMirrorTrigger.requestNoteRefresh(noteId)
            true
        }
}
