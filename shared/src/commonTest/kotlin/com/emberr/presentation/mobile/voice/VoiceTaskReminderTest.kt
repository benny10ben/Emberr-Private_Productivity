package com.emberr.presentation.mobile.voice

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceTaskReminderTest {

    private val today = LocalDate(2026, 9, 23)

    private fun timestampAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.UTC).toEpochMilliseconds()

    @Test
    fun aTaskWithoutAReminderIsForTodayWithNoTime() {
        assertEquals("Today", voiceTaskDayLabel(null, today, TimeZone.UTC))
        assertEquals("Add time", voiceTaskTimeLabel(null, TimeZone.UTC))
    }

    @Test
    fun aReminderLaterTodayShowsTodayAndTheTime() {
        val reminder = timestampAt(2026, 9, 23, 18, 30)

        assertEquals("Today", voiceTaskDayLabel(reminder, today, TimeZone.UTC))
        assertEquals("6:30 PM", voiceTaskTimeLabel(reminder, TimeZone.UTC))
    }

    @Test
    fun aReminderForTomorrowShowsTomorrow() {
        assertEquals("Tomorrow", voiceTaskDayLabel(timestampAt(2026, 9, 24, 9, 0), today, TimeZone.UTC))
    }

    @Test
    fun aReminderFurtherAwayShowsTheWeekdayAndDate() {
        val reminder = timestampAt(2026, 9, 25, 14, 5)

        assertEquals("Fri, Sep 25", voiceTaskDayLabel(reminder, today, TimeZone.UTC))
        assertEquals("2:05 PM", voiceTaskTimeLabel(reminder, TimeZone.UTC))
    }

    @Test
    fun pickingADateKeepsTheTimeTheTaskAlreadyHad() {
        val reminder = timestampAt(2026, 9, 24, 17, 45)
        val pickedDate = timestampAt(2026, 10, 2, 0, 0)

        assertEquals(timestampAt(2026, 10, 2, 17, 45), reminderMovedToDate(reminder, pickedDate, TimeZone.UTC))
    }

    @Test
    fun pickingADateForATaskWithNoTimeUsesNineInTheMorning() {
        val pickedDate = timestampAt(2026, 10, 2, 0, 0)

        assertEquals(timestampAt(2026, 10, 2, 9, 0), reminderMovedToDate(null, pickedDate, TimeZone.UTC))
    }

    @Test
    fun pickingATimeKeepsTheDateTheTaskAlreadyHad() {
        val reminder = timestampAt(2026, 9, 25, 9, 0)

        assertEquals(
            timestampAt(2026, 9, 25, 20, 15),
            reminderMovedToTime(reminder, 20, 15, today, TimeZone.UTC)
        )
    }

    @Test
    fun pickingATimeForATaskWithNoDateUsesToday() {
        assertEquals(
            timestampAt(2026, 9, 23, 20, 15),
            reminderMovedToTime(null, 20, 15, today, TimeZone.UTC)
        )
    }
}
