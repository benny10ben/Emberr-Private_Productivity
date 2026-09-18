package com.emberr.data.local.room.entity

import com.emberr.domain.model.RecurrenceFrequency
import com.emberr.domain.model.RecurrenceRule
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarTaskRecurrenceMappingTest {

    private fun task(
        recurrenceFrequency: RecurrenceFrequency? = null,
        recurrenceInterval: Int = 1,
        recurrenceDaysOfWeek: String? = null,
        recurrenceUntil: String? = null
    ) = CalendarTaskEntity(
        blockId = "block-1",
        noteId = "note-1",
        text = "Call the plumber",
        isChecked = false,
        targetDate = "2026-01-05",
        reminderTimestamp = 1_700_000_000_000L,
        sourceType = TaskSource.NOTE,
        recurrenceFrequency = recurrenceFrequency,
        recurrenceInterval = recurrenceInterval,
        recurrenceDaysOfWeek = recurrenceDaysOfWeek,
        recurrenceUntil = recurrenceUntil
    )

    @Test
    fun aTaskWithNoFrequencyDoesNotRepeat() {
        assertNull(task().toRecurrenceRule())
    }

    @Test
    fun aTaskWithNoFrequencyDoesNotRepeatEvenIfOtherRecurrenceColumnsAreFilledIn() {
        val leftoverColumns = task(
            recurrenceFrequency = null,
            recurrenceInterval = 3,
            recurrenceDaysOfWeek = "1,3",
            recurrenceUntil = "2026-12-31"
        )

        assertNull(leftoverColumns.toRecurrenceRule())
    }

    @Test
    fun aStoredFrequencyBecomesARepeatingRule() {
        val rule = assertNotNull(task(recurrenceFrequency = RecurrenceFrequency.DAILY).toRecurrenceRule())

        assertEquals(RecurrenceFrequency.DAILY, rule.frequency)
        assertEquals(1, rule.interval)
        assertTrue(rule.daysOfWeek.isEmpty())
        assertNull(rule.untilDateString)
    }

    @Test
    fun theStoredIntervalAndEndDateAreCarriedOver() {
        val rule = assertNotNull(
            task(
                recurrenceFrequency = RecurrenceFrequency.MONTHLY,
                recurrenceInterval = 3,
                recurrenceUntil = "2026-12-31"
            ).toRecurrenceRule()
        )

        assertEquals(3, rule.interval)
        assertEquals("2026-12-31", rule.untilDateString)
    }

    @Test
    fun storedWeekdayNumbersBecomeDaysOfTheWeek() {
        val rule = assertNotNull(
            task(
                recurrenceFrequency = RecurrenceFrequency.WEEKLY,
                recurrenceDaysOfWeek = "1,3,7"
            ).toRecurrenceRule()
        )

        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            rule.daysOfWeek
        )
    }

    @Test
    fun weekdayNumbersSurviveSurroundingSpaces() {
        val rule = assertNotNull(
            task(
                recurrenceFrequency = RecurrenceFrequency.WEEKLY,
                recurrenceDaysOfWeek = " 1 , 3 "
            ).toRecurrenceRule()
        )

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), rule.daysOfWeek)
    }

    @Test
    fun unreadableWeekdayNumbersAreSkippedRatherThanBreakingTheWholeRule() {
        val rule = assertNotNull(
            task(
                recurrenceFrequency = RecurrenceFrequency.WEEKLY,
                recurrenceDaysOfWeek = "1,not-a-number,9,,3"
            ).toRecurrenceRule()
        )

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), rule.daysOfWeek)
    }

    @Test
    fun aRuleWithChosenWeekdaysIsStoredAsACommaSeparatedList() {
        val rule = RecurrenceRule(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 2,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        )

        val (frequency, interval, daysOfWeek) = rule.toEntityColumns()

        assertEquals(RecurrenceFrequency.WEEKLY, frequency)
        assertEquals(2, interval)
        assertEquals("1,3", daysOfWeek)
    }

    @Test
    fun aRuleWithNoChosenWeekdaysStoresNothingRatherThanAnEmptyString() {
        val rule = RecurrenceRule(frequency = RecurrenceFrequency.DAILY, interval = 1)

        assertNull(rule.toEntityColumns().third)
    }

    @Test
    fun aRepeatingRuleSurvivesARoundTripThroughTheDatabaseColumns() {
        val originals = listOf(
            RecurrenceRule(RecurrenceFrequency.DAILY, interval = 1),
            RecurrenceRule(RecurrenceFrequency.DAILY, interval = 5, untilDateString = "2026-06-30"),
            RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 2,
                daysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY),
                untilDateString = "2027-01-01"
            ),
            RecurrenceRule(RecurrenceFrequency.MONTHLY, interval = 3),
            RecurrenceRule(RecurrenceFrequency.YEARLY, interval = 1, untilDateString = "2030-12-31")
        )

        originals.forEach { original ->
            val (frequency, interval, daysOfWeek) = original.toEntityColumns()

            val restored = task(
                recurrenceFrequency = frequency,
                recurrenceInterval = interval,
                recurrenceDaysOfWeek = daysOfWeek,
                recurrenceUntil = original.untilDateString
            ).toRecurrenceRule()

            assertEquals(original, restored, "round trip changed $original")
        }
    }
}
