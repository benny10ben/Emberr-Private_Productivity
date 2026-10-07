package com.emberr.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unused_media_files")
data class UnusedMediaFileEntity(
    @PrimaryKey val fileName: String,
    val unusedSince: Long
)
