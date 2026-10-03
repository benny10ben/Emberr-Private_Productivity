package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseNumberDisplay
import com.emberr.domain.model.DatabaseNumberFormat
import com.emberr.domain.model.DatabaseNumberStyle
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DatabaseNumberFormatsTest {

    private val numberColumn = DatabaseColumnTarget.Property(PropertyType.NUMBER)
    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val totalColumn = DatabaseColumnTarget.CustomProperty("total")
    private val dollars = DatabaseNumberFormat(style = DatabaseNumberStyle.US_DOLLAR)

    private val database = DatabaseBlock(
        id = "block",
        databaseId = "budget",
        columns = listOf(numberColumn, statusColumn, totalColumn),
        customProperties = listOf(DatabaseCustomProperty(id = "total", name = "Total", valueType = PropertyValueType.FORMULA)),
        updatedAt = 100L
    )

    private fun textIn(style: DatabaseNumberStyle, number: Double, decimalPlaces: Int? = null) =
        DatabaseNumberFormat(style = style, decimalPlaces = decimalPlaces).textFor(number)

    @Test
    fun eachStyleShowsTheNumberItsOwnWay() {
        assertEquals("1234.5", textIn(DatabaseNumberStyle.NUMBER, 1234.5))
        assertEquals("1,234,567.25", textIn(DatabaseNumberStyle.NUMBER_WITH_COMMAS, 1234567.25))
        assertEquals("75%", textIn(DatabaseNumberStyle.PERCENT, 75.0))
        assertEquals("$1,234.50", textIn(DatabaseNumberStyle.US_DOLLAR, 1234.5))
        assertEquals("€12.00", textIn(DatabaseNumberStyle.EURO, 12.0))
        assertEquals("£0.99", textIn(DatabaseNumberStyle.POUND, 0.99))
        assertEquals("₹100.00", textIn(DatabaseNumberStyle.RUPEE, 100.0))
        assertEquals("¥1,235", textIn(DatabaseNumberStyle.YEN, 1234.5))
    }

    @Test
    fun chosenDecimalPlacesRoundTheNumber() {
        assertEquals("2.3", textIn(DatabaseNumberStyle.NUMBER, 2.25, decimalPlaces = 1))
        assertEquals("3", textIn(DatabaseNumberStyle.NUMBER, 2.5, decimalPlaces = 0))
        assertEquals("$1,234.5", textIn(DatabaseNumberStyle.US_DOLLAR, 1234.5, decimalPlaces = 1))
        assertEquals("1.050", textIn(DatabaseNumberStyle.NUMBER, 1.05, decimalPlaces = 3))
    }

    @Test
    fun negativeNumbersPutTheMinusBeforeTheSymbol() {
        assertEquals("-$5.00", textIn(DatabaseNumberStyle.US_DOLLAR, -5.0))
        assertEquals("-1,500", textIn(DatabaseNumberStyle.NUMBER_WITH_COMMAS, -1500.0))
        assertEquals("$0.00", textIn(DatabaseNumberStyle.US_DOLLAR, -0.001))
    }

    @Test
    fun progressIsTheShareOfTheGoalKeptBetweenEmptyAndFull() {
        val outOfTen = DatabaseNumberFormat(display = DatabaseNumberDisplay.BAR, progressGoal = 10.0)

        assertEquals(0.5f, outOfTen.progressFor(5.0))
        assertEquals(1f, outOfTen.progressFor(15.0))
        assertEquals(0f, outOfTen.progressFor(-3.0))
        assertEquals(0f, outOfTen.copy(progressGoal = 0.0).progressFor(5.0))
    }

    @Test
    fun onlyNumberAndFormulaColumnsCanHaveAFormat() {
        assertEquals(dollars, database.withNumberFormat(numberColumn, dollars).numberFormatOf(numberColumn))
        assertEquals(dollars, database.withNumberFormat(totalColumn, dollars).numberFormatOf(totalColumn))
        assertSame(database, database.withNumberFormat(statusColumn, dollars))
    }

    @Test
    fun choosingThePlainFormatForgetsTheSavedFormat() {
        val formatted = database.withNumberFormat(numberColumn, dollars)

        assertEquals(emptyMap(), formatted.withNumberFormat(numberColumn, DatabaseNumberFormat()).numberFormats)
    }

    @Test
    fun removingAColumnForgetsItsFormat() {
        val formatted = database.withNumberFormat(numberColumn, dollars)

        assertEquals(emptyMap(), formatted.withColumnRemoved(numberColumn).numberFormats)
    }

    @Test
    fun formatsChosenOnTwoDevicesForDifferentColumnsAreBothKept() {
        val percent = DatabaseNumberFormat(style = DatabaseNumberStyle.PERCENT)
        val phone = database.withNumberFormat(numberColumn, dollars).copy(updatedAt = 200L).withSettingTimesStamped(before = database, now = 200L)
        val laptop = database.withNumberFormat(totalColumn, percent).copy(updatedAt = 300L).withSettingTimesStamped(before = database, now = 300L)

        val merged = mergeDatabaseBlocks(phone, laptop)

        assertEquals(mapOf("total" to percent, PropertyType.NUMBER.name to dollars), merged.numberFormats)
    }
}
