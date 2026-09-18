package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "note_blocks",
    primaryKeys = ["noteId", "blockId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteMetadataEntity::class,
            parentColumns = ["noteId"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class NoteBlockEntity(
    val blockId: String,
    val noteId: String,
    val displayOrder: Int,
    val blockDataJson: String,
    val updatedAt: Long,
    val isDeleted: Boolean
)