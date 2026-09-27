package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.builtInPropertiesNotYetAdded
import com.emberr.domain.database.customPropertiesNotShown
import com.emberr.domain.database.isPropertyNameTaken
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.customPropertyWithId
import com.emberr.domain.model.labelOf
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.properties.PropertyEditor
import com.emberr.presentation.properties.PropertyEditorState
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.iconResource
import com.emberr.presentation.shared.editor.components.DesktopCursor
import com.emberr.presentation.shared.editor.components.desktopPointerCursor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.minus
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.trash
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DatabaseHeaderRow(
    block: DatabaseBlock,
    rowCount: Int,
    inSelectionMode: Boolean,
    columnWidth: (DatabaseColumnTarget) -> Int,
    onColumnWidthDragged: (DatabaseColumnTarget, Int) -> Unit,
    onColumnWidthChosen: (DatabaseColumnTarget, Int) -> Unit,
    editor: DatabaseBlockEditor,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    val headerColumns = listOf<DatabaseColumnTarget>(DatabaseColumnTarget.NotesTitle) + block.columns

    Row(modifier = Modifier.height(IntrinsicSize.Max)) {
        headerColumns.forEach { column ->
            DatabaseColumnHeader(
                block = block,
                column = column,
                width = columnWidth(column),
                rowCount = rowCount,
                inSelectionMode = inSelectionMode,
                onWidthDragged = { onColumnWidthDragged(column, it) },
                onWidthChosen = { onColumnWidthChosen(column, it) },
                editor = editor,
                runAfterKeyboardCloses = runAfterKeyboardCloses
            )
        }
        DatabaseAddColumnButton(
            block = block,
            rowCount = rowCount,
            inSelectionMode = inSelectionMode,
            editor = editor,
            runAfterKeyboardCloses = runAfterKeyboardCloses
        )
    }
}

@Composable
private fun DatabaseColumnHeader(
    block: DatabaseBlock,
    column: DatabaseColumnTarget,
    width: Int,
    rowCount: Int,
    inSelectionMode: Boolean,
    onWidthDragged: (Int) -> Unit,
    onWidthChosen: (Int) -> Unit,
    editor: DatabaseBlockEditor,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    val isNotesColumn = column == DatabaseColumnTarget.NotesTitle
    val canOpenMenu = !inSelectionMode && (!isNotesColumn || !isDesktopPlatform)

    Box(
        modifier = Modifier
            .width(width.dp)
            .fillMaxHeight()
            .defaultMinSize(minHeight = DatabaseCellMinHeight)
            .databaseCellLines(lineColor)
            .clickable(enabled = canOpenMenu) { runAfterKeyboardCloses { showMenu = true } }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = DatabaseCellHorizontalPadding, vertical = DatabaseCellVerticalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(block.iconOf(column)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = block.labelOf(column),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isDesktopPlatform && !inSelectionMode) {
            DatabaseColumnResizeHandle(
                width = width,
                onWidthDragged = onWidthDragged,
                onWidthChosen = onWidthChosen,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        DatabaseColumnMenu(
            expanded = showMenu,
            block = block,
            column = column,
            width = width,
            rowCount = rowCount,
            onWidthChosen = onWidthChosen,
            editor = editor,
            onDismiss = { showMenu = false }
        )
    }
}

@Composable
private fun DatabaseColumnResizeHandle(
    width: Int,
    onWidthDragged: (Int) -> Unit,
    onWidthChosen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val latestWidth by rememberUpdatedState(width)
    var dragRemainder by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(8.dp)
            .desktopPointerCursor(DesktopCursor.RESIZE_HORIZONTAL)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragRemainder += with(density) { delta.toDp().value }
                    val wholeSteps = dragRemainder.toInt()
                    if (wholeSteps != 0) {
                        dragRemainder -= wholeSteps
                        onWidthDragged((latestWidth + wholeSteps).coerceIn(MinimumColumnWidth, MaximumColumnWidth))
                    }
                },
                onDragStopped = {
                    dragRemainder = 0f
                    onWidthChosen(latestWidth)
                }
            )
    )
}

