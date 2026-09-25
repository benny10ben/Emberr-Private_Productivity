package com.emberr.presentation.daily

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import com.emberr.presentation.shared.rememberStableStatusBarsPadding
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TableCellStyle
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.ViewType
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.editor.BlockSelectionMenuContent
import com.emberr.presentation.shared.editor.BlockSelectionPill
import com.emberr.presentation.shared.editor.EditorActions
import com.emberr.presentation.shared.editor.EditorScreen
import com.emberr.presentation.shared.editor.EditorToolbar
import com.emberr.presentation.shared.editor.GlobalEditorState
import com.emberr.presentation.shared.editor.MobileMenuState
import com.emberr.presentation.shared.editor.EditorEventBus
import com.emberr.presentation.shared.editor.blockViews.databaseBlockView.DatabaseTemplatePickerSheet
import com.emberr.presentation.home.note.SubNotePanel
import com.emberr.presentation.calendar.EventEditorSheetHost
import com.emberr.presentation.calendar.RecurrenceScopeChooser
import com.emberr.presentation.shared.SubNoteOpenMode
import com.emberr.presentation.shared.editor.BlockStyleBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.koin.compose.koinInject

/**
 * Single-day daily editor pane. The caller selects the day via viewModel.selectDate(date);
 * this renders viewModel.visibleBlocks for whatever day is currently loaded. No pager.
 *
 * Used by the desktop merged screen (right panel). Owns its own editor menu/keyboard state,
 * its sub-note panel, and - like DailyScreen - the event editor sheet + recurrence scope
 * chooser, since both are driven by state that lives in this composable's EditorActions.
 */
