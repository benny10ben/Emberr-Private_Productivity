package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.emberr.domain.database.applyFiltersAndSort
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.columnKey
import com.emberr.presentation.shared.components.EmberrHorizontalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.funnel
import emberr.shared.generated.resources.list_sort_descending
import emberr.shared.generated.resources.plus
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal const val MinimumColumnWidth = 80
internal const val MaximumColumnWidth = 600
internal const val ColumnWidthStep = 20
internal val DatabaseCellMinHeight = 44.dp
internal val DatabaseGutterWidth = 44.dp
internal val DatabaseCellHorizontalPadding = 12.dp
internal val DatabaseCellVerticalPadding = 9.dp
private val DatabaseSidePadding = 18.dp
private val HeaderEndPadding = 10.dp
private const val NotesColumnDefaultWidth = 240
private const val PropertyColumnDefaultWidth = 180

internal fun defaultColumnWidth(target: DatabaseColumnTarget): Int =
    if (target == DatabaseColumnTarget.NotesTitle) NotesColumnDefaultWidth else PropertyColumnDefaultWidth

internal fun Modifier.databaseCellLines(color: Color): Modifier = drawBehind {
    val lineWidth = 0.5.dp.toPx()
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), lineWidth)
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), lineWidth)
}

internal fun Modifier.databaseGutterLines(color: Color): Modifier = drawBehind {
    val lineWidth = 0.5.dp.toPx()
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), lineWidth)
}

@Composable
fun DatabaseBlockView(
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onOpenRow: (String) -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    val rows by remember(block.databaseId) { editor.rowsOf(block.databaseId) }.collectAsState(initial = emptyList())
    val visibleRows = remember(rows, block) { applyFiltersAndSort(rows, block) }
    val historyStepsApplied by editor.historyStepsApplied.collectAsState()
    val rowToFocus by editor.rowToFocus.collectAsState()
    val scrollState = rememberScrollState()
    val tableBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val liveColumnWidths = remember(block.columnWidths) { mutableStateMapOf<String, Int>() }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    fun savedColumnWidth(target: DatabaseColumnTarget): Int =
        block.columnWidths[target.columnKey] ?: defaultColumnWidth(target)

    fun columnWidth(target: DatabaseColumnTarget): Int =
        liveColumnWidths[target.columnKey] ?: savedColumnWidth(target)

    fun chooseColumnWidth(target: DatabaseColumnTarget, width: Int) {
        if (width == savedColumnWidth(target)) {
            liveColumnWidths.remove(target.columnKey)
            return
        }
        editor.setColumnWidth(block.id, target.columnKey, width)
    }

    Box {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DatabaseSidePadding, end = HeaderEndPadding, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DatabaseTitleField(
                    title = block.title,
                    inSelectionMode = inSelectionMode,
                    onTitleChange = { editor.setTitle(block.id, it) },
                    modifier = Modifier.weight(1f)
                )
                DatabaseHeaderButton(
                    label = if (block.filters.isEmpty()) "Filter" else "Filter · ${block.filters.size}",
                    icon = Res.drawable.funnel,
                    isActive = block.filters.isNotEmpty(),
                    enabled = !inSelectionMode,
                    onClick = { runAfterKeyboardCloses { showFilterMenu = true } }
                ) {
                    DatabaseFilterMenu(
                        expanded = showFilterMenu,
                        block = block,
                        editor = editor,
                        onDismiss = { showFilterMenu = false }
                    )
                }
                DatabaseHeaderButton(
                    label = "Sort",
                    icon = Res.drawable.list_sort_descending,
                    isActive = block.sort != null,
                    enabled = !inSelectionMode,
                    onClick = { runAfterKeyboardCloses { showSortMenu = true } }
                ) {
                    DatabaseSortMenu(
                        expanded = showSortMenu,
                        block = block,
                        editor = editor,
                        onDismiss = { showSortMenu = false }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .smoothWheelScroll(scrollState, horizontal = true)
            ) {
                Column(modifier = Modifier.padding(horizontal = DatabaseSidePadding)) {
                    Surface(
                        shape = RectangleShape,
                        color = Color.Transparent,
                        border = BorderStroke(0.6.dp, tableBorderColor)
                    ) {
                        Column {
                            DatabaseHeaderRow(
                                block = block,
                                rowCount = rows.size,
                                inSelectionMode = inSelectionMode,
                                columnWidth = ::columnWidth,
                                onColumnWidthDragged = { target, width -> liveColumnWidths[target.columnKey] = width },
                                onColumnWidthChosen = ::chooseColumnWidth,
                                editor = editor,
                                runAfterKeyboardCloses = runAfterKeyboardCloses
                            )
                            visibleRows.forEach { row ->
                                key(row.noteId) {
                                    DatabaseRowItem(
                                        row = row,
                                        database = block,
                                        columnWidth = ::columnWidth,
                                        inSelectionMode = inSelectionMode,
                                        shouldTakeFocus = rowToFocus == row.noteId,
                                        historyStepsApplied = historyStepsApplied,
                                        editor = editor,
                                        onOpenRow = onOpenRow,
                                        runAfterKeyboardCloses = runAfterKeyboardCloses
                                    )
                                }
                            }
                        }
                    }

                    if (!inSelectionMode) {
                        DatabaseAddRowButton(onClick = { editor.addRow(block.id) })
                    }
                }
            }

            EmberrHorizontalScrollbar(
                scrollState = scrollState,
                modifier = Modifier.fillMaxWidth().padding(start = DatabaseSidePadding, end = DatabaseSidePadding, top = 4.dp)
            )
        }

        if (inSelectionMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleSelection
                    )
            )
        }
    }
}

@Composable
private fun DatabaseTitleField(
    title: String,
    inSelectionMode: Boolean,
    onTitleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(title, TextRange(title.length))) }

    LaunchedEffect(title) {
        if (fieldValue.text != title) fieldValue = TextFieldValue(title, TextRange(title.length))
    }

    val titleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )

    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            val cleanedValue = newValue.copy(text = newValue.text.replace('\n', ' ').replace('\r', ' '))
            val textChanged = cleanedValue.text != fieldValue.text
            fieldValue = cleanedValue
            if (textChanged) onTitleChange(cleanedValue.text)
        },
        enabled = !inSelectionMode,
        textStyle = titleStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier.onPreviewKeyEvent { event ->
            val isEnterPress = event.key == Key.Enter && event.type == KeyEventType.KeyDown
            if (isEnterPress) focusManager.clearFocus()
            isEnterPress
        },
        decorationBox = { innerTextField ->
            Box {
                if (fieldValue.text.isEmpty()) {
                    Text(text = "Untitled", style = titleStyle.copy(color = MaterialTheme.colorScheme.outline))
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun DatabaseHeaderButton(
    label: String,
    icon: DrawableResource,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    menu: @Composable () -> Unit
) {
    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1)
        }
        menu()
    }
}

@Composable
private fun DatabaseAddRowButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.plus),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(text = "New", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
