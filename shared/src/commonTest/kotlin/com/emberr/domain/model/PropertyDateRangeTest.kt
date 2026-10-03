package com.emberr.domain.model

import com.emberr.domain.database.DatabaseBoardGroupValue
import com.emberr.domain.database.movedBetweenGroups
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropertyDateRangeTest {

    private val october2 = LocalDate(2026, 10, 2)
    private val october5 = LocalDate(2026, 10, 5)
    private val morning = LocalTime(9, 30)
    private val evening = LocalTime(17, 0)

    @Test
    fun anEndBeforeTheStartIsSwapped() {
        assertEquals(
            PropertyDateRange(start = october2, end = october5),
            PropertyDateRange(start = october5, end = october2).inOrder()
        )
        assertEquals(
            PropertyDateRange(start = october2, end = october2, startTime = morning, endTime = evening),
            PropertyDateRange(start = october2, end = october2, startTime = evening, endTime = morning).inOrder()
        )
    }

    @Test
    fun timesOnlyStayWhenTheyMakeSense() {
        assertEquals(PropertyDateRange(), PropertyDateRange(end = october5, startTime = morning).inOrder())
        assertEquals(PropertyDateRange(start = october2, startTime = morning), PropertyDateRange(start = october2, startTime = morning, endTime = evening).inOrder())
        assertEquals(PropertyDateRange(start = october2, end = october5), PropertyDateRange(start = october2, end = october5, endTime = evening).inOrder())
        assertEquals(
            PropertyDateRange(start = october2, end = october5, startTime = morning, endTime = morning),
            PropertyDateRange(start = october2, end = october5, startTime = morning).inOrder()
        )
    }

    @Test
    fun turningOnTheEndDateStartsItOnTheStartDate() {
        val withTime = PropertyDateRange(start = october2, startTime = morning)

        assertEquals(PropertyDateRange(start = october2, end = october2, startTime = morning, endTime = morning), withTime.withEndDateShown(true))
        assertEquals(withTime, withTime.withEndDateShown(true).withEndDateShown(false))
    }

    @Test
    fun turningOnTimeUsesNineInTheMorning() {
        val range = PropertyDateRange(start = october2, end = october5)

        assertEquals(range.copy(startTime = DEFAULT_PROPERTY_TIME, endTime = DEFAULT_PROPERTY_TIME), range.withTimeIncluded(true))
        assertEquals(range, range.withTimeIncluded(true).withTimeIncluded(false))
    }

    @Test
    fun aRangeIsWrittenAsTextAndReadBack() {
        val ranges = listOf(
            PropertyDateRange(start = october2),
            PropertyDateRange(start = october2, startTime = morning),
            PropertyDateRange(start = october2, end = october5),
            PropertyDateRange(start = october2, end = october5, startTime = morning, endTime = evening)
        )

        assertEquals(
            listOf("2026-10-02", "2026-10-02 09:30", "2026-10-02 → 2026-10-05", "2026-10-02 09:30 → 2026-10-05 17:00"),
            ranges.map { it.asText() }
        )
        ranges.forEach { range -> assertEquals(range, parsePropertyDateRange(range.asText())) }
    }

    @Test
    fun textThatIsNotADateIsNotRead() {
        assertNull(parsePropertyDateRange("next tuesday"))
        assertNull(parsePropertyDateRange("2026-10-02 9am"))
        assertNull(parsePropertyDateRange("2026-10-02 → 2026-10-03 → 2026-10-04"))
    }

    @Test
    fun aPropertyBlockSavesTheRangeInOrder() {
        val block = PropertyBlock(id = "trip", propertyType = PropertyType.DATE)
            .withDateRange(PropertyDateRange(start = october5, end = october2, startTime = evening, endTime = morning))

        assertEquals(PropertyDateRange(start = october2, end = october5, startTime = morning, endTime = evening), block.dateRange)
        assertEquals("2026-10-02 09:30 → 2026-10-05 17:00", block.valueAsText())
    }

    @Test
    fun movingTheStartDateKeepsTheLengthOfTheRange() {
        val trip = PropertyBlock(id = "trip", propertyType = PropertyType.DATE, date = october2, endDate = october5, time = morning)

        val moved = trip.withStartDateMovedTo(LocalDate(2026, 11, 1))

        assertEquals(PropertyDateRange(start = LocalDate(2026, 11, 1), end = LocalDate(2026, 11, 4), startTime = morning), moved.dateRange)
        assertEquals(PropertyDateRange(), trip.withStartDateMovedTo(null).dateRange)
    }

    @Test
    fun movingABoardCardToAnotherDateGroupMovesTheWholeRange() {
        val trip = PropertyBlock(id = "trip", propertyType = PropertyType.DATE, date = october2, endDate = october5)

        val moved = trip.movedBetweenGroups(
            from = DatabaseBoardGroupValue.DatePeriod(LocalDate(2026, 10, 1), DatabaseDateGrouping.MONTH),
            to = DatabaseBoardGroupValue.DatePeriod(LocalDate(2026, 12, 1), DatabaseDateGrouping.MONTH)
        )

        assertEquals(PropertyDateRange(start = LocalDate(2026, 12, 1), end = LocalDate(2026, 12, 4)), moved.dateRange)
    }
}
