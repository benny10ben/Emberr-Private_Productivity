package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
