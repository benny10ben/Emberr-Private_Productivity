package com.emberr.presentation.mobile.daily

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.koin.compose.viewmodel.koinViewModel
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.ViewType
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.editor.BlockSelectionPill
import com.emberr.presentation.shared.editor.EditorActions
import com.emberr.presentation.shared.editor.EditorScreen
import com.emberr.presentation.shared.editor.SelectionModeObserver
import com.emberr.presentation.shared.editor.MobileMenuState
import com.emberr.presentation.shared.editor.EditorEventBus
import com.emberr.presentation.shared.editor.blockViews.databaseBlockView.DatabaseTemplatePickerSheet
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.daysUntil
import com.emberr.presentation.shared.editor.EditorToolbar
import com.emberr.presentation.shared.editor.GlobalEditorState
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import com.emberr.data.local.room.entity.CalendarTaskEntity
import com.emberr.domain.quote.DailyQuote
import com.emberr.domain.quote.DailyQuoteLibrary
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.BOTTOM_BAR_PILL_SHRINK_COMPENSATION
import com.emberr.presentation.calendar.CalendarViewModel
import com.emberr.presentation.calendar.EventEditorSheetHost
import com.emberr.presentation.calendar.RecurrenceScopeChooser
import com.emberr.presentation.shared.UserSettings
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrTopHeaderBar
import com.emberr.presentation.shared.components.TopHeaderTitlePlacement
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.topHeaderBarPadding
import com.emberr.presentation.shared.components.TopBarIconButtonGroup
import com.emberr.presentation.shared.components.TopBarIconButtonItem
import com.emberr.presentation.shared.components.rememberKeyboardHandoff
import com.emberr.presentation.shared.editor.BlockStyleBar
import com.emberr.presentation.shared.rememberStableStatusBarsPadding
import com.emberr.presentation.sync.SyncViewModel
import com.emberr.domain.util.system.showNativeToast
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.calendar
import emberr.shared.generated.resources.ellipsis
import emberr.shared.generated.resources.history2
import org.jetbrains.compose.resources.painterResource

private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

