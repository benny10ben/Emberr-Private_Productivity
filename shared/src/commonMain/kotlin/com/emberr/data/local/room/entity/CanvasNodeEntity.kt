package com.emberr.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable

@Serializable
enum class CanvasNodeType {
    TEXT,
    GROUP,
    FREE_TEXT,
    IMAGE
}

@Serializable
enum class CanvasNodeShape {
    RECTANGLE,
    SQUARE,
    CIRCLE,
    OVAL,
    TRIANGLE,
    DIAMOND,
    PENTAGON,
    HEXAGON,
    PARALLELOGRAM,
    TRAPEZOID,
    PILL,
    DATABASE,
    DOUBLE_RECTANGLE,
    DOUBLE_SQUARE,
    DOUBLE_CIRCLE,
    DOUBLE_TRIANGLE
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
    @ColumnInfo(defaultValue = "TEXT") val type: CanvasNodeType = CanvasNodeType.TEXT,
    @ColumnInfo(defaultValue = "RECTANGLE") val shape: CanvasNodeShape = CanvasNodeShape.RECTANGLE,
    val fontFamily: String? = null,
    val fontWeight: Int? = null,
    val textColor: String? = null,
    val fontSize: Float? = null,
    val textAlign: String? = null,
    val imagePath: String? = null
)
