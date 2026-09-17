package com.emberr.data.local.room

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "calendar_event_exceptions", primaryKeys = ["blockId", "occurrenceDate"])
data class CalendarEventExceptionEntity(
    val blockId: String,
    val occurrenceDate: String,
    val isCancelled: Boolean = false,
    val isChecked: Boolean = false,
    val completedAt: Long? = null,
    val overrideTimestamp: Long? = null,
    val overrideDurationMinutes: Int? = null,
    val overrideText: String? = null,
    val overrideCategoryId: String? = null,
    val overrideUrl: String? = null,
    val overrideDescription: String? = null,
    val updatedAt: Long
)
