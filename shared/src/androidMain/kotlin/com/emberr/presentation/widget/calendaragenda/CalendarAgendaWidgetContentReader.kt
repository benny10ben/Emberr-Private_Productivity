// Builds the compact month grid and the ordered list of that month's events.
package com.emberr.presentation.widget.calendaragenda

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
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number

private const val weeksShown = 6
private const val daysPerWeek = 7
private const val maximumEventsShown = 40
private const val maximumCharactersPerTitle = 80

class CalendarAgendaWidgetContentReader(
    private val calendarTaskDao: CalendarTaskDao,
    private val categoryDao: CategoryDao,
) {
    suspend fun readContentOnce(spaceId: String, shownMonth: String?): CalendarAgendaWidgetContent? =
        withContext(Dispatchers.IO) {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val firstOfMonth = parseFirstOfMonth(shownMonth) ?: firstOfMonthFor(today)
            val monthKey = monthKeyOf(firstOfMonth)

            val tasks = try {
                calendarTaskDao.getTasksInMonth(spaceId, monthKey)
            } catch (cause: Exception) {
                WidgetLog.e("Could not read events for $monthKey", cause)
                return@withContext null
            }

            val colorsByCategoryId = try {
                categoryDao.getAllCategoriesOnce(spaceId).associate { it.categoryId to it.colorHex }
            } catch (cause: Exception) {
                WidgetLog.e("Could not read the event categories", cause)
                emptyMap()
            }

            CalendarAgendaWidgetContent(
                shownMonth = monthKey,
                monthLabel = formatMonthLabel(firstOfMonth),
                weekdayLabels = weekdayLabels,
                weeks = buildWeeks(firstOfMonth, today, tasks),
                events = buildEvents(tasks, colorsByCategoryId)
            )
        }

    private fun buildWeeks(
        firstOfMonth: LocalDate,
        today: LocalDate,
        tasks: List<CalendarTaskEntity>
    ): List<List<AgendaDayCell>> {
        val datesWithEvents = tasks
            .filterNot { task -> task.isChecked }
            .mapNotNull { task -> task.targetDate }
            .toSet()

        val leadingBlanks = firstOfMonth.dayOfWeek.ordinal
        val gridStart = firstOfMonth.minus(DatePeriod(days = leadingBlanks))

        val allWeeks = (0 until weeksShown).map { weekIndex ->
            (0 until daysPerWeek).map { dayIndex ->
                val date = gridStart.plus(DatePeriod(days = weekIndex * daysPerWeek + dayIndex))

                if (date.month.number != firstOfMonth.month.number || date.year != firstOfMonth.year) {
                    AgendaDayCell(dayNumber = null, isToday = false, hasEvents = false)
                } else {
                    AgendaDayCell(
                        dayNumber = date.day,
                        isToday = date == today,
                        hasEvents = date.toString() in datesWithEvents
                    )
                }
            }
        }

        return allWeeks.dropLastWhile { week -> week.all { cell -> cell.dayNumber == null } }
    }

    private fun buildEvents(
        tasks: List<CalendarTaskEntity>,
        colorsByCategoryId: Map<String, String>
    ): List<AgendaEvent> =
        tasks
            .filter { task -> task.reminderTimestamp != null }
            .sortedBy { task -> task.reminderTimestamp }
            .take(maximumEventsShown)
            .map { task ->
                AgendaEvent(
                    blockId = task.blockId,
                    title = task.text.trim().take(maximumCharactersPerTitle).ifBlank { "Untitled event" },
                    whenLabel = formatWhenLabel(task.reminderTimestamp ?: 0L),
                    isDone = task.isChecked,
                    accentColorHex = task.categoryId?.let { colorsByCategoryId[it] }
                )
            }
}

private val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val shortMonthNames = monthNames.map { it.take(3) }

fun shiftAgendaMonth(shownMonth: String?, monthsToAdd: Int): String {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val firstOfMonth = parseFirstOfMonth(shownMonth) ?: firstOfMonthFor(today)
    return monthKeyOf(firstOfMonth.plus(DatePeriod(months = monthsToAdd)))
}

private fun parseFirstOfMonth(shownMonth: String?): LocalDate? {
    if (shownMonth.isNullOrBlank()) return null
    return try {
        LocalDate.parse("$shownMonth-01")
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun firstOfMonthFor(date: LocalDate): LocalDate =
    LocalDate(date.year, date.month.number, 1)

private fun monthKeyOf(date: LocalDate): String =
    "${date.year}-${date.month.number.toString().padStart(2, '0')}"

private fun formatMonthLabel(firstOfMonth: LocalDate): String =
    monthNames.getOrNull(firstOfMonth.month.number - 1) ?: firstOfMonth.month.name

private fun formatWhenLabel(timestamp: Long): String {
    val moment = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val month = shortMonthNames.getOrNull(moment.month.number - 1) ?: moment.month.name.take(3)
    return "$month ${moment.day}, ${formatClockTime(moment)}"
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
