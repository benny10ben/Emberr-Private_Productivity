package com.emberr.domain.sync

interface SyncRepository {
    // Collects all changes since the given `since` timestamp for a specific peer.
    // Set `uploadMedia` to true when pushing changes to upload referenced media files before returning envelopes.
    suspend fun collectLocalChanges(since: Long, uploadMedia: Boolean = false): List<SyncEnvelope>

    // Applies incoming changes to the local database and file storage.
    // Returns true only if every envelope in the batch applies successfully.
    suspend fun applyRemoteChanges(changes: List<SyncEnvelope>): Boolean

    // Scans all referenced media against the remote peer's media list and retries transfers for missing files in either direction.
    suspend fun reconcileMedia()

    // Triggers an immediate retry for a specific media file download.
    fun retryMediaDownload(fileName: String)
}