@Composable
fun DailyEditorPane(
    modifier: Modifier = Modifier,
    viewModel: DailyEditorViewModel,
    hazeState: HazeState,
    bottomContentPadding: Dp = 0.dp,
    isSidebarVisible: Boolean = true,
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onNavigateToEditor: (String) -> Unit = {},
    onExportMarkdown: (fileName: String, content: String) -> Unit = { _, _ -> },
    onExportPdf: (fileName: String, title: String, blocks: List<NoteBlock>) -> Unit = { _, _, _ -> },
    onSelectionModeChange: (Boolean) -> Unit = {}
) {
    val allLinkableNotes by viewModel.allLinkableNotes.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    val loadedBlocks by viewModel.visibleBlocks.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val loadedDateString by viewModel.loadedDateString.collectAsState()
    val previewCache by viewModel.previewCache.collectAsState()

    val selectedDateString = selectedDate.toString()
    val isSelectedDayLive = loadedDateString == selectedDateString
    val blocks = if (isSelectedDayLive) loadedBlocks else previewCache[selectedDateString] ?: emptyList()
    val selectedBlockIds by viewModel.selectedBlockIds.collectAsState()
    val focusRequest by viewModel.focusRequest.collectAsState()
    val selectionRequest by viewModel.selectionRequest.collectAsState()
    val globalTags by viewModel.globalTags.collectAsState()
    val databaseTemplates by viewModel.databaseTemplates.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val pendingRecurringDeletion by viewModel.pendingRecurringDeletion.collectAsState()
    var showDatabasePicker by remember { mutableStateOf(false) }
    var showNoteLinkMenu by remember { mutableStateOf(false) }
    var showCanvasLinkMenu by remember { mutableStateOf(false) }

    val isSelectionMode = selectedBlockIds.isNotEmpty()
    val selectedBlocksList = blocks.filter { it.id in selectedBlockIds }
    val isSelectionPinned = selectedBlocksList.isNotEmpty() && selectedBlocksList.all { it.isPinned }

    var showBlockStyleBar by remember { mutableStateOf(false) }
    LaunchedEffect(isSelectionMode) {
        onSelectionModeChange(isSelectionMode)
        if (!isSelectionMode) showBlockStyleBar = false
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    var isKeyboardOpen by remember { mutableStateOf(false) }
    var previousImeBottom by remember { mutableIntStateOf(0) }

    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && imeBottom >= previousImeBottom) {
            isKeyboardOpen = true
        } else if (imeBottom < previousImeBottom) {
            isKeyboardOpen = false
        }
        if (imeBottom == 0) {
            isKeyboardOpen = false
        }
        previousImeBottom = imeBottom
    }

    var mobileMenuState by remember { mutableStateOf(MobileMenuState.MAIN) }
    var slashQuery by remember { mutableStateOf("") }
    var mentionQuery by remember { mutableStateOf<String?>(null) }

    val onMobileMenuStateChange: (MobileMenuState) -> Unit = { newState ->
        if (mobileMenuState == MobileMenuState.MENTION && newState != MobileMenuState.MENTION) {
            EditorEventBus.cancelMentionEvent.tryEmit(Unit)
        }
        mobileMenuState = newState
    }

    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) return@LaunchedEffect
        delay(250.milliseconds)
        if (!isKeyboardOpen && mobileMenuState != MobileMenuState.MAIN) {
            onMobileMenuStateChange(MobileMenuState.MAIN)
        }
    }

    val showToolbar = !isSelectionMode && (isKeyboardOpen || isDesktopPlatform || mobileMenuState != MobileMenuState.MAIN)

    var subNotePanelId by remember { mutableStateOf<String?>(null) }

    // Mirrors DailyScreen: the checkbox row's three-dot menu routes through
    // EditorActions.onOpenEventOptions, which just parks the target blockId here for
    // EventEditorSheetHost (below) to resolve into a CalendarEvent and open the sheet.
    var eventOptionsTargetBlockId by remember { mutableStateOf<String?>(null) }
    var eventOptionsOccurrenceDate by remember { mutableStateOf<String?>(null) }

    val settingsManager: SettingsManager = koinInject()
    val subNoteOpenModeName by settingsManager.subNoteOpenModeFlow.collectAsState(
        initial = SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE
    )
    val subNoteOpenMode = runCatching { SubNoteOpenMode.valueOf(subNoteOpenModeName) }
        .getOrDefault(SubNoteOpenMode.SIDE_PANEL)

    val actions = remember(viewModel, onOpenFile) {
        object : EditorActions {
            override fun onClearSlashQuery() = viewModel.clearActiveSlashQuery()
            override fun onClearFocusRequest() = viewModel.clearFocusRequest()
            override fun onUpdateText(id: String, text: String) = viewModel.updateBlockText(id, text)
            override fun onToggleCheckbox(id: String, checked: Boolean) = viewModel.toggleCheckbox(id, checked)
            override fun onToggleExpand(id: String) = viewModel.toggleToggleBlock(id)
            override fun onFocusBlock(id: String) = viewModel.setFocusedBlock(id)
            override fun onRequestCursorPosition(id: String, offset: Int) = viewModel.requestCursorPosition(id, offset)
            override fun onChangeBlockType(type: String) = viewModel.changeFocusedBlockType(type)
            override fun onToggleFormat(format: String) = viewModel.toggleFormat(format)
            override fun onAdjustIndentation(increase: Boolean) = viewModel.adjustIndentation(increase)
            override fun onSetBlockAlignment(alignment: TextAlignment) = viewModel.setFocusedBlockAlignment(alignment)
            override fun onEnterPressed(id: String, before: String, after: String) = viewModel.handleEnter(id, before, after)
            override fun onBackspaceOnEmpty(id: String) = viewModel.handleBackspaceOnEmpty(id)
            override fun onToggleSelection(id: String) = viewModel.toggleSelection(id)
            override fun onUpdateReminder(id: String, timestamp: Long?) = viewModel.updateReminder(id, timestamp)
            override fun onOpenEventOptions(blockId: String, occurrenceDate: String?) {
                eventOptionsTargetBlockId = blockId
                eventOptionsOccurrenceDate = occurrenceDate
            }
            override fun onUrlSubmit(id: String, url: String) = viewModel.handleUrlSubmit(id, url)
            override fun onImagePicked(id: String, uri: String) = viewModel.handleImagePicked(id, uri)
            override fun onDocumentPicked(id: String, uri: String) = viewModel.handleDocumentPicked(id, uri)
            override fun onAddBlankBlock() = viewModel.addBlankBlockBelowFocused()
            override fun onInsertMediaBlock(type: String) {
                when (type) {
                    "database" -> showDatabasePicker = true
                    "linked_note" -> showNoteLinkMenu = true
                    "linked_canvas" -> showCanvasLinkMenu = true
                    else -> viewModel.insertNewMediaBlock(type)
                }
            }
            override fun onInsertLinkedNoteBlock(noteId: String) =
                viewModel.insertNewMediaBlock("linked_note", linkedNoteId = noteId)
            override fun onInsertCanvasBlock(canvasNoteId: String) =
                viewModel.insertNewMediaBlock("canvas", canvasNoteId = canvasNoteId)
            override suspend fun getLinkableCanvases() = viewModel.getLinkableCanvases()
            override fun onSaveDatabaseAsTemplate(blockId: String, templateName: String) =
                viewModel.saveDatabaseAsTemplate(blockId, templateName)
            override fun onOutsideTap() {}
            override fun onUpdateDbTitle(id: String, title: String) = viewModel.updateDbTitle(id, title)
            override fun onAddDbRow(id: String) = viewModel.addDbRow(id)
            override fun onAddDbColumn(id: String) = viewModel.addDbColumn(id)
            override fun onUpdateDbCell(blockId: String, rowId: String, colId: String, value: CellData) = viewModel.updateDbCell(blockId, rowId, colId, value)
            override fun onUpdateDbColumn(blockId: String, colId: String, name: String, type: ColumnType, isManualNameChange: Boolean) = viewModel.updateDbColumn(blockId, colId, name, type, isManualNameChange)
            override fun onUpdateDbSort(blockId: String, colId: String, isAscending: Boolean?) = viewModel.updateDbSort(blockId, colId, isAscending)
            override fun onUpdateDbGroupBy(blockId: String, colId: String?) = viewModel.updateDbGroupBy(blockId, colId)
            override fun onUpdateDbGalleryCardSize(blockId: String, size: GalleryCardSize) = viewModel.updateDbGalleryCardSize(blockId, size)
            override fun onToggleKanbanGroupVisibility(blockId: String, viewId: String, groupName: String, isHidden: Boolean) = viewModel.toggleKanbanGroupVisibility(blockId, viewId, groupName, isHidden)
            override fun onReorderKanbanGroups(blockId: String, viewId: String, orderedGroupKeys: List<String>) = viewModel.reorderKanbanGroups(blockId, viewId, orderedGroupKeys)
            override fun onAddDbFilter(blockId: String, colId: String, operator: String, value: String) = viewModel.addDbFilter(blockId, colId, operator, value)
            override fun onRemoveDbFilter(blockId: String, config: FilterConfig) = viewModel.removeDbFilter(blockId, config)
            override fun onReorderDbColumns(blockId: String, from: Int, to: Int) = viewModel.reorderDbColumns(blockId, from, to)
            override fun onReorderDbRows(blockId: String, from: Int, to: Int) = viewModel.reorderDbRows(blockId, from, to)
            override fun onReorderDatabaseViews(blockId: String, from: Int, to: Int) = viewModel.reorderDatabaseViews(blockId, from, to)
            override fun onUpdateDbFormula(blockId: String, colId: String, expression: String) = viewModel.updateDbFormula(blockId, colId, expression)
            override fun onDeleteDbColumn(blockId: String, colId: String) = viewModel.deleteDbColumn(blockId, colId)
            override fun onDeleteDbRow(blockId: String, rowId: String) = viewModel.deleteDbRow(blockId, rowId)
            override fun onAddDbRowAt(blockId: String, index: Int) = viewModel.addDbRowAt(blockId, index)
            override fun onAddDbColumnAt(blockId: String, index: Int) = viewModel.addDbColumnAt(blockId, index)
            override fun onUpdateDbColumnWidth(blockId: String, colId: String, width: Int) = viewModel.updateDbColumnWidth(blockId, colId, width)
            override fun onVoiceRecorded(id: String, filePath: String, duration: Int) = viewModel.handleVoiceRecorded(id, filePath, duration)
            override fun onRemoveVoice(id: String) = viewModel.handleRemoveVoice(id)
            override fun onDeleteImageBlock(id: String) = viewModel.deleteImageBlock(id)
            override fun onCreateGlobalTag(name: String, colorHex: String): String = viewModel.createGlobalTag(name, colorHex)
            override fun onRequestImagePicker(blockId: String) {
                onPickImage { path -> viewModel.handleImagePicked(blockId, path) }
            }
            override fun onRequestCamera(blockId: String) {
                onTakePhoto { path -> viewModel.handleImagePicked(blockId, path) }
            }
            override fun onRequestDocumentPicker(blockId: String) {
                onPickDocument { path -> viewModel.handleDocumentPicked(blockId, path) }
            }
            override fun onRequestDbFilePicker(blockId: String, rowId: String, colId: String, isAudio: Boolean) {
                onPickDocument { path ->
                    viewModel.handleDbFilePicked(blockId, rowId, colId, path)
                }
            }
            override fun onStopDbAudioRecording(blockId: String, rowId: String, colId: String, cancel: Boolean) {
                viewModel.stopDbHardwareRecording(blockId, rowId, colId, cancel)
            }
            override fun onOpenFile(filePath: String, mimeType: String) {
                onOpenFile(filePath, mimeType)
            }
            override fun onStartRecording() = viewModel.startHardwareRecording()
            override fun onStopRecording(blockId: String, cancel: Boolean) = viewModel.stopHardwareRecording(blockId, cancel)
            override fun onPlayAudio(filePath: String, onComplete: () -> Unit) = viewModel.playAudio(filePath, onComplete)
            override fun onStopAudio() = viewModel.stopAudio()
            override fun onTogglePin() = viewModel.togglePinSelectedBlocks()
            override fun onUpdateTable(id: String, rows: List<List<String>>) =
                viewModel.updateTable(id, rows)
            override fun onUpdateTableColumnWidth(id: String, columnIndex: Int, width: Int) =
                viewModel.updateTableColumnWidth(id, columnIndex, width)
            override fun onUpdateTableStyle(
                id: String,
                cellStyles: Map<String, TableCellStyle>,
                rowStyles: Map<String, TableCellStyle>,
                columnStyles: Map<String, TableCellStyle>
            ) = viewModel.updateTableStyle(id, cellStyles, rowStyles, columnStyles)
            override fun onAddBlockAbove(id: String) = viewModel.addBlockAbove(id)
            override fun onAddBlockBelow(id: String) = viewModel.addBlockBelow(id)
            override fun onUpdateDbAggregation(blockId: String, colId: String, aggregationType: String?) =
                viewModel.updateDbAggregation(blockId, colId, aggregationType)
            override fun onUpdateDbCurrency(blockId: String, colId: String, symbol: String) =
                viewModel.updateDbCurrency(blockId, colId, symbol)
            override fun onUpdateDbFormulaCurrency(blockId: String, colId: String, enabled: Boolean) =
                viewModel.updateDbFormulaCurrency(blockId, colId, enabled)
            override fun onAddDatabaseView(blockId: String, type: ViewType) = viewModel.addDatabaseView(blockId, type)
            override fun onDeleteDatabaseView(blockId: String, viewId: String) = viewModel.deleteDatabaseView(blockId, viewId)
            override fun onSetActiveDatabaseView(blockId: String, viewId: String) = viewModel.setActiveDatabaseView(blockId, viewId)
            override fun onRenameDatabaseView(blockId: String, viewId: String, newName: String) = viewModel.renameDatabaseView(blockId, viewId, newName)
            override fun onNoteLinkClick(noteId: String) {
                if (isDesktopPlatform) {
                    subNotePanelId = noteId
                } else {
                    onNavigateToEditor(noteId)
                }
            }
            override fun onCreateLinkedNote(title: String): String {
                return viewModel.createLinkedNote(title)
            }
            override fun onOpenDatabaseNote(blockId: String, rowId: String, colId: String, existingNoteId: String?) {
                viewModel.openDatabaseNote(blockId, rowId, colId, existingNoteId) { resolvedNoteId ->
                    if (isDesktopPlatform) {
                        subNotePanelId = resolvedNoteId
                    } else {
                        onNavigateToEditor(resolvedNoteId)
                    }
                }
            }
            override suspend fun getNoteTitle(noteId: String): String {
                return viewModel.getNoteTitle(noteId)
            }
            override suspend fun getNoteMetadata(noteId: String) = viewModel.getNoteMetadata(noteId)
            override fun onUpdateLinkedNoteOptions(id: String, showIcon: Boolean, showCoverImage: Boolean) =
                viewModel.updateLinkedNoteOptions(id, showIcon, showCoverImage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val editorTopPadding = if (isDesktopPlatform) {
            if (!isSidebarVisible) 72.dp else 16.dp
        } else {
            rememberStableStatusBarsPadding().calculateTopPadding() + 150.dp
        }

        EditorScreen(
            blocks = blocks,
            allLinkableNotes = allLinkableNotes,
            globalTags = globalTags,
            actions = actions,
            focusRequest = if (isSelectedDayLive) focusRequest else null,
            selectionRequest = if (isSelectedDayLive) selectionRequest else null,
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
                    onClearSelection = { viewModel.clearSelection() },
                    selectedBlocks = selectedBlocksList,
                    isSelectionPinned = isSelectionPinned,
                    onTogglePin = { actions.onTogglePin() },
                    onAddBlockAbove = { viewModel.addBlockAboveSelection() },
                    onAddBlockBelow = { viewModel.addBlockBelowSelection() },
                    onChangeBlockType = { viewModel.changeBlockTypeForSelectedBlocks(it) },
                    onToggleFormat = { viewModel.toggleFormatForSelectedBlocks(it) },
                    onSetAlignment = { viewModel.setSelectedBlocksAlignment(it) },
                    onAdjustIndentation = { viewModel.adjustIndentationForSelectedBlocks(it) },
                    onUpdateLinkedNoteOptions = { id, showIcon, showCoverImage ->
                        viewModel.updateLinkedNoteOptions(id, showIcon, showCoverImage)
                    }
                )
            },
            mobileMenuState = mobileMenuState,
            onMobileMenuStateChange = onMobileMenuStateChange,
            slashQuery = slashQuery,
            onSlashQueryChange = { slashQuery = it },
            showNoteLinkMenu = showNoteLinkMenu,
            onDismissNoteLinkMenu = { showNoteLinkMenu = false },
            showCanvasLinkMenu = showCanvasLinkMenu,
            onDismissCanvasLinkMenu = { showCanvasLinkMenu = false },
            onMentionQueryChange = { newQuery ->
                mentionQuery = newQuery
                if (!isDesktopPlatform) {
                    mobileMenuState = when {
                        newQuery != null -> MobileMenuState.MENTION
                        mobileMenuState == MobileMenuState.MENTION -> MobileMenuState.MAIN
                        else -> mobileMenuState
                    }
                }
            },
            bottomContentPadding = bottomContentPadding,
            topContentPadding = editorTopPadding,
            modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            emptyContent = {
                DailyEmptyDayMessage(
                    date = selectedDate,
                    topPadding = editorTopPadding,
                    bottomPadding = bottomContentPadding
                )
            }
        )

        AnimatedVisibility(
            visible = showToolbar,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 250, delayMillis = 100, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(durationMillis = 250, delayMillis = 100)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(durationMillis = 200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .then(if (isDesktopPlatform) Modifier else Modifier.navigationBarsPadding())
                .padding(bottom = 8.dp, start = if (isDesktopPlatform) 16.dp else 6.dp, end = if (isDesktopPlatform) 16.dp else 6.dp)
        ) {
            EditorToolbar(
                mobileMenuState = mobileMenuState,
                onMenuStateChange = onMobileMenuStateChange,
                query = slashQuery,
                hazeState = hazeState,
                onChangeBlockType = { actions.onChangeBlockType(it) },
                onToggleFormat = { actions.onToggleFormat(it) },
                onAdjustIndentation = { actions.onAdjustIndentation(it) },
                onSetAlignment = { actions.onSetBlockAlignment(it) },
                onInsertMediaBlock = { actions.onInsertMediaBlock(it) },
                allLinkableNotes = allLinkableNotes,
                mentionQuery = mentionQuery ?: "",
                onMentionNoteSelected = { noteId ->
                    val note = allLinkableNotes.find { it.noteId == noteId }
                    val safeTitle = (note?.title ?: "").replace("[", "").replace("]", "").ifEmpty { "Untitled" }
                    EditorEventBus.confirmMentionEvent.tryEmit(safeTitle to noteId)
                },
                onMentionCreateNote = { title ->
                    val safeTitle = title.replace("[", "").replace("]", "").trim().ifEmpty { "Untitled" }
                    val newNoteId = actions.onCreateLinkedNote(safeTitle)
                    EditorEventBus.confirmMentionEvent.tryEmit(safeTitle to newNoteId)
                },
                onMentionCreateBlank = {
                    val newNoteId = actions.onCreateLinkedNote("Untitled")
                    EditorEventBus.confirmMentionAndOpenEvent.tryEmit("Untitled" to newNoteId)
                },
                onNoteLinkSelected = { noteId -> actions.onInsertLinkedNoteBlock(noteId) },
                onNoteLinkCreateNote = { title ->
                    val newNoteId = actions.onCreateLinkedNote(title)
                    actions.onInsertLinkedNoteBlock(newNoteId)
                },
                onNoteLinkCreateBlank = {
                    val newNoteId = actions.onCreateLinkedNote("Untitled")
                    actions.onInsertLinkedNoteBlock(newNoteId)
                    actions.onNoteLinkClick(newNoteId)
                },
                onCanvasLinkSelected = { canvasNoteId -> actions.onInsertCanvasBlock(canvasNoteId) },
                loadLinkableCanvases = { actions.getLinkableCanvases() },
                onSelectCurrentBlock = {
                    GlobalEditorState.currentlyFocusedBlockId?.let { id ->
                        actions.onToggleSelection(id)
                    }
                },
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                showHistory = true
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .then(if (isDesktopPlatform) Modifier.padding(bottom = 16.dp) else Modifier.navigationBarsPadding()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BlockStyleBar(
                isVisible = isSelectionMode && showBlockStyleBar,
                onChangeBlockType = { viewModel.changeBlockTypeForSelectedBlocks(it) },
                onToggleFormat = { viewModel.toggleFormatForSelectedBlocks(it) },
                onAdjustIndentation = { viewModel.adjustIndentationForSelectedBlocks(it) },
                onSetAlignment = { viewModel.setSelectedBlocksAlignment(it) },
                hazeState = hazeState
            )
            BlockSelectionPill(
                isVisible = isSelectionMode && !isDesktopPlatform,
                selectedCount = selectedBlockIds.size,
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAllBlocks() },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(viewModel.getSelectedText()))
                    viewModel.clearSelection()
                },
                onCut = {
                    clipboardManager.setText(AnnotatedString(viewModel.cutSelectedBlocks()))
                },
                onAddBlockAbove = { viewModel.addBlockAboveSelection() },
                onAddBlockBelow = { viewModel.addBlockBelowSelection() },
                onDelete = { viewModel.deleteSelectedBlocks() },
                onTogglePin = { actions.onTogglePin() },
                isSelectionPinned = isSelectionPinned,
                selectedBlocks = selectedBlocksList,
                onUpdateLinkedNoteOptions = { id, showIcon, showCoverImage -> viewModel.updateLinkedNoteOptions(id, showIcon, showCoverImage) },
                showStyleButton = true,
                isStyleBarOpen = showBlockStyleBar,
                onToggleStyleBar = { showBlockStyleBar = !showBlockStyleBar },
                hazeState = hazeState
            )
        }

        if (subNotePanelId != null) {
            com.emberr.presentation.home.note.SubNotePanel(
                noteId = subNotePanelId!!,
                onClose = { subNotePanelId = null },
                onExpand = { noteId ->
                    subNotePanelId = null
                    onNavigateToEditor(noteId)
                },
                onPickImage = onPickImage,
                onPickDocument = onPickDocument,
                onOpenFile = onOpenFile,
                onExportMarkdown = onExportMarkdown,
                onExportPdf = onExportPdf,
                openMode = subNoteOpenMode
            )
        }

        DatabaseTemplatePickerSheet(
            expanded = showDatabasePicker,
            templates = databaseTemplates,
            onDismiss = { showDatabasePicker = false },
            onCreateBlank = { viewModel.insertNewMediaBlock("database") },
            onSelectTemplate = { viewModel.insertNewMediaBlock("database", it) }
        )

        EventEditorSheetHost(
            targetBlockId = eventOptionsTargetBlockId,
            targetOccurrenceDate = eventOptionsOccurrenceDate,
            onDismissTarget = {
                eventOptionsTargetBlockId = null
                eventOptionsOccurrenceDate = null
            }
        )

        if (pendingRecurringDeletion != null) {
            RecurrenceScopeChooser(
                onDismiss = { viewModel.dismissRecurringDeletion() },
                onScopeSelected = { scope -> viewModel.confirmRecurringDeletion(scope) }
            )
        }
    }
}