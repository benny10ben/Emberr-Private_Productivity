package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