@Composable
private fun DatabaseColumnMenu(
    expanded: Boolean,
    block: DatabaseBlock,
    column: DatabaseColumnTarget,
    width: Int,
    rowCount: Int,
    onWidthChosen: (Int) -> Unit,
    editor: DatabaseBlockEditor,
    onDismiss: () -> Unit
) {
    val label = block.labelOf(column)
    val customProperty = (column as? DatabaseColumnTarget.CustomProperty)?.let { block.customPropertyWithId(it.propertyId) }

    DatabaseMenu(expanded = expanded, title = label, onDismiss = onDismiss) { closeMenuAnd ->
        if (!isDesktopPlatform) {
            DatabaseColumnWidthStepper(width = width, onWidthChosen = onWidthChosen)
        }
        if (customProperty != null) {
            DatabaseMenuLayer(
                title = "Edit property",
                anchor = { openLayer ->
                    DatabaseMenuOption(
                        label = "Edit property",
                        icon = { DatabaseOptionIcon(Res.drawable.pen) },
                        onClick = openLayer
                    )
                }
            ) { closeLayerAnd ->
                DatabaseEditPropertyPage(
                    block = block,
                    property = customProperty,
                    rowCount = rowCount,
                    editor = editor,
                    onCancel = { closeLayerAnd { } },
                    closeAnd = { action -> closeLayerAnd { closeMenuAnd(action) } }
                )
            }
        }
        if (column != DatabaseColumnTarget.NotesTitle) {
            DatabaseMenuLayer(
                title = "Remove column",
                anchor = { openLayer ->
                    DatabaseMenuOption(
                        label = "Remove column",
                        icon = { DatabaseOptionIcon(Res.drawable.trash, tint = MaterialTheme.colorScheme.error) },
                        labelColor = MaterialTheme.colorScheme.error,
                        onClick = openLayer
                    )
                }
            ) { closeLayerAnd ->
                DatabaseRemoveColumnConfirmation(
                    label = label,
                    rowCount = rowCount,
                    onCancel = { closeLayerAnd { } },
                    onRemove = { closeLayerAnd { closeMenuAnd { editor.removeColumn(block.id, column) } } }
                )
            }
        }
    }
}

@Composable
private fun DatabaseRemoveColumnConfirmation(
    label: String,
    rowCount: Int,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val message = if (rowCount == 0) {
        "Remove $label?"
    } else {
        "Remove $label? Its values in ${rowCountText(rowCount)} will be deleted."
    }

    DatabaseMenuMessage(text = message)
    DatabaseMenuButtons(cancelText = "Cancel", onCancel = onCancel, confirmText = "Remove", onConfirm = onRemove)
}

@Composable
private fun DatabaseDeletePropertyConfirmation(
    name: String,
    rowCount: Int,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val message = if (rowCount == 0) {
        "Delete $name?"
    } else {
        "Delete $name? Its values in ${rowCountText(rowCount)} will be deleted."
    }

    DatabaseMenuMessage(text = message)
    DatabaseMenuButtons(cancelText = "Cancel", onCancel = onCancel, confirmText = "Delete", onConfirm = onDelete)
}

private fun rowCountText(rowCount: Int): String = if (rowCount == 1) "1 row" else "$rowCount rows"

@Composable
private fun DatabaseColumnWidthStepper(width: Int, onWidthChosen: (Int) -> Unit) {
    DatabaseMenuSectionLabel(text = "Column width")
    Row(
        modifier = Modifier.padding(horizontal = DatabaseMenuTextInset, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DatabaseWidthStepButton(
            icon = Res.drawable.minus,
            contentDescription = "Decrease column width",
            onClick = { onWidthChosen((width - ColumnWidthStep).coerceIn(MinimumColumnWidth, MaximumColumnWidth)) }
        )
        Text(
            text = "$width px",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 50.dp)
        )
        DatabaseWidthStepButton(
            icon = Res.drawable.plus,
            contentDescription = "Increase column width",
            onClick = { onWidthChosen((width + ColumnWidthStep).coerceIn(MinimumColumnWidth, MaximumColumnWidth)) }
        )
    }
}

@Composable
private fun DatabaseWidthStepButton(icon: DrawableResource, contentDescription: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(8.dp).size(18.dp)
        )
    }
}

@Composable
private fun DatabaseAddColumnButton(
    block: DatabaseBlock,
    rowCount: Int,
    inSelectionMode: Boolean,
    editor: DatabaseBlockEditor,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)

    Box(
        modifier = Modifier
            .width(DatabaseGutterWidth)
            .fillMaxHeight()
            .defaultMinSize(minHeight = DatabaseCellMinHeight)
            .databaseGutterLines(lineColor)
            .clickable(enabled = !inSelectionMode) { runAfterKeyboardCloses { showPicker = true } },
        contentAlignment = Alignment.Center
    ) {
        if (!inSelectionMode) {
            Icon(
                painter = painterResource(Res.drawable.plus),
                contentDescription = "Add column",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(17.dp)
            )
        }

        DatabaseMenu(expanded = showPicker, title = "Add property", onDismiss = { showPicker = false }) { closePickerAnd ->
            DatabaseAddColumnChoices(
                block = block,
                rowCount = rowCount,
                editor = editor,
                closePickerAnd = closePickerAnd
            )
        }
    }
}

