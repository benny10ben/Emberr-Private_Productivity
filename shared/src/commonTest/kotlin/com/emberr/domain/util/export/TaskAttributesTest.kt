package com.emberr.domain.util.export

import com.emberr.domain.model.RecurrenceFrequency
import com.emberr.domain.model.RecurrenceRule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskAttributesTest {

    private val utc = TimeZone.UTC

    @Test
    fun anEmptyGroupIsNotWrittenAtAll() {
        assertNull(TaskAttributesFormat.render(TaskAttributes()))
    }

    @Test
    fun everyFieldIsWrittenInAFixedOrder() {
        val rendered = TaskAttributesFormat.render(
            TaskAttributes(
                dueText = "2026-09-12 14:00",
                durationMinutes = 45,
                categoryName = "Work",
                repeatText = "weekly on mon"
            )
        )

        assertEquals(
            "{due: 2026-09-12 14:00; for: 45m; category: Work; repeat: weekly on mon}",
            rendered
        )
    }

    @Test
    fun aLinkAndDescriptionAreWrittenLast() {
        val rendered = TaskAttributesFormat.render(
            TaskAttributes(
                dueText = "2026-09-12 14:00",
                link = "https://example.com/q3",
                details = "Bring the Q3 numbers"
            )
        )

        assertEquals(
            "{due: 2026-09-12 14:00; link: https://example.com/q3; details: Bring the Q3 numbers}",
            rendered
        )
    }

    @Test
    fun specialCharactersInAValueAreEscaped() {
        val rendered = TaskAttributesFormat.render(
            TaskAttributes(details = "see {this}; and C:\\notes")
        )

        assertEquals("{details: see \\{this\\}\\; and C:\\\\notes}", rendered)
    }

    @Test
    fun aDescriptionHoldingALineBreakStaysOnOneLine() {
        val rendered = TaskAttributesFormat.render(
            TaskAttributes(details = "first line\nsecond line")
        )!!

        assertTrue(!rendered.contains('\n'), "the group has to stay on one line: $rendered")
        assertEquals("{details: first line\\nsecond line}", rendered)
    }

    @Test
    fun secondsAreOnlyWrittenWhenTheyMatter() {
        val onTheMinute = LocalDateTime(2026, 9, 12, 14, 0).toInstant(utc).toEpochMilliseconds()

        assertEquals("2026-09-12 14:00", TaskDueTimeFormat.render(onTheMinute, utc))
        assertEquals("2026-09-12 14:00:30", TaskDueTimeFormat.render(onTheMinute + 30_000L, utc))
    }

    @Test
    fun repeatTextReadsTheWayAPersonWouldWriteIt() {
        assertEquals("daily", TaskRepeatFormat.render(RecurrenceRule(RecurrenceFrequency.DAILY)))
        assertEquals(
            "every 3 days",
            TaskRepeatFormat.render(RecurrenceRule(RecurrenceFrequency.DAILY, interval = 3))
        )
        assertEquals(
            "weekly on mon,fri",
            TaskRepeatFormat.render(
                RecurrenceRule(
                    RecurrenceFrequency.WEEKLY,
                    daysOfWeek = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)
                )
            )
        )
        assertEquals(
            "every 2 weeks on fri until 2027-06-30",
            TaskRepeatFormat.render(
                RecurrenceRule(
                    RecurrenceFrequency.WEEKLY,
                    interval = 2,
                    daysOfWeek = setOf(DayOfWeek.FRIDAY),
                    untilDateString = "2027-06-30"
                )
            )
        )
    }
}
