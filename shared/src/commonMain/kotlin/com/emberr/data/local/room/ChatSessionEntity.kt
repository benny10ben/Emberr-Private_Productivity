package com.emberr.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "chat_sessions",
    indices = [
        Index("updatedAt"),
        Index(value = ["spaceId"])
    ]
)
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val messagesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val spaceId: String = DEFAULT_SPACE_ID
)
