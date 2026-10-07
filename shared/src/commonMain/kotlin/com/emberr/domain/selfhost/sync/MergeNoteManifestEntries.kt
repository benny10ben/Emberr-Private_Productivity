package com.emberr.domain.selfhost.sync

internal fun mergeNoteManifestEntries(
    serverEntries: List<SelfHostManifestEntry>,
    localEntries: List<SelfHostManifestEntry>,
    entryIdsStillWaitingToUpload: Set<String>,
    tombstoneIds: Set<String>
): List<SelfHostManifestEntry> {
    val serverEntriesById = serverEntries
        .filter { it.entryType == SelfHostEntryType.NOTE || it.entryType == SelfHostEntryType.DAILY }
        .filter { !it.isDeleted && it.entryId !in tombstoneIds }
        .associateBy { it.entryId }
    val localEntriesById = localEntries
        .filter { it.entryId !in tombstoneIds }
        .groupBy { it.entryId }
        .mapValues { (_, entriesWithSameId) -> entriesWithSameId.maxBy { it.updatedAt } }

    val entriesForLocalNotes = localEntriesById.values.mapNotNull { localEntry ->
        val serverEntry = serverEntriesById[localEntry.entryId]
        val isStillWaitingToUpload = localEntry.entryId in entryIdsStillWaitingToUpload
        when {
            serverEntry == null -> localEntry.takeUnless { isStillWaitingToUpload }
            isStillWaitingToUpload -> serverEntry
            serverEntry.updatedAt > localEntry.updatedAt -> serverEntry
            else -> localEntry
        }
    }
    val entriesOnlyOnServer = serverEntriesById.values.filter { it.entryId !in localEntriesById }

    return entriesForLocalNotes + entriesOnlyOnServer
}
