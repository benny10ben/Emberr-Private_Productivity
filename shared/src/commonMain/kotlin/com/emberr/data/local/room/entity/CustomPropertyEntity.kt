package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emberr.domain.model.PropertyValueType
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "custom_properties",
    indices = [Index(value = ["spaceId"])]
)
data class CustomPropertyEntity(
    @PrimaryKey val propertyId: String,
    val name: String,
    val valueType: PropertyValueType,
    val createdAt: Long,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val spaceId: String = DEFAULT_SPACE_ID
)
