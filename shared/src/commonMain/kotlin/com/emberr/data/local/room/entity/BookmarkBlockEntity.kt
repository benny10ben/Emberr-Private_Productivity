package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "bookmark_blocks",
    indices = [
        Index("noteId"),
        Index("noteUpdatedAt")
    ]
)
data class BookmarkBlockEntity(
    @PrimaryKey val blockId: String,
    val noteId: String,
    val url: String,
    val title: String?,
    val description: String?,
    val previewImageUrl: String?,
    val noteUpdatedAt: Long,
    val sourceType: TaskSource
)