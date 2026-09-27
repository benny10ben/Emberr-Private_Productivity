package com.emberr.domain.selfhost.merge

import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.domain.database.DatabaseRowCleanup
import com.emberr.domain.database.mergeDatabaseBlocks
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.markDeleted
import com.emberr.domain.selfhost.sync.SelfHostSyncLog
import com.emberr.domain.selfhost.translation.BlockTombstone
import kotlinx.serialization.json.Json
import kotlin.collections.forEach

object NoteMergeHelper {

    private val blockJson = Json { ignoreUnknownKeys = true }
    private val databaseBlockJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun mergeBlocks(
        noteId: String,
        localBlocks: List<NoteBlockEntity>,
        remoteUpserts: List<NoteBlockEntity>,
        remoteDeletions: List<BlockTombstone>
    ): List<NoteBlockEntity> {
        val safeLocalBlocks = localBlocks.filterOwnedBy(noteId, "local")
        val safeRemoteUpserts = remoteUpserts.filterOwnedBy(noteId, "remote")

        val merged = LinkedHashMap<String, NoteBlockEntity>()
        safeLocalBlocks.forEach { merged[it.blockId] = it }

        safeRemoteUpserts.forEach { remoteBlock ->
            val localBlock = merged[remoteBlock.blockId]
            val mergedDatabaseBlock = localBlock?.let { mergedDatabaseBlockOrNull(it, remoteBlock) }
            // Strictly greater, not >= - on an exact-millisecond tie (two devices editing the same
            // block within the same debounce/merge window), the remote side otherwise always won
            // regardless of which edit actually landed second, silently discarding a local edit that
            // was just as valid.
            if (mergedDatabaseBlock != null) {
                merged[remoteBlock.blockId] = mergedDatabaseBlock
            } else if (localBlock == null || remoteBlock.updatedAt > localBlock.updatedAt) {
                merged[remoteBlock.blockId] = remoteBlock
            }
        }

        remoteDeletions.forEach { tombstone ->
            val localBlock = merged[tombstone.blockId]
            if (localBlock == null || tombstone.deletedAt >= localBlock.updatedAt) {
                merged[tombstone.blockId] = NoteBlockEntity(
                    blockId = tombstone.blockId,
                    noteId = noteId,
                    displayOrder = localBlock?.displayOrder ?: 0,
                    blockDataJson = tombstonedBlockJson(tombstone.blockId, localBlock),
                    updatedAt = tombstone.deletedAt,
                    isDeleted = true
                )
            }
        }

        return merged.values.toList()
    }

    private fun mergedDatabaseBlockOrNull(localBlock: NoteBlockEntity, remoteBlock: NoteBlockEntity): NoteBlockEntity? {
        val localDatabase = decodeDatabaseBlockOrNull(localBlock.blockDataJson) ?: return null
        val remoteDatabase = decodeDatabaseBlockOrNull(remoteBlock.blockDataJson) ?: return null
        val mergedDatabase = mergeDatabaseBlocks(localDatabase, remoteDatabase)
        val newerBlock = if (remoteBlock.updatedAt > localBlock.updatedAt) remoteBlock else localBlock
        return newerBlock.copy(
            blockDataJson = databaseBlockJson.encodeToString(NoteBlock.serializer(), mergedDatabase),
            updatedAt = mergedDatabase.updatedAt,
            isDeleted = mergedDatabase.isDeleted
        )
    }

    private fun decodeDatabaseBlockOrNull(blockDataJson: String): DatabaseBlock? {
        if (DatabaseRowCleanup.DATABASE_BLOCK_JSON_MARKER !in blockDataJson) return null
        return try {
            blockJson.decodeFromString(NoteBlock.serializer(), blockDataJson) as? DatabaseBlock
        } catch (cause: Exception) {
            SelfHostSyncLog.e("NoteMergeHelper: could not decode a database block, keeping the newer copy whole", cause)
            null
        }
    }

    // An empty string is never valid JSON - returning it as a "we don't have real content" placeholder
    // means every future decode of this row (refreshing the UI cache on every subsequent reconcile)
    // fails permanently, forever, for a tombstone that's otherwise perfectly legitimate. Falling back
    // to an empty, already-deleted TextBlock keeps it decodable; downstream code only cares that
    // isDeleted is true, never the tombstone's original content or type.
    private fun tombstonedBlockJson(blockId: String, localBlock: NoteBlockEntity?): String {
        val fallback = {
            blockJson.encodeToString(
                NoteBlock.serializer(),
                TextBlock(id = blockId, text = "", updatedAt = System.currentTimeMillis()).markDeleted()
            )
        }
        if (localBlock == null) return fallback()
        return try {
            val decoded = blockJson.decodeFromString(NoteBlock.serializer(), localBlock.blockDataJson)
            blockJson.encodeToString(NoteBlock.serializer(), decoded.markDeleted())
        } catch (cause: Exception) {
            SelfHostSyncLog.e(
                "NoteMergeHelper: could not decode local block ${localBlock.blockId} to apply tombstone, using an empty deleted placeholder instead",
                cause
            )
            fallback()
        }
    }

    private fun List<NoteBlockEntity>.filterOwnedBy(noteId: String, source: String): List<NoteBlockEntity> {
        return filter { block ->
            val ownedByNote = block.noteId == noteId
            if (!ownedByNote) {
                SelfHostSyncLog.e(
                    "NoteMergeHelper: dropped $source block ${block.blockId} belonging to note " +
                        "${block.noteId} while merging note $noteId"
                )
            }
            ownedByNote
        }
    }
}