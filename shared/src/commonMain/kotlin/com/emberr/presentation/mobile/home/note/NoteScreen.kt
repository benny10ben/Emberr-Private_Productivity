package com.emberr.presentation.mobile.home.note

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import com.emberr.presentation.shared.stableStatusBarsPadding
import com.emberr.presentation.shared.editor.BlockSelectionMenuContent
import com.emberr.presentation.shared.editor.BlockSelectionPill
import com.emberr.presentation.shared.editor.EditorScreen
import com.emberr.presentation.shared.editor.EditorActions
import com.emberr.presentation.shared.editor.SelectionModeObserver
import com.emberr.presentation.shared.editor.blockViews.databaseBlockView.DatabaseTemplatePickerSheet
import com.emberr.presentation.calendar.EventEditorSheetHost
import com.emberr.presentation.calendar.RecurrenceScopeChooser
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.ViewType
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.editor.EditorToolbar
import dev.chrisbanes.haze.HazeState
import coil3.compose.AsyncImage
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.editor.GlobalEditorState
import com.emberr.presentation.shared.editor.MobileMenuState
import com.emberr.presentation.shared.editor.EditorEventBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.repository.EmojiRepository
import com.emberr.domain.util.system.showFeedback
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.StickyNoteWindowBus
import com.emberr.presentation.shared.SubNoteOpenMode
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.painter.Painter
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.presentation.shared.editor.BlockStyleBar
import com.emberr.presentation.shared.components.rememberKeyboardHandoff
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import emberr.shared.generated.resources.code
import emberr.shared.generated.resources.copy
import emberr.shared.generated.resources.ellipsis
import emberr.shared.generated.resources.file_pdf
import emberr.shared.generated.resources.file_code_corner
import emberr.shared.generated.resources.image
import emberr.shared.generated.resources.share
import emberr.shared.generated.resources.ghost_smile
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.square_arrow_out_up_right
import emberr.shared.generated.resources.textalign_center2
import emberr.shared.generated.resources.textalign_justifycenter2
import emberr.shared.generated.resources.textalign_left2
import emberr.shared.generated.resources.textalign_right2
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

enum class MenuLevel { MAIN, EXPORT, ICON, COVER }

private val WordCountPillShape = RoundedCornerShape(20.dp)

