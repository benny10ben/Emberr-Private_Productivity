@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.emberr.data.local.room.TagEntity
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.editor.EditorActions
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import org.jetbrains.compose.resources.painterResource

@Stable
class DatabaseSheetContext(
    val block: DatabaseBlock,
    val activeView: DatabaseView,
    val visibleColumns: List<DatabaseColumn>,
    val globalTags: List<TagEntity>,
    val actions: EditorActions,
    val state: DatabaseSheetState
) {
    val activeColumn: DatabaseColumn? get() = visibleColumns.find { it.id == state.activeColId }
}

internal fun DatabaseSheetContext.sheetTitleFor(sheet: DatabaseSheet): String? = when (sheet) {
    DatabaseSheet.CELL_OPTIONS -> "Cell Actions"
    DatabaseSheet.COLUMN_OPTIONS -> activeColumn?.name ?: "Column Options"
    DatabaseSheet.RENAME -> "Rename Column"
    DatabaseSheet.RENAME_VIEW -> "Rename View"
    DatabaseSheet.FORMULA -> "Edit Formula"
    DatabaseSheet.CURRENCY_SELECTION -> "Select Currency"
    DatabaseSheet.SORT -> "Sort by"
    DatabaseSheet.FILTER -> "Filter"
    DatabaseSheet.GROUP_BY -> "Group By"
    DatabaseSheet.CARD_SIZE -> "Card Size"
    DatabaseSheet.FILE_OPTIONS -> "Attached Files"
    DatabaseSheet.PRIORITY_SELECTION -> "Set Priority"
    DatabaseSheet.STATUS_SELECTION -> "Set Status"
    DatabaseSheet.AGGREGATION -> "Calculate"
    DatabaseSheet.TAG_SELECTION -> "Select Tag"
    DatabaseSheet.SAVE_AS_TEMPLATE -> "Save as Template"
    DatabaseSheet.ADD_VIEW -> "Add View"
    DatabaseSheet.TABLE_SETTINGS -> "Table Settings"
    else -> null
}

private fun parentSheetOnDesktop(sheet: DatabaseSheet): DatabaseSheet? = when (sheet) {
    DatabaseSheet.RENAME, DatabaseSheet.FORMULA, DatabaseSheet.CURRENCY_SELECTION ->
        DatabaseSheet.COLUMN_OPTIONS

    DatabaseSheet.FILTER, DatabaseSheet.SAVE_AS_TEMPLATE, DatabaseSheet.ADD_VIEW,
    DatabaseSheet.GROUP_BY, DatabaseSheet.CARD_SIZE ->
        DatabaseSheet.TABLE_SETTINGS

    else -> null
}

@Composable
internal fun SheetCancelAndConfirmButtons(
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().sheetSidePadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EmberrButtonSecondary(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
        EmberrButtonPrimary(text = confirmText, onClick = onConfirm, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun OptionSheetBody(context: DatabaseSheetContext, targetSheet: DatabaseSheet) {
    MuteRippleOnMobile {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isDesktopPlatform) {
                DesktopSheetHeader(context, targetSheet)
            }

            when (targetSheet) {
                DatabaseSheet.CELL_OPTIONS -> CellActionsSheet(context)
                DatabaseSheet.COLUMN_OPTIONS -> ColumnOptionsSheet(context)
                DatabaseSheet.RENAME -> RenameColumnSheet(context)
                DatabaseSheet.FORMULA -> EditFormulaSheet(context)
                DatabaseSheet.CURRENCY_SELECTION -> PickCurrencySheet(context)

                DatabaseSheet.RENAME_VIEW -> RenameViewSheet(context)
                DatabaseSheet.SAVE_AS_TEMPLATE -> SaveAsTemplateSheet(context)
                DatabaseSheet.ADD_VIEW -> AddViewSheet(context)
                DatabaseSheet.TABLE_SETTINGS -> TableSettingsSheet(context)
                DatabaseSheet.SORT -> SortSheet(context)
                DatabaseSheet.FILTER -> FilterSheet(context)
                DatabaseSheet.GROUP_BY -> GroupBySheet(context)
                DatabaseSheet.CARD_SIZE -> CardSizeSheet(context)

                DatabaseSheet.TAG_SELECTION -> PickTagsSheet(context)
                DatabaseSheet.FILE_OPTIONS -> AttachedFilesSheet(context)
                DatabaseSheet.PRIORITY_SELECTION -> PickPrioritySheet(context)
                DatabaseSheet.STATUS_SELECTION -> PickStatusSheet(context)
                DatabaseSheet.AGGREGATION -> CalculateSheet(context)

                DatabaseSheet.NONE -> Unit
            }
        }
    }
}

@Composable
private fun DesktopSheetHeader(context: DatabaseSheetContext, targetSheet: DatabaseSheet) {
    val backTarget = parentSheetOnDesktop(targetSheet)
    if (backTarget != null) {
        SheetMenuRow(painterResource(Res.drawable.chevron_left), "Back to Options") {
            context.state.open(backTarget)
        }
        SheetDivider()
    }

    val title = context.sheetTitleFor(targetSheet)
    if (!title.isNullOrBlank()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp, top = 8.dp).sheetSidePadding()
        )
    }
}
