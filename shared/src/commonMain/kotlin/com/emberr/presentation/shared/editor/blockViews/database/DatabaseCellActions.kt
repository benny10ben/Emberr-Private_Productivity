package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.activeView
import com.emberr.domain.database.columnsInTableOrder
import com.emberr.domain.database.visibleColumnsInTableOrder
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCellStyle
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.labelOf
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.MenuAtTap
import com.emberr.presentation.shared.components.menuTapAnchor
import com.emberr.presentation.shared.components.rememberMenuTapAnchor
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_left
import emberr.shared.generated.resources.arrow_right
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.copy
import emberr.shared.generated.resources.move_left
import emberr.shared.generated.resources.move_right
import emberr.shared.generated.resources.square_check
import emberr.shared.generated.resources.trash
import org.jetbrains.compose.resources.DrawableResource

@Composable
internal fun DatabaseCellWithActions(
    block: DatabaseBlock,
    rowNoteId: String,
    column: DatabaseColumnTarget,
    style: DatabaseCellStyle,
    shownRowIds: List<String>,
    rowCount: Int,
    width: Int,
    onWidthChosen: (Int) -> Unit,
    inSelectionMode: Boolean,
    onSelectRow: () -> Unit,
    editor: DatabaseBlockEditor,
    runAfterKeyboardCloses: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    cell: @Composable () -> Unit
) {
    var showsActions by remember { mutableStateOf(false) }
    val tapAnchor = rememberMenuTapAnchor()
    val latestRunAfterKeyboardCloses by rememberUpdatedState(runAfterKeyboardCloses)
    val backgroundColor = databaseBackgroundColorNamed(style.backgroundColorName)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(if (backgroundColor != null) Modifier.background(backgroundColor) else Modifier)
            .menuTapAnchor(tapAnchor)
            .then(
                if (inSelectionMode) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectCellLongPress { latestRunAfterKeyboardCloses { showsActions = true } }
                    }
                }
            )
            .then(if (showsActions) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary) else Modifier)
    ) {
        cell()

        MenuAtTap(tapAnchor, menuWidth = DesktopDatabaseMenuMaxWidth) {
            DatabaseCellActionsMenu(
                expanded = showsActions,
                block = block,
                rowNoteId = rowNoteId,
                column = column,
                shownRowIds = shownRowIds,
                rowCount = rowCount,
                width = width,
                onWidthChosen = onWidthChosen,
                onSelectRow = onSelectRow,
                editor = editor,
                onDismiss = { showsActions = false }
            )
        }
    }
}

