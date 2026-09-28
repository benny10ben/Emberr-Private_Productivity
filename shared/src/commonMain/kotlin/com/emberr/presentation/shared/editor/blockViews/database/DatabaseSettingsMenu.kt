package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.activeView
import com.emberr.domain.database.builtInPropertiesNotYetAdded
import com.emberr.domain.database.canGroupBy
import com.emberr.domain.database.calculationsFor
import com.emberr.domain.database.columnWithKey
import com.emberr.domain.database.customPropertiesNotShown
import com.emberr.domain.database.groupByColumn
import com.emberr.domain.database.groupableColumns
import com.emberr.domain.database.groupedBy
import com.emberr.domain.database.valueTypesThatCanGroupABoard
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCardSize
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseDateGrouping
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.valueTypeOf
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.iconResource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.calendar_day
import emberr.shared.generated.resources.eye3
import emberr.shared.generated.resources.ghost_smile
import emberr.shared.generated.resources.hash
import emberr.shared.generated.resources.image
import emberr.shared.generated.resources.kanban
import emberr.shared.generated.resources.maximize_2
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.sigma

@Composable
internal fun DatabaseSettingsMenu(
    expanded: Boolean,
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    onDismiss: () -> Unit
) {
    val activeView = block.activeView()
    val hiddenColumnCount = block.columns.count { it.columnKey in activeView.hiddenColumnKeys }

    DatabaseMenu(expanded = expanded, title = "Database settings", onDismiss = onDismiss) { _ ->
        DatabaseMenuSectionLabel(text = "${activeView.name.ifBlank { activeView.type.label }} view")
        DatabaseMenuOption(
            label = "Show icon",
            icon = { DatabaseOptionIcon(Res.drawable.ghost_smile) },
            trailing = { DatabaseSettingSwitch(isOn = activeView.showsIcon) },
            onClick = { editor.setViewShowsIcon(block.id, activeView.id, !activeView.showsIcon) }
        )
        if (activeView.type != DatabaseViewType.TABLE) {
            DatabaseMenuOption(
                label = "Show cover image",
                icon = { DatabaseOptionIcon(Res.drawable.image) },
                trailing = { DatabaseSettingSwitch(isOn = activeView.showsCoverImage) },
                onClick = { editor.setViewShowsCoverImage(block.id, activeView.id, !activeView.showsCoverImage) }
            )
        }
        if (activeView.type == DatabaseViewType.BOARD) {
            DatabaseBoardGroupingOptions(block = block, view = activeView, editor = editor)
        }
        if (activeView.type != DatabaseViewType.TABLE) {
            DatabaseMenuLayer(
                title = "Card size",
                anchor = { openLayer ->
                    DatabaseMenuOption(
                        label = "Card size",
                        icon = { DatabaseOptionIcon(Res.drawable.maximize_2) },
                        trailing = {
                            Text(
                                text = activeView.cardSize.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        },
                        onClick = openLayer
                    )
                }
            ) { closeLayerAnd ->
                DatabaseCardSize.entries.forEach { cardSize ->
                    DatabaseMenuOption(
                        label = cardSize.label,
                        isSelected = cardSize == activeView.cardSize,
                        onClick = { closeLayerAnd { editor.setViewCardSize(block.id, activeView.id, cardSize) } }
                    )
                }
            }
        }

        DatabaseMenuLayer(
            title = "Show / hide columns",
            anchor = { openLayer ->
                DatabaseMenuOption(
                    label = "Show / hide columns",
                    icon = { DatabaseOptionIcon(Res.drawable.eye3) },
                    trailing = if (hiddenColumnCount == 0) null else {
                        {
                            Text(
                                text = "$hiddenColumnCount hidden",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1
                            )
                        }
                    },
                    onClick = openLayer
                )
            }
        ) { _ ->
            if (block.columns.isEmpty()) {
                DatabaseMenuMessage(text = "No columns to show or hide yet", color = MaterialTheme.colorScheme.outline)
            }
            block.columns.forEach { column ->
                key(column.columnKey) {
                    val isShown = column.columnKey !in activeView.hiddenColumnKeys
                    DatabaseMenuOption(
                        label = block.labelOf(column),
                        icon = { DatabaseOptionIcon(block.iconOf(column)) },
                        trailing = { DatabaseSettingSwitch(isOn = isShown) },
                        onClick = { editor.setColumnShown(block.id, activeView.id, column, !isShown) }
                    )
                }
            }
        }

        DatabaseMenuSectionLabel(text = "All views")
        DatabaseMenuOption(
            label = "Row count",
            icon = { DatabaseOptionIcon(Res.drawable.hash) },
            trailing = { DatabaseSettingSwitch(isOn = block.showsRowCount) },
            onClick = { editor.setShowsRowCount(block.id, !block.showsRowCount) }
        )
    }
}

@Composable
private fun DatabaseBoardGroupingOptions(block: DatabaseBlock, view: DatabaseView, editor: DatabaseBlockEditor) {
    val groupColumn = block.groupByColumn(view)
    val groupValueType = groupColumn?.let { block.valueTypeOf(it) }

    DatabaseMenuLayer(
        title = "Group by",
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = "Group by",
                icon = { DatabaseOptionIcon(Res.drawable.kanban) },
                trailing = if (groupColumn == null) null else { { DatabaseSettingValueText(text = block.labelOf(groupColumn)) } },
                onClick = openLayer
            )
        }
    ) { closeLayerAnd ->
        block.groupableColumns().forEach { column ->
            key(column.columnKey) {
                DatabaseMenuOption(
                    label = block.labelOf(column),
                    icon = { DatabaseOptionIcon(block.iconOf(column)) },
                    isSelected = column == groupColumn,
                    onClick = { closeLayerAnd { editor.changeView(block.id, view.id) { it.groupedBy(column.columnKey) } } }
                )
            }
        }

        val builtInPropertiesToAdd = block.builtInPropertiesNotYetAdded().filter { canGroupBy(it.valueType) }
        val customPropertiesToShow = block.customPropertiesNotShown().filter { canGroupBy(it.valueType) }
        if (builtInPropertiesToAdd.isNotEmpty() || customPropertiesToShow.isNotEmpty()) {
            DatabaseMenuSectionLabel(text = "Add a property")
        }
        builtInPropertiesToAdd.forEach { propertyType ->
            DatabaseMenuOption(
                label = propertyType.label,
                icon = { DatabaseOptionIcon(propertyType.iconResource()) },
                onClick = {
                    closeLayerAnd { editor.addColumn(block.id, DatabaseColumnTarget.Property(propertyType), viewIdToGroupByIt = view.id) }
                }
            )
        }
        customPropertiesToShow.forEach { property ->
            key(property.id) {
                DatabaseMenuOption(
                    label = property.name,
                    icon = { DatabaseOptionIcon(property.valueType.iconResource()) },
                    onClick = {
                        closeLayerAnd { editor.addColumn(block.id, DatabaseColumnTarget.CustomProperty(property.id), viewIdToGroupByIt = view.id) }
                    }
                )
            }
        }

        DatabaseMenuLayer(
            title = "New property",
            anchor = { openNewProperty ->
                DatabaseMenuOption(
                    label = "New property",
                    icon = { DatabaseOptionIcon(Res.drawable.plus) },
                    onClick = openNewProperty
                )
            }
        ) { closeNewPropertyAnd ->
            DatabaseNewPropertyPage(
                block = block,
                editor = editor,
                onCancel = { closeNewPropertyAnd { } },
                closeAnd = { action -> closeNewPropertyAnd { closeLayerAnd(action) } },
                allowedValueTypes = valueTypesThatCanGroupABoard,
                viewIdToGroupByIt = view.id
            )
        }
    }

    if (groupValueType?.holdsDate == true) {
        DatabaseMenuLayer(
            title = "Group dates by",
            anchor = { openLayer ->
                DatabaseMenuOption(
                    label = "Group dates by",
                    icon = { DatabaseOptionIcon(Res.drawable.calendar_day) },
                    trailing = { DatabaseSettingValueText(text = view.dateGrouping.label) },
                    onClick = openLayer
                )
            }
        ) { closeLayerAnd ->
            DatabaseDateGrouping.entries.forEach { dateGrouping ->
                DatabaseMenuOption(
                    label = dateGrouping.label,
                    isSelected = dateGrouping == view.dateGrouping,
                    onClick = {
                        closeLayerAnd {
                            editor.changeView(block.id, view.id) {
                                it.copy(dateGrouping = dateGrouping, hiddenGroupKeys = emptyList(), collapsedGroupKeys = emptyList())
                            }
                        }
                    }
                )
            }
        }
    }

    DatabaseMenuOption(
        label = "Hide empty groups",
        icon = { DatabaseOptionIcon(Res.drawable.eye3) },
        trailing = { DatabaseSettingSwitch(isOn = view.hidesEmptyGroups) },
        onClick = { editor.changeView(block.id, view.id) { it.copy(hidesEmptyGroups = !it.hidesEmptyGroups) } }
    )

    DatabaseGroupCalculationOption(block = block, view = view, editor = editor)
}

