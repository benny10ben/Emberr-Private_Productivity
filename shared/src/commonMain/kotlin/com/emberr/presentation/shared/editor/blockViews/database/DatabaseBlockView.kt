package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.emberr.domain.database.frozenColumns
import com.emberr.domain.database.inManualOrder
import com.emberr.domain.database.loadedRows
import com.emberr.domain.database.matchingSearch
import com.emberr.domain.database.visibleColumns
import com.emberr.domain.database.visibleColumnsInTableOrder
import com.emberr.domain.database.withFormulaResults
import com.emberr.domain.database.withSharedSettingsFrom
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.columnKey
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrHorizontalScrollbar
import com.emberr.presentation.shared.components.horizontalScrollFromBackAndForwardButtons
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.DatabaseSettingsState
import com.emberr.ui.theme.LocalEmberrFontStyle
import com.emberr.ui.theme.fontFamilyFor
import com.emberr.ui.theme.tableGridLineColor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.chevron_down
import emberr.shared.generated.resources.chevron_right
import emberr.shared.generated.resources.list_sort_descending
import emberr.shared.generated.resources.lock
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.search
import emberr.shared.generated.resources.sliders_horizontal
import emberr.shared.generated.resources.x
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal const val MinimumColumnWidth = 80
internal const val MaximumColumnWidth = 600
internal const val ColumnWidthStep = 20
internal val DatabaseCellMinHeight = 44.dp
internal val DatabaseGutterWidth = 44.dp
internal val DatabaseCellHorizontalPadding = 12.dp
internal val DatabaseCellVerticalPadding = 9.dp
internal val DatabaseSidePadding = 18.dp
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
    val lineWidth = DatabaseTableBorderWidth.toPx()
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), lineWidth)
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), lineWidth)
}

internal fun Modifier.databaseGutterLines(color: Color): Modifier = drawBehind {
    val lineWidth = DatabaseTableBorderWidth.toPx()
    drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), lineWidth)
}

private fun Modifier.databaseTableOutline(color: Color, drawsBottomLine: Boolean): Modifier = drawWithContent {
    drawContent()
    val lineWidth = DatabaseTableBorderWidth.toPx()
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), lineWidth)
    drawLine(color, Offset(0f, 0f), Offset(0f, size.height), lineWidth)
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), lineWidth)
    if (drawsBottomLine) drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), lineWidth)
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
    val settingsState by remember(block.databaseId) { editor.settingsShownFor(block.databaseId) }
        .collectAsState(initial = DatabaseSettingsState.Loading)

    when (val shownSettings = settingsState) {
        DatabaseSettingsState.Loading -> Unit
        DatabaseSettingsState.Missing -> Text(
            text = "This database was deleted or has not synced to this device yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DatabaseSidePadding, vertical = DatabaseVerticalPadding)
        )
        is DatabaseSettingsState.Found -> {
            val shownBlock = remember(block, shownSettings.settings) { block.withSharedSettingsFrom(shownSettings.settings) }
            DatabaseBlockContent(
                block = shownBlock,
                editor = editor,
                inSelectionMode = inSelectionMode,
                onToggleSelection = onToggleSelection,
                onOpenRow = onOpenRow,
                runAfterKeyboardCloses = runAfterKeyboardCloses
            )
        }
    }
}

