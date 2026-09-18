package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
