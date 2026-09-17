package com.emberr.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emberr.domain.model.RecurrenceFrequency
import com.emberr.domain.model.RecurrenceRule
import com.emberr.domain.model.isoDayNumberToDayOfWeek
import com.emberr.domain.model.toIsoDayNumberCsv
import kotlinx.serialization.Serializable

enum class TaskSource {
    DAILY,
    NOTE
}

@Serializable
@Entity(
    tableName = "calendar_tasks",
    indices = [
        Index(value = ["spaceId", "targetDate"]),
        Index("noteId")
    ]
)
data class CalendarTaskEntity(
    @PrimaryKey val blockId: String,
    val noteId: String,
    val text: String,
    val isChecked: Boolean,
    val targetDate: String?,
    val reminderTimestamp: Long?,
    val sourceType: TaskSource,
    val categoryId: String? = null,
    @ColumnInfo(defaultValue = "30") val durationMinutes: Int = 30,
    val url: String? = null,
    val description: String? = null,
    val recurrenceFrequency: RecurrenceFrequency? = null,
    @ColumnInfo(defaultValue = "1") val recurrenceInterval: Int = 1,
    val recurrenceDaysOfWeek: String? = null,
    val recurrenceUntil: String? = null,
    val spaceId: String = DEFAULT_SPACE_ID
)

fun CalendarTaskEntity.toRecurrenceRule(): RecurrenceRule? {
    val frequency = recurrenceFrequency ?: return null
    return RecurrenceRule(
        frequency = frequency,
        interval = recurrenceInterval,
        daysOfWeek = recurrenceDaysOfWeek
            ?.split(",")
            ?.mapNotNull { isoDayNumberToDayOfWeek(it.trim().toIntOrNull()) }
            ?.toSet()
            ?: emptySet(),
        untilDateString = recurrenceUntil
    )
}

fun RecurrenceRule.toEntityColumns(): Triple<RecurrenceFrequency, Int, String?> =
    Triple(frequency, interval, daysOfWeek.takeIf { it.isNotEmpty() }?.toIsoDayNumberCsv())