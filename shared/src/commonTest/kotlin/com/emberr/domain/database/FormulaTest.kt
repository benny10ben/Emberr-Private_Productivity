package com.emberr.domain.database

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FormulaTest {

    private val today = LocalDate(2026, 9, 28)
    private val properties = mapOf(
        "Price" to FormulaValue.NumberValue(12.5),
        "Quantity" to FormulaValue.NumberValue(4.0),
        "Name" to FormulaValue.TextValue("Tea"),
        "Done" to FormulaValue.BooleanValue(true),
        "Due Date" to FormulaValue.DateValue(LocalDate(2026, 10, 5)),
        "Blank" to FormulaValue.Empty
    )

    private fun resultOf(formula: String): FormulaValue =
        evaluateFormula(parseFormula(formula), today) { name -> properties[name] ?: throw FormulaException("No property named $name") }

    private fun problemIn(formula: String): String? = assertFailsWith<FormulaException> { parseFormula(formula) }.message

    private fun number(value: Double) = FormulaValue.NumberValue(value)
    private fun text(value: String) = FormulaValue.TextValue(value)
    private fun yesOrNo(value: Boolean) = FormulaValue.BooleanValue(value)
    private fun date(year: Int, month: Int, day: Int) = FormulaValue.DateValue(LocalDate(year, month, day))

    @Test
    fun mathFollowsTheUsualOrderOfOperations() {
        assertEquals(number(14.0), resultOf("2 + 3 * 4"))
        assertEquals(number(20.0), resultOf("(2 + 3) * 4"))
        assertEquals(number(-1.0), resultOf("-(3 - 2)"))
        assertEquals(number(2.5), resultOf("10 / 4"))
    }

    @Test
    fun propertiesCanBeUsedInMath() {
        assertEquals(number(50.0), resultOf("""prop("Price") * prop("Quantity")"""))
    }

    @Test
    fun anEmptyValueCountsAsZeroInMath() {
        assertEquals(number(2.0), resultOf("""prop("Blank") + 2"""))
    }

    @Test
    fun addingTextJoinsItTogether() {
        assertEquals(text("Tea x 4"), resultOf("""prop("Name") + " x " + prop("Quantity")"""))
    }

    @Test
    fun ifOnlyWorksOutTheBranchItPicks() {
        assertEquals(text("Finished"), resultOf("""if(prop("Done"), "Finished", 1 / 0)"""))
        assertEquals(number(0.0), resultOf("""if(prop("Blank"), 1, 0)"""))
    }

    @Test
    fun comparisonsGiveTrueOrFalse() {
        assertEquals(yesOrNo(true), resultOf("""prop("Price") >= 12.5"""))
        assertEquals(yesOrNo(false), resultOf("""prop("Name") == "Coffee""""))
        assertEquals(yesOrNo(true), resultOf("""prop("Due Date") > now()"""))
        assertEquals(yesOrNo(true), resultOf("""prop("Blank") == """""))
    }

    @Test
    fun comparingWithAnEmptyValueIsAlwaysFalse() {
        assertEquals(yesOrNo(false), resultOf("""prop("Blank") < 5"""))
        assertEquals(yesOrNo(false), resultOf("""prop("Blank") > 5"""))
    }

    @Test
    fun trueOrFalseFunctionsWork() {
        assertEquals(yesOrNo(false), resultOf("and(true, false)"))
        assertEquals(yesOrNo(true), resultOf("or(false, true)"))
        assertEquals(yesOrNo(true), resultOf("not(false)"))
        assertEquals(yesOrNo(true), resultOf("""empty(prop("Blank"))"""))
        assertEquals(yesOrNo(true), resultOf("""empty(" ")"""))
        assertEquals(yesOrNo(false), resultOf("""empty(prop("Name"))"""))
    }

    @Test
    fun textAndNumberFunctionsWork() {
        assertEquals(text("a1true"), resultOf("""concat("a", 1, true)"""))
        assertEquals(number(5.0), resultOf("""length("hello")"""))
        assertEquals(number(3.142), resultOf("round(3.14159, 3)"))
        assertEquals(number(3.0), resultOf("round(2.5)"))
        assertEquals(number(-2.0), resultOf("round(-2.5)"))
        assertEquals(number(3.0), resultOf("abs(-3)"))
        assertEquals(number(1.0), resultOf("min(4, 1, 7)"))
        assertEquals(number(7.0), resultOf("max(4, 1, 7)"))
    }

    @Test
    fun dateFunctionsWork() {
        assertEquals(date(2026, 9, 28), resultOf("now()"))
        assertEquals(date(2026, 11, 5), resultOf("""dateAdd(prop("Due Date"), 1, "month")"""))
        assertEquals(date(2026, 9, 26), resultOf("""dateAdd(now(), -2, "days")"""))
        assertEquals(number(7.0), resultOf("""dateBetween(prop("Due Date"), now(), "days")"""))
        assertEquals(number(1.0), resultOf("""dateBetween(prop("Due Date"), now(), "weeks")"""))
        assertEquals(number(-7.0), resultOf("""dateBetween(now(), prop("Due Date"), "days")"""))
    }

    @Test
    fun dateFunctionsGiveNothingForAnEmptyDate() {
        assertEquals(FormulaValue.Empty, resultOf("""dateAdd(prop("Blank"), 1, "days")"""))
        assertEquals(FormulaValue.Empty, resultOf("""dateBetween(prop("Blank"), now(), "days")"""))
    }

    @Test
    fun functionNamesAndTrueOrFalseIgnoreCase() {
        assertEquals(number(1.0), resultOf("IF(TRUE, 1, 2)"))
    }

    @Test
    fun aBlankFormulaGivesNothing() {
        assertEquals(FormulaValue.Empty, resultOf("   "))
    }

    @Test
    fun quotesInsideTextCanBeEscaped() {
        assertEquals(text("say \"hi\""), resultOf("\"say \\\"hi\\\"\""))
    }

    @Test
    fun numbersShowWithoutNeedlessDecimals() {
        assertEquals("4", number(4.0).displayText)
        assertEquals("0.3", resultOf("0.1 + 0.2").displayText)
        assertEquals("0.33333333", resultOf("1 / 3").displayText)
        assertEquals("-0.5", number(-0.5).displayText)
    }

    @Test
    fun mistakesInTheFormulaTextAreExplained() {
        assertEquals("Missing \")\"", problemIn("""prop("Price" * 2"""))
        assertEquals("The formula ends too early", problemIn("2 +"))
        assertEquals("Unknown function \"sum\"", problemIn("sum(1, 2)"))
        assertEquals("if() needs 3 values", problemIn("if(true, 1)"))
        assertEquals("now() needs no values", problemIn("now(1)"))
        assertEquals("Unknown word \"Price\". To use a property, write prop(\"Price\")", problemIn("Price * 2"))
        assertEquals("Text is missing its closing quote", problemIn("\"open"))
        assertEquals("Unexpected \"=\"", problemIn("2 = 2"))
        assertEquals("prop() needs one property name in quotes, like prop(\"Price\")", problemIn("prop(2)"))
    }

    @Test
    fun mistakesWhileCalculatingBecomeErrorValues() {
        assertEquals(FormulaValue.Error("Can't divide by zero"), resultOf("1 / 0"))
        assertEquals(FormulaValue.Error("Expected a number but got text"), resultOf("""prop("Name") * 2"""))
        assertEquals(FormulaValue.Error("Can't compare a number with text"), resultOf("""1 < "a""""))
        assertEquals(
            FormulaValue.Error("The unit must be \"days\", \"weeks\", \"months\" or \"years\""),
            resultOf("""dateAdd(now(), 1, "hours")""")
        )
        assertEquals(FormulaValue.Error("dateAdd() needs a whole number"), resultOf("""dateAdd(now(), 1.5, "days")"""))
    }

    @Test
    fun todayGivesTheSameDateAsNow() {
        assertEquals(date(2026, 9, 28), resultOf("today()"))
    }

    @Test
    fun formatDateFollowsThePattern() {
        assertEquals(text("Oct 5, 2026"), resultOf("""formatDate(prop("Due Date"), "MMM D, YYYY")"""))
        assertEquals(text("Monday, October 05"), resultOf("""formatDate(prop("Due Date"), "dddd, MMMM DD")"""))
        assertEquals(text("05/10/26"), resultOf("""formatDate(prop("Due Date"), "DD/MM/YY")"""))
        assertEquals(text("Due 10-5"), resultOf("""formatDate(prop("Due Date"), "[Due] M-D")"""))
        assertEquals(FormulaValue.Empty, resultOf("""formatDate(prop("Blank"), "YYYY")"""))
    }

    @Test
    fun yearMonthAndDayReadPartsOfADate() {
        assertEquals(number(2026.0), resultOf("""year(prop("Due Date"))"""))
        assertEquals(number(10.0), resultOf("""month(prop("Due Date"))"""))
        assertEquals(number(5.0), resultOf("""day(prop("Due Date"))"""))
        assertEquals(FormulaValue.Empty, resultOf("""day(prop("Blank"))"""))
        assertEquals(FormulaValue.Error("year() needs a date but got text"), resultOf("""year(prop("Name"))"""))
    }

    @Test
    fun containsLooksForTextIgnoringCase() {
        assertEquals(yesOrNo(true), resultOf("""contains(prop("Name"), "te")"""))
        assertEquals(yesOrNo(false), resultOf("""contains(prop("Name"), "coffee")"""))
        assertEquals(yesOrNo(true), resultOf("""contains(prop("Price"), "12.5")"""))
    }

    @Test
    fun floorCeilAndModWorkOnNumbers() {
        assertEquals(number(12.0), resultOf("""floor(prop("Price"))"""))
        assertEquals(number(13.0), resultOf("""ceil(prop("Price"))"""))
        assertEquals(number(-3.0), resultOf("floor(-2.5)"))
        assertEquals(number(1.0), resultOf("mod(7, 3)"))
        assertEquals(number(0.5), resultOf("mod(prop(\"Price\"), 4)"))
        assertEquals(FormulaValue.Error("Can't divide by zero"), resultOf("mod(7, 0)"))
    }

    @Test
    fun ifsGivesTheValueOfTheFirstTrueConditionOrTheLastValue() {
        assertEquals(text("big"), resultOf("""ifs(prop("Price") > 10, "big", prop("Price") > 5, "medium", "small")"""))
        assertEquals(text("medium"), resultOf("""ifs(prop("Quantity") > 10, "big", prop("Quantity") > 3, "medium", "small")"""))
        assertEquals(text("small"), resultOf("""ifs(false, "big", false, "medium", "small")"""))
    }

    @Test
    fun ifsNeedsAnOddNumberOfValues() {
        assertEquals("ifs() needs pairs of a condition and a value, then one value to use otherwise", problemIn("ifs(true, 1, false, 2)"))
        assertEquals("ifs() needs at least 3 values", problemIn("ifs(true, 1)"))
    }
}