@Composable
private fun DatabaseBlockContent(
    block: DatabaseBlock,
    editor: DatabaseBlockEditor,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onOpenRow: (String) -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    val savedRows by remember(block.databaseId) { editor.rowsOf(block.databaseId) }.collectAsState(initial = emptyList())
    val rows = remember(savedRows, block) { savedRows.withFormulaResults(block) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    val visibleRows = remember(rows, block, searchText) { applyFiltersAndSort(rows, block).matchingSearch(searchText, block) }
    val historyStepsApplied by editor.historyStepsApplied.collectAsState()
    val rowToFocus by editor.rowToFocus.collectAsState()
    val scrollState = rememberScrollState()
    val columnDragState = remember { DatabaseColumnDragState() }
    var tableViewportWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val tableBorderColor = tableGridLineColor
    val liveColumnWidths = remember(block.columnWidths) { mutableStateMapOf<String, Int>() }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }
    val rowSelection = remember(block.databaseId) { DatabaseRowSelection() }
    val newRowIds = remember(block.databaseId) { mutableStateListOf<String>() }

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

    val selectionColumnWidth = if (rowSelection.isSelecting) DatabaseSelectionColumnWidth else 0.dp
    val tableWidth = (columnWidth(DatabaseColumnTarget.NotesTitle) + block.visibleColumns().sumOf { columnWidth(it) }).dp +
        DatabaseGutterWidth + selectionColumnWidth
    val activeView = block.activeView()
    var loadedRowCount by remember(block.databaseId, activeView.id, activeView.loadLimit) { mutableIntStateOf(activeView.loadLimit) }
    val frozenColumns = block.frozenColumns()
    val blocksEdits = inSelectionMode || block.isLocked
    val rowCountCaption = if (block.showsRowCount) rowCountLabel(shownRowCount = visibleRows.size, totalRowCount = rows.size) else null

    LaunchedEffect(columnDragState.isDragging) {
        if (!columnDragState.isDragging) return@LaunchedEffect
        val tableStartPx = with(density) { DatabaseSidePadding.toPx() }
        val edgeWidthPx = with(density) { ColumnAutoScrollEdgeWidth.toPx() }
        val maxStepPx = with(density) { ColumnAutoScrollMaxStep.toPx() }
        while (true) {
            withFrameNanos { }
            val pointerInViewport = columnDragState.pointerX + tableStartPx - scrollState.value
            val step = columnAutoScrollStep(pointerInViewport, tableViewportWidth.toFloat(), edgeWidthPx, maxStepPx)
            if (step != 0f) columnDragState.pointerX += scrollState.scrollBy(step)
        }
    }

    LaunchedEffect(activeView.id, block.isLocked) {
        rowSelection.clear()
    }

    LaunchedEffect(rowToFocus) {
        val newRowNoteId = rowToFocus ?: return@LaunchedEffect
        isSearchOpen = false
        searchText = ""
        if (newRowNoteId !in newRowIds) newRowIds += newRowNoteId
    }

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
                    inSelectionMode = blocksEdits,
                    onTitleChange = { editor.setTitle(block.id, it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DatabaseSidePadding, end = HeaderEndPadding, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(HeaderButtonGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DatabaseViewTabs(
                    block = block,
                    editor = editor,
                    inSelectionMode = inSelectionMode,
                    isLocked = block.isLocked,
                    runAfterKeyboardCloses = runAfterKeyboardCloses,
                    modifier = Modifier.weight(1f)
                )
                DatabaseHeaderButton(
                    label = "Search",
                    icon = Res.drawable.search,
                    isActive = searchText.isNotBlank(),
                    enabled = !inSelectionMode,
                    onClick = {
                        searchText = ""
                        isSearchOpen = !isSearchOpen
                    }
                ) {}
                if (block.isLocked) {
                    DatabaseHeaderButton(
                        label = "Locked",
                        icon = Res.drawable.lock,
                        isActive = true,
                        enabled = !inSelectionMode,
                        onClick = { editor.setLocked(block.id, false) }
                    ) {}
                } else {
                    DatabaseHeaderButton(
                        label = "Sort",
                        icon = Res.drawable.list_sort_descending,
                        isActive = activeView.sorts.isNotEmpty(),
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
            }

            if (isSearchOpen) {
                DatabaseSearchField(
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    onClose = {
                        isSearchOpen = false
                        searchText = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = DatabaseSidePadding, end = HeaderEndPadding, bottom = 8.dp)
                )
            }

            val orderedRows = remember(visibleRows, activeView.sorts, activeView.manualRowOrder) {
                if (activeView.sorts.isEmpty()) visibleRows.inManualOrder(activeView.manualRowOrder) else visibleRows
            }

            when (activeView.type) {
                DatabaseViewType.GALLERY -> {
                    val galleryRows = orderedRows.loadedRows(loadedRowCount, newRowIds)
                    DatabaseGallery(
                        block = block,
                        view = activeView,
                        rows = galleryRows,
                        inSelectionMode = inSelectionMode,
                        canReorder = activeView.sorts.isEmpty() && !blocksEdits,
                        onOpenRow = { rowNoteId -> editor.openRow(rowNoteId, onOpenRow) },
                        onMoveRow = { rowNoteId, nextToRowId, isAfter ->
                            editor.moveRow(block.id, activeView.id, orderedRows.map { it.noteId }, rowNoteId, nextToRowId, isAfter)
                        },
                        modifier = Modifier.padding(horizontal = DatabaseSidePadding)
                    )
                    if (galleryRows.size < orderedRows.size) {
                        DatabaseLoadMoreButton(
                            onClick = { loadedRowCount += activeView.loadLimit },
                            modifier = Modifier.padding(start = DatabaseSidePadding, top = 6.dp)
                        )
                    }
                    DatabaseNewRowLine(
                        showsNewButton = !blocksEdits,
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
                        newRowIds = newRowIds,
                        inSelectionMode = inSelectionMode,
                        isLocked = block.isLocked,
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
                    val loadedTableRows = orderedRows.loadedRows(loadedRowCount, newRowIds)
                    val shownRowIds = remember(orderedRows) { orderedRows.map { it.noteId } }
                    LaunchedEffect(shownRowIds) {
                        rowSelection.keepOnly(shownRowIds)
                    }
                    if (rowSelection.isSelecting) {
                        DatabaseSelectionBar(
                            block = block,
                            selectedRowIds = shownRowIds.filter { rowSelection.isSelected(it) },
                            shownRowIds = shownRowIds,
                            editor = editor,
                            onClearSelection = rowSelection::clear,
                            runAfterKeyboardCloses = runAfterKeyboardCloses,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = DatabaseSidePadding, end = HeaderEndPadding, bottom = 8.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { tableViewportWidth = it.width }
                            .horizontalScrollFromBackAndForwardButtons(scrollState)
                            .horizontalScroll(scrollState)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = DatabaseSidePadding)) {
                            Surface(
                                shape = RectangleShape,
                                color = Color.Transparent,
                                modifier = Modifier.databaseTableOutline(
                                    color = tableBorderColor,
                                    drawsBottomLine = loadedTableRows.size < orderedRows.size
                                )
                            ) {
                                Column(
                                    modifier = Modifier.columnDropLine(
                                        dragState = columnDragState,
                                        columns = block.visibleColumnsInTableOrder(),
                                        lineColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    DatabaseHeaderRow(
                                        block = block,
                                        rowCount = rows.size,
                                        inSelectionMode = blocksEdits,
                                        columnWidth = ::columnWidth,
                                        onColumnWidthDragged = { target, width -> liveColumnWidths[target.columnKey] = width },
                                        onColumnWidthChosen = ::chooseColumnWidth,
                                        dragState = columnDragState,
                                        frozenColumns = frozenColumns,
                                        tableScrollState = scrollState,
                                        rowSelection = rowSelection,
                                        shownRowIds = shownRowIds,
                                        editor = editor,
                                        runAfterKeyboardCloses = runAfterKeyboardCloses
                                    )
                                    loadedTableRows.forEach { row ->
                                        key(row.noteId) {
                                            DatabaseRowItem(
                                                row = row,
                                                database = block,
                                                shownRowIds = shownRowIds,
                                                rowCount = rows.size,
                                                columnWidth = ::columnWidth,
                                                onColumnWidthChosen = ::chooseColumnWidth,
                                                showsIcon = activeView.showsIcon,
                                                wrapsText = activeView.wrapsCellText,
                                                frozenColumns = frozenColumns,
                                                tableScrollState = scrollState,
                                                rowSelection = rowSelection,
                                                inSelectionMode = inSelectionMode,
                                                isLocked = block.isLocked,
                                                shouldTakeFocus = rowToFocus == row.noteId,
                                                historyStepsApplied = historyStepsApplied,
                                                columnDragState = columnDragState,
                                                editor = editor,
                                                onOpenRow = onOpenRow,
                                                runAfterKeyboardCloses = runAfterKeyboardCloses
                                            )
                                        }
                                    }
                                    if (loadedTableRows.size < orderedRows.size) {
                                        DatabaseLoadMoreButton(
                                            onClick = { loadedRowCount += activeView.loadLimit },
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            DatabaseCalculationRow(
                                block = block,
                                rows = visibleRows,
                                columnWidth = ::columnWidth,
                                columnDragState = columnDragState,
                                frozenColumns = frozenColumns,
                                tableScrollState = scrollState,
                                showsSelectionColumn = rowSelection.isSelecting
                            )

                            DatabaseNewRowLine(
                                showsNewButton = !blocksEdits,
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
private fun DatabaseSearchField(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val isEscapePress = event.key == Key.Escape && event.type == KeyEventType.KeyDown
                    if (isEscapePress) onClose()
                    isEscapePress
                },
            decorationBox = { innerTextField ->
                Box {
                    if (searchText.isEmpty()) {
                        Text(text = "Search this database", style = textStyle.copy(color = MaterialTheme.colorScheme.outline))
                    }
                    innerTextField()
                }
            }
        )
        Icon(
            painter = painterResource(Res.drawable.x),
            contentDescription = "Close search",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClose)
                .padding(4.dp)
                .size(14.dp)
        )
    }
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
                painter = painterResource(Res.drawable.chevron_down),
                contentDescription = "Templates",
                tint = Color.White,
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onOpenTemplates)
                    .padding(start = 5.dp, end = 4.dp, top = 6.dp)
                    .size(22.dp)
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
internal fun DatabaseLoadMoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.arrow_down),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(text = "Load more", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
    }
}

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
