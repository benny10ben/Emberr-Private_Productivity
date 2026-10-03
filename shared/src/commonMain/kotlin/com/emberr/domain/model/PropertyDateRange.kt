package com.emberr.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

private const val DATE_RANGE_ARROW = "→"
val DEFAULT_PROPERTY_TIME = LocalTime(9, 0)

@Immutable
data class PropertyDateRange(
    val start: LocalDate? = null,
    val end: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null
) {
    val hasEnd: Boolean get() = start != null && end != null
    val includesTime: Boolean get() = start != null && startTime != null

    fun withEndDateShown(isShown: Boolean): PropertyDateRange =
        if (isShown) copy(end = end ?: start, endTime = endTime ?: startTime) else copy(end = null, endTime = null)

    fun withTimeIncluded(isIncluded: Boolean): PropertyDateRange = if (isIncluded) {
        val newStartTime = startTime ?: DEFAULT_PROPERTY_TIME
        copy(startTime = newStartTime, endTime = if (end == null) null else endTime ?: newStartTime)
    } else {
        copy(startTime = null, endTime = null)
    }

    fun inOrder(): PropertyDateRange {
        val startDate = start ?: return PropertyDateRange()
        val endDate = end ?: return PropertyDateRange(start = startDate, startTime = startTime)
        val cleanEndTime = if (startTime == null) null else endTime ?: startTime
        val endsBeforeStart = endDate < startDate ||
            (endDate == startDate && startTime != null && cleanEndTime != null && cleanEndTime < startTime)
        return if (endsBeforeStart) {
            PropertyDateRange(start = endDate, end = startDate, startTime = cleanEndTime, endTime = startTime)
        } else {
            PropertyDateRange(start = startDate, end = endDate, startTime = startTime, endTime = cleanEndTime)
        }
    }

    fun asText(): String {
        val startDate = start ?: return ""
        val startText = dateAndTimeText(startDate, startTime)
        val endDate = end ?: return startText
        return "$startText $DATE_RANGE_ARROW ${dateAndTimeText(endDate, endTime)}"
    }
}

fun shortDateText(date: LocalDate): String {
    val monthName = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$monthName ${date.day}, ${date.year}"
}

fun parsePropertyDateRange(text: String): PropertyDateRange? {
    val parts = text.split(DATE_RANGE_ARROW).map { it.trim() }
    if (parts.size > 2) return null
    val (startDate, startTime) = parseDateAndTime(parts[0]) ?: return null
    if (parts.size == 1) return PropertyDateRange(start = startDate, startTime = startTime)
    val (endDate, endTime) = parseDateAndTime(parts[1]) ?: return null
    return PropertyDateRange(start = startDate, end = endDate, startTime = startTime, endTime = endTime).inOrder()
}

private fun dateAndTimeText(date: LocalDate, time: LocalTime?): String =
    if (time == null) date.toString() else "$date ${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

private fun parseDateAndTime(text: String): Pair<LocalDate, LocalTime?>? {
    val pieces = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (pieces.isEmpty() || pieces.size > 2) return null
    val date = runCatching { LocalDate.parse(pieces[0]) }.getOrNull() ?: return null
    val time = pieces.getOrNull(1)?.let { runCatching { LocalTime.parse(it) }.getOrNull() ?: return null }
    return date to time
}
