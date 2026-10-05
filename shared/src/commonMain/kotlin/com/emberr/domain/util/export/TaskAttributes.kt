package com.emberr.domain.util.export

import com.emberr.domain.model.RecurrenceFrequency
import com.emberr.domain.model.RecurrenceRule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

const val DEFAULT_TASK_DURATION_MINUTES = 30

private const val DUE_KEY = "due"
private const val DURATION_KEY = "for"
private const val CATEGORY_KEY = "category"
private const val REPEAT_KEY = "repeat"
private const val LINK_KEY = "link"
private const val DETAILS_KEY = "details"

data class TaskAttributes(
    val dueText: String? = null,
    val durationMinutes: Int? = null,
    val categoryName: String? = null,
    val repeatText: String? = null,
    val link: String? = null,
    val details: String? = null
) {
    val isEmpty: Boolean
        get() = dueText == null && durationMinutes == null && categoryName == null &&
            repeatText == null && link == null && details == null
}

object TaskAttributesFormat {

    fun render(attributes: TaskAttributes): String? {
        if (attributes.isEmpty) return null

        val fields = mutableListOf<String>()
        attributes.dueText?.let { fields.add("$DUE_KEY: ${escapeValue(it)}") }
        attributes.durationMinutes?.let { fields.add("$DURATION_KEY: ${it}m") }
        attributes.categoryName?.let { fields.add("$CATEGORY_KEY: ${escapeValue(it)}") }
        attributes.repeatText?.let { fields.add("$REPEAT_KEY: ${escapeValue(it)}") }
        attributes.link?.let { fields.add("$LINK_KEY: ${escapeValue(it)}") }
        attributes.details?.let { fields.add("$DETAILS_KEY: ${escapeValue(it)}") }

        return "{" + fields.joinToString("; ") + "}"
    }

    private fun escapeValue(value: String): String = buildString(value.length) {
        for (character in value) {
            when {
                character == '\n' -> append("\\n")
                character == '\r' -> Unit
                character == ';' || character == '{' || character == '}' || character == '\\' -> {
                    append('\\')
                    append(character)
                }
                else -> append(character)
            }
        }
    }
}

object TaskDueTimeFormat {

    fun render(epochMilliseconds: Long, timeZone: TimeZone): String {
        val local = Instant.fromEpochMilliseconds(epochMilliseconds).toLocalDateTime(timeZone)
        val date = "${pad(local.year, 4)}-${pad(local.month.number, 2)}-${pad(local.day, 2)}"
        val time = "${pad(local.hour, 2)}:${pad(local.minute, 2)}"

        return if (local.second == 0) "$date $time" else "$date $time:${pad(local.second, 2)}"
    }

    private fun pad(value: Int, width: Int): String = value.toString().padStart(width, '0')
}

object TaskRepeatFormat {

    fun render(rule: RecurrenceRule): String {
        val head = if (rule.interval <= 1) {
            when (rule.frequency) {
                RecurrenceFrequency.DAILY -> "daily"
                RecurrenceFrequency.WEEKLY -> "weekly"
                RecurrenceFrequency.MONTHLY -> "monthly"
                RecurrenceFrequency.YEARLY -> "yearly"
            }
        } else {
            val unit = when (rule.frequency) {
                RecurrenceFrequency.DAILY -> "days"
                RecurrenceFrequency.WEEKLY -> "weeks"
                RecurrenceFrequency.MONTHLY -> "months"
                RecurrenceFrequency.YEARLY -> "years"
            }
            "every ${rule.interval} $unit"
        }

        val days = if (rule.daysOfWeek.isEmpty()) {
            ""
        } else {
            " on " + rule.daysOfWeek.sortedBy { it.isoDayNumber }.joinToString(",") { shortDayName(it) }
        }
        val until = rule.untilDateString?.let { " until $it" }.orEmpty()

        return head + days + until
    }

    private fun shortDayName(day: DayOfWeek): String = day.name.take(3).lowercase()
}
