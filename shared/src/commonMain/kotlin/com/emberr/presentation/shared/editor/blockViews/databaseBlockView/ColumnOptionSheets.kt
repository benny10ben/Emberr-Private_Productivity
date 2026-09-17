@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.propertyLabel
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrTextField
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_left
import emberr.shared.generated.resources.arrow_right
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.badge_dollar_sign
import emberr.shared.generated.resources.check
import emberr.shared.generated.resources.minus
import emberr.shared.generated.resources.move_left
import emberr.shared.generated.resources.move_right
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.sigma
import emberr.shared.generated.resources.trash
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun CellActionsSheet(context: DatabaseSheetContext) {
    val state = context.state
    val column = context.activeColumn ?: return
    val row = context.block.rows.find { it.id == state.activeRowId } ?: return

    val colIndex = context.visibleColumns.indexOf(column)
    val rowIndex = context.block.rows.indexOf(row)
    val actions = context.actions
    val blockId = context.block.id

    SheetMenuRow(painterResource(Res.drawable.arrow_up), "Insert Row Above") {
        state.applyAction { actions.onAddDbRowAt(blockId, rowIndex) }
    }
    SheetMenuRow(painterResource(Res.drawable.arrow_down), "Insert Row Below") {
        state.applyAction { actions.onAddDbRowAt(blockId, rowIndex + 1) }
    }
    SheetMenuRow(painterResource(Res.drawable.arrow_left), "Insert Column Left") {
        state.applyAction { actions.onAddDbColumnAt(blockId, colIndex) }
    }
    SheetMenuRow(painterResource(Res.drawable.arrow_right), "Insert Column Right") {
        state.applyAction { actions.onAddDbColumnAt(blockId, colIndex + 1) }
    }

    SheetDivider()

    if (rowIndex > 0) {
        SheetMenuRow(rememberVectorPainter(Icons.Default.ArrowUpward), "Move Row Up") {
            state.applyAction { actions.onReorderDbRows(blockId, rowIndex, rowIndex - 1) }
        }
    }
    if (rowIndex < context.block.rows.lastIndex) {
        SheetMenuRow(rememberVectorPainter(Icons.Default.ArrowDownward), "Move Row Down") {
            state.applyAction { actions.onReorderDbRows(blockId, rowIndex, rowIndex + 1) }
        }
    }
    if (colIndex > 0) {
        SheetMenuRow(painterResource(Res.drawable.move_left), "Move Column Left") {
            state.applyAction { actions.onReorderDbColumns(blockId, colIndex, colIndex - 1) }
        }
    }
    if (colIndex < context.visibleColumns.lastIndex) {
        SheetMenuRow(painterResource(Res.drawable.move_right), "Move Column Right") {
            state.applyAction { actions.onReorderDbColumns(blockId, colIndex, colIndex + 1) }
        }
    }

    SheetDivider()

    SheetMenuRow(painterResource(Res.drawable.trash), "Delete Row", MaterialTheme.colorScheme.error) {
        state.applyAction { actions.onDeleteDbRow(blockId, row.id) }
    }
    SheetMenuRow(painterResource(Res.drawable.trash), "Delete Column", MaterialTheme.colorScheme.error) {
        state.applyAction { actions.onDeleteDbColumn(blockId, column.id) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnOptionsSheet(context: DatabaseSheetContext) {
    val state = context.state
    val column = context.activeColumn ?: return
    val actions = context.actions
    val blockId = context.block.id

    SheetMenuRow(painterResource(Res.drawable.pen), "Rename Column") {
        state.textInput = column.name
        state.open(DatabaseSheet.RENAME)
    }

    if (column.type == ColumnType.FORMULA) {
        SheetMenuRow(
            icon = painterResource(Res.drawable.sigma),
            text = "Edit Formula",
            color = MaterialTheme.colorScheme.primary
        ) {
            state.textInput = column.formulaExpression ?: ""
            state.open(DatabaseSheet.FORMULA)
        }

        val isCurrency = column.isFormulaCurrency
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { actions.onUpdateDbFormulaCurrency(blockId, column.id, !isCurrency) }
                .padding(vertical = 6.dp).sheetSidePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(Res.drawable.badge_dollar_sign),
                    null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "Format as currency",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(checked = isCurrency, onCheckedChange = null, modifier = Modifier.scale(0.8f))
        }

        if (isCurrency) {
            SheetMenuRow(
                icon = painterResource(Res.drawable.badge_dollar_sign),
                text = "Currency: ${column.currencySymbol ?: "$"}",
                color = MaterialTheme.colorScheme.primary
            ) { state.open(DatabaseSheet.CURRENCY_SELECTION) }
        }
    }

    if (column.type == ColumnType.MONEY) {
        SheetMenuRow(
            icon = painterResource(Res.drawable.badge_dollar_sign),
            text = "Format: ${column.currencySymbol ?: "$"}",
            color = MaterialTheme.colorScheme.primary
        ) { state.open(DatabaseSheet.CURRENCY_SELECTION) }
    }

    if (!isDesktopPlatform) {
        SheetDivider()
        SheetSectionLabel("Column Width", Modifier.padding(top = 4.dp, bottom = 8.dp))

        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ColumnWidthStepper(painterResource(Res.drawable.minus)) {
                actions.onUpdateDbColumnWidth(blockId, column.id, column.width - 20)
            }
            Text(
                text = "${column.width} px",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 50.dp),
                textAlign = TextAlign.Center
            )
            ColumnWidthStepper(painterResource(Res.drawable.plus)) {
                actions.onUpdateDbColumnWidth(blockId, column.id, column.width + 20)
            }
        }
    }

    SheetDivider()
    SheetSectionLabel("Property Type", Modifier.padding(bottom = 10.dp, top = 12.dp).sheetSidePadding())

    FlowRow(
        modifier = Modifier.sheetSidePadding(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ColumnType.entries.forEach { type ->
            val isSelected = column.type == type
            FilterChip(
                selected = isSelected,
                onClick = {
                    state.applyAction {
                        actions.onUpdateDbColumn(
                            blockId,
                            column.id,
                            if (column.isNameManuallySet) column.name else type.propertyLabel(),
                            type,
                            isManualNameChange = false
                        )
                    }
                },
                label = { Text(text = type.propertyLabel(), style = MaterialTheme.typography.labelSmall) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }

    SheetDivider()
    SheetMenuRow(
        icon = painterResource(Res.drawable.trash),
        text = "Delete Column",
        color = MaterialTheme.colorScheme.error
    ) { state.applyAction { actions.onDeleteDbColumn(blockId, column.id) } }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ColumnWidthStepper(icon: Painter, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.padding(8.dp).size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun RenameColumnSheet(context: DatabaseSheetContext) {
    val state = context.state

    fun onConfirmRenameColumn() {
        val column = context.activeColumn
        if (column != null && state.textInput.isNotBlank()) {
            val newName = state.textInput.trim()
            state.applyAction { context.actions.onUpdateDbColumn(context.block.id, column.id, newName, column.type) }
        }
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = state.textInput,
            onValueChange = { state.textInput = it },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            onSubmit = ::onConfirmRenameColumn
        )
    }
    SheetCancelAndConfirmButtons(
        confirmText = "Save",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = ::onConfirmRenameColumn,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditFormulaSheet(context: DatabaseSheetContext) {
    val state = context.state

    SheetSectionLabel("Properties", Modifier.padding(bottom = 8.dp, top = 12.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).sheetSidePadding()
    ) {
        context.visibleColumns.filter { it.id != state.activeColId }.forEach { column ->
            SuggestionChip(
                onClick = { state.textInput += "prop(\"${column.name}\") " },
                label = {
                    Text(
                        column.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )
        }
    }

    SheetSectionLabel("Operators", Modifier.padding(bottom = 8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).sheetSidePadding()
    ) {
        listOf("+", "-", "*", "/", "(", ")").forEach { operator ->
            SuggestionChip(
                onClick = { state.textInput += "$operator " },
                label = {
                    Text(
                        operator,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )
        }
    }

    fun onConfirmFormula() {
        val columnId = state.activeColId ?: return
        val expression = state.textInput.trim()
        state.applyAction { context.actions.onUpdateDbFormula(context.block.id, columnId, expression) }
    }

    Column(modifier = Modifier.sheetSidePadding()) {
        EmberrTextField(
            value = state.textInput,
            onValueChange = { state.textInput = it },
            placeholder = "e.g. prop(\"Price\") * 2",
            modifier = Modifier.fillMaxWidth(),
            onSubmit = ::onConfirmFormula
        )
    }
    SheetCancelAndConfirmButtons(
        confirmText = "Save",
        onCancel = { state.dismissCurrentSheet() },
        onConfirm = ::onConfirmFormula,
        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
    )
}

private val SUPPORTED_CURRENCIES = listOf(
    "$" to "US Dollar",
    "€" to "Euro",
    "£" to "British Pound",
    "¥" to "Yen",
    "₹" to "Rupee",
    "A$" to "Australian Dollar",
    "C$" to "Canadian Dollar"
)

@Composable
internal fun PickCurrencySheet(context: DatabaseSheetContext) {
    val column = context.activeColumn ?: return

    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())
    ) {
        SUPPORTED_CURRENCIES.forEach { (symbol, name) ->
            val isSelected = (column.currencySymbol ?: "$") == symbol
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.state.close()
                        context.actions.onUpdateDbCurrency(context.block.id, column.id, symbol)
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "$name ($symbol)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSelected) {
                    Icon(
                        painterResource(Res.drawable.check),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
