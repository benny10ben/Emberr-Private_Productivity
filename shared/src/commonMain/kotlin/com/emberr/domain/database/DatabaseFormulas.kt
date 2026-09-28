package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.numberOrNull
import com.emberr.domain.model.valueAsText
import com.emberr.domain.model.valueTypeOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

fun todayInThisTimeZone(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun DatabaseBlock.isFormulaColumn(column: DatabaseColumnTarget): Boolean = valueTypeOf(column)?.holdsFormula == true

fun DatabaseBlock.formulaOf(column: DatabaseColumnTarget): String = formulas[column.columnKey].orEmpty()

fun DatabaseBlock.withFormulaSet(column: DatabaseColumnTarget, formula: String): DatabaseBlock {
    if (!isFormulaColumn(column)) return this
    val cleanedFormula = formula.trim()
    val updatedFormulas = if (cleanedFormula.isEmpty()) {
        formulas - column.columnKey
    } else {
        formulas + (column.columnKey to cleanedFormula)
    }
    return copy(formulas = updatedFormulas)
}

fun DatabaseBlock.formulaProblem(formula: String): String? {
    val expression = try {
        parseFormula(formula)
    } catch (problem: FormulaException) {
        return problem.message
    }
    val columnsByName = columnsByLowercaseName()
    val unknownName = expression.propertyNames().firstOrNull { it.trim().lowercase() !in columnsByName } ?: return null
    return "No property named ${formulaTextLiteral(unknownName)}"
}

fun String.withPropertyReferenceRenamed(oldName: String, newName: String): String {
    val oldReference = Regex("prop\\(\\s*" + Regex.escape(formulaTextLiteral(oldName)) + "\\s*\\)", RegexOption.IGNORE_CASE)
    return oldReference.replace(this, Regex.escapeReplacement("prop(${formulaTextLiteral(newName)})"))
}

fun List<DatabaseRow>.withFormulaResults(database: DatabaseBlock, today: LocalDate = todayInThisTimeZone()): List<DatabaseRow> {
    val formulaColumns = database.columns.filter { database.isFormulaColumn(it) }
    if (formulaColumns.isEmpty()) return this
    val parsedFormulas = formulaColumns.associateWith { column ->
        try {
            parseFormula(database.formulaOf(column))
        } catch (problem: FormulaException) {
            FormulaExpression.Literal(FormulaValue.Error(problem.message.orEmpty()))
        }
    }
    val columnsByName = database.columnsByLowercaseName()
    return map { row ->
        val calculator = RowFormulaCalculator(row, database, parsedFormulas, columnsByName, today)
        row.copy(formulaResults = formulaColumns.associateWith { calculator.resultOf(it) })
    }
}

internal fun compareFormulaResults(first: FormulaValue?, second: FormulaValue?): Int = when {
    first is FormulaValue.NumberValue && second is FormulaValue.NumberValue -> first.number.compareTo(second.number)
    first is FormulaValue.DateValue && second is FormulaValue.DateValue -> first.date.compareTo(second.date)
    first is FormulaValue.BooleanValue && second is FormulaValue.BooleanValue -> first.isTrue.compareTo(second.isTrue)
    else -> first?.displayText.orEmpty().compareTo(second?.displayText.orEmpty(), ignoreCase = true)
}

private fun DatabaseBlock.columnsByLowercaseName(): Map<String, DatabaseColumnTarget> =
    (columns + DatabaseColumnTarget.NotesTitle).associateBy { labelOf(it).trim().lowercase() }

private class RowFormulaCalculator(
    private val row: DatabaseRow,
    private val database: DatabaseBlock,
    private val parsedFormulas: Map<DatabaseColumnTarget, FormulaExpression>,
    private val columnsByName: Map<String, DatabaseColumnTarget>,
    private val today: LocalDate
) {
    private val finishedResults = mutableMapOf<DatabaseColumnTarget, FormulaValue>()
    private val columnsBeingCalculated = mutableSetOf<DatabaseColumnTarget>()

    fun resultOf(column: DatabaseColumnTarget): FormulaValue {
        finishedResults[column]?.let { return it }
        if (!columnsBeingCalculated.add(column)) throw FormulaException("This formula refers to itself")
        val result = evaluateFormula(parsedFormulas.getValue(column), today, ::valueOfProperty)
        columnsBeingCalculated.remove(column)
        finishedResults[column] = result
        return result
    }

    private fun valueOfProperty(name: String): FormulaValue {
        val column = columnsByName[name.trim().lowercase()] ?: throw FormulaException("No property named ${formulaTextLiteral(name)}")
        if (column in parsedFormulas) return resultOf(column)
        return row.formulaInputAt(column, database.valueTypeOf(column))
    }
}

private fun DatabaseRow.formulaInputAt(column: DatabaseColumnTarget, valueType: PropertyValueType?): FormulaValue {
    if (column == DatabaseColumnTarget.NotesTitle) return textOrEmpty(title)
    val cell = cell(column)
    return when {
        valueType == null -> FormulaValue.Empty
        valueType.holdsCheck -> FormulaValue.BooleanValue(cell?.isChecked == true)
        cell == null -> FormulaValue.Empty
        valueType.holdsNumber -> cell.numberOrNull()?.let { FormulaValue.NumberValue(it) } ?: FormulaValue.Empty
        valueType.holdsDate -> cell.date?.let { FormulaValue.DateValue(it) } ?: FormulaValue.Empty
        else -> textOrEmpty(cell.valueAsText())
    }
}

private fun textOrEmpty(text: String): FormulaValue =
    if (text.isBlank()) FormulaValue.Empty else FormulaValue.TextValue(text)