@Composable
fun DailyScreen(
    onSelectionModeChange: (Boolean) -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    isCompact: Boolean = false,
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onNavigateToEditor: (String) -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToTrash: () -> Unit = {},
    showAddNoteDialog: Boolean = false,
    isSearchActive: Boolean = false,
    dateArg: String? = null,
    viewModel: DailyEditorViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel(),
    calendarViewModel: CalendarViewModel = koinViewModel()
) {
    val hazeState = remember { HazeState() }

    val allLinkableNotes by viewModel.allLinkableNotes.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val blocks by viewModel.visibleBlocks.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedBlockIds by viewModel.selectedBlockIds.collectAsState()
    val focusRequest by viewModel.focusRequest.collectAsState()
    val selectionRequest by viewModel.selectionRequest.collectAsState()
    var topBarBottomPx by remember { mutableFloatStateOf(0f) }
    val loadedDateString by viewModel.loadedDateString.collectAsState()
    val previewCache by viewModel.previewCache.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val pendingRecurringDeletion by viewModel.pendingRecurringDeletion.collectAsState()

    val initialDate = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val initialPage = remember { Int.MAX_VALUE / 2 }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })

    val isSelectionMode = selectedBlockIds.isNotEmpty()
    val selectedBlocksList = blocks.filter { it.id in selectedBlockIds }
    val isSelectionPinned = selectedBlocksList.isNotEmpty() && selectedBlocksList.all { it.isPinned }

    var showBlockStyleBar by remember { mutableStateOf(false) }
    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) showBlockStyleBar = false
    }

    var showCalendarSheet by remember { mutableStateOf(false) }
    var showTimelineDialog by remember { mutableStateOf(false) }
    val timelineDays by viewModel.timelineDays.collectAsState()
    val isTimelineLoading by viewModel.isTimelineLoading.collectAsState()

    // User Settings & Sync State
    var showSettingsMenu by remember { mutableStateOf(false) }
    val syncState by syncViewModel.syncStatus.collectAsState()

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

    // Lets a search result (or any other future deep link) jump straight to a specific day.
    // Mirrors what the in-screen calendar picker already does: calling viewModel.selectDate()
    // updates _selectedDate, which the LaunchedEffect(selectedDate) below picks up to scroll
    // the pager. Keyed on dateArg so re-tapping the same search result while already on this
    // screen re-triggers the jump even though the composable itself isn't recreated.
    LaunchedEffect(dateArg) {
        dateArg?.let { viewModel.selectDate(LocalDate.parse(it)) }
    }

    LaunchedEffect(syncState) {
        if (syncState != "Idle" && syncState != "Syncing...") {
            showNativeToast(syncState)
            syncViewModel.resetSyncStatus()
        }
    }

    val showToolbar = !isSelectionMode && !showAddNoteDialog && !showTimelineDialog &&
        !isSearchActive && isKeyboardOpen

    val globalTags by viewModel.globalTags.collectAsState()
    val calendarTaskMap by viewModel.calendarTaskMap.collectAsState()
    val databaseTemplates by viewModel.databaseTemplates.collectAsState()
    var showDatabasePicker by remember { mutableStateOf(false) }
    var showNoteLinkMenu by remember { mutableStateOf(false) }
    val handoff = rememberKeyboardHandoff()

    var eventOptionsTargetBlockId by remember { mutableStateOf<String?>(null) }
    var eventOptionsOccurrenceDate by remember { mutableStateOf<String?>(null) }

    SelectionModeObserver(isSelectionMode, onSelectionModeChange)

    KmpBackHandler(enabled = showSettingsMenu) {
        showSettingsMenu = false
    }
    KmpBackHandler(enabled = showCalendarSheet) {
        showCalendarSheet = false
    }
    KmpBackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.selectDate(initialDate.plus((page - initialPage).toLong(), DateTimeUnit.DAY))
            }
    }

    LaunchedEffect(selectedDate) {
        GlobalEditorState.currentlyFocusedBlockId = null
        val targetPage = initialPage + initialDate.daysUntil(selectedDate)
        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            if (abs(pagerState.currentPage - targetPage) > 3) {
                pagerState.scrollToPage(targetPage)
            } else {
                pagerState.animateScrollToPage(targetPage)
            }
        }
        val keepDates = (-2..2).map { offset ->
            selectedDate.plus(offset.toLong(), DateTimeUnit.DAY).toString()
        }.toSet()
        viewModel.evictPreviewCache(keepDates)
    }

    val sharedEditorActions = remember(viewModel, onOpenFile, handoff) {
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
                    "database" -> handoff.run { showDatabasePicker = true }
                    "linked_note" -> showNoteLinkMenu = true
                    else -> viewModel.insertNewMediaBlock(type)
                }
            }
            override fun onInsertLinkedNoteBlock(noteId: String) =
                viewModel.insertNewMediaBlock("linked_note", linkedNoteId = noteId)
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
                cellStyles: Map<String, com.emberr.domain.model.TableCellStyle>,
                rowStyles: Map<String, com.emberr.domain.model.TableCellStyle>,
                columnStyles: Map<String, com.emberr.domain.model.TableCellStyle>
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
            override fun onNoteLinkClick(noteId: String) = onNavigateToEditor(noteId)
            override fun onCreateLinkedNote(title: String): String {
                return viewModel.createLinkedNote(title)
            }
            override fun onOpenDatabaseNote(blockId: String, rowId: String, colId: String, existingNoteId: String?) {
                viewModel.openDatabaseNote(blockId, rowId, colId, existingNoteId) { resolvedNoteId ->
                    onNavigateToEditor(resolvedNoteId)
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

    val rightPanelContent = @Composable {
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

        val showToolbar = showToolbar || mobileMenuState != MobileMenuState.MAIN

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).background(MaterialTheme.colorScheme.background)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize(),
                    userScrollEnabled = false,
                    beyondViewportPageCount = 1,
                    key = { page ->
                        initialDate.plus(
                            (page - initialPage).toLong(),
                            DateTimeUnit.DAY
                        ).toString()
                    }
                ) { page ->
                    val pageDate = initialDate.plus((page - initialPage).toLong(), DateTimeUnit.DAY)
                    val pageDateString = pageDate.toString()

                    LaunchedEffect(pageDateString) {
                        viewModel.prefetchDateIfNeeded(pageDateString)
                    }

                    val isCurrentActivePage =
                        pageDate == selectedDate && loadedDateString == pageDateString

                    val displayBlocks: List<NoteBlock> = if (isCurrentActivePage) {
                        blocks
                    } else {
                        previewCache[pageDateString] ?: emptyList()
                    }

                    val pageListState = remember(pageDateString) { LazyListState() }

                    Box(modifier = Modifier.fillMaxSize()) {
                        EditorScreen(
                            blocks = displayBlocks,
                            allLinkableNotes = allLinkableNotes,
                            globalTags = globalTags,
                            actions = sharedEditorActions,
                            focusRequest = if (isCurrentActivePage) focusRequest else null,
                            selectionRequest = if (isCurrentActivePage) selectionRequest else null,
                            topBarClearancePx = topBarBottomPx,
                            listState = pageListState,
                            selectedBlockIds = selectedBlockIds,
                            mobileMenuState = mobileMenuState,
                            onMobileMenuStateChange = onMobileMenuStateChange,
                            slashQuery = slashQuery,
                            onSlashQueryChange = { slashQuery = it },
                            showNoteLinkMenu = showNoteLinkMenu,
                            onDismissNoteLinkMenu = { showNoteLinkMenu = false },
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
                            bottomContentPadding = bottomContentPadding +
                                if (bottomContentPadding > 0.dp) 60.dp else 0.dp,
                            isCurrentActivePage = isCurrentActivePage,
                            topContentPadding = rememberStableStatusBarsPadding().calculateTopPadding() + 72.dp,
                            onUndo = { viewModel.undo() },
                            onRedo = { viewModel.redo() },
                            emptyContent = {
                                DailyEmptyDayMessage(
                                    date = pageDate,
                                    topPadding = rememberStableStatusBarsPadding().calculateTopPadding() + 72.dp,
                                    bottomPadding = bottomContentPadding +
                                            if (bottomContentPadding > 0.dp) 40.dp else 0.dp
                                )
                            }
                        )
                    }
                }
            }

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
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp, start = 6.dp, end = 6.dp)
            ) {
                EditorToolbar(
                    mobileMenuState = mobileMenuState,
                    onMenuStateChange = onMobileMenuStateChange,
                    query = slashQuery,
                    hazeState = hazeState,
                    onChangeBlockType = { sharedEditorActions.onChangeBlockType(it) },
                    onToggleFormat = { sharedEditorActions.onToggleFormat(it) },
                    onAdjustIndentation = { sharedEditorActions.onAdjustIndentation(it) },
                    onSetAlignment = { sharedEditorActions.onSetBlockAlignment(it) },
                    onInsertMediaBlock = { sharedEditorActions.onInsertMediaBlock(it) },
                    allLinkableNotes = allLinkableNotes,
                    mentionQuery = mentionQuery ?: "",
                    onMentionNoteSelected = { noteId ->
                        val note = allLinkableNotes.find { it.noteId == noteId }
                        val safeTitle = (note?.title ?: "").replace("[", "").replace("]", "").ifEmpty { "Untitled" }
                        EditorEventBus.confirmMentionEvent.tryEmit(safeTitle to noteId)
                    },
                    onMentionCreateNote = { title ->
                        val safeTitle = title.replace("[", "").replace("]", "").trim().ifEmpty { "Untitled" }
                        val newNoteId = sharedEditorActions.onCreateLinkedNote(safeTitle)
                        EditorEventBus.confirmMentionEvent.tryEmit(safeTitle to newNoteId)
                    },
                    onMentionCreateBlank = {
                        val newNoteId = sharedEditorActions.onCreateLinkedNote("Untitled")
                        EditorEventBus.confirmMentionAndOpenEvent.tryEmit("Untitled" to newNoteId)
                    },
                    onNoteLinkSelected = { noteId -> sharedEditorActions.onInsertLinkedNoteBlock(noteId) },
                    onNoteLinkCreateNote = { title ->
                        val newNoteId = sharedEditorActions.onCreateLinkedNote(title)
                        sharedEditorActions.onInsertLinkedNoteBlock(newNoteId)
                    },
                    onNoteLinkCreateBlank = {
                        val newNoteId = sharedEditorActions.onCreateLinkedNote("Untitled")
                        sharedEditorActions.onInsertLinkedNoteBlock(newNoteId)
                        sharedEditorActions.onNoteLinkClick(newNoteId)
                    },
                    onSelectCurrentBlock = {
                        GlobalEditorState.currentlyFocusedBlockId?.let { id ->
                            sharedEditorActions.onToggleSelection(id)
                        }
                    },
                    onClearSlashQuery = { sharedEditorActions.onClearSlashQuery() },
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
                    .navigationBarsPadding(),
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
                    isVisible = isSelectionMode,
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
                    onTogglePin = { sharedEditorActions.onTogglePin() },
                    isSelectionPinned = isSelectionPinned,
                    selectedBlocks = selectedBlocksList,
                    onUpdateLinkedNoteOptions = { id, showIcon, showCoverImage -> viewModel.updateLinkedNoteOptions(id, showIcon, showCoverImage) },
                    showStyleButton = true,
                    isStyleBarOpen = showBlockStyleBar,
                    onToggleStyleBar = { showBlockStyleBar = !showBlockStyleBar },
                    hazeState = hazeState
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
                },
                calendarViewModel = calendarViewModel
            )

            if (pendingRecurringDeletion != null) {
                RecurrenceScopeChooser(
                    onDismiss = { viewModel.dismissRecurringDeletion() },
                    onScopeSelected = { scope -> viewModel.confirmRecurringDeletion(scope) }
                )
            }
        }
    }

    var weekStripBottomOffset by remember { mutableStateOf(bottomContentPadding) }
    LaunchedEffect(bottomContentPadding) {
        if (bottomContentPadding > 0.dp) weekStripBottomOffset = bottomContentPadding
    }
    val weekStripCompactDrop by animateDpAsState(
        targetValue = if (isCompact) BOTTOM_BAR_PILL_SHRINK_COMPENSATION else 0.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
    )
    val weekStripHideDistancePx = with(density) { (weekStripBottomOffset + 8.dp).roundToPx() }
    val isBottomBarOnScreen = bottomContentPadding > 0.dp

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    rightPanelContent()
                }

                AnimatedVisibility(
                    visible = !isSelectionMode && !isKeyboardOpen && isBottomBarOnScreen,
                    enter = slideInVertically(
                        initialOffsetY = { it + weekStripHideDistancePx },
                        animationSpec = tween(durationMillis = 250, delayMillis = 100, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(durationMillis = 250, delayMillis = 100)),
                    exit = slideOutVertically(
                        targetOffsetY = { it + weekStripHideDistancePx },
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(1f)
                        .padding(bottom = weekStripBottomOffset + 8.dp - weekStripCompactDrop)
                ) {
                    DailyBottomWeekStrip(
                        selectedDate = selectedDate,
                        onDateSelected = { viewModel.selectDate(it) },
                        hazeState = hazeState,
                        isCompact = isCompact
                    )
                }

                EmberrTopHeaderBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(2f)
                        .pointerInput(Unit) { detectTapGestures {} },
                    title = dailyHeaderTitle(selectedDate),
                    titlePlacement = TopHeaderTitlePlacement.Start,
                    titleStyle = MaterialTheme.typography.titleLarge,
                    titleColor = MaterialTheme.colorScheme.onBackground,
                    titlePadding = PaddingValues(top = 10.dp, bottom = 8.dp),
                    onTitleClick = { showCalendarSheet = true },
                    showBackButton = false,
                    reserveBackButtonSpace = false,
                    hazeState = hazeState,
                    applyStatusBarPadding = true,
                    contentPadding = topHeaderBarPadding(top = 8.dp, bottom = 14.dp),
                    onPositioned = { topBarBottomPx = it.positionInRoot().y + it.size.height },
                    actions = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TopBarIconButton(
                                icon = painterResource(Res.drawable.history2),
                                contentDescription = "Open timeline",
                                bgColor = Color.Transparent,
                                tint = MaterialTheme.colorScheme.primary,
                                hazeState = hazeState,
                                hazeStyle = EmberrBlur.Regular,
                                onClick = {
                                    viewModel.loadTimeline()
                                    showTimelineDialog = true
                                }
                            )

                            Box {
                                TopBarIconButtonGroup(
                                    bgColor = Color.Transparent,
                                    tint = MaterialTheme.colorScheme.primary,
                                    hazeState = hazeState,
                                    hazeStyle = EmberrBlur.Regular,
                                    items = listOf(
                                        TopBarIconButtonItem(
                                            icon = painterResource(Res.drawable.calendar),
                                            contentDescription = "Open Calendar",
                                            onClick = onNavigateToCalendar
                                        ),
                                        TopBarIconButtonItem(
                                            icon = painterResource(Res.drawable.ellipsis),
                                            contentDescription = "Settings",
                                            onClick = { showSettingsMenu = true }
                                        )
                                    )
                                )

                                UserSettings(
                                    expanded = showSettingsMenu,
                                    onDismiss = { showSettingsMenu = false },
                                    onNavigateToSettings = onNavigateToSettings,
                                    onNavigateToTrash = onNavigateToTrash
                                )
                            }
                        }
                    }
                )
            }

            if (showTimelineDialog) {
                DailyTimelineDialog(
                    days = timelineDays,
                    isLoading = isTimelineLoading,
                    anchorDate = selectedDate,
                    today = initialDate,
                    editorActions = sharedEditorActions,
                    globalTags = globalTags,
                    allLinkableNotes = allLinkableNotes,
                    onDismiss = {
                        showTimelineDialog = false
                        viewModel.clearTimeline()
                    },
                    onBlockClick = { date, blockId ->
                        showTimelineDialog = false
                        viewModel.clearTimeline()
                        viewModel.openTimelineBlock(date, blockId)
                    }
                )
            }

            if (showCalendarSheet) {
                DailyCalendarSheet(
                    selectedDate = selectedDate,
                    initialDate = initialDate,
                    calendarTaskMap = calendarTaskMap,
                    onDismiss = { showCalendarSheet = false },
                    onDateSelected = { viewModel.selectDate(it) },
                    onGoToToday = { viewModel.selectDate(initialDate) }
                )
            }
        }
    }
}

