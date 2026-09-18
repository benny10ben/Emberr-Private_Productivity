package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
