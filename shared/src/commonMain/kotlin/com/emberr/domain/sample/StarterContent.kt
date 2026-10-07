package com.emberr.domain.sample

import com.emberr.data.local.room.entity.NoteMetadataEntity

const val STARTER_CONTENT_UPDATED_AT = 0L

fun NoteMetadataEntity.isUntouchedStarterContent(): Boolean = updatedAt == STARTER_CONTENT_UPDATED_AT

fun NoteMetadataEntity.lastEditedAtForDisplay(): Long = if (isUntouchedStarterContent()) createdAt else updatedAt
