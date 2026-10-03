package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseRepeatFrequency
import com.emberr.domain.model.DatabaseTemplateRepeat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseRepeatingTemplatesTest {

    private val saturday = LocalDate(2026, 10, 3)
    private val sunday = LocalDate(2026, 10, 4)
    private val monday = LocalDate(2026, 10, 5)
    private val wednesday = LocalDate(2026, 10, 7)
    private val sundayMorning = LocalDateTime(sunday, LocalTime(7, 0))
    private val eightAm = LocalTime(8, 0)

    private fun repeat(frequency: DatabaseRepeatFrequency, isoDays: List<Int> = emptyList()) =
        DatabaseTemplateRepeat(frequency = frequency, startsOn = saturday, isoDaysOfWeek = isoDays)

    private val journal = DatabaseBlock(id = "block", databaseId = "journal", updatedAt = 100L)

    @Test
    fun eachFrequencyRepeatsOnItsOwnDays() {
        assertEquals(listOf(true, true, true, true), listOf(saturday, sunday, monday, wednesday).map { repeat(DatabaseRepeatFrequency.DAILY).occursOn(it) })
        assertEquals(listOf(false, false, true, true), listOf(saturday, sunday, monday, wednesday).map { repeat(DatabaseRepeatFrequency.WEEKDAYS).occursOn(it) })
        assertEquals(
            listOf(false, false, true, true),
            listOf(saturday, sunday, monday, wednesday).map { repeat(DatabaseRepeatFrequency.WEEKLY, isoDays = listOf(1, 3)).occursOn(it) }
        )
        assertEquals(
            listOf(true, false, true),
            listOf(saturday, LocalDate(2026, 10, 4), LocalDate(2026, 11, 3)).map { repeat(DatabaseRepeatFrequency.MONTHLY).occursOn(it) }
        )
    }

    @Test
    fun nothingRepeatsBeforeTheStartDay() {
        assertEquals(false, repeat(DatabaseRepeatFrequency.DAILY).occursOn(LocalDate(2026, 10, 2)))
    }

    @Test
    fun theRowForADayHasTheSameIdOnEveryDevice() {
        val withRepeat = journal.withTemplateRepeat("daily-log", repeat(DatabaseRepeatFrequency.DAILY))

        assertEquals(
            listOf(RepeatedRowToCreate(rowNoteId = "repeat-journal-daily-log-2026-10-04", templateNoteId = "daily-log", date = sunday)),
            withRepeat.repeatedRowsDueAt(sundayMorning)
        )
        assertEquals(withRepeat.repeatedRowsDueAt(sundayMorning), withRepeat.repeatedRowsDueAt(sundayMorning))
    }

    @Test
    fun aDeletedDatabaseOrAnEndedRepeatMakesNoRows() {
        val withRepeat = journal.withTemplateRepeat("daily-log", repeat(DatabaseRepeatFrequency.DAILY))

        assertEquals(emptyList(), withRepeat.copy(isDeleted = true).repeatedRowsDueAt(sundayMorning))
        assertEquals(emptyList(), withRepeat.withTemplateRepeat("daily-log", null).repeatedRowsDueAt(sundayMorning))
    }

    @Test
    fun aRepeatedRowIsNamedAfterItsTemplateAndDay() {
        assertEquals("Daily Log Oct 4, 2026", repeatedRowTitle("Daily Log ", sunday))
        assertEquals("Oct 4, 2026", repeatedRowTitle("", sunday))
    }

    @Test
    fun repeatsChosenOnTwoDevicesForDifferentTemplatesAreBothKept() {
        val phone = journal.withTemplateRepeat("daily-log", repeat(DatabaseRepeatFrequency.DAILY))
            .copy(updatedAt = 200L).withSettingTimesStamped(before = journal, now = 200L)
        val laptop = journal.withTemplateRepeat("weekly-review", repeat(DatabaseRepeatFrequency.WEEKLY, isoDays = listOf(7)))
            .copy(updatedAt = 300L).withSettingTimesStamped(before = journal, now = 300L)

        assertEquals(setOf("daily-log", "weekly-review"), mergeDatabaseBlocks(phone, laptop).repeatingTemplates.keys)
    }

    @Test
    fun unlockingOnOneDeviceWinsOverTheOlderLockedCopy() {
        val locked = journal.copy(isLocked = true)
        val phone = locked.copy(isLocked = false, updatedAt = 300L).withSettingTimesStamped(before = locked, now = 300L)
        val laptop = locked.copy(title = "Journal", updatedAt = 200L).withSettingTimesStamped(before = locked, now = 200L)

        val merged = mergeDatabaseBlocks(laptop, phone)

        assertEquals(false, merged.isLocked)
        assertEquals("Journal", merged.title)
    }

    @Test
    fun aRowWithATimeIsOnlyDueOnceThatTimeHasCome() {
        val withRepeat = journal.withTemplateRepeat("daily-log", repeat(DatabaseRepeatFrequency.DAILY).copy(time = eightAm))

        assertEquals(emptyList(), withRepeat.repeatedRowsDueAt(LocalDateTime(sunday, LocalTime(7, 59))))
        assertEquals(1, withRepeat.repeatedRowsDueAt(LocalDateTime(sunday, eightAm)).size)
        assertEquals(1, withRepeat.repeatedRowsDueAt(LocalDateTime(sunday, LocalTime(22, 0))).size)
    }

    @Test
    fun theNextCheckIsTheNextRepeatTimeTodayOrElseMidnight() {
        val withRepeats = journal
            .withTemplateRepeat("daily-log", repeat(DatabaseRepeatFrequency.DAILY).copy(time = eightAm))
            .withTemplateRepeat("evening-review", repeat(DatabaseRepeatFrequency.DAILY).copy(time = LocalTime(20, 30)))

        val laterTimesInTheMorning = withRepeats.laterRepeatTimesToday(sundayMorning)
        val laterTimesAtNight = withRepeats.laterRepeatTimesToday(LocalDateTime(sunday, LocalTime(21, 0)))

        assertEquals(LocalDateTime(sunday, eightAm), nextRepeatCheckAfter(sundayMorning, laterTimesInTheMorning))
        assertEquals(LocalDateTime(monday, LocalTime(0, 0)), nextRepeatCheckAfter(sundayMorning, laterTimesAtNight))
    }

    @Test
    fun aRepeatSetUpAfterTodaysTimeStartsTomorrow() {
        val setUpInTheAfternoon = repeatStartingNow(DatabaseRepeatFrequency.DAILY, emptyList(), eightAm, LocalDateTime(saturday, LocalTime(15, 0)))
        val setUpEarly = repeatStartingNow(DatabaseRepeatFrequency.DAILY, emptyList(), eightAm, LocalDateTime(saturday, LocalTime(6, 0)))
        val setUpWithoutATime = repeatStartingNow(DatabaseRepeatFrequency.DAILY, emptyList(), null, LocalDateTime(saturday, LocalTime(15, 0)))

        assertEquals(listOf(false, true), listOf(saturday, sunday).map { setUpInTheAfternoon.occursOn(it) })
        assertEquals(true, setUpEarly.occursOn(saturday))
        assertEquals(true, setUpWithoutATime.occursOn(saturday))
    }

    @Test
    fun changingOnlyTheTimeKeepsTheDayAMonthlyRepeatFallsOn() {
        val monthly = repeat(DatabaseRepeatFrequency.MONTHLY)

        val changed = monthly.withTime(eightAm, LocalDateTime(LocalDate(2026, 10, 20), LocalTime(12, 0)))

        assertEquals(saturday, changed.startsOn)
        assertEquals(eightAm, changed.time)
        assertEquals(true, changed.occursOn(LocalDate(2026, 11, 3)))
    }
}
