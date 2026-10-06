package com.emberr.domain.sync

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteContent

data class MergedIncomingNote(
    val metadata: NoteMetadataEntity,
    val content: NoteContent,
    val hasChanges: Boolean
)

object IncomingNoteMerge {

    fun merge(
        localMeta: NoteMetadataEntity,
        localContent: NoteContent?,
        remoteMeta: NoteMetadataEntity,
        remoteContent: NoteContent,
        remoteUpdatedAt: Long
    ): MergedIncomingNote {
        val mergedContent = NoteMergeHelper.mergeNoteContent(
            localContent = localContent,
            localUpdatedAt = localMeta.updatedAt,
            remoteContent = remoteContent,
            remoteUpdatedAt = remoteUpdatedAt
        )
        val contentChanged = mergedContent != localContent
        val metadataChanged = localMeta.copy(
            updatedAt = remoteMeta.updatedAt,
            filePath = remoteMeta.filePath,
            selfHostSyncedAt = remoteMeta.selfHostSyncedAt,
            kind = remoteMeta.kind
        ) != remoteMeta

        val resolvedUpdatedAt = maxOf(localMeta.updatedAt, remoteUpdatedAt)
        val winningMeta = if (remoteUpdatedAt > localMeta.updatedAt) {
            remoteMeta.copy(
                updatedAt = resolvedUpdatedAt,
                selfHostSyncedAt = localMeta.selfHostSyncedAt,
                kind = localMeta.kind
            )
        } else {
            localMeta.copy(updatedAt = resolvedUpdatedAt)
        }

        return MergedIncomingNote(
            metadata = winningMeta,
            content = mergedContent,
            hasChanges = contentChanged || metadataChanged
        )
    }
}
