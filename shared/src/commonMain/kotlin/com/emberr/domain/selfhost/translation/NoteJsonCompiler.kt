package com.emberr.domain.selfhost.translation

import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.selfhost.sync.SelfHostSyncLog
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object NoteJsonCompiler {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun compileNoteToJson(
        metadata: NoteMetadataEntity,
        blocks: List<NoteBlockEntity>,
        embeddedBlocks: List<EmbeddedBlockPayload> = emptyList(),
        canvas: CanvasContent = CanvasContent()
    ): String {
        val foreignBlocks = blocks.filter { it.noteId != metadata.noteId }
        if (foreignBlocks.isNotEmpty()) {
            SelfHostSyncLog.e(
                "NoteJsonCompiler: dropping ${foreignBlocks.size} block(s) that do not belong to note " +
                    "${metadata.noteId}: ${foreignBlocks.map { "${it.blockId} (note ${it.noteId})" }}"
            )
        }
        val ownBlocks = blocks.filter { it.noteId == metadata.noteId }

        val payload = NotePayload(
            noteId = metadata.noteId,
            spaceId = metadata.spaceId,
            title = metadata.title,
            icon = metadata.icon,
            folderId = metadata.folderId,
            isDaily = metadata.isDaily,
            dateString = metadata.dateString,
            createdAt = metadata.createdAt,
            updatedAt = metadata.updatedAt,
            filePath = metadata.filePath,
            snippet = metadata.snippet,
            isFavorite = metadata.isFavorite,
            coverImagePath = metadata.coverImagePath,
            trashedAt = metadata.trashedAt,
            isSubNote = metadata.isSubNote,
            showWordCount = metadata.showWordCount,
            sortOrder = metadata.sortOrder,
            isTemplate = metadata.isTemplate,
            blocks = ownBlocks.filter { !it.isDeleted }.map { it.toPayload() },
            tombstones = ownBlocks.filter { it.isDeleted }
                .map { BlockTombstone(it.blockId, it.updatedAt) },
            embeddedBlocks = embeddedBlocks,
            kind = metadata.kind,
            canvasNodes = canvas.nodes.filter { it.noteId == metadata.noteId },
            canvasEdges = canvas.edges.filter { it.noteId == metadata.noteId }
        )

        return try {
            json.encodeToString(NotePayload.serializer(), payload)
        } catch (cause: SerializationException) {
            throw NotePayloadSyncException(
                "Failed to encode note ${metadata.noteId} to JSON",
                cause
            )
        }
    }

    private fun NoteBlockEntity.toPayload(): NoteBlockPayload {
        val content = try {
            json.parseToJsonElement(blockDataJson)
        } catch (cause: SerializationException) {
            throw NotePayloadSyncException("Block $blockId has malformed blockDataJson", cause)
        }
        return NoteBlockPayload(
            blockId = blockId,
            displayOrder = displayOrder,
            updatedAt = updatedAt,
            content = content
        )
    }
}