package com.emberr.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable

@Serializable
enum class CanvasNodeType {
    TEXT,
    GROUP
}

@Serializable
@Entity(
    tableName = "canvas_nodes",
    primaryKeys = ["noteId", "nodeId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteMetadataEntity::class,
            parentColumns = ["noteId"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CanvasNodeEntity(
    val nodeId: String,
    val noteId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val color: String? = null,
    @ColumnInfo(defaultValue = "TEXT") val type: CanvasNodeType = CanvasNodeType.TEXT
)
