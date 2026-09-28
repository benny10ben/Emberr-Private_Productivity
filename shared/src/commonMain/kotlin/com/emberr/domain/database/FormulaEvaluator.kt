package com.emberr.domain.database

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlinx.datetime.yearsUntil
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong

private const val SHOWN_DECIMAL_PLACES = 8
private const val SHOWN_DECIMAL_SCALE = 100_000_000L
private const val LARGEST_NUMBER_WITH_SHOWN_DECIMALS = 1e10
private val dateUnitNames = listOf("day", "week", "month", "year")

@Immutable
sealed class FormulaValue {
    abstract val displayText: String

    data class NumberValue(val number: Double) : FormulaValue() {
        override val displayText: String get() = formulaNumberText(number)
    }

    data class TextValue(val text: String) : FormulaValue() {
        override val displayText: String get() = text
    }

    data class BooleanValue(val isTrue: Boolean) : FormulaValue() {
        override val displayText: String get() = isTrue.toString()
    }

    data class DateValue(val date: LocalDate) : FormulaValue() {
        override val displayText: String get() = date.toString()
    }

    data object Empty : FormulaValue() {
        override val displayText: String get() = ""
    }

    data class Error(val message: String) : FormulaValue() {
        override val displayText: String get() = ""
    }
}

private fun formulaNumberText(number: Double): String {
    if (!number.isFinite()) return number.toString()
    if (abs(number) >= LARGEST_NUMBER_WITH_SHOWN_DECIMALS) return number.roundToLong().toString()
    val scaledNumber = (number * SHOWN_DECIMAL_SCALE).roundToLong()
    val sign = if (scaledNumber < 0) "-" else ""
    val wholePart = abs(scaledNumber) / SHOWN_DECIMAL_SCALE
    val decimalPart = (abs(scaledNumber) % SHOWN_DECIMAL_SCALE).toString().padStart(SHOWN_DECIMAL_PLACES, '0').trimEnd('0')
    return if (decimalPart.isEmpty()) "$sign$wholePart" else "$sign$wholePart.$decimalPart"
}

fun evaluateFormula(
    expression: FormulaExpression,
    today: LocalDate,
    valueOfProperty: (String) -> FormulaValue
): FormulaValue =
    try {
        FormulaEvaluator(today, valueOfProperty).valueOf(expression)
    } catch (problem: FormulaException) {
        FormulaValue.Error(problem.message.orEmpty())
    }

