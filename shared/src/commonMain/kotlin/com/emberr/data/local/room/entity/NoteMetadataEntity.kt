package com.emberr.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents the metadata for a note.
 * Crucially, this does NOT hold the actual note content (blocks, text, images).
 * Content lives in the note_blocks table, one row per block. The filePath column is unused.
 * This entity just keeps track of titles, dates, and UI state so the app can quickly load lists and search.
 */
@Serializable
@Entity(
    tableName = "notes_metadata",
    indices = [
        Index(value = ["spaceId", "isDaily", "dateString"]),
        Index(value = ["spaceId", "updatedAt"])
    ]
)
data class NoteMetadataEntity(
    @PrimaryKey val noteId: String,
    val title: String,
    val icon: String? = null,
    val folderId: String?,
    val isDaily: Boolean,
    val dateString: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val filePath: String,
    val snippet: String = "",
    val isFavorite: Boolean = false,
    val coverImagePath: String? = null,
    val trashedAt: Long? = null,
    val isSubNote: Boolean = false,
    val showWordCount: Boolean = false,
    val sortOrder: Int = 0,
    val isTemplate: Boolean = false,
    val selfHostSyncedAt: Long = 0L,
    val spaceId: String = DEFAULT_SPACE_ID,
    @ColumnInfo(defaultValue = "NOTE") val kind: NoteKind = NoteKind.NOTE
)
