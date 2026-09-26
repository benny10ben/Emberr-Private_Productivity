package com.emberr.domain.selfhost.translation

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasStrokeEntity
import com.emberr.data.local.room.entity.DEFAULT_SPACE_ID
import com.emberr.data.local.room.entity.NoteKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val NOTE_PAYLOAD_SCHEMA_VERSION = 1

@Serializable
data class NotePayload(
    val schemaVersion: Int = NOTE_PAYLOAD_SCHEMA_VERSION,
    val noteId: String,
    val spaceId: String = DEFAULT_SPACE_ID,
    val title: String,
    val icon: String? = null,
    val folderId: String? = null,
    val isDaily: Boolean = false,
    val dateString: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val filePath: String,
    val snippet: String = "",
    val isFavorite: Boolean = false,
    val coverImagePath: String? = null,
    val trashedAt: Long? = null,
    val isSubNote: Boolean = false,
    val showWordCount: Boolean = false,
    val sortOrder: Int = 0,
    val isTemplate: Boolean = false,
    val blocks: List<NoteBlockPayload> = emptyList(),
    val tombstones: List<BlockTombstone> = emptyList(),
    val embeddedBlocks: List<EmbeddedBlockPayload> = emptyList(),
    val kind: NoteKind = NoteKind.NOTE,
    val canvasNodes: List<CanvasNodeEntity> = emptyList(),
    val canvasEdges: List<CanvasEdgeEntity> = emptyList(),
    val canvasStrokes: List<CanvasStrokeEntity> = emptyList()
)

@Serializable
data class NoteBlockPayload(
    val blockId: String,
    val displayOrder: Int,
    val updatedAt: Long,
    val content: JsonElement
)

@Serializable
data class BlockTombstone(
    val blockId: String,
    val deletedAt: Long
)

@Serializable
data class EmbeddedBlockPayload(
    val blockId: String,
    val chunkText: String,
    val embedding: String
)
