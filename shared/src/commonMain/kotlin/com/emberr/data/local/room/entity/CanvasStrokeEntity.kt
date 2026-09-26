package com.emberr.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable

@Serializable
enum class CanvasStrokeTool {
    PEN,
    HIGHLIGHTER,
    LINE
}

@Serializable
@Entity(
    tableName = "canvas_strokes",
    primaryKeys = ["noteId", "strokeId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteMetadataEntity::class,
            parentColumns = ["noteId"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CanvasStrokeEntity(
    val strokeId: String,
    val noteId: String,
    val tool: CanvasStrokeTool = CanvasStrokeTool.PEN,
    val x: Float,
    val y: Float,
    val points: String,
    val width: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val color: String? = null,
    @ColumnInfo(defaultValue = "1") val opacity: Float = 1f,
    @ColumnInfo(defaultValue = "1") val usesPressure: Boolean = true,
    val linePattern: String? = null,
    @ColumnInfo(defaultValue = "0") val hasArrowHead: Boolean = false
)
