package com.emberr.presentation.calendar

import com.emberr.data.local.room.entity.CalendarTaskEntity
import com.emberr.data.local.room.entity.TaskSource
import com.emberr.data.local.room.entity.toRecurrenceRule
import com.emberr.domain.model.RecurrenceRule

data class CalendarEvent(
    val blockId: String,
    val noteId: String,
    val text: String,
    val isChecked: Boolean,
    val dateString: String,
    val reminderTimestamp: Long,
    val categoryId: String?,
    val durationMinutes: Int,
    val sourceType: TaskSource,
    val url: String?,
    val description: String?,
    val recurrenceRule: RecurrenceRule?
)

fun CalendarTaskEntity.toCalendarEvent(): CalendarEvent? {
    val timestamp = reminderTimestamp ?: return null
    val date = targetDate ?: return null
    return CalendarEvent(
        blockId = blockId,
        noteId = noteId,
        text = text,
        isChecked = isChecked,
        dateString = date,
        reminderTimestamp = timestamp,
        categoryId = categoryId,
        durationMinutes = durationMinutes,
        sourceType = sourceType,
        url = url,
        description = description,
        recurrenceRule = toRecurrenceRule()
    )
}
