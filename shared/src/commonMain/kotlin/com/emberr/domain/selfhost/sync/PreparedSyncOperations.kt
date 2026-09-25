package com.emberr.domain.selfhost.sync

import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.selfhost.translation.BlockTombstone
import com.emberr.domain.selfhost.translation.EmbeddedBlockPayload

data class PreparedSyncOperations(
    val metadataUpsert: NoteMetadataEntity,
    val blockUpserts: List<NoteBlockEntity>,
    val blockDeletions: List<BlockTombstone>,
    val embeddedBlocks: List<EmbeddedBlockPayload> = emptyList(),
    val canvas: CanvasContent = CanvasContent()
)