private fun dailyHeaderTitle(selectedDate: LocalDate): String {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    if (selectedDate == today) return "Today"

    val shortDayName = selectedDate.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$shortDayName ${selectedDate.day}"
}

@Composable
private fun DailyCalendarSheet(
    selectedDate: LocalDate,
    initialDate: LocalDate,
    calendarTaskMap: Map<LocalDate, List<CalendarTaskEntity>>,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onGoToToday: () -> Unit
) {
    EmberrBottomSheet(
        expanded = true,
        onDismiss = onDismiss,
        title = null,
        subtitle = null
    ) { closeAnd ->
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            BottomSheetMonthCalendar(
                selectedDate = selectedDate,
                today = initialDate,
                taskMap = calendarTaskMap,
                onDateSelected = {
                    onDateSelected(it)
                    closeAnd { onDismiss() }
                },
                onGoToToday = {
                    onGoToToday()
                    closeAnd { onDismiss() }
                }
            )

            EmberrButtonPrimary(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 20.dp)
            )
        }
    }
}

@Composable
fun DailyEmptyDayMessage(
    date: LocalDate,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp
) {
    val quoteOfTheDay by produceState<DailyQuote?>(initialValue = null, key1 = date) {
        value = DailyQuoteLibrary.quoteForDate(date)
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val halfwayBetweenQuoteAndBottom = BiasAlignment(horizontalBias = 0f, verticalBias = 0.5f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        AnimatedVisibility(
            visible = quoteOfTheDay != null,
            enter = fadeIn(tween(durationMillis = 350)),
            exit = fadeOut(tween(durationMillis = 150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            quoteOfTheDay?.let { quote ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isDesktopPlatform) 72.dp else 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = quote.text,
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = textColor.copy(alpha = 0.55f),
                        textAlign = TextAlign.Center
                    )
                    if (quote.author.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "— ${quote.author}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Text(
            text = if (isDesktopPlatform) "Double click to write your thoughts" else "Double tap to write your thoughts",
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(halfwayBetweenQuoteAndBottom)
                .padding(horizontal = 24.dp)
        )
    }
}
