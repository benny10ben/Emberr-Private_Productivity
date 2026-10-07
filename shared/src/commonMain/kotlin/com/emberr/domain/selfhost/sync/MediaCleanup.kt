package com.emberr.domain.selfhost.sync

import com.emberr.domain.selfhost.webdav.WebDavResourceInfo

internal const val MEDIA_UNCLAIMED_WAIT_MS = 24L * 60 * 60 * 1000
internal const val MEDIA_TRASH_KEEP_MS = 30L * 24 * 60 * 60 * 1000
internal const val ABANDONED_UPLOAD_WAIT_MS = 7L * 24 * 60 * 60 * 1000

internal data class MediaCleanupDecisions(
    val filesToMoveToTrash: Set<String>,
    val filesToRestoreFromTrash: Set<String>,
    val filesToEmptyFromTrash: Set<String>
)

internal data class MediaChangesThisRun(
    val movedToTrash: Set<String> = emptySet(),
    val restoredFromTrash: Set<String> = emptySet(),
    val goneFromServer: Set<String> = emptySet(),
    val newlyUploaded: Map<String, Long> = emptyMap(),
    val missingOnServer: Set<String> = emptySet()
)

internal fun mediaClaimedByLiveNotes(entries: List<SelfHostManifestEntry>): Set<String> =
    entries
        .filter { it.entryType == SelfHostEntryType.NOTE || it.entryType == SelfHostEntryType.DAILY }
        .filter { !it.isDeleted }
        .flatMapTo(mutableSetOf()) { it.mediaFileNames }

internal fun decideMediaCleanup(
    mediaEntries: List<SelfHostManifestEntry>,
    claimedFileNames: Set<String>,
    nowMs: Long
): MediaCleanupDecisions {
    val unclaimedEntries = mediaEntries.filter { it.entryId !in claimedFileNames }
    return MediaCleanupDecisions(
        filesToMoveToTrash = unclaimedEntries
            .filter { it.trashedAt == null }
            .filter { entry -> entry.orphanedAt?.let { nowMs - it > MEDIA_UNCLAIMED_WAIT_MS } == true }
            .mapTo(mutableSetOf()) { it.entryId },
        filesToRestoreFromTrash = mediaEntries
            .filter { it.trashedAt != null && it.entryId in claimedFileNames }
            .mapTo(mutableSetOf()) { it.entryId },
        filesToEmptyFromTrash = unclaimedEntries
            .filter { entry -> entry.trashedAt?.let { nowMs - it > MEDIA_TRASH_KEEP_MS } == true }
            .mapTo(mutableSetOf()) { it.entryId }
    )
}

internal fun updatedMediaEntries(
    mediaEntries: List<SelfHostManifestEntry>,
    claimedFileNames: Set<String>,
    changes: MediaChangesThisRun,
    nowMs: Long
): List<SelfHostManifestEntry> {
    val existingFileNames = mediaEntries.mapTo(mutableSetOf()) { it.entryId }
    val newEntries = (changes.newlyUploaded.keys - existingFileNames).map { fileName ->
        SelfHostManifestEntry(entryId = fileName, entryType = SelfHostEntryType.MEDIA, updatedAt = nowMs)
    }
    return (mediaEntries + newEntries)
        .filter { it.entryId !in changes.goneFromServer }
        .filterNot { it.entryId in changes.missingOnServer && it.trashedAt == null }
        .map { entry ->
            val fileName = entry.entryId
            entry.copy(
                orphanedAt = if (fileName in claimedFileNames) null else entry.orphanedAt ?: nowMs,
                trashedAt = when (fileName) {
                    in changes.newlyUploaded, in changes.restoredFromTrash -> null
                    in changes.movedToTrash -> nowMs
                    else -> entry.trashedAt
                },
                mediaSizeBytes = changes.newlyUploaded[fileName] ?: entry.mediaSizeBytes
            )
        }
}

internal fun lastUploadProgressAt(folderContents: List<WebDavResourceInfo>): Long? =
    folderContents.mapNotNull { it.lastModifiedMs }.maxOrNull()

internal fun isAbandonedUpload(lastProgressAtMs: Long?, nowMs: Long): Boolean =
    lastProgressAtMs != null && nowMs - lastProgressAtMs > ABANDONED_UPLOAD_WAIT_MS
