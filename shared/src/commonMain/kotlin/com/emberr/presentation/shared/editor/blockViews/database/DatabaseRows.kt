package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.DatabaseRow
import com.emberr.domain.database.emptyCell
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.PropertyDateValue
import com.emberr.presentation.shared.editor.blockViews.PropertyTagsValue
import com.emberr.presentation.shared.editor.blockViews.PropertyTextValue
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.ellipsis
import emberr.shared.generated.resources.square_arrow_out_up_right
import emberr.shared.generated.resources.trash
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DatabaseRowItem(
    row: DatabaseRow,
    database: DatabaseBlock,
    columnWidth: (DatabaseColumnTarget) -> Int,
    inSelectionMode: Boolean,
    shouldTakeFocus: Boolean,
    historyStepsApplied: Int,
    editor: DatabaseBlockEditor,
    onOpenRow: (String) -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    Row(modifier = Modifier.height(IntrinsicSize.Max).defaultMinSize(minHeight = DatabaseCellMinHeight)) {
        key(historyStepsApplied) {
            DatabaseTitleCell(
                title = row.title,
                width = columnWidth(DatabaseColumnTarget.NotesTitle),
                inSelectionMode = inSelectionMode,
                shouldTakeFocus = shouldTakeFocus,
                onTitleChange = { editor.renameRow(database.id, row.noteId, it) },
                onFocusLost = { editor.finishTyping() },
                onFocusTaken = { editor.clearRowToFocus() },
                onOpen = { editor.openRow(row.noteId, onOpenRow) }
            )
        }
        database.columns.forEach { column ->
            DatabasePropertyCell(
                row = row,
                database = database,
                column = column,
                width = columnWidth(column),
                inSelectionMode = inSelectionMode,
                historyStepsApplied = historyStepsApplied,
                editor = editor,
                runAfterKeyboardCloses = runAfterKeyboardCloses
            )
        }
        DatabaseRowMenuButton(
            inSelectionMode = inSelectionMode,
            onOpen = { editor.openRow(row.noteId, onOpenRow) },
            onDelete = { editor.deleteRow(row.noteId) },
            runAfterKeyboardCloses = runAfterKeyboardCloses
        )
    }
}

