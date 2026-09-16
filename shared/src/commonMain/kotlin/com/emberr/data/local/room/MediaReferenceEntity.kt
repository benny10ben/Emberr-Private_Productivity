package com.emberr.data.local.room

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "media_references",
    primaryKeys = ["noteId", "fileName"],
    indices = [Index("fileName")]
)
data class MediaReferenceEntity(
    val noteId: String,
    val fileName: String
)
