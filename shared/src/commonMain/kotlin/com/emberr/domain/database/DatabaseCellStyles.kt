package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCellStyle
import com.emberr.domain.model.DatabaseColorRule
import com.emberr.domain.model.DatabaseColorRuleTarget
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.columnKey
import kotlinx.datetime.LocalDate

enum class DatabaseStyleTarget { CELL, ROW, COLUMN }

private const val CELL_STYLE_KEY_SEPARATOR = ":"

internal fun cellStyleKey(rowNoteId: String, columnKey: String): String = "$rowNoteId$CELL_STYLE_KEY_SEPARATOR$columnKey"

internal fun Map<String, DatabaseCellStyle>.withCellRowIdsReplaced(copyIdsBySourceRowId: Map<String, String>): Map<String, DatabaseCellStyle> =
    entries.mapNotNull { (styleKey, style) ->
        val copyRowNoteId = copyIdsBySourceRowId[styleKey.substringBefore(CELL_STYLE_KEY_SEPARATOR)] ?: return@mapNotNull null
        cellStyleKey(copyRowNoteId, styleKey.substringAfter(CELL_STYLE_KEY_SEPARATOR)) to style
    }.toMap()

internal fun Map<String, DatabaseCellStyle>.withoutCellsOfColumn(columnKey: String): Map<String, DatabaseCellStyle> =
    filterKeys { styleKey -> !styleKey.endsWith("$CELL_STYLE_KEY_SEPARATOR$columnKey") }

fun DatabaseBlock.styleOf(target: DatabaseStyleTarget, rowNoteId: String, column: DatabaseColumnTarget): DatabaseCellStyle =
    when (target) {
        DatabaseStyleTarget.CELL -> cellStyles[cellStyleKey(rowNoteId, column.columnKey)]
        DatabaseStyleTarget.ROW -> rowStyles[rowNoteId]
        DatabaseStyleTarget.COLUMN -> columnStyles[column.columnKey]
    } ?: DatabaseCellStyle()

fun DatabaseBlock.withStyle(
    target: DatabaseStyleTarget,
    rowNoteId: String,
    column: DatabaseColumnTarget,
    style: DatabaseCellStyle
): DatabaseBlock {
    fun Map<String, DatabaseCellStyle>.withStyleAt(key: String) = if (style == DatabaseCellStyle()) this - key else this + (key to style)
    return when (target) {
        DatabaseStyleTarget.CELL -> copy(cellStyles = cellStyles.withStyleAt(cellStyleKey(rowNoteId, column.columnKey)))
        DatabaseStyleTarget.ROW -> copy(rowStyles = rowStyles.withStyleAt(rowNoteId))
        DatabaseStyleTarget.COLUMN -> copy(columnStyles = columnStyles.withStyleAt(column.columnKey))
    }
}

fun DatabaseBlock.effectiveStyleOf(row: DatabaseRow, column: DatabaseColumnTarget, today: LocalDate): DatabaseCellStyle {
    val matchingRuleStyles = colorRules
        .filter { rule -> rule.appliesTo(column) && rule.condition.isMetBy(row, this, today) }
        .map { rule -> DatabaseCellStyle(textColorName = rule.textColorName, backgroundColorName = rule.backgroundColorName) }
    return styleFromLayers(
        listOfNotNull(cellStyles[cellStyleKey(row.noteId, column.columnKey)], rowStyles[row.noteId]) +
            matchingRuleStyles +
            listOfNotNull(columnStyles[column.columnKey])
    )
}

private fun DatabaseColorRule.appliesTo(column: DatabaseColumnTarget): Boolean =
    target == DatabaseColorRuleTarget.ROW || condition.target == column

private fun styleFromLayers(layersNearestFirst: List<DatabaseCellStyle>): DatabaseCellStyle {
    return DatabaseCellStyle(
        textColorName = layersNearestFirst.firstNotNullOfOrNull { it.textColorName },
        backgroundColorName = layersNearestFirst.firstNotNullOfOrNull { it.backgroundColorName },
        alignment = layersNearestFirst.firstNotNullOfOrNull { it.alignment }
    )
}
