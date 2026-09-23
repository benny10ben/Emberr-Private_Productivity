package com.emberr.presentation.mobile.voice

import com.emberr.presentation.calendar.formatTimeOfDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val DEFAULT_REMINDER_TIME = LocalTime(9, 0)

internal fun voiceTaskDayLabel(reminder: Long?, today: LocalDate, timeZone: TimeZone): String {
    val date = reminder?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone).date } ?: today
    return when (date) {
        today -> "Today"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        else -> {
            val shortDay = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val shortMonth = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$shortDay, $shortMonth ${date.day}"
        }
    }
}

internal fun voiceTaskTimeLabel(reminder: Long?, timeZone: TimeZone): String {
    if (reminder == null) return "Add time"
    val dateTime = Instant.fromEpochMilliseconds(reminder).toLocalDateTime(timeZone)
    return formatTimeOfDay(dateTime.hour, dateTime.minute)
}

internal fun reminderMovedToDate(currentReminder: Long?, pickedDate: Long, timeZone: TimeZone): Long {
    val date = Instant.fromEpochMilliseconds(pickedDate).toLocalDateTime(timeZone).date
    val time = currentReminder?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone).time }
        ?: DEFAULT_REMINDER_TIME
    return LocalDateTime(date, time).toInstant(timeZone).toEpochMilliseconds()
}

internal fun reminderMovedToTime(
    currentReminder: Long?,
    hour: Int,
    minute: Int,
    today: LocalDate,
    timeZone: TimeZone
): Long {
    val date = currentReminder?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(timeZone).date } ?: today
    return LocalDateTime(date, LocalTime(hour, minute)).toInstant(timeZone).toEpochMilliseconds()
}
