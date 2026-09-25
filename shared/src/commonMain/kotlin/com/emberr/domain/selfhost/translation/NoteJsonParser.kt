package com.emberr.domain.selfhost.translation

import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.selfhost.sync.PreparedSyncOperations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object NoteJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parseJsonToDatabaseOperations(jsonString: String): PreparedSyncOperations {
        val payload = try {
            json.decodeFromString(NotePayload.serializer(), jsonString)
        } catch (cause: Exception) {
            throw NotePayloadSyncException("Failed to decode note payload JSON", cause)
        }

        val dedupedTombstones = payload.tombstones
            .groupBy { it.blockId }
            .map { (_, tombstones) -> tombstones.maxBy { it.deletedAt } }
        val tombstoneIds = dedupedTombstones.map { it.blockId }.toSet()
        val liveBlockIds = payload.blocks.map { it.blockId }.toSet()
        val conflicting = tombstoneIds.intersect(liveBlockIds)
        require(conflicting.isEmpty()) {
            "Note ${payload.noteId} has blocks marked both live and deleted: $conflicting"
        }

        val metadataUpsert = NoteMetadataEntity(
            noteId = payload.noteId,
            title = payload.title,
            icon = payload.icon,
            folderId = payload.folderId,
            isDaily = payload.isDaily,
            dateString = payload.dateString,
            createdAt = payload.createdAt,
            updatedAt = payload.updatedAt,
            filePath = "",
            snippet = payload.snippet,
            isFavorite = payload.isFavorite,
            coverImagePath = payload.coverImagePath,
            trashedAt = payload.trashedAt,
            isSubNote = payload.isSubNote,
            showWordCount = payload.showWordCount,
            sortOrder = payload.sortOrder,
            isTemplate = payload.isTemplate,
            spaceId = payload.spaceId,
            kind = payload.kind
        )

        val blockUpserts = payload.blocks.map { block ->
            NoteBlockEntity(
                blockId = block.blockId,
                noteId = payload.noteId,
                displayOrder = block.displayOrder,
                blockDataJson = block.content.toDataJson(),
                updatedAt = block.updatedAt,
                isDeleted = false
            )
        }

        return PreparedSyncOperations(
            metadataUpsert = metadataUpsert,
            blockUpserts = blockUpserts,
            blockDeletions = dedupedTombstones,
            embeddedBlocks = payload.embeddedBlocks,
            canvas = CanvasContent(nodes = payload.canvasNodes, edges = payload.canvasEdges).withNoteId(payload.noteId)
        )
    }

    private fun JsonElement.toDataJson(): String =
        json.encodeToString(JsonElement.serializer(), this)
}