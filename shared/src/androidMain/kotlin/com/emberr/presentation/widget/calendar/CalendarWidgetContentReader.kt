// Builds the month grid, marking today and adding a coloured dot for each task on a day.
package com.emberr.presentation.widget.calendar

import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.presentation.widget.WidgetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.number

private const val maximumDotsPerDay = 4
private const val weeksShown = 6
private const val daysPerWeek = 7
private const val neutralDotColorHex = "#848484"

class CalendarWidgetContentReader(
    private val calendarTaskDao: CalendarTaskDao,
    private val categoryDao: CategoryDao,
) {
    suspend fun readContentOnce(spaceId: String, shownMonth: String?): CalendarWidgetContent? =
        withContext(Dispatchers.IO) {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val firstOfMonth = parseFirstOfMonth(shownMonth) ?: firstOfMonthFor(today)
            val monthKey = monthKeyOf(firstOfMonth)

            val tasks = try {
                calendarTaskDao.getTasksInMonth(spaceId, monthKey)
            } catch (cause: Exception) {
                WidgetLog.e("Could not read tasks for $monthKey", cause)
                return@withContext null
            }

            val colorsByCategoryId = try {
                categoryDao.getAllCategoriesOnce(spaceId).associate { it.categoryId to it.colorHex }
            } catch (cause: Exception) {
                WidgetLog.e("Could not read the task categories", cause)
                emptyMap()
            }

            val dotsByDate = tasks
                .filterNot { task -> task.isChecked }
                .groupBy { task -> task.targetDate }
                .mapNotNull { (dateString, tasksOnDate) ->
                    if (dateString == null) null else dateString to tasksOnDate
                        .take(maximumDotsPerDay)
                        .map { task ->
                            task.categoryId?.let { colorsByCategoryId[it] } ?: neutralDotColorHex
                        }
                }
                .toMap()

            CalendarWidgetContent(
                shownMonth = monthKey,
                monthLabel = formatMonthLabel(firstOfMonth),
                weekdayLabels = weekdayLabels,
                weeks = buildWeeks(firstOfMonth, today, dotsByDate)
            )
        }

    private fun buildWeeks(
        firstOfMonth: LocalDate,
        today: LocalDate,
        dotsByDate: Map<String, List<String>>
    ): List<List<CalendarDayCell>> {
        val leadingBlanks = firstOfMonth.dayOfWeek.ordinal
        val gridStart = firstOfMonth.minus(DatePeriod(days = leadingBlanks))

        val allWeeks = (0 until weeksShown).map { weekIndex ->
            (0 until daysPerWeek).map { dayIndex ->
                val date = gridStart.plus(DatePeriod(days = weekIndex * daysPerWeek + dayIndex))

                if (date.month.number != firstOfMonth.month.number || date.year != firstOfMonth.year) {
                    CalendarDayCell(
                        dateString = null,
                        dayNumber = null,
                        isToday = false,
                        dotColorHexes = emptyList()
                    )
                } else {
                    val dateString = date.toString()
                    CalendarDayCell(
                        dateString = dateString,
                        dayNumber = date.day,
                        isToday = date == today,
                        dotColorHexes = dotsByDate[dateString].orEmpty()
                    )
                }
            }
        }

        return allWeeks.dropLastWhile { week -> week.all { cell -> cell.dayNumber == null } }
    }
}

private val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

fun shiftMonth(shownMonth: String?, monthsToAdd: Int): String {
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

private fun formatMonthLabel(firstOfMonth: LocalDate): String {
    val month = monthNames.getOrNull(firstOfMonth.month.number - 1) ?: firstOfMonth.month.name
    return "$month ${firstOfMonth.year}"
}
