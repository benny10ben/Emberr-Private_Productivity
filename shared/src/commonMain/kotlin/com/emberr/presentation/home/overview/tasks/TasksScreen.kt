package com.emberr.presentation.home.overview.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.compose.viewmodel.koinViewModel
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.util.eventbus.WidgetComposeRequest
import com.emberr.domain.util.eventbus.WidgetComposeRequestBus
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.editor.BlockSelectionMenuContent
import com.emberr.presentation.shared.editor.BlockSelectionPill
import com.emberr.presentation.shared.editor.EditorScreen
import com.emberr.presentation.shared.editor.EditorActions
import com.emberr.presentation.shared.editor.FocusRequest
import dev.chrisbanes.haze.HazeState
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.circle_plus
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onNavigateBack: () -> Unit,
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onNavigateToEditor: (String) -> Unit = {},
    viewModel: TasksViewModel = koinViewModel(),
) {
    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val blocks: List<NoteBlock> by viewModel.visibleBlocks.collectAsState()
    val isShowingCompleted: Boolean by viewModel.isShowingCompleted.collectAsState()

    val selectedBlockIds: Set<String> by viewModel.selectedBlockIds.collectAsState()
    val isSelectionMode = selectedBlockIds.isNotEmpty()
    val clipboardManager = LocalClipboardManager.current
    val focusRequest: FocusRequest? by viewModel.focusRequest.collectAsState()
    val allLinkableNotes by viewModel.allLinkableNotes.collectAsState(emptyList())
    val blockLocations by viewModel.blockLocations.collectAsState()

    val inboxNoteId = remember(allLinkableNotes) {
        allLinkableNotes.firstOrNull { it.title.equals("Inbox", ignoreCase = true) && !it.isDaily }?.noteId
    }
    val noteTitleById = remember(allLinkableNotes) {
        allLinkableNotes.associate { it.noteId to it.title }
    }
    val sectionLabelFor = remember(blockLocations, inboxNoteId, noteTitleById) {
        { block: NoteBlock ->
            val location = blockLocations[block.id]
            when {
                location == null -> null
                !location.isDaily && location.noteId == inboxNoteId -> null
                location.isDaily -> dailyDateLabel(location.noteId)
                else -> noteTitleById[location.noteId] ?: "Note"
            }
        }
    }
    val groupedBlocks = remember(blocks, blockLocations, inboxNoteId, noteTitleById) {
        val (rest, located) = blocks.partition { block ->
            val location = blockLocations[block.id]
            location == null || (!location.isDaily && location.noteId == inboxNoteId)
        }
        val dailyBlocks = located
            .filter { blockLocations[it.id]?.isDaily == true }
            .sortedBy { blockLocations[it.id]?.noteId }
        val noteBlocks = located
            .filter { blockLocations[it.id]?.isDaily == false }
            .sortedBy { noteTitleById[blockLocations[it.id]?.noteId] ?: "" }
        rest + dailyBlocks + noteBlocks
    }

    KmpBackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    KmpBackHandler(enabled = isShowingCompleted) {
        viewModel.toggleCompletedView()
    }

    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var titleTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var topBarBottomPx by remember { mutableFloatStateOf(0f) }
    var baselineDistancePx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val collapseRangePx = with(density) { 32.dp.toPx() }
    val isAtScrollTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    LaunchedEffect(isAtScrollTop, titleTopPx, topBarBottomPx) {
        if (isAtScrollTop) baselineDistancePx = titleTopPx - topBarBottomPx
    }
    val titleCollapseProgress by remember {
        derivedStateOf {
            val distance = titleTopPx - topBarBottomPx
            val scrolledPx = (baselineDistancePx - distance).coerceAtLeast(0f)
            (scrolledPx / collapseRangePx).coerceIn(0f, 1f)
        }
    }
    val onCollapsedTitleClick: () -> Unit = {
        scope.launch { listState.animateScrollToItem(0) }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllTasks()
        if (WidgetComposeRequestBus.consume(WidgetComposeRequest.NEW_TASK)) {
            viewModel.insertNewReminder()
        }
    }

    val topPadding = if (isDesktopPlatform) 80.dp else 110.dp
    val bottomPadding = 120.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {

            if (isLoading || blocks.isEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)
                ) {
                    item {
                        ScreenTitle(
                            isShowingCompleted,
                            modifier = Modifier.onGloballyPositioned { titleTopPx = it.positionInRoot().y }
                        )
                    }
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(
                                    text = if (isShowingCompleted) "No completed tasks yet." else "All caught up!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                EmberrVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 80.dp, bottom = 24.dp)
                )
            } else {
                val editorActions = remember(viewModel, onOpenFile) {
                    object : EditorActions {
                        override fun onClearSlashQuery() {}
                        override fun onClearFocusRequest() = viewModel.clearFocusRequest()
                        override fun onUpdateText(id: String, text: String) = viewModel.updateBlockText(id, text)
                        override fun onToggleCheckbox(id: String, checked: Boolean) = viewModel.toggleCheckbox(id, checked)
                        override fun onFocusBlock(id: String) = viewModel.setFocusedBlock()
                        override fun onRequestCursorPosition(id: String, offset: Int) {}
                        override fun onEnterPressed(id: String, before: String, after: String) = viewModel.handleEnter(id, before, after)
                        override fun onBackspaceOnEmpty(id: String) = viewModel.handleBackspaceOnEmpty(id)
                        override fun onToggleSelection(id: String) = viewModel.toggleSelection(id)
                        override fun onUpdateReminder(id: String, timestamp: Long?) = viewModel.updateReminder(id, timestamp)
                        override fun onOpenFile(filePath: String, mimeType: String) { onOpenFile(filePath, mimeType) }
                        override fun onChangeBlockType(type: String) {}
                        override fun onToggleFormat(format: String) {}
                        override fun onAdjustIndentation(increase: Boolean) {}
                        override fun onSetBlockAlignment(alignment: TextAlignment) {}
                        override fun onToggleExpand(id: String) {}
                        override fun onUrlSubmit(id: String, url: String) {}
                        override fun onImagePicked(id: String, uri: String) {}
                        override fun onDocumentPicked(id: String, uri: String) {}
                        override fun onAddBlankBlock() {}
                        override fun onInsertMediaBlock(type: String) {}
                        override fun onOutsideTap() {}
                        override fun onUpdateDbTitle(id: String, title: String) {}
                        override fun onAddDbRow(id: String) {}
                        override fun onAddDbColumn(id: String) {}
                        override fun onUpdateDbCell(blockId: String, rowId: String, colId: String, value: CellData) {}
                        override fun onUpdateDbColumn(blockId: String, colId: String, name: String, type: ColumnType, isManualNameChange: Boolean) {}
                        override fun onUpdateDbSort(blockId: String, colId: String, isAscending: Boolean?) {}
                        override fun onUpdateDbGroupBy(blockId: String, colId: String?) {}
                        override fun onUpdateDbGalleryCardSize(blockId: String, size: GalleryCardSize) {}
                        override fun onToggleKanbanGroupVisibility(blockId: String, viewId: String, groupName: String, isHidden: Boolean) {}
                        override fun onReorderKanbanGroups(blockId: String, viewId: String, orderedGroupKeys: List<String>) {}
                        override fun onAddDbFilter(blockId: String, colId: String, operator: String, value: String) {}
                        override fun onRemoveDbFilter(blockId: String, config: FilterConfig) {}
                        override fun onReorderDbColumns(blockId: String, from: Int, to: Int) {}
                        override fun onReorderDbRows(blockId: String, from: Int, to: Int) {}
                        override fun onReorderDatabaseViews(blockId: String, from: Int, to: Int) {}
                        override fun onUpdateDbFormula(blockId: String, colId: String, expression: String) {}
                        override fun onDeleteDbColumn(blockId: String, colId: String) {}
                        override fun onDeleteDbRow(blockId: String, rowId: String) {}
                        override fun onAddDbRowAt(blockId: String, index: Int) {}
                        override fun onAddDbColumnAt(blockId: String, index: Int) {}
                        override fun onUpdateDbColumnWidth(blockId: String, colId: String, width: Int) {}
                        override fun onVoiceRecorded(id: String, filePath: String, duration: Int) {}
                        override fun onRemoveVoice(id: String) {}
                        override fun onStartRecording() {}
                        override fun onStopRecording(blockId: String, cancel: Boolean) {}
                        override fun onPlayAudio(filePath: String, onComplete: () -> Unit) {}
                        override fun onStopAudio() {}
                        override fun onDeleteImageBlock(id: String) {}
                        override fun onCreateGlobalTag(name: String, colorHex: String): String = ""
                        override fun onRequestImagePicker(blockId: String) {}
                        override fun onRequestDocumentPicker(blockId: String) {}
                        override fun onRequestDbFilePicker(blockId: String, rowId: String, colId: String, isAudio: Boolean) {}
                        override fun onStopDbAudioRecording(blockId: String, rowId: String, colId: String, cancel: Boolean) {}
                        override fun onTogglePin() {}
                        override fun onUpdateTable(id: String, rows: List<List<String>>) {}
                        override fun onUpdateTableColumnWidth(id: String, columnIndex: Int, width: Int) {}
                        override fun onUpdateTableStyle(
                            id: String,
                            cellStyles: Map<String, com.emberr.domain.model.TableCellStyle>,
                            rowStyles: Map<String, com.emberr.domain.model.TableCellStyle>,
                            columnStyles: Map<String, com.emberr.domain.model.TableCellStyle>
                        ) {}
                        override fun onAddBlockAbove(id: String) {}
                        override fun onAddBlockBelow(id: String) {}
                        override fun onUpdateDbAggregation(blockId: String, colId: String, aggregationType: String?) {}
                        override fun onUpdateDbCurrency(blockId: String, colId: String, symbol: String) {}
                        override fun onUpdateDbFormulaCurrency(blockId: String, colId: String, enabled: Boolean) {}
                        override fun onAddDatabaseView(blockId: String, type: com.emberr.domain.model.ViewType) {}
                        override fun onDeleteDatabaseView(blockId: String, viewId: String) {}
                        override fun onSetActiveDatabaseView(blockId: String, viewId: String) {}
                        override fun onRenameDatabaseView(blockId: String, viewId: String, newName: String) {}
                        override fun onOpenDatabaseNote(blockId: String, rowId: String, colId: String, existingNoteId: String?) {}
                        override fun onSaveDatabaseAsTemplate(blockId: String, templateName: String) {}
                        override fun onRequestCamera(blockId: String) {}
                        override fun onNoteLinkClick(noteId: String) {
                            onNavigateToEditor(noteId)
                        }
                        override fun onCreateLinkedNote(title: String): String {
                            return viewModel.createLinkedNote(title)
                        }
                        override suspend fun getNoteTitle(noteId: String): String {
                            return viewModel.getNoteTitle(noteId)
                        }
                        override suspend fun getNoteMetadata(noteId: String) = viewModel.getNoteMetadata(noteId)
                        override fun onUpdateLinkedNoteOptions(id: String, showIcon: Boolean, showCoverImage: Boolean) {}
                    }
                }

                EditorScreen(
                    blocks = groupedBlocks,
                    globalTags = emptyList(),
                    actions = editorActions,
                    focusRequest = focusRequest,
                    selectedBlockIds = selectedBlockIds,
                    onClearSelection = { viewModel.clearSelection() },
                    selectionMenuContent = { closeMenu ->
                        BlockSelectionMenuContent(
                            selectedCount = selectedBlockIds.size,
                            onCloseMenu = closeMenu,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(viewModel.getSelectedText()))
                                viewModel.clearSelection()
                            },
                            onCut = { clipboardManager.setText(AnnotatedString(viewModel.cutSelectedBlocks())) },
                            onDelete = { viewModel.deleteSelectedBlocks() },
                            onSelectAll = { viewModel.selectAllBlocks() },
                            onClearSelection = { viewModel.clearSelection() }
                        )
                    },
                    topContentPadding = topPadding,
                    allLinkableNotes = allLinkableNotes,
                    sectionLabelFor = sectionLabelFor,
                    headerContent = {
                        ScreenTitle(
                            isShowingCompleted,
                            modifier = Modifier.onGloballyPositioned { titleTopPx = it.positionInRoot().y }
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                        .background(MaterialTheme.colorScheme.background),
                    listState = listState
                )
            }


            EmberrTopHeaderBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = if (isShowingCompleted) "Completed" else "Tasks",
                titleVisibility = titleCollapseProgress,
                titleSideInset = 112.dp,
                onTitleClick = onCollapsedTitleClick,
                hazeState = hazeState,
                onPositioned = { topBarBottomPx = it.positionInRoot().y + it.size.height },
                onBackClick = {
                    if (isSelectionMode) viewModel.clearSelection()
                    else if (isShowingCompleted) viewModel.toggleCompletedView()
                    else onNavigateBack()
                },
                actions = {
                    if (!isSelectionMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TopBarIconButton(
                                icon = painterResource(Res.drawable.check_square),
                                contentDescription = "Completed Tasks",
                                bgColor = Color.Transparent,
                                tint = if (isShowingCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                hazeState = hazeState,
                                hazeStyle = EmberrBlur.Regular,
                                onClick = { viewModel.toggleCompletedView() }
                            )

                            TopBarIconButton(
                                icon = painterResource(Res.drawable.circle_plus),
                                contentDescription = "Add Task",
                                bgColor = Color.Transparent,
                                tint = MaterialTheme.colorScheme.primary,
                                hazeState = hazeState,
                                hazeStyle = EmberrBlur.Regular,
                                onClick = { viewModel.insertNewReminder() }
                            )
                        }
                    }
                }
            )

            BlockSelectionPill(
                isVisible = isSelectionMode && !isDesktopPlatform,
                selectedCount = selectedBlockIds.size,
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAllBlocks() },
                hazeState = hazeState,
                onCopy = {
                    clipboardManager.setText(AnnotatedString(viewModel.getSelectedText()))
                    viewModel.clearSelection()
                },
                onCut = { clipboardManager.setText(AnnotatedString(viewModel.cutSelectedBlocks())) },
                onAddBlockAbove = {},
                onAddBlockBelow = {},
                onTogglePin = {},
                onDelete = { viewModel.deleteSelectedBlocks() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (isDesktopPlatform) Modifier.padding(bottom = 16.dp) else Modifier.navigationBarsPadding())
            )
        }
    }
}

private fun dailyDateLabel(dateString: String): String {
    val date = try {
        LocalDate.parse(dateString)
    } catch (e: IllegalArgumentException) {
        return dateString
    }
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return when (date) {
        today -> "Today"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        today.minus(DatePeriod(days = 1)) -> "Yesterday"
        else -> {
            val shortDay = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val shortMonth = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$shortDay, $shortMonth ${date.day}"
        }
    }
}

@Composable
private fun ScreenTitle(isShowingCompleted: Boolean, modifier: Modifier = Modifier) {
    val titleStyle = MaterialTheme.typography.titleLarge.let {
        it.copy(fontSize = it.fontSize * 1.5f, lineHeight = it.lineHeight * 1.2f)
    }
    Text(
        text = if (isShowingCompleted) "Completed" else "Tasks",
        style = titleStyle,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .padding(horizontal = if (isDesktopPlatform) 40.dp else 16.dp)
            .padding(bottom = 8.dp)
    )
}
