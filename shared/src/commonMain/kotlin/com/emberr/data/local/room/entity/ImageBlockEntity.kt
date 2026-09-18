package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "image_blocks",
    indices = [
        Index("noteId"),
        Index("noteCreatedAt")
    ]
)
data class ImageBlockEntity(
    @PrimaryKey val blockId: String,
    val noteId: String,
    val localFilePath: String,
    val noteCreatedAt: Long,
    val sourceType: TaskSource
)