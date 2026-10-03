package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseNumberFormat
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.valueTypeOf
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

fun DatabaseBlock.canFormatNumbersIn(column: DatabaseColumnTarget): Boolean {
    val valueType = valueTypeOf(column) ?: return false
    return valueType.holdsNumber || valueType.holdsFormula
}

fun DatabaseBlock.numberFormatOf(column: DatabaseColumnTarget): DatabaseNumberFormat =
    numberFormats[column.columnKey] ?: DatabaseNumberFormat()

fun DatabaseBlock.withNumberFormat(column: DatabaseColumnTarget, format: DatabaseNumberFormat): DatabaseBlock {
    if (!canFormatNumbersIn(column)) return this
    val updatedFormats = if (format == DatabaseNumberFormat()) {
        numberFormats - column.columnKey
    } else {
        numberFormats + (column.columnKey to format)
    }
    return copy(numberFormats = updatedFormats)
}

fun DatabaseNumberFormat.textFor(number: Double): String {
    if (!number.isFinite()) return number.toString()
    val decimalPlaces = decimalPlaces ?: style.defaultDecimalPlaces
    val positiveText = if (decimalPlaces == null) formulaNumberText(abs(number)) else fixedDecimalText(abs(number), decimalPlaces)
    val isNegative = positiveText.any { it in '1'..'9' } && number < 0
    val wholePart = positiveText.substringBefore('.')
    val decimalPart = positiveText.substringAfter('.', missingDelimiterValue = "")
    val groupedWholePart = if (style.groupsThousands) wholePart.withThousandsSeparators() else wholePart
    val numberText = if (decimalPart.isEmpty()) groupedWholePart else "$groupedWholePart.$decimalPart"
    return (if (isNegative) "-" else "") + style.prefix + numberText + style.suffix
}

fun DatabaseNumberFormat.progressFor(number: Double): Float {
    if (progressGoal <= 0.0 || !number.isFinite()) return 0f
    return (number / progressGoal).coerceIn(0.0, 1.0).toFloat()
}

private fun fixedDecimalText(number: Double, decimalPlaces: Int): String {
    val scale = 10.0.pow(decimalPlaces)
    val scaledNumber = (number * scale).roundToLong()
    if (decimalPlaces == 0) return scaledNumber.toString()
    val scaleAsLong = scale.roundToLong()
    val decimalPart = (scaledNumber % scaleAsLong).toString().padStart(decimalPlaces, '0')
    return "${scaledNumber / scaleAsLong}.$decimalPart"
}

private fun String.withThousandsSeparators(): String =
    reversed().chunked(3).joinToString(",").reversed()