@Composable
private fun DatabaseAddColumnChoices(
    block: DatabaseBlock,
    rowCount: Int,
    editor: DatabaseBlockEditor,
    closePickerAnd: (() -> Unit) -> Unit
) {
    block.builtInPropertiesNotYetAdded().forEach { propertyType ->
        DatabaseMenuOption(
            label = propertyType.label,
            icon = { DatabaseOptionIcon(propertyType.iconResource()) },
            onClick = { closePickerAnd { editor.addColumn(block.id, DatabaseColumnTarget.Property(propertyType)) } }
        )
    }

    val hiddenProperties = block.customPropertiesNotShown()
    if (hiddenProperties.isNotEmpty()) {
        DatabaseMenuSectionLabel(text = "In this database")
        hiddenProperties.forEach { property ->
            key(property.id) {
                DatabaseMenuLayer(
                    title = "Edit property",
                    anchor = { openLayer ->
                        DatabaseMenuOption(
                            label = property.name,
                            icon = { DatabaseOptionIcon(property.valueType.iconResource()) },
                            trailing = {
                                Icon(
                                    painter = painterResource(Res.drawable.pen),
                                    contentDescription = "Edit ${property.name}",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(onClick = openLayer)
                                        .padding(4.dp)
                                        .size(16.dp)
                                )
                            },
                            onClick = { closePickerAnd { editor.addColumn(block.id, DatabaseColumnTarget.CustomProperty(property.id)) } }
                        )
                    }
                ) { closeLayerAnd ->
                    DatabaseEditPropertyPage(
                        block = block,
                        property = property,
                        rowCount = rowCount,
                        editor = editor,
                        onCancel = { closeLayerAnd { } },
                        closeAnd = closeLayerAnd
                    )
                }
            }
        }
    }

    DatabaseMenuLayer(
        title = "New property",
        anchor = { openLayer ->
            DatabaseMenuOption(
                label = "New property",
                icon = { DatabaseOptionIcon(Res.drawable.plus) },
                onClick = openLayer
            )
        }
    ) { closeLayerAnd ->
        DatabaseNewPropertyPage(
            block = block,
            editor = editor,
            onCancel = { closeLayerAnd { } },
            closeAnd = { action -> closeLayerAnd { closePickerAnd(action) } }
        )
    }
}

@Composable
private fun DatabaseNewPropertyPage(
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    onCancel: () -> Unit,
    closeAnd: (() -> Unit) -> Unit
) {
    var state by remember {
        mutableStateOf(PropertyEditorState(propertyId = null, originalName = "", name = "", valueType = PropertyValueType.TEXT))
    }
    val isNameTaken = block.isPropertyNameTaken(state.name, ignoringPropertyId = null)

    DatabaseMenuContent {
        PropertyEditor(
            state = state,
            isNameTaken = isNameTaken,
            onNameChange = { state = state.copy(name = it) },
            onValueTypeChange = { state = state.copy(valueType = it) },
            onDeleteRequest = {},
            onDeleteCancel = {},
            onDeleteConfirm = {},
            onCancel = onCancel,
            onSave = {
                if (state.name.isNotBlank() && !isNameTaken) {
                    val name = state.name
                    val valueType = state.valueType
                    closeAnd { editor.createProperty(block.id, name, valueType) }
                }
            }
        )
    }
}

@Composable
private fun DatabaseEditPropertyPage(
    block: DatabaseBlock,
    property: DatabaseCustomProperty,
    rowCount: Int,
    editor: DatabaseBlockEditor,
    onCancel: () -> Unit,
    closeAnd: (() -> Unit) -> Unit
) {
    var state by remember(property.id) {
        mutableStateOf(
            PropertyEditorState(
                propertyId = property.id,
                originalName = property.name,
                name = property.name,
                valueType = property.valueType
            )
        )
    }
    val isNameTaken = block.isPropertyNameTaken(state.name, ignoringPropertyId = property.id)

    DatabaseMenuLayer(
        title = "Delete property",
        anchor = { openLayer ->
            DatabaseMenuContent {
                PropertyEditor(
                    state = state,
                    isNameTaken = isNameTaken,
                    onNameChange = { state = state.copy(name = it) },
                    onValueTypeChange = {},
                    onDeleteRequest = openLayer,
                    onDeleteCancel = {},
                    onDeleteConfirm = {},
                    onCancel = onCancel,
                    onSave = {
                        if (state.name.isNotBlank() && !isNameTaken) {
                            val newName = state.name
                            closeAnd {
                                if (newName.trim() != property.name) editor.renameProperty(block.id, property.id, newName)
                            }
                        }
                    }
                )
            }
        }
    ) { closeConfirmationAnd ->
        DatabaseDeletePropertyConfirmation(
            name = property.name,
            rowCount = rowCount,
            onCancel = { closeConfirmationAnd { } },
            onDelete = { closeConfirmationAnd { closeAnd { editor.deleteProperty(block.id, property.id) } } }
        )
    }
}