@Composable
private fun DatabaseTitleCell(
    title: String,
    width: Int,
    inSelectionMode: Boolean,
    shouldTakeFocus: Boolean,
    onTitleChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    onFocusTaken: () -> Unit,
    onOpen: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember { mutableStateOf(TextFieldValue(title, TextRange(title.length))) }
    val textsSentButNotYetEchoed = remember { mutableListOf<String>() }
    var isFocused by remember { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground)

    LaunchedEffect(title) {
        if (fieldValue.text == title) {
            textsSentButNotYetEchoed.clear()
            return@LaunchedEffect
        }
        if (title in textsSentButNotYetEchoed) return@LaunchedEffect
        textsSentButNotYetEchoed.clear()
        fieldValue = TextFieldValue(title, TextRange(title.length))
    }

    LaunchedEffect(shouldTakeFocus) {
        if (!shouldTakeFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
        onFocusTaken()
    }

    Box(
        modifier = Modifier
            .width(width.dp)
            .fillMaxHeight()
            .defaultMinSize(minHeight = DatabaseCellMinHeight)
            .databaseCellLines(lineColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = DatabaseCellHorizontalPadding, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    val cleanedValue = newValue.copy(text = newValue.text.replace('\n', ' ').replace('\r', ' '))
                    val textChanged = cleanedValue.text != fieldValue.text
                    fieldValue = cleanedValue
                    if (textChanged) {
                        textsSentButNotYetEchoed += cleanedValue.text
                        onTitleChange(cleanedValue.text)
                    }
                },
                enabled = !inSelectionMode,
                textStyle = textStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = DatabaseCellVerticalPadding)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (isFocused && !focusState.isFocused) onFocusLost()
                        isFocused = focusState.isFocused
                    }
                    .onPreviewKeyEvent { event ->
                        val isEnterPress = event.key == Key.Enter && event.type == KeyEventType.KeyDown
                        if (isEnterPress) focusManager.clearFocus()
                        isEnterPress
                    },
                decorationBox = { innerTextField ->
                    Box {
                        if (fieldValue.text.isEmpty()) {
                            Text(text = "Untitled", style = textStyle.copy(color = MaterialTheme.colorScheme.outline))
                        }
                        innerTextField()
                    }
                }
            )

            if (!inSelectionMode) {
                Icon(
                    painter = painterResource(Res.drawable.square_arrow_out_up_right),
                    contentDescription = "Open row",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpen)
                        .padding(4.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DatabasePropertyCell(
    row: DatabaseRow,
    database: DatabaseBlock,
    column: DatabaseColumnTarget,
    width: Int,
    inSelectionMode: Boolean,
    historyStepsApplied: Int,
    editor: DatabaseBlockEditor,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    val cell = row.cell(column) ?: database.emptyCell(column, row.noteId, now = 0L)
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    val valueModifier = Modifier.fillMaxWidth()
    var hadFocus by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(width.dp)
            .fillMaxHeight()
            .defaultMinSize(minHeight = DatabaseCellMinHeight)
            .databaseCellLines(lineColor)
            .onFocusChanged { focusState ->
                if (hadFocus && !focusState.hasFocus) editor.finishTyping()
                hadFocus = focusState.hasFocus
            },
        contentAlignment = Alignment.CenterStart
    ) {
        when {
            cell == null -> Unit
            cell.valueType.holdsDate -> PropertyDateValue(
                block = cell,
                inSelectionMode = inSelectionMode,
                onUpdateDate = { editor.updateCellDate(database.id, row.noteId, column, it) },
                runAfterKeyboardCloses = runAfterKeyboardCloses,
                widthModifier = valueModifier
            )
            cell.valueType.holdsTags -> PropertyTagsValue(
                block = cell,
                inSelectionMode = inSelectionMode,
                onUpdateTags = { editor.updateCellTags(database.id, row.noteId, column, it) },
                runAfterKeyboardCloses = runAfterKeyboardCloses,
                widthModifier = valueModifier,
                tagTextStyle = MaterialTheme.typography.labelSmall
            )
            else -> key(historyStepsApplied) {
                PropertyTextValue(
                    block = cell,
                    inSelectionMode = inSelectionMode,
                    onUpdateText = { editor.updateCellText(database.id, row.noteId, column, it) },
                    widthModifier = valueModifier
                )
            }
        }
    }
}

@Composable
private fun DatabaseRowMenuButton(
    inSelectionMode: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)

    Box(
        modifier = Modifier
            .width(DatabaseGutterWidth)
            .fillMaxHeight()
            .defaultMinSize(minHeight = DatabaseCellMinHeight)
            .databaseGutterLines(lineColor),
        contentAlignment = Alignment.Center
    ) {
        if (!inSelectionMode) {
            Icon(
                painter = painterResource(Res.drawable.ellipsis),
                contentDescription = "Row options",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { runAfterKeyboardCloses { showMenu = true } }
                    .padding(6.dp)
                    .size(16.dp)
            )
        }

        DatabaseMenu(expanded = showMenu, title = "Row", onDismiss = { showMenu = false }) { closeAnd ->
            DatabaseMenuOption(
                label = "Open",
                icon = { DatabaseOptionIcon(Res.drawable.square_arrow_out_up_right) },
                onClick = { closeAnd(onOpen) }
            )
            DatabaseMenuOption(
                label = "Delete row",
                icon = { DatabaseOptionIcon(Res.drawable.trash, tint = MaterialTheme.colorScheme.error) },
                onClick = { closeAnd(onDelete) }
            )
        }
    }
}