private class FormulaEvaluator(
    private val today: LocalDate,
    private val valueOfProperty: (String) -> FormulaValue
) {
    fun valueOf(expression: FormulaExpression): FormulaValue = when (expression) {
        is FormulaExpression.Literal -> expression.value
        is FormulaExpression.PropertyReference -> {
            val value = valueOfProperty(expression.propertyName)
            if (value is FormulaValue.Error) throw FormulaException(value.message)
            value
        }
        is FormulaExpression.Negation -> finiteNumber(-numberOf(expression.operand))
        is FormulaExpression.Operation -> resultOf(expression.operator, valueOf(expression.left), valueOf(expression.right))
        is FormulaExpression.FunctionCall -> resultOf(expression.function, expression.arguments)
    }

    private fun resultOf(operator: FormulaOperator, left: FormulaValue, right: FormulaValue): FormulaValue = when (operator) {
        FormulaOperator.PLUS -> if (left is FormulaValue.TextValue || right is FormulaValue.TextValue) {
            FormulaValue.TextValue(left.displayText + right.displayText)
        } else {
            finiteNumber(numberFrom(left) + numberFrom(right))
        }
        FormulaOperator.MINUS -> finiteNumber(numberFrom(left) - numberFrom(right))
        FormulaOperator.TIMES -> finiteNumber(numberFrom(left) * numberFrom(right))
        FormulaOperator.DIVIDE -> {
            val divisor = numberFrom(right)
            if (divisor == 0.0) throw FormulaException("Can't divide by zero")
            finiteNumber(numberFrom(left) / divisor)
        }
        FormulaOperator.EQUALS -> FormulaValue.BooleanValue(areEqual(left, right))
        FormulaOperator.NOT_EQUALS -> FormulaValue.BooleanValue(!areEqual(left, right))
        FormulaOperator.GREATER -> FormulaValue.BooleanValue(compare(left, right)?.let { it > 0 } == true)
        FormulaOperator.GREATER_OR_EQUAL -> FormulaValue.BooleanValue(compare(left, right)?.let { it >= 0 } == true)
        FormulaOperator.LESS -> FormulaValue.BooleanValue(compare(left, right)?.let { it < 0 } == true)
        FormulaOperator.LESS_OR_EQUAL -> FormulaValue.BooleanValue(compare(left, right)?.let { it <= 0 } == true)
    }

    private fun resultOf(function: FormulaFunction, arguments: List<FormulaExpression>): FormulaValue = when (function) {
        FormulaFunction.IF -> if (booleanOf(arguments[0])) valueOf(arguments[1]) else valueOf(arguments[2])
        FormulaFunction.AND -> FormulaValue.BooleanValue(arguments.all { booleanOf(it) })
        FormulaFunction.OR -> FormulaValue.BooleanValue(arguments.any { booleanOf(it) })
        FormulaFunction.NOT -> FormulaValue.BooleanValue(!booleanOf(arguments[0]))
        FormulaFunction.CONCAT -> FormulaValue.TextValue(arguments.joinToString("") { valueOf(it).displayText })
        FormulaFunction.LENGTH -> FormulaValue.NumberValue(valueOf(arguments[0]).displayText.length.toDouble())
        FormulaFunction.ROUND -> {
            val decimalPlaces = if (arguments.size == 2) wholeNumberOf(arguments[1], function) else 0
            val scale = 10.0.pow(decimalPlaces)
            finiteNumber(floor(numberOf(arguments[0]) * scale + 0.5) / scale)
        }
        FormulaFunction.ABS -> FormulaValue.NumberValue(abs(numberOf(arguments[0])))
        FormulaFunction.MIN -> FormulaValue.NumberValue(arguments.minOf { numberOf(it) })
        FormulaFunction.MAX -> FormulaValue.NumberValue(arguments.maxOf { numberOf(it) })
        FormulaFunction.EMPTY -> FormulaValue.BooleanValue(valueOf(arguments[0]).isEmptyValue())
        FormulaFunction.NOW -> FormulaValue.DateValue(today)
        FormulaFunction.DATE_ADD -> dateAdded(arguments)
        FormulaFunction.DATE_BETWEEN -> timeBetweenDates(arguments)
    }

    private fun dateAdded(arguments: List<FormulaExpression>): FormulaValue {
        val date = dateOrNullOf(arguments[0], FormulaFunction.DATE_ADD) ?: return FormulaValue.Empty
        val amount = wholeNumberOf(arguments[1], FormulaFunction.DATE_ADD)
        val unit = when (dateUnitNameOf(arguments[2])) {
            "day" -> DateTimeUnit.DAY
            "week" -> DateTimeUnit.WEEK
            "month" -> DateTimeUnit.MONTH
            else -> DateTimeUnit.YEAR
        }
        return try {
            FormulaValue.DateValue(date.plus(amount, unit))
        } catch (problem: DateTimeArithmeticException) {
            throw FormulaException("The date is too far away")
        }
    }

    private fun timeBetweenDates(arguments: List<FormulaExpression>): FormulaValue {
        val laterDate = dateOrNullOf(arguments[0], FormulaFunction.DATE_BETWEEN) ?: return FormulaValue.Empty
        val earlierDate = dateOrNullOf(arguments[1], FormulaFunction.DATE_BETWEEN) ?: return FormulaValue.Empty
        val amount = when (dateUnitNameOf(arguments[2])) {
            "day" -> earlierDate.daysUntil(laterDate)
            "week" -> earlierDate.daysUntil(laterDate) / 7
            "month" -> earlierDate.monthsUntil(laterDate)
            else -> earlierDate.yearsUntil(laterDate)
        }
        return FormulaValue.NumberValue(amount.toDouble())
    }

    private fun dateUnitNameOf(expression: FormulaExpression): String {
        val unitName = (valueOf(expression) as? FormulaValue.TextValue)?.text?.trim()?.lowercase()?.removeSuffix("s")
        return unitName?.takeIf { it in dateUnitNames }
            ?: throw FormulaException("The unit must be \"days\", \"weeks\", \"months\" or \"years\"")
    }

    private fun numberOf(expression: FormulaExpression): Double = numberFrom(valueOf(expression))

    private fun booleanOf(expression: FormulaExpression): Boolean = when (val value = valueOf(expression)) {
        is FormulaValue.BooleanValue -> value.isTrue
        FormulaValue.Empty -> false
        else -> throw FormulaException("Expected true or false but got ${value.kindName}")
    }

    private fun wholeNumberOf(expression: FormulaExpression, function: FormulaFunction): Int {
        val number = numberOf(expression)
        if (number != floor(number) || abs(number) > Int.MAX_VALUE) {
            throw FormulaException("${function.formulaName}() needs a whole number")
        }
        return number.toInt()
    }

    private fun dateOrNullOf(expression: FormulaExpression, function: FormulaFunction): LocalDate? =
        when (val value = valueOf(expression)) {
            is FormulaValue.DateValue -> value.date
            FormulaValue.Empty -> null
            else -> throw FormulaException("${function.formulaName}() needs a date but got ${value.kindName}")
        }
}

private fun numberFrom(value: FormulaValue): Double = when (value) {
    is FormulaValue.NumberValue -> value.number
    FormulaValue.Empty -> 0.0
    else -> throw FormulaException("Expected a number but got ${value.kindName}")
}

private fun finiteNumber(number: Double): FormulaValue.NumberValue {
    if (!number.isFinite()) throw FormulaException("The number is too big")
    return FormulaValue.NumberValue(number)
}

private fun areEqual(left: FormulaValue, right: FormulaValue): Boolean = when {
    left.isEmptyValue() && right.isEmptyValue() -> true
    left is FormulaValue.NumberValue && right is FormulaValue.NumberValue -> left.number == right.number
    else -> left == right
}

private fun compare(left: FormulaValue, right: FormulaValue): Int? = when {
    left == FormulaValue.Empty || right == FormulaValue.Empty -> null
    left is FormulaValue.NumberValue && right is FormulaValue.NumberValue -> left.number.compareTo(right.number)
    left is FormulaValue.DateValue && right is FormulaValue.DateValue -> left.date.compareTo(right.date)
    left is FormulaValue.TextValue && right is FormulaValue.TextValue -> left.text.compareTo(right.text)
    else -> throw FormulaException("Can't compare ${left.kindName} with ${right.kindName}")
}

private fun FormulaValue.isEmptyValue(): Boolean =
    this == FormulaValue.Empty || (this is FormulaValue.TextValue && text.isBlank())

private val FormulaValue.kindName: String
    get() = when (this) {
        is FormulaValue.NumberValue -> "a number"
        is FormulaValue.TextValue -> "text"
        is FormulaValue.BooleanValue -> "true or false"
        is FormulaValue.DateValue -> "a date"
        FormulaValue.Empty -> "nothing"
        is FormulaValue.Error -> "an error"
    }
