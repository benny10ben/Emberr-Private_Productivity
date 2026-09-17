package com.emberr.data.local.room

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
    val spaceId: String = DEFAULT_SPACE_ID
)

/**
 * Basic structure for folders to organize notes.
 * `updatedAt` exists purely for self-host sync last-write-wins merging, same reasoning as
 * [CategoryEntity]. `isDeleted` already existed here as a soft-delete tombstone.
 */
@Serializable
@Entity(
    tableName = "folders",
    indices = [Index(value = ["spaceId"])]
)
data class FolderEntity(
    @PrimaryKey val folderId: String,
    val name: String,
    val parentFolderId: String?,
    val createdAt: Long,
    val isDeleted: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val spaceId: String = DEFAULT_SPACE_ID
)

data class FolderNoteCount(
    val folderId: String,
    val noteCount: Int
)

/**
 * Central registry for tags used across all databases.
 * `updatedAt`/`isDeleted` exist purely for self-host sync, same reasoning as [CategoryEntity].
 */
@Serializable
@Entity(
    tableName = "global_tags",
    indices = [Index(value = ["spaceId"])]
)
data class TagEntity(
    @PrimaryKey val tagId: String,
    val name: String,
    val colorHex: String,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val spaceId: String = DEFAULT_SPACE_ID
)

/**
 * A user-defined calendar category (e.g. "Personal", "Work") used to color-code events.
 * `updatedAt`/`isDeleted` exist purely for sync (see SyncRepositoryImpl) - `updatedAt` drives the
 * "modified since last sync" query and last-write-wins merge, `isDeleted` is a soft-delete
 * tombstone so a deletion on one device actually propagates to others instead of just vanishing
 * locally with nothing left to sync.
 */
@Serializable
@Entity(
    tableName = "calendar_categories",
    indices = [Index(value = ["spaceId"])]
)
data class CategoryEntity(
    @PrimaryKey val categoryId: String,
    val name: String,
    val colorHex: String,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val spaceId: String = DEFAULT_SPACE_ID
)

/**
 * A saved DatabaseBlock schema (columns + views only, never rows) that can be reused when
 * creating a new database. Columns/views are stored as JSON so the schema shape can evolve
 * without a Room migration for every DatabaseColumn/DatabaseView field addition.
 */
@Serializable
@Entity(tableName = "database_templates")
data class DatabaseTemplateEntity(
    @PrimaryKey val templateId: String,
    val name: String,
    val serializedColumns: String,
    val serializedViews: String
)

/**
 * Recorded the moment a note/daily is hard-deleted (permanent Trash delete, or folder deletion) -
 * the Room row and block files are already gone by then, so without this there is nothing left
 * locally to tell another device "this one was deleted" versus "this one never existed here."
 * Self-host sync carries this forward as an `isDeleted` manifest entry indefinitely, exactly like
 * [CategoryEntity]'s tombstone, so a device that hasn't purged its own copy yet is told to.
 */
@Serializable
@Entity(
    tableName = "self_host_deleted_notes",
    indices = [Index(value = ["spaceId"])]
)
data class SelfHostDeletedNoteEntity(
    @PrimaryKey val noteId: String,
    val isDaily: Boolean,
    val dateString: String?,
    val deletedAt: Long,
    val remoteFileDeleted: Boolean = false,
    val spaceId: String = DEFAULT_SPACE_ID
)