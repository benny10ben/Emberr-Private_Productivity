package com.emberr.domain.sync

import com.emberr.data.local.room.DEFAULT_SPACE_ID
import kotlinx.serialization.Serializable

@Serializable
enum class SyncType {
    NOTE,
    SPACE,
    DAILY_NOTE,
    TAG,
    FOLDER,
    CATEGORY,
    EVENT_EXCEPTION,
    NOTE_TOMBSTONE,
    CHAT_SESSION,
    EXTERNAL_API_CONFIG,
    BOOKMARK_CATEGORY_ORDER,
    FAVORITE_NOTE_ORDER
}

// Carries a permanent (Trash "delete forever", or folder delete) note deletion over the LAN
// protocol - a plain NOTE envelope has no way to say "this was permanently deleted, not just
// trashed," since isDeleted on a NOTE envelope already means "soft-deleted to Trash." Without this,
// a hard delete on one device never reached the other, and a stale copy on the peer could even get
// pushed back and resurrect it.
@Serializable
data class NoteTombstonePayload(
    val noteId: String,
    val spaceId: String = DEFAULT_SPACE_ID,
    val isDaily: Boolean,
    val dateString: String?,
    val deletedAt: Long
)

@Serializable
data class SyncEnvelope(
    val entityId: String,
    // Defaulted so Json { coerceInputValues = true } can substitute this instead of throwing
    // when a peer running older code sends an entityType this build doesn't recognize (e.g. a
    // future SyncType case) - that envelope then simply fails to decode as the wrong entity type
    // downstream (caught per-envelope in SyncRepositoryImpl) instead of corrupting the whole batch.
    val entityType: SyncType = SyncType.NOTE,
    val updatedAt: Long,
    val isDeleted: Boolean,
    val metadataJson: String,
    val contentJson: String,
    // Only populated for NOTE/DAILY_NOTE envelopes that have local embeddings to offer - lets a peer
    // without the embedding model installed (or one that just never opened this note) adopt already-
    // computed vectors instead of having no index entry for it at all until it can embed locally.
    val embeddedBlocksJson: String = ""
)

@Serializable
data class SyncPayload(
    val changes: List<SyncEnvelope>
)

@Serializable
data class RemoteMediaEntry(
    val fileName: String,
    val lastModified: Long
)

@Serializable
data class RemoteMediaList(
    val entries: List<RemoteMediaEntry>
)

// Lets an upload client discover how many bytes of a previous, interrupted attempt the peer is
// still holding onto in its receiving temp file, so a retry can send only the remaining bytes
// instead of the whole file again.
@Serializable
data class MediaUploadStatus(
    val receivedBytes: Long
)