@Composable
private fun DatabaseGroupCalculationOption(block: DatabaseBlock, view: DatabaseView, editor: DatabaseBlockEditor) {
    val calculationColumn = block.columnWithKey(view.groupCalculationColumnKey)
    val calculation = view.groupCalculation
    val columnsToCalculate = listOf<DatabaseColumnTarget>(DatabaseColumnTarget.NotesTitle) + block.columns

    DatabaseMenuLayer(
        title = "Group calculation",
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = "Group calculation",
                icon = { DatabaseOptionIcon(Res.drawable.sigma) },
                trailing = if (calculationColumn == null || calculation == null) null else {
                    { DatabaseSettingValueText(text = "${calculation.shortLabel} · ${block.labelOf(calculationColumn)}") }
                },
                onClick = openLayer
            )
        }
    ) { closeCalculationAnd ->
        DatabaseMenuOption(
            label = "Count only",
            isSelected = calculation == null,
            onClick = {
                closeCalculationAnd {
                    editor.changeView(block.id, view.id) { it.copy(groupCalculationColumnKey = null, groupCalculation = null) }
                }
            }
        )
        columnsToCalculate.forEach { column ->
            key(column.columnKey) {
                val valueType = block.valueTypeOf(column) ?: PropertyValueType.TEXT
                DatabaseMenuLayer(
                    title = block.labelOf(column),
                    anchor = { openColumn ->
                        DatabaseMenuOption(
                            label = block.labelOf(column),
                            icon = { DatabaseOptionIcon(block.iconOf(column)) },
                            isSelected = column == calculationColumn && calculation != null,
                            onClick = openColumn
                        )
                    }
                ) { closeColumnAnd ->
                    calculationsFor(valueType).forEach { option ->
                        DatabaseMenuOption(
                            label = option.label,
                            isSelected = column == calculationColumn && option == calculation,
                            onClick = {
                                closeColumnAnd {
                                    closeCalculationAnd {
                                        editor.changeView(block.id, view.id) {
                                            it.copy(groupCalculationColumnKey = column.columnKey, groupCalculation = option)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatabaseSettingValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        maxLines = 1
    )
}

@Composable
private fun DatabaseSettingSwitch(isOn: Boolean) {
    Box(modifier = Modifier.size(width = 40.dp, height = 24.dp), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked = isOn,
                onCheckedChange = null,
                modifier = Modifier.scale(0.75f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.surface,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
