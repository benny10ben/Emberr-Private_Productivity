package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable

@Serializable
enum class CanvasSide {
    TOP,
    RIGHT,
    BOTTOM,
    LEFT
}

@Serializable
@Entity(
    tableName = "canvas_edges",
    primaryKeys = ["noteId", "edgeId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteMetadataEntity::class,
            parentColumns = ["noteId"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CanvasEdgeEntity(
    val edgeId: String,
    val noteId: String,
    val fromNodeId: String,
    val fromSide: CanvasSide,
    val toNodeId: String,
    val toSide: CanvasSide,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