private suspend fun PointerInputScope.detectCellLongPress(onLongPress: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (currentEvent.buttons.isSecondaryPressed) return@awaitEachGesture
        awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }

        try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial)
            }
        } catch (_: PointerEventTimeoutCancellationException) {
            onLongPress()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun DatabaseCellActionsMenu(
    expanded: Boolean,
    block: DatabaseBlock,
    rowNoteId: String,
    column: DatabaseColumnTarget,
    shownRowIds: List<String>,
    rowCount: Int,
    width: Int,
    onWidthChosen: (Int) -> Unit,
    onSelectRow: () -> Unit,
    editor: DatabaseBlockEditor,
    onDismiss: () -> Unit
) {
    val view = block.activeView()
    val canPlaceRows = view.sorts.isEmpty()
    val rowPosition = shownRowIds.indexOf(rowNoteId)
    val rowAbove = shownRowIds.getOrNull(rowPosition - 1).takeIf { canPlaceRows }
    val rowBelow = shownRowIds.getOrNull(rowPosition + 1).takeIf { canPlaceRows }

    val isPropertyColumn = column != DatabaseColumnTarget.NotesTitle
    val visibleColumns = block.visibleColumnsInTableOrder()
    val columnPosition = visibleColumns.indexOf(column)
    val columnToTheLeft = visibleColumns.getOrNull(columnPosition - 1)
    val columnToTheRight = visibleColumns.getOrNull(columnPosition + 1)
    val allColumns = block.columnsInTableOrder()
    val columnAfterThisOne = allColumns.getOrNull(allColumns.indexOf(column) + 1)
    val canMoveSomething = rowAbove != null || rowBelow != null || columnToTheLeft != null || columnToTheRight != null

    DatabaseMenu(expanded = expanded, title = "Cell actions", onDismiss = onDismiss) { closeAnd ->
        if (canPlaceRows) {
            DatabaseMenuOption(
                label = "Insert row above",
                icon = { DatabaseOptionIcon(Res.drawable.arrow_up) },
                onClick = { closeAnd { editor.insertRow(block.id, view.id, shownRowIds, rowNoteId, isAfter = false) } }
            )
            DatabaseMenuOption(
                label = "Insert row below",
                icon = { DatabaseOptionIcon(Res.drawable.arrow_down) },
                onClick = { closeAnd { editor.insertRow(block.id, view.id, shownRowIds, rowNoteId, isAfter = true) } }
            )
        }
        DatabaseInsertColumnOption(
            label = "Insert column left",
            icon = Res.drawable.arrow_left,
            block = block,
            rowCount = rowCount,
            beforeColumn = column,
            editor = editor,
            closeMenuAnd = closeAnd
        )
        DatabaseInsertColumnOption(
            label = "Insert column right",
            icon = Res.drawable.arrow_right,
            block = block,
            rowCount = rowCount,
            beforeColumn = columnAfterThisOne,
            editor = editor,
            closeMenuAnd = closeAnd
        )

        if (canMoveSomething) {
            DatabaseMenuSectionDivider()
        }
        if (rowAbove != null) {
            DatabaseMenuOption(
                label = "Move row up",
                icon = { DatabaseOptionVectorIcon(Icons.Default.ArrowUpward) },
                onClick = { closeAnd { editor.moveRow(block.id, view.id, shownRowIds, rowNoteId, rowAbove, isAfter = false) } }
            )
        }
        if (rowBelow != null) {
            DatabaseMenuOption(
                label = "Move row down",
                icon = { DatabaseOptionVectorIcon(Icons.Default.ArrowDownward) },
                onClick = { closeAnd { editor.moveRow(block.id, view.id, shownRowIds, rowNoteId, rowBelow, isAfter = true) } }
            )
        }
        if (columnToTheLeft != null) {
            DatabaseMenuOption(
                label = "Move column left",
                icon = { DatabaseOptionIcon(Res.drawable.move_left) },
                onClick = { closeAnd { editor.moveColumnBefore(block.id, column, columnToTheLeft) } }
            )
        }
        if (columnToTheRight != null) {
            DatabaseMenuOption(
                label = "Move column right",
                icon = { DatabaseOptionIcon(Res.drawable.move_right) },
                onClick = { closeAnd { editor.moveColumnBefore(block.id, columnToTheRight, column) } }
            )
        }

        if (!isDesktopPlatform) {
            DatabaseMenuSectionDivider()
            DatabaseColumnWidthStepper(width = width, onWidthChosen = onWidthChosen)
        }

        DatabaseMenuSectionDivider()
        DatabaseStyleOptions(block = block, rowNoteId = rowNoteId, column = column, editor = editor)

        DatabaseMenuSectionDivider()
        DatabaseMenuOption(
            label = "Duplicate row",
            icon = { DatabaseOptionIcon(Res.drawable.copy) },
            onClick = { closeAnd { editor.duplicateRow(block.id, view.id, shownRowIds, rowNoteId) } }
        )
        DatabaseMenuOption(
            label = "Select row",
            icon = { DatabaseOptionIcon(Res.drawable.square_check) },
            onClick = { closeAnd(onSelectRow) }
        )
        DatabaseMenuOption(
            label = "Delete row",
            icon = { DatabaseOptionIcon(Res.drawable.trash, tint = MaterialTheme.colorScheme.error) },
            labelColor = MaterialTheme.colorScheme.error,
            onClick = { closeAnd { editor.deleteRow(rowNoteId) } }
        )
        if (isPropertyColumn) {
            DatabaseMenuLayer(
                title = "Remove column",
                showsCloseButton = false,
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
                    label = block.labelOf(column),
                    rowCount = rowCount,
                    onCancel = { closeLayerAnd { } },
                    onRemove = { closeLayerAnd { closeAnd { editor.removeColumn(block.id, column) } } }
                )
            }
        }
    }
}

@Composable
private fun DatabaseInsertColumnOption(
    label: String,
    icon: DrawableResource,
    block: DatabaseBlock,
    rowCount: Int,
    beforeColumn: DatabaseColumnTarget?,
    editor: DatabaseBlockEditor,
    closeMenuAnd: (() -> Unit) -> Unit
) {
    DatabaseMenuLayer(
        title = "Add property",
        anchor = { openLayer ->
            DatabaseMenuOption(label = label, icon = { DatabaseOptionIcon(icon) }, onClick = openLayer)
        }
    ) { closeLayerAnd ->
        DatabaseAddColumnChoices(
            block = block,
            rowCount = rowCount,
            editor = editor,
            closePickerAnd = { action -> closeLayerAnd { closeMenuAnd(action) } },
            beforeColumn = beforeColumn
        )
    }
}

@Composable
private fun DatabaseOptionVectorIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(18.dp)
    )
}
