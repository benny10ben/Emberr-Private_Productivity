package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

const val DEFAULT_SPACE_ID = "00000000-0000-0000-0000-000000000001"
const val DEFAULT_SPACE_NAME = "Default"

const val PLACEHOLDER_SPACE_UPDATED_AT = 0L

@Serializable
@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey val spaceId: String,
    val displayName: String,
    val createdAt: Long,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
)
