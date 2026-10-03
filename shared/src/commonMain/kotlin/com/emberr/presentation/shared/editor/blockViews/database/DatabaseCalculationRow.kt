package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.DatabaseCalculationResult
import com.emberr.domain.database.DatabaseRow
import com.emberr.domain.database.calculate
import com.emberr.domain.database.calculationOf
import com.emberr.domain.database.calculationsFor
import com.emberr.domain.database.formatCalculatedNumber
import com.emberr.domain.database.textFor
import com.emberr.domain.database.visibleColumnsInTableOrder
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseNumberFormat
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.valueTypeOf
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.property.formatPropertyDate
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_right
import emberr.shared.generated.resources.sigma
import emberr.shared.generated.resources.x
import org.jetbrains.compose.resources.painterResource

private val CalculationCellMinHeight = 36.dp

@Composable
internal fun DatabaseCalculationRow(
    block: DatabaseBlock,
    rows: List<DatabaseRow>,
    columnWidth: (DatabaseColumnTarget) -> Int,
    columnDragState: DatabaseColumnDragState,
    frozenColumns: Set<DatabaseColumnTarget>,
    tableScrollState: ScrollState,
    showsSelectionColumn: Boolean
) {
    val columns = block.visibleColumnsInTableOrder()
    if (columns.none { block.calculationOf(it) != null }) return
    val draggedBackground = draggedColumnBackground()
    val frozenBackground = MaterialTheme.colorScheme.background

    Row {
        if (showsSelectionColumn) {
            Spacer(
                modifier = Modifier
                    .staysInPlaceWhileScrolling(frozenColumns.isNotEmpty(), tableScrollState, frozenBackground)
                    .width(DatabaseSelectionColumnWidth)
                    .defaultMinSize(minHeight = CalculationCellMinHeight)
            )
        }
        columns.forEach { column ->
            key(column.columnKey) {
                DatabaseCalculationCell(
                    block = block,
                    rows = rows,
                    column = column,
                    width = columnWidth(column),
                    modifier = Modifier
                        .staysInPlaceWhileScrolling(column in frozenColumns, tableScrollState, frozenBackground)
                        .raisedWhileColumnDragged(columnDragState, column)
                        .followsColumnDrag(columnDragState, column, draggedBackground)
                )
            }
        }
    }
}

@Composable
private fun DatabaseCalculationCell(
    block: DatabaseBlock,
    rows: List<DatabaseRow>,
    column: DatabaseColumnTarget,
    width: Int,
    modifier: Modifier = Modifier
) {
    val calculation = block.calculationOf(column)
    val valueType = block.valueTypeOf(column) ?: PropertyValueType.TEXT
    val result = remember(rows, column, valueType, calculation) {
        calculation?.let { calculate(rows, column, valueType, it) }
    }

    Box(
        modifier = modifier
            .width(width.dp)
            .defaultMinSize(minHeight = CalculationCellMinHeight)
            .padding(horizontal = DatabaseCellHorizontalPadding, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (calculation != null && result != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = calculation.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = result.displayTextIn(block.numberFormats[column.columnKey]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun DatabaseCalculateOption(
    block: DatabaseBlock,
    column: DatabaseColumnTarget,
    editor: DatabaseBlockEditor,
    closeMenuAnd: (() -> Unit) -> Unit
) {
    val calculation = block.calculationOf(column)
    val valueType = block.valueTypeOf(column) ?: PropertyValueType.TEXT
    val calculationsByGroup = calculationsFor(valueType).groupBy { it.group }

    DatabaseMenuLayer(
        title = "Calculate",
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = "Calculate",
                icon = { DatabaseOptionIcon(Res.drawable.sigma) },
                trailing = if (calculation == null) null else {
                    {
                        Text(
                            text = calculation.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                    }
                },
                onClick = openLayer
            )
        }
    ) { closeCalculateAnd ->
        calculationsByGroup.forEach { (group, options) ->
            key(group) {
                DatabaseMenuLayer(
                    title = group.label,
                    anchor = { openGroup ->
                        DatabaseMenuOption(
                            label = group.label,
                            isSelected = calculation?.group == group,
                            trailing = {
                                Icon(
                                    painter = painterResource(Res.drawable.chevron_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            onClick = openGroup
                        )
                    }
                ) { closeGroupAnd ->
                    options.forEach { option ->
                        DatabaseMenuOption(
                            label = option.label,
                            isSelected = option == calculation,
                            onClick = {
                                closeGroupAnd { closeCalculateAnd { closeMenuAnd { editor.setCalculation(block.id, column, option) } } }
                            }
                        )
                    }
                }
            }
        }

        if (calculation != null) {
            DatabaseMenuOption(
                label = "Remove calculation",
                icon = { DatabaseOptionIcon(Res.drawable.x, tint = MaterialTheme.colorScheme.error) },
                labelColor = MaterialTheme.colorScheme.error,
                onClick = { closeCalculateAnd { closeMenuAnd { editor.setCalculation(block.id, column, null) } } }
            )
        }
    }
}

internal fun DatabaseCalculationResult.displayTextIn(numberFormat: DatabaseNumberFormat?): String {
    val number = (this as? DatabaseCalculationResult.NumberValue)?.number
    return if (number != null && numberFormat != null) numberFormat.textFor(number) else displayText()
}

internal fun DatabaseCalculationResult.displayText(): String = when (this) {
    is DatabaseCalculationResult.Count -> value.toString()
    is DatabaseCalculationResult.Percent -> {
        val wholePercent = tenthsOfAPercent / 10
        val tenths = tenthsOfAPercent % 10
        if (tenths == 0) "$wholePercent%" else "$wholePercent.$tenths%"
    }
    is DatabaseCalculationResult.NumberValue -> number?.let { formatCalculatedNumber(it) } ?: "None"
    is DatabaseCalculationResult.DateValue -> date?.let { formatPropertyDate(it) } ?: "None"
    is DatabaseCalculationResult.DayCount -> when (days) {
        null -> "None"
        1 -> "1 day"
        else -> "$days days"
    }
}
