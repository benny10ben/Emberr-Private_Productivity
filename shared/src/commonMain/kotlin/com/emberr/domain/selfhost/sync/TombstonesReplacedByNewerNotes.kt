package com.emberr.domain.selfhost.sync

internal fun tombstoneIdsReplacedByNewerNotes(
    tombstones: List<SelfHostManifestEntry>,
    serverEntries: List<SelfHostManifestEntry>,
    localEntries: List<SelfHostManifestEntry>,
    entryIdsStillWaitingToUpload: Set<String>
): Set<String> {
    val liveServerEntries = serverEntries.filter { !it.isDeleted }
    val uploadedLocalEntries = localEntries.filter { it.entryId !in entryIdsStillWaitingToUpload }
    val newestLiveUpdatedAtById = (liveServerEntries + uploadedLocalEntries)
        .filter { it.entryType == SelfHostEntryType.NOTE || it.entryType == SelfHostEntryType.DAILY }
        .groupBy { it.entryId }
        .mapValues { (_, entriesWithSameId) -> entriesWithSameId.maxOf { it.updatedAt } }

    return tombstones
        .filter { tombstone ->
            val newestLiveUpdatedAt = newestLiveUpdatedAtById[tombstone.entryId] ?: return@filter false
            newestLiveUpdatedAt > tombstone.updatedAt
        }
        .mapTo(HashSet()) { it.entryId }
}