private object NoRippleIndicationNodeFactory : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun equals(other: Any?) = other === this
    override fun hashCode(): Int = -1
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit = {},
    onPickImage: (onPathSelected: (String) -> Unit) -> Unit = {},
    onTakePhoto: (onPathSelected: (String) -> Unit) -> Unit = {},
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit = {},
    onOpenFile: (filePath: String, mimeType: String) -> Unit = { _, _ -> },
    onNavigateToEditor: (String) -> Unit = {},
    showBackButton: Boolean = true,
    isStickyNote: Boolean = false,
    onExportMarkdown: (fileName: String, content: String) -> Unit = { _, _ -> },
    onExportPdf: (fileName: String, title: String, blocks: List<NoteBlock>) -> Unit = { _, _, _ -> },
    externalHazeState: HazeState? = null,
    topBarBgColor: Color? = null,
    topBarContentColor: Color? = null,
    desktopTopMargin: Dp = 0.dp,
    isSearchDialogOpen: Boolean = false,
    viewModel: NoteEditorViewModel = koinViewModel(key = noteId)
) {

    val hazeState = externalHazeState ?: remember { HazeState() }
    val clipboardManager = LocalClipboardManager.current
    val blocks by viewModel.visibleBlocks.collectAsState()
    val noteTitle by viewModel.noteTitle.collectAsState()
    val selectedBlockIds by viewModel.selectedBlockIds.collectAsState()
    val focusRequest by viewModel.focusRequest.collectAsState()
    val selectionRequest by viewModel.selectionRequest.collectAsState()
    val noteIcon by viewModel.noteIcon.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val coverImagePath by viewModel.coverImagePath.collectAsState()
    val isEditingTemplate by viewModel.isTemplate.collectAsState()

    // Word Count States
    val showWordCount by viewModel.showWordCount.collectAsState()
    val wordCount by viewModel.wordCount.collectAsState()

    val blockAlignment by viewModel.blockAlignment.collectAsState()

    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val pendingRecurringDeletion by viewModel.pendingRecurringDeletion.collectAsState()

    var mobileMenuState by remember { mutableStateOf(MobileMenuState.MAIN) }
    var slashQuery by remember { mutableStateOf("") }
    var mentionQuery by remember { mutableStateOf<String?>(null) }

    val onMobileMenuStateChange: (MobileMenuState) -> Unit = { newState ->
        if (mobileMenuState == MobileMenuState.MENTION && newState != MobileMenuState.MENTION) {
            EditorEventBus.cancelMentionEvent.tryEmit(Unit)
        }
        mobileMenuState = newState
    }

    val allLinkableNotes by viewModel.allLinkableNotes.collectAsState()

    LaunchedEffect(noteId) { viewModel.loadNote(noteId) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, noteId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadNote(noteId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showIconPicker by remember { mutableStateOf(false) }

    val isSelectionMode = selectedBlockIds.isNotEmpty()
    val selectedBlocksList = blocks.filter { it.id in selectedBlockIds }
    val isSelectionPinned = selectedBlocksList.isNotEmpty() && selectedBlocksList.all { it.isPinned }

    var showBlockStyleBar by remember { mutableStateOf(false) }
    LaunchedEffect(isSelectionMode) {
        if (!isSelectionMode) showBlockStyleBar = false
    }

    var showOptionsMenu by remember { mutableStateOf(false) }
    var subNotePanelId by remember { mutableStateOf<String?>(null) }
    val settingsManager: SettingsManager = org.koin.compose.koinInject()
    val subNoteOpenModeName by settingsManager.subNoteOpenModeFlow.collectAsState(
        initial = SyncConstants.DEFAULT_SUBNOTE_OPEN_MODE
    )
    val subNoteOpenMode = runCatching { SubNoteOpenMode.valueOf(subNoteOpenModeName) }
        .getOrDefault(SubNoteOpenMode.SIDE_PANEL)
    val globalTags by viewModel.globalTags.collectAsState()
    val databaseTemplates by viewModel.databaseTemplates.collectAsState()
    var showDatabasePicker by remember { mutableStateOf(false) }
    var showNoteLinkMenu by remember { mutableStateOf(false) }
    var eventOptionsTargetBlockId by remember { mutableStateOf<String?>(null) }
    var eventOptionsOccurrenceDate by remember { mutableStateOf<String?>(null) }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    var isKeyboardOpen by remember { mutableStateOf(false) }
    var previousImeBottom by remember { mutableIntStateOf(0) }

    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) return@LaunchedEffect
        delay(250.milliseconds)
        if (!isKeyboardOpen && mobileMenuState != MobileMenuState.MAIN) {
            onMobileMenuStateChange(MobileMenuState.MAIN)
        }
    }

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

    val showToolbar = !isSelectionMode && !isSearchDialogOpen &&
        (isKeyboardOpen || isDesktopPlatform || mobileMenuState != MobileMenuState.MAIN)

    SelectionModeObserver(isSelectionMode, onSelectionModeChange)

    KmpBackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    KmpBackHandler(enabled = mobileMenuState != MobileMenuState.MAIN) {
        onMobileMenuStateChange(MobileMenuState.MAIN)
    }

    val isLoading by viewModel.isLoading.collectAsState()

    val scope = rememberCoroutineScope()
    val handoff = rememberKeyboardHandoff()

    val handleToggleFavorite: () -> Unit = {
        showOptionsMenu = false
        scope.launch { if (!isDesktopPlatform) delay(250.milliseconds); viewModel.toggleFavorite() }
    }
    val handleToggleWordCount: () -> Unit = {
        showOptionsMenu = false
        viewModel.toggleWordCount()
    }
    val handleSetAlignment: (TextAlignment) -> Unit = { viewModel.setAllBlocksAlignment(it) }
    val handleAddIcon: () -> Unit = {
        showOptionsMenu = false
        showIconPicker = true
    }
    val handleRemoveIcon: () -> Unit = {
        showOptionsMenu = false
        scope.launch { if (!isDesktopPlatform) delay(250.milliseconds); viewModel.updateIcon(null) }
    }
    val handleAddCover: () -> Unit = {
        showOptionsMenu = false
        scope.launch {
            delay((if (!isDesktopPlatform) 250L else 100L).milliseconds)
            withContext(Dispatchers.IO) {
                onPickImage { path -> viewModel.handleCoverImagePicked(path) }
            }
        }
    }
    val handleRemoveCover: () -> Unit = {
        showOptionsMenu = false
        scope.launch { if (!isDesktopPlatform) delay(250.milliseconds); viewModel.removeCoverImage() }
    }
    val handleMoveToTrash: () -> Unit = {
        showOptionsMenu = false
        scope.launch { if (!isDesktopPlatform) delay(250.milliseconds); viewModel.moveToTrash { onNavigateBack() } }
    }

    val handleCopyPlain: () -> Unit = {
        showOptionsMenu = false
        val text = viewModel.generatePlainTextExport()
        clipboardManager.setText(AnnotatedString(text))
        showFeedback("Copied to clipboard")
    }

    // Copy and Download both render through the same engine the AI vault uses:
    // shared/src/commonMain/kotlin/com/emberr/domain/vault/NoteMarkdownWriter.kt
    val handleCopyMarkdown: () -> Unit = {
        showOptionsMenu = false
        scope.launch {
            val md = viewModel.generateMarkdownExport()
            clipboardManager.setText(AnnotatedString(md))
            showFeedback("Copied as Markdown")
        }
    }

    val handleDownloadMarkdown: () -> Unit = {
        showOptionsMenu = false
        scope.launch {
            val safeTitle = noteTitle.ifBlank { "Untitled_Note" }.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val content = viewModel.generateMarkdownExport()
            onExportMarkdown("$safeTitle.md", content)
        }
    }

    val handleDownloadPdf: () -> Unit = {
        showOptionsMenu = false
        scope.launch {
            delay(300.milliseconds)
            val safeTitle = noteTitle.ifBlank { "Untitled_Note" }.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            onExportPdf("$safeTitle.pdf", noteTitle, blocks)
        }
    }

    val editorListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var titleTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var topBarBottomPx by remember { mutableFloatStateOf(0f) }
    val collapseRangePx = with(density) { 32.dp.toPx() }
    var baselineDistancePx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val isAtScrollTop by remember {
        derivedStateOf {
            editorListState.firstVisibleItemIndex == 0 && editorListState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(isAtScrollTop, titleTopPx, topBarBottomPx) {
        if (isAtScrollTop) {
            baselineDistancePx = titleTopPx - topBarBottomPx
        }
    }
    val titleCollapseProgress by remember {
        derivedStateOf {
            val distance = titleTopPx - topBarBottomPx
            val scrolledPx = (baselineDistancePx - distance).coerceAtLeast(0f)
            (scrolledPx / collapseRangePx).coerceIn(0f, 1f)
        }
    }
    val onCollapsedTitleClick: () -> Unit = {
        scope.launch { editorListState.animateScrollToItem(0) }
    }

    val editorActions = remember(viewModel, onOpenFile, handoff) {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .consumeWindowInsets(PaddingValues(bottom = paddingValues.calculateBottomPadding()))
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                EditorScreen(
                    blocks = blocks,
                    allLinkableNotes = allLinkableNotes,
                    actions = editorActions,
                    focusRequest = focusRequest,
                    selectionRequest = selectionRequest,
                    topBarClearancePx = topBarBottomPx,
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
                            onTogglePin = { viewModel.togglePinSelectedBlocks() },
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
                    headerContent = {
                        NoteHeader(
                            noteIcon = noteIcon,
                            noteTitle = noteTitle,
                            coverImagePath = coverImagePath,
                            showIconPicker = showIconPicker,
                            onDismissIconPicker = { showIconPicker = false },
                            onIconChange = { viewModel.updateIcon(it) },
                            onTitleChange = { viewModel.updateTitle(it) },
                            onIconClick = { showIconPicker = true },
                            onTitlePositioned = { titleTopPx = it.positionInRoot().y }
                        )
                    },
                    globalTags = globalTags,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState),
                    listState = editorListState,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() }
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
                        onClearSlashQuery = { editorActions.onClearSlashQuery() },
                        mobileMenuState = mobileMenuState,
                        onMenuStateChange = onMobileMenuStateChange,
                        query = slashQuery,
                        hazeState = hazeState,
                        onChangeBlockType = { editorActions.onChangeBlockType(it) },
                        onToggleFormat = { editorActions.onToggleFormat(it) },
                        onAdjustIndentation = { editorActions.onAdjustIndentation(it) },
                        onSetAlignment = { editorActions.onSetBlockAlignment(it) },
                        onInsertMediaBlock = { editorActions.onInsertMediaBlock(it) },
                        allLinkableNotes = allLinkableNotes,
                        mentionQuery = mentionQuery ?: "",
                        onMentionNoteSelected = { noteId ->
                            val note = allLinkableNotes.find { it.noteId == noteId }
                            val safeTitle = (note?.title ?: "").replace("[", "").replace("]", "").ifEmpty { "Untitled" }
                            EditorEventBus.confirmMentionEvent.tryEmit(safeTitle to noteId)
                        },
                        onMentionCreateNote = { title ->
                            val safeTitle = title.replace("[", "").replace("]", "").trim().ifEmpty { "Untitled" }
                            val newNoteId = editorActions.onCreateLinkedNote(safeTitle)
                            EditorEventBus.confirmMentionEvent.tryEmit(safeTitle to newNoteId)
                        },
                        onMentionCreateBlank = {
                            val newNoteId = editorActions.onCreateLinkedNote("Untitled")
                            EditorEventBus.confirmMentionAndOpenEvent.tryEmit("Untitled" to newNoteId)
                        },
                        onNoteLinkSelected = { noteId -> editorActions.onInsertLinkedNoteBlock(noteId) },
                        onNoteLinkCreateNote = { title ->
                            val newNoteId = editorActions.onCreateLinkedNote(title)
                            editorActions.onInsertLinkedNoteBlock(newNoteId)
                        },
                        onNoteLinkCreateBlank = {
                            val newNoteId = editorActions.onCreateLinkedNote("Untitled")
                            editorActions.onInsertLinkedNoteBlock(newNoteId)
                            editorActions.onNoteLinkClick(newNoteId)
                        },
                        onSelectCurrentBlock = {
                            GlobalEditorState.currentlyFocusedBlockId?.let { id ->
                                editorActions.onToggleSelection(id)
                            }
                        },
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        showHistory = true
                    )
                }

                // Word Count Overlay
                AnimatedVisibility(
                    visible = showWordCount && !isSelectionMode,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .imePadding()
                        .then(if (isDesktopPlatform) Modifier else Modifier.navigationBarsPadding())
                        .padding(
                            bottom = if (isDesktopPlatform) {14.dp} else {if (showToolbar) 60.dp else 15.dp},
                            end = 16.dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .clip(WordCountPillShape)
                            .emberrBlur(hazeState, EmberrBlur.Regular)
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = WordCountPillShape
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "$wordCount words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }

                NoteTopBar(
                    showOptionsMenu = showOptionsMenu,
                    showBackButton = showBackButton,
                    isEditingTemplate = isEditingTemplate,
                    onDismissOptionsMenu = { showOptionsMenu = false },
                    hazeState = hazeState,
                    topBarBgColor = topBarBgColor,
                    topBarContentColor = topBarContentColor,
                    collapsedTitle = noteTitle.ifBlank { "Untitled" },
                    collapsedTitleProgress = titleCollapseProgress,
                    onCollapsedTitleClick = onCollapsedTitleClick,
                    topMargin = desktopTopMargin,
                    onPositioned = { topBarBottomPx = it.positionInRoot().y + it.size.height },
                    onBackClick = {
                        if (isSelectionMode) {
                            viewModel.clearSelection()
                        } else {
                            onNavigateBack()
                        }
                    },
                    onOptionsClick = { showOptionsMenu = true },
                    desktopMenuContent = {
                        NoteOptionsDesktopMenu(
                            isFavorite = isFavorite,
                            hasIcon = noteIcon != null,
                            hasCover = coverImagePath != null,
                            showWordCount = showWordCount,
                            blockAlignment = blockAlignment,
                            onDismiss = { showOptionsMenu = false },
                            onToggleFavorite = handleToggleFavorite,
                            onAddIcon = handleAddIcon,
                            onRemoveIcon = handleRemoveIcon,
                            isEditingTemplate = isEditingTemplate,
                            onAddCover = handleAddCover,
                            onRemoveCover = handleRemoveCover,
                            onToggleWordCount = handleToggleWordCount,
                            onSetAlignment = handleSetAlignment,
                            onMoveToTrash = handleMoveToTrash,
                            onCopyPlain = handleCopyPlain,
                            onCopyMarkdown = handleCopyMarkdown,
                            onDownloadMarkdown = handleDownloadMarkdown,
                            onDownloadPdf = handleDownloadPdf,
                            showStickyNoteOption = !isStickyNote,
                            onOpenAsStickyNote = {
                                showOptionsMenu = false
                                StickyNoteWindowBus.open(noteId)
                            }
                        )
                    }
                )

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
                        onCut = { clipboardManager.setText(AnnotatedString(viewModel.cutSelectedBlocks())) },
                        onAddBlockAbove = { viewModel.addBlockAboveSelection() },
                        onAddBlockBelow = { viewModel.addBlockBelowSelection() },
                        onDelete = { viewModel.deleteSelectedBlocks() },
                        onTogglePin = { viewModel.togglePinSelectedBlocks() },
                        isSelectionPinned = isSelectionPinned,
                        selectedBlocks = selectedBlocksList,
                        onUpdateLinkedNoteOptions = { id, showIcon, showCoverImage -> viewModel.updateLinkedNoteOptions(id, showIcon, showCoverImage) },
                        showStyleButton = true,
                        isStyleBarOpen = showBlockStyleBar,
                        onToggleStyleBar = { showBlockStyleBar = !showBlockStyleBar },
                        hazeState = hazeState
                    )
                }

                if (!isDesktopPlatform) {
                    NoteOptionsBottomSheet(
                        expanded = showOptionsMenu,
                        isFavorite = isFavorite,
                        hasIcon = noteIcon != null,
                        hasCover = coverImagePath != null,
                        showWordCount = showWordCount,
                        blockAlignment = blockAlignment,
                        onDismiss = { showOptionsMenu = false },
                        onToggleFavorite = handleToggleFavorite,
                        onAddIcon = handleAddIcon,
                        onRemoveIcon = handleRemoveIcon,
                        isEditingTemplate = isEditingTemplate,
                        onAddCover = handleAddCover,
                        onRemoveCover = handleRemoveCover,
                        onToggleWordCount = handleToggleWordCount,
                        onSetAlignment = handleSetAlignment,
                        onMoveToTrash = handleMoveToTrash,
                        onCopyPlain = handleCopyPlain,
                        onCopyMarkdown = handleCopyMarkdown,
                        onDownloadMarkdown = handleDownloadMarkdown,
                        onDownloadPdf = handleDownloadPdf
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

                if (subNotePanelId != null) {
                    SubNotePanel(
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

                if (!isDesktopPlatform) {
                    EmberrBottomSheet(
                        expanded = showIconPicker,
                        onDismiss = { showIconPicker = false },
                        title = "Choose Icon",
                    ) { _ ->
                      CompositionLocalProvider(
                        LocalIndication provides NoRippleIndicationNodeFactory,
                        LocalRippleConfiguration provides null
                      ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(450.dp)
                            ) {
                                CategorizedEmojiPicker(
                                    onEmojiSelected = { emoji ->
                                        viewModel.updateIcon(emoji)
                                        showIconPicker = false
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            EmberrButtonPrimary(
                                text = "Close",
                                onClick = { showIconPicker = false },
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 20.dp)
                            )
                        }
                      }
                    }
                }
            }
        }
    }
}

// Note header — cover image, icon, title
@Composable
private fun NoteHeader(
    noteIcon: String?,
    noteTitle: String,
    coverImagePath: String?,
    showIconPicker: Boolean,
    onDismissIconPicker: () -> Unit,
    onIconChange: (String?) -> Unit,
    onTitleChange: (String) -> Unit,
    onIconClick: () -> Unit,
    onTitlePositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {}
) {
    val mediaStorageHelper: com.emberr.domain.util.media.MediaStorageHelper = org.koin.compose.koinInject()

    val topPadding by animateDpAsState(
        targetValue = if (noteIcon != null) 48.dp else 16.dp,
        label = "TopPadding"
    )

    Column(modifier = Modifier.fillMaxWidth()) {

        Box(modifier = Modifier.fillMaxWidth()) {

            if (coverImagePath != null || noteIcon != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (coverImagePath != null) {
                        val absolutePath = mediaStorageHelper.getAbsoluteMediaPath(coverImagePath)
                        val file = java.io.File(absolutePath)

                        val context = coil3.compose.LocalPlatformContext.current
                        val request = remember(absolutePath) {
                            coil3.request.ImageRequest.Builder(context)
                                .data(file)
                                .memoryCacheKey(absolutePath)
                                .diskCacheKey(absolutePath)
                                .build()
                        }

                        AsyncImage(
                            model = request,
                            contentDescription = "Cover Image",
                            modifier = Modifier.fillMaxWidth().height(210.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .stableStatusBarsPadding()
                                .height(120.dp)
                        )
                    }

                    if (noteIcon != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp)
                                .graphicsLayer {
                                    translationY = 36.dp.toPx()
                                }
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onIconClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = noteIcon,
                                style = TextStyle(
                                    fontSize = 58.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 58.sp
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .stableStatusBarsPadding()
                        .height(56.dp)
                )
            }

            if (isDesktopPlatform) {
                EmberrDesktopMenu(
                    expanded = showIconPicker,
                    onDismissRequest = onDismissIconPicker
                ) {
                    Box(modifier = Modifier.size(width = 340.dp, height = 380.dp)) {
                        CategorizedEmojiPicker(
                            onEmojiSelected = { emoji ->
                                onIconChange(emoji)
                                onDismissIconPicker()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (isDesktopPlatform) 28.dp else 16.dp)
        ) {
            Spacer(modifier = Modifier.height(topPadding))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .onGloballyPositioned(onTitlePositioned)
            ) {
                val titleStyle = MaterialTheme.typography.titleLarge.let {
                    it.copy(fontSize = it.fontSize * 1.5f, lineHeight = it.lineHeight * 1.2f)
                }
                NoteTitleField(
                    noteTitle = noteTitle,
                    onTitleChange = onTitleChange,
                    style = titleStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun NoteTitleField(
    noteTitle: String,
    onTitleChange: (String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    Box(modifier = modifier) {
        if (noteTitle.isEmpty()) {
            Text(
                text = "Untitled",
                style = style,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
        }
        BasicTextField(
            value = noteTitle,
            onValueChange = { onTitleChange(it) },
            textStyle = style.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine
        )
    }
}

// CategorizedEmojiPicker
@Composable
fun CategorizedEmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!EmojiRepository.isLoaded) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        return
    }

    val categoryNames = EmojiRepository.categories
    val flatEmojiList = EmojiRepository.flatList
    val categoryEmojiLists = EmojiRepository.categoryEmojiLists

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(200.milliseconds)
        val q = searchQuery.lowercase()
        searchResults = withContext(Dispatchers.Default) {
            flatEmojiList.mapNotNullTo(ArrayList(32)) { (emoji, keywords) ->
                emoji.takeIf { keywords.contains(q) }
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { categoryNames.size })
    val coroutineScope = rememberCoroutineScope()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val onTabClick: (Int) -> Unit = remember(pagerState, coroutineScope) {
        { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(bottom = 4.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search emojis...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (searchQuery.isNotEmpty()) {
            if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No emojis found",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = navBarPadding + 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = searchResults,
                        key = { it },
                        contentType = { "emoji_item" }
                    ) { emoji ->
                        EmojiGridItem(emoji = emoji, onClick = { onEmojiSelected(emoji) })
                    }
                }
            }
        } else {
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                edgePadding = 8.dp,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(pagerState.currentPage),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {}
            ) {
                categoryNames.forEachIndexed { index, title ->
                    val selected = pagerState.currentPage == index
                    Tab(
                        selected = selected,
                        onClick = { onTabClick(index) },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val activeEmojis = categoryEmojiLists[page]

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = navBarPadding + 16.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = activeEmojis,
                        key = { it },
                        contentType = { "emoji_item" }
                    ) { emoji ->
                        EmojiGridItem(emoji = emoji, onClick = { onEmojiSelected(emoji) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiGridItem(emoji: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )
    }
}

// Top bar (back + options). isEditingTemplate adds a centered pill so users don't mistake a
// template for a regular note (templates never show up in the normal notes list/search).
@Composable
private fun NoteTopBar(
    onBackClick: () -> Unit,
    onOptionsClick: () -> Unit,
    showBackButton: Boolean = true,
    showOptionsMenu: Boolean = false,
    isEditingTemplate: Boolean = false,
    hazeState: HazeState? = null,
    topBarBgColor: Color? = null,
    topBarContentColor: Color? = null,
    onDismissOptionsMenu: () -> Unit = {},
    desktopMenuContent: @Composable () -> Unit = {},
    collapsedTitle: String = "",
    collapsedTitleProgress: Float = 0f,
    onCollapsedTitleClick: () -> Unit = {},
    topMargin: Dp = 0.dp,
    onPositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {}
) {
    val defaultContentColor = topBarContentColor ?: MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isDesktopPlatform) Modifier else Modifier.stableStatusBarsPadding())
            .padding(top = topMargin + if (isDesktopPlatform) 16.dp else 10.dp).padding(horizontal = if (isDesktopPlatform) 22.dp else 16.dp)
            .onGloballyPositioned(onPositioned),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                TopBarIconButton(
                    icon = painterResource(Res.drawable.chevron_left),
                    contentDescription = "Back",
                    bgColor = Color.Transparent,
                    tint = MaterialTheme.colorScheme.primary,
                    hazeState = hazeState,
                    hazeStyle = EmberrBlur.Regular,
                    onClick = onBackClick
                )
            } else {
                Spacer(Modifier.size(1.dp))
            }

            if (!isEditingTemplate && collapsedTitleProgress > 0f) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer { alpha = collapsedTitleProgress }
                        .clickable(onClick = onCollapsedTitleClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = collapsedTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = defaultContentColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Box {
                TopBarIconButton(
                    icon = painterResource(Res.drawable.ellipsis),
                    contentDescription = "Options",
                    bgColor = Color.Transparent,
                    tint = MaterialTheme.colorScheme.primary,
                    hazeState = hazeState,
                    hazeStyle = EmberrBlur.Regular,
                    onClick = onOptionsClick
                )

                if (isDesktopPlatform) {
                    EmberrDesktopMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = onDismissOptionsMenu
                    ) {
                        desktopMenuContent()
                    }
                }
            }
        }

        if (isEditingTemplate) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .emberrBlur(hazeState, EmberrBlur.Regular)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                Text(
                    text = "Editing Template",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// Options menus (BottomSheet & Desktop Popup)
@Composable
fun NoteOptionsDesktopMenu(
    isFavorite: Boolean,
    hasIcon: Boolean,
    hasCover: Boolean,
    showWordCount: Boolean,
    blockAlignment: TextAlignment?,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddIcon: () -> Unit,
    onRemoveIcon: () -> Unit,
    onAddCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onToggleWordCount: () -> Unit,
    onSetAlignment: (TextAlignment) -> Unit,
    onMoveToTrash: () -> Unit,
    onCopyPlain: () -> Unit = {},
    onCopyMarkdown: () -> Unit = {},
    onDownloadMarkdown: () -> Unit = {},
    isEditingTemplate: Boolean = false,
    onDownloadPdf: () -> Unit = {},
    showStickyNoteOption: Boolean = true,
    onOpenAsStickyNote: () -> Unit = {}
) {
    var currentMenu by remember { mutableStateOf(MenuLevel.MAIN) }

    Column(modifier = Modifier.width(240.dp).padding(vertical = 4.dp)) {
        AnimatedContent(
            targetState = currentMenu,
            transitionSpec = {
                if (targetState != MenuLevel.MAIN && initialState == MenuLevel.MAIN) {
                    (slideInHorizontally(tween(200)) { it } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 2 } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(200)) { -it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200)))
                }
            },
            label = "DesktopMenuTransition"
        ) { targetMenu ->
            Column(modifier = Modifier.fillMaxWidth()) {
                when (targetMenu) {
                    MenuLevel.MAIN -> {
                        DesktopMenuItem(
                            painterResource(Res.drawable.ghost_smile),
                            "Icon Options"
                        ) { currentMenu = MenuLevel.ICON }
                        DesktopMenuItem(
                            painterResource(Res.drawable.image),
                            "Cover Options"
                        ) { currentMenu = MenuLevel.COVER }

                        if (showStickyNoteOption) {
                            DesktopMenuItem(
                                painterResource(Res.drawable.square_arrow_out_up_right),
                                "Open as Sticky Note"
                            ) { onOpenAsStickyNote() }
                        }

                        val favIcon =
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder
                        val favText =
                            if (isFavorite) "Remove from Favorites" else "Add to Favorites"

                        if (!isEditingTemplate) {
                        DesktopMenuItem(favIcon, favText) { onDismiss(); onToggleFavorite() }}

                        val wordCountText =
                            if (showWordCount) "Hide Word Count" else "Show Word Count"
                        DesktopMenuItem(
                            Icons.Default.FormatSize,
                            wordCountText
                        ) { onDismiss(); onToggleWordCount() }

                        AlignmentIconRow(
                            currentAlignment = blockAlignment,
                            onSelect = onSetAlignment,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        if (!isEditingTemplate) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )

                            DesktopMenuItem(
                                painterResource(Res.drawable.share),
                                "Export Note"
                            ) { currentMenu = MenuLevel.EXPORT }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )

                            DesktopMenuItem(
                                painterResource(Res.drawable.trash),
                                "Move to Trash",
                                isDestructive = true
                            ) {
                                onDismiss(); onMoveToTrash()
                            }
                        }
                    }

                    MenuLevel.EXPORT -> {
                        DesktopMenuItem(painterResource(Res.drawable.chevron_left), "Back to Options") { currentMenu = MenuLevel.MAIN }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        DesktopMenuItem(painterResource(Res.drawable.copy), "Copy Text") { onDismiss(); onCopyPlain() }
                        DesktopMenuItem(painterResource(Res.drawable.code), "Copy as Markdown") { onDismiss(); onCopyMarkdown() }
                        DesktopMenuItem(painterResource(Res.drawable.file_code_corner), "Download .md") { onDismiss(); onDownloadMarkdown() }
                        DesktopMenuItem(painterResource(Res.drawable.file_pdf), "Download PDF") { onDismiss(); onDownloadPdf() }
                    }

                    MenuLevel.ICON -> {
                        DesktopMenuItem(painterResource(Res.drawable.chevron_left), "Back to Options") { currentMenu = MenuLevel.MAIN }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        DesktopMenuItem(painterResource(Res.drawable.ghost_smile), if (hasIcon) "Change Icon" else "Add Icon") {
                            onDismiss()
                            onAddIcon()
                        }
                        if (hasIcon) {
                            DesktopMenuItem(painterResource(Res.drawable.trash), "Remove Icon", isDestructive = true) {
                                onDismiss()
                                onRemoveIcon()
                            }
                        }
                    }

                    MenuLevel.COVER -> {
                        DesktopMenuItem(painterResource(Res.drawable.chevron_left), "Back to Options") { currentMenu = MenuLevel.MAIN }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        DesktopMenuItem(painterResource(Res.drawable.image), if (hasCover) "Change Cover" else "Add Cover") { onDismiss(); onAddCover() }
                        if (hasCover) {
                            DesktopMenuItem(painterResource(Res.drawable.trash), "Remove Cover", isDestructive = true) { onDismiss(); onRemoveCover() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopMenuItem(
    icon: Painter,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}

@Composable
private fun DesktopMenuItem(
    icon: ImageVector,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}

private val ALIGNMENT_OPTIONS = listOf(
    TextAlignment.LEFT to Res.drawable.textalign_left2,
    TextAlignment.RIGHT to Res.drawable.textalign_right2,
    TextAlignment.CENTER to Res.drawable.textalign_center2,
    TextAlignment.JUSTIFY to Res.drawable.textalign_justifycenter2
)

@Composable
private fun AlignmentIconRow(
    currentAlignment: TextAlignment?,
    onSelect: (TextAlignment) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ALIGNMENT_OPTIONS.forEach { (alignment, icon) ->
            val isSelected = alignment == currentAlignment
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onSelect(alignment) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = alignment.name,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteOptionsBottomSheet(
    expanded: Boolean,
    isFavorite: Boolean,
    hasIcon: Boolean,
    hasCover: Boolean,
    showWordCount: Boolean,
    blockAlignment: TextAlignment?,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddIcon: () -> Unit,
    onRemoveIcon: () -> Unit,
    onAddCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onToggleWordCount: () -> Unit,
    onSetAlignment: (TextAlignment) -> Unit,
    onMoveToTrash: () -> Unit,
    onCopyPlain: () -> Unit = {},
    onCopyMarkdown: () -> Unit = {},
    isEditingTemplate: Boolean = false,
    onDownloadMarkdown: () -> Unit = {},
    onDownloadPdf: () -> Unit = {}
) {
    var currentMenu by remember { mutableStateOf(MenuLevel.MAIN) }

    // Reset back to MAIN once everything is fully closed
    LaunchedEffect(expanded) {
        if (!expanded) {
            delay(300.milliseconds)
            currentMenu = MenuLevel.MAIN
        }
    }

    // --- Main options sheet (always the base layer while `expanded`) ---
    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Note Options",
    ) { closeAnd ->
      CompositionLocalProvider(
        LocalIndication provides NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides null
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            BottomSheetOptionItem(
                painterResource(Res.drawable.ghost_smile),
                "Icon Options"
            ) { currentMenu = MenuLevel.ICON }

            BottomSheetOptionItem(
                painterResource(Res.drawable.image),
                "Cover Options"
            ) { currentMenu = MenuLevel.COVER }

            val favIcon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder
            val favText = if (isFavorite) "Remove from Favorites" else "Add to Favorites"
            if (!isEditingTemplate) {
                BottomSheetOptionItem(favIcon, favText) { closeAnd { onToggleFavorite() } }
            }

            val wordCountText = if (showWordCount) "Hide Word Count" else "Show Word Count"
            BottomSheetOptionItem(Icons.Default.FormatSize, wordCountText) {
                closeAnd { onToggleWordCount() }
            }

            AlignmentIconRow(
                currentAlignment = blockAlignment,
                onSelect = onSetAlignment,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            if (!isEditingTemplate) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
                BottomSheetOptionItem(
                    painterResource(Res.drawable.share),
                    "Export Note"
                ) { currentMenu = MenuLevel.EXPORT }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            BottomSheetOptionItem(
                painterResource(Res.drawable.trash),
                "Move to Trash",
                isDestructive = true
            ) { closeAnd { onMoveToTrash() } }

            EmberrButtonPrimary(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
      }
    }

    // --- Export sub-sheet, stacked on top ---
    EmberrBottomSheet(
        expanded = currentMenu == MenuLevel.EXPORT,
        onDismiss = { currentMenu = MenuLevel.MAIN },
        title = "Export Options",
    ) { closeAnd ->
      CompositionLocalProvider(
        LocalIndication provides NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides null
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            BottomSheetOptionItem(painterResource(Res.drawable.copy), "Copy Text") {
                closeAnd { onCopyPlain(); onDismiss() }
            }
            BottomSheetOptionItem(painterResource(Res.drawable.code), "Copy as Markdown") {
                closeAnd { onCopyMarkdown(); onDismiss() }
            }
            BottomSheetOptionItem(painterResource(Res.drawable.file_code_corner), "Download .md") {
                closeAnd { onDownloadMarkdown(); onDismiss() }
            }
            BottomSheetOptionItem(painterResource(Res.drawable.file_pdf), "Download PDF") {
                closeAnd { onDownloadPdf(); onDismiss() }
            }

            EmberrButtonPrimary(
                text = "Close",
                onClick = { currentMenu = MenuLevel.MAIN },
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 20.dp)
            )
        }
      }
    }

    // --- Icon sub-sheet, stacked on top ---
    EmberrBottomSheet(
        expanded = currentMenu == MenuLevel.ICON,
        onDismiss = { currentMenu = MenuLevel.MAIN },
        title = "Icon Options",
    ) { closeAnd ->
      CompositionLocalProvider(
        LocalIndication provides NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides null
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            BottomSheetOptionItem(
                painterResource(Res.drawable.ghost_smile),
                if (hasIcon) "Change Icon" else "Add Icon"
            ) { closeAnd { onAddIcon(); onDismiss() } }

            if (hasIcon) {
                BottomSheetOptionItem(
                    painterResource(Res.drawable.trash),
                    "Remove Icon",
                    isDestructive = true
                ) { closeAnd { onRemoveIcon(); onDismiss() } }
            }

            EmberrButtonPrimary(
                text = "Close",
                onClick = { currentMenu = MenuLevel.MAIN },
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
      }
    }

    // --- Cover sub-sheet, stacked on top ---
    EmberrBottomSheet(
        expanded = currentMenu == MenuLevel.COVER,
        onDismiss = { currentMenu = MenuLevel.MAIN },
        title = "Cover Options",
    ) { closeAnd ->
      CompositionLocalProvider(
        LocalIndication provides NoRippleIndicationNodeFactory,
        LocalRippleConfiguration provides null
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            BottomSheetOptionItem(
                painterResource(Res.drawable.image),
                if (hasCover) "Change Cover" else "Add Cover"
            ) { closeAnd { onAddCover(); onDismiss() } }

            if (hasCover) {
                BottomSheetOptionItem(
                    painterResource(Res.drawable.trash),
                    "Remove Cover",
                    isDestructive = true
                ) { closeAnd { onRemoveCover(); onDismiss() } }
            }

            EmberrButtonPrimary(
                text = "Close",
                onClick = { currentMenu = MenuLevel.MAIN },
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
      }
    }
}

@Composable
private fun BottomSheetOptionItem(
    icon: ImageVector,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}

@Composable
private fun BottomSheetOptionItem(
    icon: Painter,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val textColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
    }
}