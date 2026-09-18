// Loads today's tasks and turns them into the date heading and rows the widget draws.
package com.emberr.presentation.widget.todaytasks

import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.entity.CalendarTaskEntity
import com.emberr.presentation.widget.WidgetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number

private const val maximumTasksShown = 12
private const val maximumCharactersPerTitle = 120

class TodayTasksWidgetContentReader(
    private val calendarTaskDao: CalendarTaskDao
) {

    suspend fun readContentOnce(spaceId: String): TodayTasksWidgetContent? = withContext(Dispatchers.IO) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val tasks = try {
            calendarTaskDao.getTasksOnDate(spaceId, today.toString())
        } catch (cause: Exception) {
            WidgetLog.e("Could not read today's tasks", cause)
            return@withContext null
        }

        TodayTasksWidgetContent(
            dateLabel = formatDateHeading(today),
            rows = buildRows(tasks, System.currentTimeMillis())
        )
    }

    private fun buildRows(tasks: List<CalendarTaskEntity>, now: Long): List<TodayTaskRow> =
        tasks
            .sortedWith(
                compareBy(
                    { task -> if (task.isChecked) 1 else 0 },
                    { task -> task.reminderTimestamp ?: Long.MAX_VALUE },
                    { task -> task.text.lowercase() }
                )
            )
            .take(maximumTasksShown)
            .map { task ->
                val scheduledAt = task.reminderTimestamp
                val isOverdue = !task.isChecked && scheduledAt != null && scheduledAt < now

                TodayTaskRow(
                    blockId = task.blockId,
                    title = task.text.trim().take(maximumCharactersPerTitle).ifBlank { "Untitled task" },
                    isChecked = task.isChecked,
                    scheduleLabel = when {
                        scheduledAt == null -> null
                        isOverdue -> formatOverdueLabel(now - scheduledAt)
                        else -> formatClockTime(toLocalTime(scheduledAt))
                    },
                    isOverdue = isOverdue
                )
            }
}

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val dayNames = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
)

private fun formatDateHeading(date: LocalDate): String {
    val month = monthNames.getOrNull(date.month.number - 1) ?: date.month.name
    val day = dayNames.getOrNull(date.dayOfWeek.ordinal) ?: date.dayOfWeek.name
    return "$month ${date.day}, $day"
}

private fun toLocalTime(timestamp: Long): LocalDateTime =
    Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())

private fun formatClockTime(moment: LocalDateTime): String {
    val hourOfDay = when {
        moment.hour == 0 -> 12
        moment.hour > 12 -> moment.hour - 12
        else -> moment.hour
    }
    val suffix = if (moment.hour < 12) "am" else "pm"
    return "$hourOfDay:${moment.minute.toString().padStart(2, '0')} $suffix"
}

private fun formatOverdueLabel(elapsedMillis: Long): String {
    val elapsedMinutes = (elapsedMillis / 60_000L).coerceAtLeast(1L)

    if (elapsedMinutes < 60) return "Due $elapsedMinutes min ago"

    val hours = elapsedMinutes / 60
    val hourLabel = if (hours == 1L) "1 hour" else "$hours hours"
    return "Due $hourLabel ago"
}
