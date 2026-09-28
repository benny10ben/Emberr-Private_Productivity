package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberr.domain.database.activeView
import com.emberr.domain.database.applyFiltersAndSort
import com.emberr.domain.database.inManualOrder
import com.emberr.domain.database.visibleColumns
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.columnKey
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrHorizontalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.ui.theme.LocalEmberrFontStyle
import com.emberr.ui.theme.fontFamilyFor
import com.emberr.ui.theme.tableGridLineColor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_right
import emberr.shared.generated.resources.list_sort_descending
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.sliders_horizontal
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
private val DatabaseVerticalPadding = 12.dp
private val HeaderEndPadding = 10.dp
private val BoardSidePaddingInset = 6.dp
private val NewRowButtonColor = Color(0xFF4F5B8A)
private val HeaderButtonGap = 6.dp
internal val HeaderButtonInnerPadding = 8.dp
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
    val tableBorderColor = tableGridLineColor
    val liveColumnWidths = remember(block.columnWidths) { mutableStateMapOf<String, Int>() }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }

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

    val tableWidth = (columnWidth(DatabaseColumnTarget.NotesTitle) + block.visibleColumns().sumOf { columnWidth(it) }).dp + DatabaseGutterWidth
    val activeView = block.activeView()
    val rowCountCaption = if (block.showsRowCount) rowCountLabel(shownRowCount = visibleRows.size, totalRowCount = rows.size) else null

    LaunchedEffect(rowToFocus, activeView.type) {
        val newRowNoteId = rowToFocus ?: return@LaunchedEffect
        if (activeView.type == DatabaseViewType.TABLE) return@LaunchedEffect
        editor.clearRowToFocus()
        editor.openRow(newRowNoteId, onOpenRow)
    }

    Box {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = DatabaseVerticalPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DatabaseSidePadding, end = HeaderEndPadding, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DatabaseTitleField(
                    title = block.title,
                    inSelectionMode = inSelectionMode,
                    onTitleChange = { editor.setTitle(block.id, it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DatabaseSidePadding - HeaderButtonInnerPadding, end = HeaderEndPadding, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(HeaderButtonGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DatabaseViewTabs(
                    block = block,
                    editor = editor,
                    inSelectionMode = inSelectionMode,
                    runAfterKeyboardCloses = runAfterKeyboardCloses,
                    modifier = Modifier.weight(1f)
                )
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
                DatabaseHeaderButton(
                    label = "Settings",
                    icon = Res.drawable.sliders_horizontal,
                    isActive = false,
                    enabled = !inSelectionMode,
                    onClick = { runAfterKeyboardCloses { showSettingsMenu = true } }
                ) {
                    DatabaseSettingsMenu(
                        expanded = showSettingsMenu,
                        block = block,
                        editor = editor,
                        onDismiss = { showSettingsMenu = false }
                    )
                }
                DatabaseNewRowButton(
                    enabled = !inSelectionMode,
                    onNewRow = { editor.addRow(block.id) },
                    onOpenTemplates = { runAfterKeyboardCloses { showTemplateMenu = true } }
                ) {
                    DatabaseTemplateMenu(
                        expanded = showTemplateMenu,
                        block = block,
                        editor = editor,
                        onOpenTemplate = onOpenRow,
                        onDismiss = { showTemplateMenu = false }
                    )
                }
            }

            when (activeView.type) {
                DatabaseViewType.GALLERY -> {
                    DatabaseGallery(
                        block = block,
                        view = activeView,
                        rows = visibleRows,
                        inSelectionMode = inSelectionMode,
                        onOpenRow = { rowNoteId -> editor.openRow(rowNoteId, onOpenRow) },
                        modifier = Modifier.padding(horizontal = DatabaseSidePadding)
                    )
                    DatabaseNewRowLine(
                        showsNewButton = !inSelectionMode,
                        rowCountCaption = rowCountCaption,
                        onNewRow = { editor.addRow(block.id) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = DatabaseSidePadding)
                    )
                }
                DatabaseViewType.BOARD -> {
                    DatabaseBoard(
                        block = block,
                        view = activeView,
                        rows = visibleRows,
                        inSelectionMode = inSelectionMode,
                        editor = editor,
                        onOpenRow = onOpenRow,
                        runAfterKeyboardCloses = runAfterKeyboardCloses,
                        modifier = Modifier.padding(horizontal = DatabaseSidePadding - BoardSidePaddingInset)
                    )
                    if (rowCountCaption != null) {
                        DatabaseNewRowLine(
                            showsNewButton = false,
                            rowCountCaption = rowCountCaption,
                            onNewRow = {},
                            modifier = Modifier.fillMaxWidth().padding(horizontal = DatabaseSidePadding)
                        )
                    }
                }
                DatabaseViewType.TABLE -> {
                    val tableRows = remember(visibleRows, block.sort, activeView.manualRowOrder) {
                        if (block.sort == null) visibleRows.inManualOrder(activeView.manualRowOrder) else visibleRows
                    }
                    val shownRowIds = remember(tableRows) { tableRows.map { it.noteId } }
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
                                    tableRows.forEach { row ->
                                        key(row.noteId) {
                                            DatabaseRowItem(
                                                row = row,
                                                database = block,
                                                shownRowIds = shownRowIds,
                                                rowCount = rows.size,
                                                columnWidth = ::columnWidth,
                                                onColumnWidthChosen = ::chooseColumnWidth,
                                                showsIcon = activeView.showsIcon,
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

                            DatabaseCalculationRow(block = block, rows = visibleRows, columnWidth = ::columnWidth)

                            DatabaseNewRowLine(
                                showsNewButton = !inSelectionMode,
                                rowCountCaption = rowCountCaption,
                                onNewRow = { editor.addRow(block.id) },
                                modifier = Modifier.width(tableWidth)
                            )
                        }
                    }

                    EmberrHorizontalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.fillMaxWidth().padding(start = DatabaseSidePadding, end = DatabaseSidePadding, top = 4.dp),
                        showsWhenScrollbarsAreOff = true
                    )
                }
            }
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

    val titleStyle = TextStyle(
        fontFamily = fontFamilyFor(LocalEmberrFontStyle.current),
        fontSize = if (isDesktopPlatform) 24.sp else 20.sp,
        lineHeight = if (isDesktopPlatform) 32.sp else 26.sp,
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
                .padding(horizontal = HeaderButtonInnerPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDesktopPlatform) {
                Icon(painter = painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color, maxLines = 1)
            } else {
                Icon(painter = painterResource(icon), contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        menu()
    }
}

@Composable
private fun DatabaseNewRowButton(
    enabled: Boolean,
    onNewRow: () -> Unit,
    onOpenTemplates: () -> Unit,
    menu: @Composable () -> Unit
) {
    Box(modifier = Modifier.padding(start = HeaderButtonInnerPadding)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(NewRowButtonColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.plus),
                contentDescription = "New row",
                tint = Color.White,
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onNewRow)
                    .padding(horizontal = 7.dp, vertical = 5.dp)
                    .size(18.dp)
            )
            Icon(
                painter = painterResource(Res.drawable.chevron_right),
                contentDescription = "Templates",
                tint = Color.White,
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onOpenTemplates)
                    .padding(start = 3.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                    .rotate(90f)
                    .size(16.dp)
            )
        }
        menu()
    }
}

@Composable
private fun DatabaseNewRowLine(
    showsNewButton: Boolean,
    rowCountCaption: String?,
    onNewRow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (showsNewButton) {
            DatabaseAddRowButton(onClick = onNewRow)
        }
        if (rowCountCaption != null) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = rowCountCaption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 6.dp, end = 8.dp)
            )
        }
    }
}

private fun rowCountLabel(shownRowCount: Int, totalRowCount: Int): String =
    if (shownRowCount == totalRowCount) rowCountText(totalRowCount) else "$shownRowCount of ${rowCountText(totalRowCount)}"

@Composable
internal fun DatabaseAddRowButton(onClick: () -> Unit) {
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
