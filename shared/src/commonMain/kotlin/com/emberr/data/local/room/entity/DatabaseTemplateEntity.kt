package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
