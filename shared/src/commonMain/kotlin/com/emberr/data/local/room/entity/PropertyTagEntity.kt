package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "property_tags",
    indices = [Index(value = ["spaceId"])]
)
data class PropertyTagEntity(
    @PrimaryKey val tagId: String,
    val propertyKey: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val spaceId: String = DEFAULT_SPACE_ID
)
