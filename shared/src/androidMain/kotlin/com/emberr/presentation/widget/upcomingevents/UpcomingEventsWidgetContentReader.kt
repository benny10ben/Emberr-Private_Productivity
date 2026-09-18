// Builds the ordered, date-labelled list of every upcoming, unchecked calendar event.
package com.emberr.presentation.widget.upcomingevents

import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.data.local.room.entity.CalendarTaskEntity
import com.emberr.presentation.widget.WidgetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number

private const val maximumEventsShown = 60
private const val maximumCharactersPerTitle = 100

class UpcomingEventsWidgetContentReader(
    private val calendarTaskDao: CalendarTaskDao,
    private val categoryDao: CategoryDao,
) {
    suspend fun readContentOnce(spaceId: String): UpcomingEventsWidgetContent? = withContext(Dispatchers.IO) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val tasks = try {
            calendarTaskDao.getUpcomingTasks(spaceId, today.toString())
        } catch (cause: Exception) {
            WidgetLog.e("Could not read upcoming events", cause)
            return@withContext null
        }

        val colorsByCategoryId = try {
            categoryDao.getAllCategoriesOnce(spaceId).associate { category -> category.categoryId to category.colorHex }
        } catch (cause: Exception) {
            WidgetLog.e("Could not read the event categories", cause)
            emptyMap()
        }

        UpcomingEventsWidgetContent(events = buildEvents(tasks, today, colorsByCategoryId))
    }

    private fun buildEvents(
        tasks: List<CalendarTaskEntity>,
        today: LocalDate,
        colorsByCategoryId: Map<String, String>
    ): List<UpcomingEvent> =
        tasks
            .sortedWith(
                compareBy(
                    { task -> task.targetDate },
                    { task -> task.reminderTimestamp ?: Long.MAX_VALUE }
                )
            )
            .take(maximumEventsShown)
            .mapNotNull { task ->
                val targetDate = task.targetDate?.let(LocalDate::parse) ?: return@mapNotNull null

                UpcomingEvent(
                    blockId = task.blockId,
                    title = task.text.trim().take(maximumCharactersPerTitle).ifBlank { "Untitled event" },
                    dateGroupKey = task.targetDate,
                    dateLabel = formatDateLabel(targetDate, today),
                    timeLabel = task.reminderTimestamp?.let { timestamp ->
                        formatClockTime(Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()))
                    },
                    isOverdue = targetDate == today &&
                        task.reminderTimestamp != null &&
                        task.reminderTimestamp < System.currentTimeMillis(),
                    accentColorHex = task.categoryId?.let { categoryId -> colorsByCategoryId[categoryId] }
                )
            }
}

private val shortMonthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun formatDateLabel(targetDate: LocalDate, today: LocalDate): String =
    when (targetDate) {
        today -> "Today"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        else -> {
            val month = shortMonthNames.getOrNull(targetDate.month.number - 1) ?: targetDate.month.name.take(3)
            "$month ${targetDate.day}"
        }
    }

private fun formatClockTime(moment: LocalDateTime): String {
    val hourOfDay = when {
        moment.hour == 0 -> 12
        moment.hour > 12 -> moment.hour - 12
        else -> moment.hour
    }
    val suffix = if (moment.hour < 12) "am" else "pm"
    return "$hourOfDay:${moment.minute.toString().padStart(2, '0')} $suffix"
}
