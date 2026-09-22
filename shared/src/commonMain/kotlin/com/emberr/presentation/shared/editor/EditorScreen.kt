package com.emberr.presentation.shared.editor

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.emberr.ui.theme.HighlightColor
import com.emberr.ui.theme.LocalAppIsDark
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CellData
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.ViewType
import com.emberr.domain.model.VoiceBlock
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.editor.blockViews.LinkedNoteOptionsMenu
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.animateScrollBy
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.MutableSharedFlow
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.presentation.shared.components.EmberrVerticalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_right2
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.at
import emberr.shared.generated.resources.bookmark
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.code
import emberr.shared.generated.resources.copy
import emberr.shared.generated.resources.ellipsis
import emberr.shared.generated.resources.eye3
import emberr.shared.generated.resources.file_text
import emberr.shared.generated.resources.format_bold
import emberr.shared.generated.resources.highlight
import emberr.shared.generated.resources.image
import emberr.shared.generated.resources.indent_left
import emberr.shared.generated.resources.indent_right
import emberr.shared.generated.resources.italic
import emberr.shared.generated.resources.keyboard
import emberr.shared.generated.resources.link
import emberr.shared.generated.resources.microphone
import emberr.shared.generated.resources.minus
import emberr.shared.generated.resources.mouse_square2
import emberr.shared.generated.resources.ordered_list
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.quote_down2
import emberr.shared.generated.resources.redo_circle
import emberr.shared.generated.resources.scissor2
import emberr.shared.generated.resources.square_kanban
import emberr.shared.generated.resources.table
import emberr.shared.generated.resources.text_tool_2
import emberr.shared.generated.resources.text_x
import emberr.shared.generated.resources.textalign_center2
import emberr.shared.generated.resources.textalign_justifycenter2
import emberr.shared.generated.resources.textalign_left2
import emberr.shared.generated.resources.textalign_right2
import emberr.shared.generated.resources.thumbtack
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.underline
import emberr.shared.generated.resources.undo_circle
import emberr.shared.generated.resources.unordered_list
import emberr.shared.generated.resources.x
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.roundToInt

private val DefaultCornerShape = RoundedCornerShape(12.dp)

private fun Modifier.customEmberrShadow(shape: Shape): Modifier = this.shadow(
    elevation = 14.dp,
    shape = shape,
    spotColor = Color.Black.copy(alpha = 0.25f),
    ambientColor = Color.Black.copy(alpha = 0.10f)
)

// Adjusts automatic bring-into-view scroll distances to account for top and bottom floating bars (top inset)
// and key-board/navigation bars (bottom inset), ensuring focused items remain visible within the un-covered viewport.
@OptIn(ExperimentalFoundationApi::class)
private class ClearanceAwareBringIntoViewSpec(
    private val topInsetPx: Float,
    private val bottomInsetPx: Float
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val visibleTop = topInsetPx
        val visibleBottom = containerSize - bottomInsetPx
        val leadingEdge = offset
        val trailingEdge = offset + size
        val topDelta = leadingEdge - visibleTop
        val bottomDelta = trailingEdge - visibleBottom
        return when {
            leadingEdge >= visibleTop && trailingEdge <= visibleBottom -> 0f
            leadingEdge < visibleTop && trailingEdge > visibleBottom -> 0f
            abs(topDelta) < abs(bottomDelta) -> topDelta
            else -> bottomDelta
        }
    }
}

val SlashMenuIconSlot = 18.dp
val SlashMenuIconSize = 16.dp

sealed interface SlashMenuIcon {
    data class Drawable(val resource: DrawableResource, val size: Dp = SlashMenuIconSize) : SlashMenuIcon
    data class Vector(val image: ImageVector, val size: Dp = SlashMenuIconSlot) : SlashMenuIcon
    data class Label(val text: String) : SlashMenuIcon
}

data class SlashMenuItemData(
    val label: String,
    val icon: SlashMenuIcon,
    val action: () -> Unit
)

fun SlashMenuItemData(label: String, icon: DrawableResource, size: Dp = SlashMenuIconSize, action: () -> Unit) =
    SlashMenuItemData(label, SlashMenuIcon.Drawable(icon, size), action)

fun SlashMenuItemData(label: String, icon: ImageVector, size: Dp = SlashMenuIconSlot, action: () -> Unit) =
    SlashMenuItemData(label, SlashMenuIcon.Vector(icon, size), action)

data class SlashMenuSectionData(
    val title: String,
    val items: List<SlashMenuItemData>
)

// MAIN  = the quick-action strip
// SLASH = the menu shown while typing "/" (driven by the typed query)
// MENU  = the full "everything" menu opened from the + button
enum class MobileMenuState { MAIN, SLASH, MENU, MENTION, LINK_TO_NOTE }

object GlobalEditorState {
    var currentlyFocusedBlockId: String? = null

    var currentlyFocusedTableCellKey: String? = null

    var hasTextSelection by mutableStateOf(false)
        private set

    var currentSelection: TextRange = TextRange.Zero
        set(value) {
            field = value
            hasTextSelection = !value.collapsed
        }
}

object EditorEventBus {
    val insertSlashEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cleanupSlashEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    var insertMentionSignal by mutableStateOf<Pair<String, Long>?>(null)
        private set
    private var insertMentionNonce = 0L
    fun requestInsertMention(blockId: String) {
        insertMentionNonce++
        insertMentionSignal = blockId to insertMentionNonce
    }

    val confirmMentionEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val confirmMentionAndOpenEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)
    val cancelMentionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

@Stable
interface EditorActions {

    fun onClearSlashQuery()
    fun onClearFocusRequest()
    fun onUpdateText(id: String, text: String)
    fun onToggleCheckbox(id: String, checked: Boolean)
    fun onToggleExpand(id: String)
    fun onFocusBlock(id: String)
    fun onRequestCursorPosition(id: String, offset: Int)
    fun onChangeBlockType(type: String)
    fun onToggleFormat(format: String)
    fun onAdjustIndentation(increase: Boolean)
    fun onSetBlockAlignment(alignment: TextAlignment)
    fun onEnterPressed(id: String, before: String, after: String)
    fun onBackspaceOnEmpty(id: String)
    fun onToggleSelection(id: String)
    fun onUpdateReminder(id: String, timestamp: Long?)
    // occurrenceDate is derived from the tapped block's own reminderTimestamp (not the screen's
    // "currently selected day") so it stays correct even when the block is a virtual recurring
    // occurrence rendered on a page other than whatever the screen currently considers active.
    fun onOpenEventOptions(blockId: String, occurrenceDate: String?) {}
    fun onUrlSubmit(id: String, url: String)
    fun onImagePicked(id: String, uri: String)
    fun onDocumentPicked(id: String, uri: String)
    fun onAddBlankBlock()
    fun onInsertMediaBlock(type: String)
    fun onOutsideTap()
    fun onUpdateDbTitle(id: String, title: String)
    fun onAddDbRow(id: String)
    fun onAddDbColumn(id: String)
    fun onUpdateDbCell(blockId: String, rowId: String, colId: String, value: CellData)
    fun onUpdateDbColumn(blockId: String, colId: String, name: String, type: ColumnType, isManualNameChange: Boolean = true)
    fun onUpdateDbSort(blockId: String, colId: String, isAscending: Boolean?)
    fun onUpdateDbGroupBy(blockId: String, colId: String?)
    fun onUpdateDbGalleryCardSize(blockId: String, size: GalleryCardSize)
    fun onToggleKanbanGroupVisibility(blockId: String, viewId: String, groupName: String, isHidden: Boolean)
    fun onReorderKanbanGroups(blockId: String, viewId: String, orderedGroupKeys: List<String>)
    fun onAddDbFilter(blockId: String, colId: String, operator: String, value: String)
    fun onRemoveDbFilter(blockId: String, config: FilterConfig)
    fun onReorderDbColumns(blockId: String, from: Int, to: Int)
    fun onReorderDbRows(blockId: String, from: Int, to: Int)
    fun onReorderDatabaseViews(blockId: String, from: Int, to: Int)
    fun onUpdateDbFormula(blockId: String, colId: String, expression: String)
    fun onDeleteDbColumn(blockId: String, colId: String)
    fun onDeleteDbRow(blockId: String, rowId: String)
    fun onAddDbRowAt(blockId: String, index: Int)
    fun onAddDbColumnAt(blockId: String, index: Int)
    fun onUpdateDbColumnWidth(blockId: String, colId: String, width: Int)
    fun onVoiceRecorded(id: String, filePath: String, duration: Int)
    fun onRemoveVoice(id: String)
    fun onStartRecording()
    fun onStopRecording(blockId: String, cancel: Boolean)
    fun onPlayAudio(filePath: String, onComplete: () -> Unit)
    fun onStopAudio()
    fun onDeleteImageBlock(id: String)
    fun onCreateGlobalTag(name: String, colorHex: String): String
    fun onRequestImagePicker(blockId: String)
    fun onRequestDocumentPicker(blockId: String)
    fun onOpenFile(filePath: String, mimeType: String)
    fun onRequestDbFilePicker(blockId: String, rowId: String, colId: String, isAudio: Boolean)
    fun onStopDbAudioRecording(blockId: String, rowId: String, colId: String, cancel: Boolean)
    fun onTogglePin()
    fun onUpdateTable(id: String, rows: List<List<String>>)
    fun onUpdateTableColumnWidth(id: String, columnIndex: Int, width: Int)
    fun onUpdateTableStyle(
        id: String,
        cellStyles: Map<String, com.emberr.domain.model.TableCellStyle>,
        rowStyles: Map<String, com.emberr.domain.model.TableCellStyle>,
        columnStyles: Map<String, com.emberr.domain.model.TableCellStyle>
    )
    fun onAddBlockAbove(id: String)
    fun onAddBlockBelow(id: String)
    fun onUpdateDbAggregation(blockId: String, colId: String, aggregationType: String?)
    fun onUpdateDbCurrency(blockId: String, colId: String, symbol: String)
    fun onUpdateDbFormulaCurrency(blockId: String, colId: String, enabled: Boolean)
    fun onAddDatabaseView(blockId: String, type: ViewType)
    fun onDeleteDatabaseView(blockId: String, viewId: String)
    fun onSetActiveDatabaseView(blockId: String, viewId: String)
    fun onRenameDatabaseView(blockId: String, viewId: String, newName: String)
    fun onNoteLinkClick(noteId: String)
    fun onOpenDatabaseNote(blockId: String, rowId: String, colId: String, existingNoteId: String?)
    fun onSaveDatabaseAsTemplate(blockId: String, templateName: String)
    suspend fun getNoteTitle(noteId: String): String
    fun onCreateLinkedNote(title: String): String
    fun onInsertLinkedNoteBlock(noteId: String) {}
    fun onRequestCamera(blockId: String)
    suspend fun getNoteMetadata(noteId: String): NoteMetadataEntity?
    fun onUpdateLinkedNoteOptions(id: String, showIcon: Boolean, showCoverImage: Boolean)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    blocks: List<NoteBlock>,
    globalTags: List<TagEntity>,
    actions: EditorActions,
    focusRequest: FocusRequest?,
    selectionRequest: SelectionRequest? = null,
    selectedBlockIds: Set<String>,
    selectionMenuContent: (@Composable (onCloseMenu: () -> Unit) -> Unit)? = null,
    onClearSelection: () -> Unit = {},
    bottomContentPadding: Dp = 0.dp,
    topContentPadding: Dp = 0.dp,
    toolbarOffset: Dp = 0.dp,
    headerContent: (@Composable LazyItemScope.() -> Unit)? = null,
    sectionLabelFor: ((NoteBlock) -> String?)? = null,
    emptyContent: (@Composable () -> Unit)? = null,
    mobileMenuState: MobileMenuState = MobileMenuState.MAIN,
    onMobileMenuStateChange: (MobileMenuState) -> Unit = {},
    slashQuery: String = "",
    onSlashQueryChange: (String) -> Unit = {},
    showNoteLinkMenu: Boolean = false,
    onDismissNoteLinkMenu: () -> Unit = {},
    onMentionQueryChange: (String?) -> Unit = {},
    allLinkableNotes: List<NoteMetadataEntity> = emptyList(),
    isCurrentActivePage: Boolean = true,
    onScrollStateChange: (Boolean) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    topBarClearancePx: Float = 0f,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {}
) {
    val isSelectionMode = selectedBlockIds.isNotEmpty()
    val focusManager = LocalFocusManager.current
    val showsSelectionMenuOnRightClick = isDesktopPlatform && selectionMenuContent != null
    var editorTopLeftInWindow by remember { mutableStateOf(Offset.Zero) }
    var selectionAnchorBlockId by remember { mutableStateOf<String?>(null) }
    var selectionMenuPositionInEditor by remember { mutableStateOf<Offset?>(null) }
    val closeSelectionMenu: () -> Unit = remember { { selectionMenuPositionInEditor = null } }
    LaunchedEffect(isSelectionMode) { if (!isSelectionMode) selectionMenuPositionInEditor = null }
    val selectionShortcutFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSelectionMode, selectionMenuPositionInEditor) {
        val needsKeyFocus = isDesktopPlatform && isSelectionMode && selectionMenuPositionInEditor == null
        if (needsKeyFocus) {
            runCatching { selectionShortcutFocusRequester.requestFocus() }
        }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    var activeBlockId by remember { mutableStateOf<String?>(null) }
    val currentBlocks by rememberUpdatedState(blocks)

    var localFocusRequest by remember { mutableStateOf<FocusRequest?>(null) }
    val activeFocusRequest = focusRequest ?: localFocusRequest

    var focusRequestGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(activeFocusRequest?.nonce) {
        if (activeFocusRequest != null) focusRequestGeneration++
    }

    var focusHandoffInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(focusRequestGeneration) {
        if (focusRequestGeneration == 0) return@LaunchedEffect
        focusHandoffInFlight = true
        delay(500.milliseconds)
        focusHandoffInFlight = false
    }

    if (!isDesktopPlatform && isCurrentActivePage) {
        val closeDensity = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(closeDensity)
        // Some devices never settle the ime inset back to exactly 0 after a system-driven
        // dismiss (back gesture, drag-to-close) - NoteBlockItem's IsolatedEditorTextField already
        // works around the same platform gap with a timeout. A small px tolerance here avoids
        // this effect waiting forever on a residual non-zero inset.
        val keyboardClosedThresholdPx = remember(closeDensity) { with(closeDensity) { 2.dp.toPx() } }
        val isKeyboardOpen = imeBottom > keyboardClosedThresholdPx
        LaunchedEffect(isKeyboardOpen, focusHandoffInFlight) {
            if (isKeyboardOpen || focusHandoffInFlight) return@LaunchedEffect
            delay(250.milliseconds)
            focusManager.clearFocus(force = true)
            activeBlockId = null
            GlobalEditorState.currentlyFocusedBlockId = null
            localFocusRequest = null
        }
    }

    var showSlashMenu by remember { mutableStateOf(false) }

    var isSlashKilled by remember { mutableStateOf(false) }
    val previousTextMap = remember { mutableMapOf<String, String>() }
    val slashMenuLabels = remember {
        listOf(
            "Text", "Heading 1", "Heading 2", "To-do List", "Bulleted List",
            "Numbered List", "Toggle List", "Quote", "Code Block",
            "Voice Note", "Image", "Document / File", "Web Bookmark",
            "Database / Table", "Simple Table", "Link to Note", "Bold Text", "Italic Text", "Underline Text",
            "Strikethrough Text", "Decrease Indent", "Increase Indent",
            "Solid Line", "Three Dots"
        )
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    LaunchedEffect(isScrolled) {
        onScrollStateChange(isScrolled)
    }

    val latestBlocks by rememberUpdatedState(blocks)
    val latestActiveBlockId by rememberUpdatedState(activeBlockId)
    val latestSlashQuery by rememberUpdatedState(slashQuery)
    val latestMobileMenuState by rememberUpdatedState(mobileMenuState)
    val latestOnMobileMenuStateChange by rememberUpdatedState(onMobileMenuStateChange)
    val latestOnSlashQueryChange by rememberUpdatedState(onSlashQueryChange)
    val latestOnMentionQueryChange by rememberUpdatedState(onMentionQueryChange)
    val latestOnUndo by rememberUpdatedState(onUndo)
    val latestOnRedo by rememberUpdatedState(onRedo)
    val latestOnClearSelection by rememberUpdatedState(onClearSelection)
    val latestSelectedBlockIds by rememberUpdatedState(selectedBlockIds)

    val clearSelectionOnTap: () -> Unit = remember {
        { if (isDesktopPlatform) latestOnClearSelection() }
    }

    val clearSlashAndExecute: (() -> Unit) -> Unit = { executionBlock ->
        actions.onClearSlashQuery()
        if (isDesktopPlatform) showSlashMenu = false
        latestOnMobileMenuStateChange(MobileMenuState.MAIN)
        latestOnSlashQueryChange("")
        executionBlock()
    }

    val wrappedActions = remember(actions) {
        object : EditorActions by actions {
            override fun onToggleSelection(id: String) {
                selectionAnchorBlockId = id
                actions.onToggleSelection(id)
            }
            override fun onClearFocusRequest() {
                localFocusRequest = null
                actions.onClearFocusRequest()
            }
            override fun onUpdateText(id: String, text: String) {
                actions.onUpdateText(id, text)

                val hasSeenThisBlockBefore = previousTextMap.containsKey(id)
                val prevText = previousTextMap[id] ?: ""
                previousTextMap[id] = text

                val slashAdded = text.count { it == '/' } > prevText.count { it == '/' }
                if (slashAdded) {
                    isSlashKilled = false
                }

                val arrivedInOneChunk = hasSeenThisBlockBefore && text.length - prevText.length > 1
                if (arrivedInOneChunk) {
                    isSlashKilled = true
                }

                val lastSlashIndex = text.lastIndexOf('/')
                val opensASlashCommand = lastSlashIndex != -1 && (
                    lastSlashIndex == 0 ||
                        text[lastSlashIndex - 1] == ' ' ||
                        text[lastSlashIndex - 1] == '\n'
                    )
                val textAfterSlash = if (opensASlashCommand) text.substring(lastSlashIndex + 1) else ""

                if (opensASlashCommand) {
                    if (textAfterSlash.contains(" ")) {
                        isSlashKilled = true
                    }

                    if (!isSlashKilled && textAfterSlash.length > 2) {
                        val hasMatch = slashMenuLabels.any { it.contains(textAfterSlash, ignoreCase = true) }
                        if (!hasMatch) {
                            isSlashKilled = true
                        }
                    }
                }

                if (opensASlashCommand && !isSlashKilled) {
                    if (isDesktopPlatform) {
                        showSlashMenu = true
                    } else {
                        latestOnMobileMenuStateChange(MobileMenuState.SLASH)
                    }
                    latestOnSlashQueryChange(textAfterSlash)
                } else {
                    if (isDesktopPlatform) {
                        showSlashMenu = false
                    } else if (latestMobileMenuState == MobileMenuState.SLASH) {
                        latestOnMobileMenuStateChange(MobileMenuState.MAIN)
                    }
                    latestOnSlashQueryChange("")
                }

                val lastAtIndex = text.lastIndexOf('@')
                val opensAMention = lastAtIndex != -1 && (
                    lastAtIndex == 0 || text[lastAtIndex - 1] == ' ' || text[lastAtIndex - 1] == '\n'
                    )
                val textAfterAt = if (opensAMention) text.substring(lastAtIndex + 1) else ""
                val mentionActive = opensAMention && !textAfterAt.contains(' ')

                if (!isDesktopPlatform) {
                    if (mentionActive) {
                        latestOnMobileMenuStateChange(MobileMenuState.MENTION)
                    } else if (latestMobileMenuState == MobileMenuState.MENTION) {
                        latestOnMobileMenuStateChange(MobileMenuState.MAIN)
                    }
                }
                latestOnMentionQueryChange(if (mentionActive) textAfterAt else null)
            }
            override fun onChangeBlockType(type: String) = clearSlashAndExecute { actions.onChangeBlockType(type) }
            override fun onToggleFormat(format: String) = clearSlashAndExecute { actions.onToggleFormat(format) }
            override fun onAdjustIndentation(increase: Boolean) = clearSlashAndExecute { actions.onAdjustIndentation(increase) }
            override fun onSetBlockAlignment(alignment: TextAlignment) = clearSlashAndExecute { actions.onSetBlockAlignment(alignment) }
            override fun onInsertMediaBlock(type: String) = clearSlashAndExecute { actions.onInsertMediaBlock(type) }
            override fun onTogglePin() = actions.onTogglePin()
            override fun onOpenDatabaseNote(blockId: String, rowId: String, colId: String, existingNoteId: String?) =
                actions.onOpenDatabaseNote(blockId, rowId, colId, existingNoteId)
            override suspend fun getNoteTitle(noteId: String): String = actions.getNoteTitle(noteId)
            override fun onNoteLinkClick(noteId: String) = actions.onNoteLinkClick(noteId)
            override fun onCreateLinkedNote(title: String): String = actions.onCreateLinkedNote(title)
        }
    }

    LaunchedEffect(isCurrentActivePage) {
        if (!isCurrentActivePage) return@LaunchedEffect
        launch {
            EditorEventBus.insertSlashEvent.collect {
                val targetId = GlobalEditorState.currentlyFocusedBlockId ?: latestActiveBlockId
                if (targetId != null) {
                    val block = findBlockRecursive(latestBlocks, targetId)
                    val currentText = when (block) {
                        is TextBlock -> block.text
                        is HeadingBlock -> block.text
                        is CheckboxBlock -> block.text
                        is BulletedListBlock -> block.text
                        is NumberedListBlock -> block.text
                        is ToggleBlock -> block.text
                        is QuoteBlock -> block.text
                        else -> null
                    }
                    if (currentText != null) {
                        wrappedActions.onUpdateText(targetId, "$currentText/")
                        localFocusRequest = FocusRequest(id = targetId, placeCursorAtEnd = true)
                    }
                }
            }
        }
        launch {
            EditorEventBus.cleanupSlashEvent.collect {
                val targetId = GlobalEditorState.currentlyFocusedBlockId ?: latestActiveBlockId
                if (targetId != null) {
                    val block = findBlockRecursive(latestBlocks, targetId)
                    val currentText = when (block) {
                        is TextBlock -> block.text
                        is HeadingBlock -> block.text
                        is CheckboxBlock -> block.text
                        is BulletedListBlock -> block.text
                        is NumberedListBlock -> block.text
                        is ToggleBlock -> block.text
                        is QuoteBlock -> block.text
                        else -> null
                    }
                    if (currentText != null) {
                        val lastSlashIndex = currentText.lastIndexOf('/')
                        if (lastSlashIndex != -1 && lastSlashIndex == currentText.length - 1 - latestSlashQuery.length) {
                            actions.onUpdateText(targetId, currentText.substring(0, lastSlashIndex))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(activeFocusRequest?.nonce) {
        val request = activeFocusRequest ?: return@LaunchedEffect
        withFrameNanos {}
        withFrameNanos {}

        val index = currentBlocks.indexOfFirst { it.id == request.id }
        if (index != -1) {
            val hasHeader = if (headerContent != null) 1 else 0
            val hasStats = if (currentBlocks.any { it is CheckboxBlock }) 1 else 0
            val targetLazyColumnIndex = index + hasHeader + hasStats

            val layoutInfo = listState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == targetLazyColumnIndex }

            if (itemInfo == null) {
                try {
                    val viewportHeight = layoutInfo.viewportSize.height
                    val offset = if (viewportHeight > 0) -(viewportHeight / 3) else 0
                    listState.scrollToItem(index = targetLazyColumnIndex, scrollOffset = offset)
                } catch (_: Exception) {}
            } else {
                val itemBottom = itemInfo.offset + itemInfo.size
                val viewportBottom = layoutInfo.viewportEndOffset

                if (itemBottom > viewportBottom) {
                    try {
                        listState.animateScrollBy((itemBottom - viewportBottom).toFloat() + 60f)
                    } catch (_: Exception) {}
                } else if (itemInfo.offset < layoutInfo.viewportStartOffset) {
                    try {
                        listState.animateScrollBy((itemInfo.offset - layoutInfo.viewportStartOffset).toFloat() - 60f)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    val density = LocalDensity.current

    val bringIntoViewSpec = remember(topBarClearancePx, density) {
        ClearanceAwareBringIntoViewSpec(
            topInsetPx = topBarClearancePx + with(density) { 16.dp.toPx() },
            bottomInsetPx = with(density) { 64.dp.toPx() }
        )
    }

    val dynamicBottomPadding by animateDpAsState(
        targetValue = if (mobileMenuState != MobileMenuState.MAIN) 280.dp else 100.dp,
        label = "menuPadding"
    )

    LaunchedEffect(mobileMenuState) {
        if (mobileMenuState != MobileMenuState.MAIN) {
            activeBlockId?.let { id ->
                val index = currentBlocks.indexOfFirst { it.id == id }
                if (index != -1) {
                    val hasHeader = if (headerContent != null) 1 else 0
                    val hasStats = if (currentBlocks.any { it is CheckboxBlock }) 1 else 0
                    val targetIndex = index + hasHeader + hasStats

                    withFrameNanos {}

                    val layoutInfo = listState.layoutInfo
                    val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }

                    if (itemInfo != null) {
                        val menuHeightPx = with(density) { 340.dp.toPx() }
                        val viewportBottom = layoutInfo.viewportEndOffset
                        val menuTopPx = viewportBottom - menuHeightPx
                        val itemBottomPx = itemInfo.offset + itemInfo.size

                        if (itemBottomPx > menuTopPx) {
                            try {
                                listState.animateScrollBy((itemBottomPx - menuTopPx) + 60f)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    val immutableTags = remember(globalTags) { globalTags.toImmutableList() }
    val immutableSelectedIds = remember(selectedBlockIds) { selectedBlockIds.toImmutableSet() }
    val validNoteIds = remember(allLinkableNotes) { allLinkableNotes.mapTo(HashSet()) { it.noteId } }

    val onFocusBlock: (String) -> Unit = remember(wrappedActions) {
        { focusedId ->
            activeBlockId = focusedId
            GlobalEditorState.currentlyFocusedBlockId = focusedId
            wrappedActions.onFocusBlock(focusedId)
        }
    }
    val onDismissSlash: () -> Unit = remember { { showSlashMenu = false } }

    val selectBlockRangeTo: (String) -> Unit = remember(actions) {
        { targetBlockId ->
            val visibleBlockIds = latestBlocks.map { it.id }
            val anchorIndex = visibleBlockIds.indexOf(selectionAnchorBlockId)
            val targetIndex = visibleBlockIds.indexOf(targetBlockId)

            if (anchorIndex < 0 || targetIndex < 0) {
                wrappedActions.onToggleSelection(targetBlockId)
            } else {
                val alreadySelectedIds = latestSelectedBlockIds
                for (index in minOf(anchorIndex, targetIndex)..maxOf(anchorIndex, targetIndex)) {
                    val blockIdInRange = visibleBlockIds[index]
                    if (blockIdInRange !in alreadySelectedIds) actions.onToggleSelection(blockIdInRange)
                }
            }
        }
    }

    val openSelectionMenuAt: (Offset) -> Unit = remember {
        { pressPositionInWindow ->
            focusManager.clearFocus()
            keyboardController?.hide()
            activeBlockId = null
            GlobalEditorState.currentlyFocusedBlockId = null
            localFocusRequest = null
            showSlashMenu = false
            selectionMenuPositionInEditor = pressPositionInWindow - editorTopLeftInWindow
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .onGloballyPositioned { editorTopLeftInWindow = it.positionInWindow() }
            .then(
                if (isDesktopPlatform) {
                    Modifier
                        .focusRequester(selectionShortcutFocusRequester)
                        .focusTarget()
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (keyEvent.key == Key.Escape) {
                                if (!isSelectionMode) return@onPreviewKeyEvent false
                                closeSelectionMenu()
                                latestOnClearSelection()
                                return@onPreviewKeyEvent true
                            }
                            if (!keyEvent.isCtrlPressed) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.Z -> {
                                    if (keyEvent.isShiftPressed) latestOnRedo() else latestOnUndo()
                                    true
                                }
                                Key.Y -> {
                                    latestOnRedo()
                                    true
                                }
                                else -> false
                            }
                        }
                } else {
                    Modifier
                }
            )
    ) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides bringIntoViewSpec) {
        LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .smoothWheelScroll(listState)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                activeBlockId = null
                                GlobalEditorState.currentlyFocusedBlockId = null
                                localFocusRequest = null
                                showSlashMenu = false
                                onMobileMenuStateChange(MobileMenuState.MAIN)
                                clearSelectionOnTap()
                                wrappedActions.onOutsideTap()
                            },
                            onDoubleTap = {
                                val lastBlock = currentBlocks.lastOrNull()
                                if (lastBlock == null) {
                                    wrappedActions.onAddBlankBlock()
                                    return@detectTapGestures
                                }
                                val isMediaBlock = lastBlock is BookmarkBlock
                                        || lastBlock is ImageBlock
                                        || lastBlock is DocumentBlock
                                        || lastBlock is DatabaseBlock
                                        || lastBlock is VoiceBlock

                                if (isMediaBlock) {
                                    wrappedActions.onFocusBlock(lastBlock.id)
                                    wrappedActions.onAddBlankBlock()
                                } else {
                                    activeBlockId = lastBlock.id
                                    GlobalEditorState.currentlyFocusedBlockId = lastBlock.id
                                    wrappedActions.onFocusBlock(lastBlock.id)
                                    localFocusRequest = FocusRequest(id = lastBlock.id, placeCursorAtEnd = true)
                                }
                            }
                        )
                    },
                contentPadding = PaddingValues(top = topContentPadding, bottom = 0.dp)
            ) {
                if (headerContent != null) {
                    item(key = "page_header", contentType = "PageHeader") {
                        headerContent()
                    }
                }

                val allTasks = blocks.filterIsInstance<CheckboxBlock>()
                if (allTasks.isNotEmpty()) {
                    item(key = "stats_header", contentType = "StatsHeader") {
                        val doneCount = allTasks.count { it.isChecked }
                        val pendingCount = allTasks.size - doneCount

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isDesktopPlatform) 36.dp else 16.dp)
                                .padding(bottom = 20.dp, top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TaskBadge(
                                icon = Icons.Default.RadioButtonUnchecked,
                                label = "$pendingCount Pending"
                            )
                            TaskBadge(icon = Icons.Default.CheckCircle, label = "$doneCount Done")
                        }
                    }
                }

                itemsIndexed(
                    items = blocks,
                    key = { _, it -> it.id },
                    contentType = { _, it -> it::class.simpleName }
                ) { index, block ->
                    val targetedFocusRequest = when {
                        activeFocusRequest == null -> null
                        activeFocusRequest.id == block.id -> activeFocusRequest
                        else -> null
                    }

                    val previousBlock = blocks.getOrNull(index - 1)
                    val isFirstToggleChild = previousBlock is ToggleBlock && block.indentationLevel == previousBlock.indentationLevel + 1

                    if (sectionLabelFor != null) {
                        val label = sectionLabelFor(block)
                        val previousLabel = previousBlock?.let(sectionLabelFor)
                        if (label != null && label != previousLabel) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = if (isDesktopPlatform) 36.dp else 16.dp)
                                    .padding(top = if (index == 0) 0.dp else 20.dp, bottom = 8.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        NoteBlockItem(
                            block = block,
                            allLinkableNotes = allLinkableNotes,
                            globalTags = immutableTags,
                            actions = wrappedActions,
                            focusRequest = targetedFocusRequest,
                            selectedBlockIds = immutableSelectedIds,
                            inSelectionMode = isSelectionMode,
                            activeBlockId = activeBlockId,
                            onFocus = onFocusBlock,
                            showSlashMenu = showSlashMenu,
                            slashQuery = slashQuery,
                            onDismissSlashMenu = onDismissSlash,
                            showNoteLinkMenu = showNoteLinkMenu,
                            onDismissNoteLinkMenu = onDismissNoteLinkMenu,
                            isFirstToggleChild = isFirstToggleChild,
                            selectionRequest = selectionRequest,
                            validNoteIds = validNoteIds,
                            onRightClick = if (showsSelectionMenuOnRightClick) openSelectionMenuAt else null,
                            onShiftClick = selectBlockRangeTo
                        )
                    }
                }
                if (blocks.isNotEmpty()) {
                    item(key = "bottom_tap_area", contentType = "BottomTapArea") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(dynamicBottomPadding + bottomContentPadding + toolbarOffset)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            val lastBlock = currentBlocks.lastOrNull() ?: return@detectTapGestures
                                            val isMediaBlock = lastBlock is BookmarkBlock
                                                    || lastBlock is ImageBlock
                                                    || lastBlock is DocumentBlock
                                                    || lastBlock is DatabaseBlock
                                                    || lastBlock is VoiceBlock

                                            if (isMediaBlock) {
                                                wrappedActions.onFocusBlock(lastBlock.id)
                                                wrappedActions.onAddBlankBlock()
                                            } else {
                                                activeBlockId = lastBlock.id
                                                GlobalEditorState.currentlyFocusedBlockId = lastBlock.id
                                                wrappedActions.onFocusBlock(lastBlock.id)
                                                localFocusRequest = FocusRequest(id = lastBlock.id, placeCursorAtEnd = true)
                                            }
                                        },
                                        onTap = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            activeBlockId = null
                                            GlobalEditorState.currentlyFocusedBlockId = null
                                            localFocusRequest = null
                                            showSlashMenu = false
                                            onMobileMenuStateChange(MobileMenuState.MAIN)
                                            clearSelectionOnTap()
                                            wrappedActions.onOutsideTap()
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }

        if (emptyContent != null) {
            val hasNothingButOneBlankLine = blocks.size == 1 &&
                    (blocks.first() as? TextBlock)?.text?.isBlank() == true
            val isEditorVisuallyEmpty = blocks.isEmpty() || hasNothingButOneBlankLine

            AnimatedVisibility(
                visible = isEditorVisuallyEmpty && activeBlockId == null,
                enter = fadeIn(tween(durationMillis = 400, delayMillis = 200)),
                exit = fadeOut(tween(durationMillis = 180)),
                modifier = Modifier.fillMaxSize()
            ) {
                emptyContent()
            }
        }

        EmberrVerticalScrollbar(
            listState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(top = topContentPadding, bottom = bottomContentPadding)
        )

        val selectionMenuPosition = selectionMenuPositionInEditor
        if (selectionMenuContent != null && selectionMenuPosition != null) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            selectionMenuPosition.x.roundToInt(),
                            selectionMenuPosition.y.roundToInt()
                        )
                    }
                    .size(1.dp)
            ) {
                EmberrDesktopMenu(
                    expanded = true,
                    onDismissRequest = closeSelectionMenu,
                    modifier = Modifier.width(260.dp)
                ) {
                    selectionMenuContent(closeSelectionMenu)
                }
            }
        }
    }
}

private fun findBlockRecursive(blocks: List<NoteBlock>, id: String): NoteBlock? =
    blocks.find { it.id == id }

@Composable
fun EditorToolbar(
    modifier: Modifier = Modifier,
    mobileMenuState: MobileMenuState,
    onMenuStateChange: (MobileMenuState) -> Unit,
    query: String,
    onChangeBlockType: (String) -> Unit,
    onToggleFormat: (String) -> Unit,
    onAdjustIndentation: (Boolean) -> Unit,
    onSetAlignment: (TextAlignment) -> Unit,
    onInsertMediaBlock: (String) -> Unit,
    onSelectCurrentBlock: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    showHistory: Boolean = false,
    onClearSlashQuery: () -> Unit = {},
    allLinkableNotes: List<NoteMetadataEntity> = emptyList(),
    mentionQuery: String = "",
    onMentionNoteSelected: (String) -> Unit = {},
    onMentionCreateNote: (String) -> Unit = {},
    onMentionCreateBlank: () -> Unit = {},
    onNoteLinkSelected: (String) -> Unit = {},
    onNoteLinkCreateNote: (String) -> Unit = {},
    onNoteLinkCreateBlank: () -> Unit = {},
    hazeState: HazeState
) {
    if (isDesktopPlatform) return

    val keyboardController = LocalSoftwareKeyboardController.current
    val tint = MaterialTheme.colorScheme.primary
    val iconSize = 19.dp
    val customIconSize = 18.dp
    var isChoosingHighlightColor by remember { mutableStateOf(false) }

    LaunchedEffect(mobileMenuState) { isChoosingHighlightColor = false }

    KmpBackHandler(enabled = mobileMenuState != MobileMenuState.MAIN) {
        onMenuStateChange(MobileMenuState.MAIN)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (isChoosingHighlightColor) {
            FloatingHighlightColors(
                onColorChosen = onToggleFormat,
                onClose = { isChoosingHighlightColor = false }
            )
        }

        Surface(
            shape = DefaultCornerShape,
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .customEmberrShadow(DefaultCornerShape)
                .clip(DefaultCornerShape)
                .emberrBlur(hazeState, EmberrBlur.Regular)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = DefaultCornerShape
                )
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                    when (mobileMenuState) {
                        MobileMenuState.MAIN -> if (GlobalEditorState.hasTextSelection) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                InlineFormatButtons(
                                    tint = tint,
                                    iconSize = customIconSize,
                                    onToggleFormat = onToggleFormat,
                                    onHighlightClick = { isChoosingHighlightColor = !isChoosingHighlightColor }
                                )
                                ToolbarButton(onClick = { keyboardController?.hide() }) {
                                    Icon(painterResource(Res.drawable.keyboard), "Close Keyboard", tint = tint, modifier = Modifier.size(customIconSize))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(rememberScrollState()),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (showHistory) {
                                        ToolbarButton(enabled = canUndo, onClick = onUndo) {
                                            Icon(painterResource(Res.drawable.undo_circle), "Undo", tint = if (canUndo) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(customIconSize))
                                        }
                                        ToolbarButton(enabled = canRedo, onClick = onRedo) {
                                            Icon(painterResource(Res.drawable.redo_circle), "Redo", tint = if (canRedo) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(customIconSize))
                                        }
                                        ToolbarDivider(tint)
                                    }

                                    ToolbarButton(onClick = {
                                        GlobalEditorState.currentlyFocusedBlockId?.let {
                                            EditorEventBus.requestInsertMention(it)
                                        }
                                    }) {
                                        Icon(painterResource(Res.drawable.at), "Link to note", tint = tint, modifier = Modifier.size(customIconSize))
                                    }

                                    ToolbarButton(onClick = {
                                        keyboardController?.hide()
                                        onSelectCurrentBlock()
                                    }) {
                                        Icon(painterResource(Res.drawable.mouse_square2), "Select Block", tint = tint, modifier = Modifier.size(customIconSize - 2.dp))
                                    }

                                    ToolbarDivider(tint)

                                    ToolbarButton(onClick = { onChangeBlockType("text") }) {
                                        Icon(Icons.AutoMirrored.Filled.Subject, "Text", tint = tint, modifier = Modifier.size(iconSize))
                                    }
                                    ToolbarLabel("H1", tint) { onChangeBlockType("h1") }
                                    ToolbarLabel("H2", tint) { onChangeBlockType("h2") }
                                    ToolbarButton(onClick = { onChangeBlockType("checkbox") }) {
                                        Icon(painterResource(Res.drawable.check_square), "Checkbox", tint = tint, modifier = Modifier.size(customIconSize - 2.dp))
                                    }
                                    ToolbarButton(onClick = { onChangeBlockType("bullet") }) {
                                        Icon(painterResource(Res.drawable.unordered_list), "Bulleted list", tint = tint, modifier = Modifier.size(customIconSize - 2.dp))
                                    }
                                    ToolbarButton(onClick = { onChangeBlockType("number") }) {
                                        Icon(painterResource(Res.drawable.ordered_list), "Numbered list", tint = tint, modifier = Modifier.size(customIconSize - 3.dp))
                                    }
                                    ToolbarButton(onClick = { onChangeBlockType("toggle") }) {
                                        Icon(painterResource(Res.drawable.arrow_right2), "Toggle list", tint = tint, modifier = Modifier.size(customIconSize))
                                    }
                                    ToolbarButton(onClick = { onChangeBlockType("quote") }) {
                                        Icon(painterResource(Res.drawable.quote_down2), "Quote", tint = tint, modifier = Modifier.size(customIconSize - 3.dp))
                                    }
                                    ToolbarButton(onClick = { onChangeBlockType("code") }) {
                                        Icon(painterResource(Res.drawable.code), "Code", tint = tint, modifier = Modifier.size(customIconSize))
                                    }

                                    ToolbarDivider(tint)

                                    InlineFormatButtons(
                                        tint = tint,
                                        iconSize = customIconSize,
                                        onToggleFormat = onToggleFormat,
                                        onHighlightClick = { isChoosingHighlightColor = !isChoosingHighlightColor }
                                    )

                                    ToolbarDivider(tint)

                                    ToolbarButton(onClick = { onSetAlignment(TextAlignment.LEFT) }) {
                                        Icon(painterResource(Res.drawable.textalign_left2), "Align left", tint = tint, modifier = Modifier.size(customIconSize))
                                    }
                                    ToolbarButton(onClick = { onSetAlignment(TextAlignment.RIGHT) }) {
                                        Icon(painterResource(Res.drawable.textalign_right2), "Align right", tint = tint, modifier = Modifier.size(customIconSize))
                                    }
                                    ToolbarButton(onClick = { onSetAlignment(TextAlignment.CENTER) }) {
                                        Icon(painterResource(Res.drawable.textalign_center2), "Align center", tint = tint, modifier = Modifier.size(customIconSize))
                                    }
                                    ToolbarButton(onClick = { onSetAlignment(TextAlignment.JUSTIFY) }) {
                                        Icon(painterResource(Res.drawable.textalign_justifycenter2), "Justify", tint = tint, modifier = Modifier.size(customIconSize))
                                    }

                                    ToolbarDivider(tint)

                                    ToolbarButton(onClick = { EditorEventBus.insertSlashEvent.tryEmit(Unit) }) {
                                        Icon(painterResource(Res.drawable.plus), "More blocks", tint = tint, modifier = Modifier.size(customIconSize - 1.dp))
                                    }
                                }

                                ToolbarDivider(tint)
                                ToolbarButton(onClick = { keyboardController?.hide() }) {
                                    Icon(painterResource(Res.drawable.keyboard), "Close Keyboard", tint = tint, modifier = Modifier.size(customIconSize))
                                }
                            }
                        }
                        MobileMenuState.SLASH -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MenuDragHandle(onClose = { onMenuStateChange(MobileMenuState.MAIN) })
                                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                                    DesktopSlashMenuContent(
                                        query = query,
                                        onChangeBlockType = {
                                            onClearSlashQuery()
                                            onChangeBlockType(it)
                                            onMenuStateChange(MobileMenuState.MAIN)
                                        },
                                        onToggleFormat = {
                                            onClearSlashQuery()
                                            onToggleFormat(it)
                                            onMenuStateChange(MobileMenuState.MAIN)
                                        },
                                        onAdjustIndentation = {
                                            onClearSlashQuery()
                                            onAdjustIndentation(it)
                                            onMenuStateChange(MobileMenuState.MAIN)
                                        },
                                        onSetAlignment = {
                                            onClearSlashQuery()
                                            onSetAlignment(it)
                                            onMenuStateChange(MobileMenuState.MAIN)
                                        },
                                        onInsertMediaBlock = {
                                            onClearSlashQuery()
                                            onInsertMediaBlock(it)
                                            onMenuStateChange(
                                                if (it == "linked_note") MobileMenuState.LINK_TO_NOTE else MobileMenuState.MAIN
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        MobileMenuState.MENU -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MenuDragHandle(onClose = { onMenuStateChange(MobileMenuState.MAIN) })
                                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                    DesktopSlashMenuContent(
                                        query = "",
                                        onChangeBlockType = {
                                            onChangeBlockType(it)
                                            onMenuStateChange(MobileMenuState.MAIN)
                                        },
                                        onToggleFormat = {
                                            onToggleFormat(it)
                                            onMenuStateChange(MobileMenuState.MAIN)
                                        },
                                        onAdjustIndentation = {
                                            onAdjustIndentation(it)
                                            onMenuStateChange(MobileMenuState.MAIN)
                                        },
                                        onSetAlignment = {
                                            onSetAlignment(it)
                                            onMenuStateChange(MobileMenuState.MAIN)
                                        },
                                        onInsertMediaBlock = {
                                            onInsertMediaBlock(it)
                                            onMenuStateChange(
                                                if (it == "linked_note") MobileMenuState.LINK_TO_NOTE else MobileMenuState.MAIN
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        MobileMenuState.MENTION -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MenuDragHandle(onClose = { onMenuStateChange(MobileMenuState.MAIN) })
                                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                                    val filteredNotes = remember(mentionQuery, allLinkableNotes) {
                                        allLinkableNotes.filter { it.title.contains(mentionQuery, ignoreCase = true) }
                                    }
                                    val entries = remember(mentionQuery, filteredNotes) {
                                        buildList {
                                            add(
                                                SlashMenuItemData("Create new note", SlashMenuIcon.Vector(Icons.AutoMirrored.Filled.NoteAdd)) {
                                                    onMentionCreateBlank()
                                                }
                                            )
                                            filteredNotes.forEach { note ->
                                                val icon = note.icon?.let { SlashMenuIcon.Label(it) } ?: SlashMenuIcon.Vector(Icons.Default.Description)
                                                add(SlashMenuItemData(note.title.ifEmpty { "Untitled" }, icon) { onMentionNoteSelected(note.noteId) })
                                            }
                                            if (mentionQuery.isNotBlank()) {
                                                add(
                                                    SlashMenuItemData("New \"$mentionQuery\" note", SlashMenuIcon.Vector(Icons.Default.Add)) {
                                                        onMentionCreateNote(mentionQuery)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    SlashMenuList(sections = listOf(SlashMenuSectionData("Link to Note", entries)))
                                }
                            }
                        }
                        MobileMenuState.LINK_TO_NOTE -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MenuDragHandle(onClose = { onMenuStateChange(MobileMenuState.MAIN) })
                                NoteLinkMenuContent(
                                    allLinkableNotes = allLinkableNotes,
                                    onNoteSelected = {
                                        onNoteLinkSelected(it)
                                        onMenuStateChange(MobileMenuState.MAIN)
                                    },
                                    onCreateNote = {
                                        onNoteLinkCreateNote(it)
                                        onMenuStateChange(MobileMenuState.MAIN)
                                    },
                                    onCreateBlankNote = {
                                        onNoteLinkCreateBlank()
                                        onMenuStateChange(MobileMenuState.MAIN)
                                    },
                                    autoFocusSearch = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineFormatButtons(
    tint: Color,
    iconSize: Dp,
    onToggleFormat: (String) -> Unit,
    onHighlightClick: () -> Unit
) {
    ToolbarButton(onClick = { onToggleFormat("bold") }) {
        Icon(painterResource(Res.drawable.format_bold), "Bold", tint = tint, modifier = Modifier.size(iconSize - 4.dp))
    }
    ToolbarButton(onClick = { onToggleFormat("italic") }) {
        Icon(painterResource(Res.drawable.italic), "Italic", tint = tint, modifier = Modifier.size(iconSize - 4.dp))
    }
    ToolbarButton(onClick = { onToggleFormat("strike") }) {
        Icon(painterResource(Res.drawable.text_x), "Strikethrough", tint = tint, modifier = Modifier.size(iconSize - 2.dp))
    }
    ToolbarButton(onClick = { onToggleFormat("underline") }) {
        Icon(painterResource(Res.drawable.underline), "Underline", tint = tint, modifier = Modifier.size(iconSize - 2.dp))
    }
    ToolbarButton(onClick = onHighlightClick) {
        Icon(painterResource(Res.drawable.highlight), "Highlight", tint = tint, modifier = Modifier.size(iconSize - 2.dp))
    }
}

@Composable
private fun FloatingHighlightColors(onColorChosen: (String) -> Unit, onClose: () -> Unit) {
    val isDarkTheme = LocalAppIsDark.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FloatingSwatch(
            fillColor = MaterialTheme.colorScheme.background,
            onClick = onClose
        ) {
            Icon(
                Icons.Default.Close,
                "Close colours",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(FloatingSwatchSize / 2)
            )
        }

        FloatingSwatch(
            fillColor = Color.Transparent,
            showsRemoveMark = true,
            onClick = { onColorChosen("highlight:none") }
        )

        HighlightColor.entries.forEach { highlightColor ->
            FloatingSwatch(
                fillColor = highlightColor.backgroundFor(isDarkTheme),
                onClick = { onColorChosen("highlight:${highlightColor.storageName}") }
            )
        }
    }
}

@Composable
private fun FloatingSwatch(
    fillColor: Color,
    onClick: () -> Unit,
    showsRemoveMark: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(FloatingSwatchSize)
            .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(fillColor)
            .then(if (showsRemoveMark) Modifier.removeHighlightMark() else Modifier)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private val ToolbarButtonSize = 34.dp
private val FloatingSwatchSize = 30.dp

@Composable
private fun ToolbarButton(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(ToolbarButtonSize)
            .clip(CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ToolbarLabel(label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(ToolbarButtonSize)
            .defaultMinSize(minWidth = ToolbarButtonSize)
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun ToolbarDivider(tint: Color) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(18.dp)
            .background(tint.copy(alpha = 0.2f))
    )
}

@Composable
private fun MenuDragHandle(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 15f) {
                        onClose()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        )
    }
}

fun buildSlashMenuSections(
    onChangeBlockType: (String) -> Unit,
    onToggleFormat: (String) -> Unit,
    onAdjustIndentation: (Boolean) -> Unit,
    onSetAlignment: (TextAlignment) -> Unit,
    onInsertMediaBlock: (String) -> Unit
): List<SlashMenuSectionData> = listOf(
    SlashMenuSectionData("Basic Blocks", listOf(
        SlashMenuItemData("Text", Icons.AutoMirrored.Filled.Subject) { onChangeBlockType("text") },
        SlashMenuItemData("Heading 1", SlashMenuIcon.Label("H1")) { onChangeBlockType("h1") },
        SlashMenuItemData("Heading 2", SlashMenuIcon.Label("H2")) { onChangeBlockType("h2") },
        SlashMenuItemData("To-do List", Res.drawable.check_square) { onChangeBlockType("checkbox") },
        SlashMenuItemData("Bulleted List", Res.drawable.unordered_list, 15.dp) { onChangeBlockType("bullet") },
        SlashMenuItemData("Numbered List", Res.drawable.ordered_list, 14.dp) { onChangeBlockType("number") },
        SlashMenuItemData("Toggle List", Res.drawable.arrow_right2) { onChangeBlockType("toggle") },
        SlashMenuItemData("Quote", Res.drawable.quote_down2, 14.dp) { onChangeBlockType("quote") },
        SlashMenuItemData("Code Block", Res.drawable.code) { onChangeBlockType("code") }
    )),
    SlashMenuSectionData("Media & Links", listOf(
        SlashMenuItemData("Voice Note", Res.drawable.microphone) { onChangeBlockType("voice") },
        SlashMenuItemData("Image", Res.drawable.image) { onInsertMediaBlock("image") },
        SlashMenuItemData("Document / File", Res.drawable.file_text) { onInsertMediaBlock("document") },
        SlashMenuItemData("Web Bookmark", Res.drawable.bookmark) { onInsertMediaBlock("bookmark") },
        SlashMenuItemData("Database / Table", Res.drawable.square_kanban) { onInsertMediaBlock("database") },
        SlashMenuItemData("Simple Table", Res.drawable.table) { onInsertMediaBlock("table") },
        SlashMenuItemData("Link to Note", Res.drawable.link) { onInsertMediaBlock("linked_note") }
    )),
    SlashMenuSectionData("Inline Text Formatting", listOf(
        SlashMenuItemData("Bold Text", Res.drawable.format_bold, 13.dp) { onToggleFormat("bold") },
        SlashMenuItemData("Italic Text", Res.drawable.italic, 13.dp) { onToggleFormat("italic") },
        SlashMenuItemData("Underline Text", Res.drawable.underline, 15.dp) { onToggleFormat("underline") },
        SlashMenuItemData("Strikethrough Text", Res.drawable.text_x, 15.dp) { onToggleFormat("strike") },
        SlashMenuItemData("Highlight Text", Res.drawable.highlight, 15.dp) { onToggleFormat("highlight") }
    )),
    SlashMenuSectionData("Alignment", listOf(
        SlashMenuItemData("Align Left", Res.drawable.textalign_left2) { onSetAlignment(TextAlignment.LEFT) },
        SlashMenuItemData("Align Right", Res.drawable.textalign_right2) { onSetAlignment(TextAlignment.RIGHT) },
        SlashMenuItemData("Align Center", Res.drawable.textalign_center2) { onSetAlignment(TextAlignment.CENTER) },
        SlashMenuItemData("Justify", Res.drawable.textalign_justifycenter2) { onSetAlignment(TextAlignment.JUSTIFY) }
    )),
    SlashMenuSectionData("Indentation", listOf(
        SlashMenuItemData("Decrease Indent", Res.drawable.indent_right) { onAdjustIndentation(false) },
        SlashMenuItemData("Increase Indent", Res.drawable.indent_left) { onAdjustIndentation(true) }
    )),
    SlashMenuSectionData("Dividers", listOf(
        SlashMenuItemData("Solid Line", Res.drawable.minus) { onChangeBlockType("divider_solid") },
        SlashMenuItemData("Three Dots", Res.drawable.ellipsis) { onChangeBlockType("divider_dots") }
    )),
)

fun filteredSlashMenuSections(
    query: String,
    onChangeBlockType: (String) -> Unit,
    onToggleFormat: (String) -> Unit,
    onAdjustIndentation: (Boolean) -> Unit,
    onSetAlignment: (TextAlignment) -> Unit,
    onInsertMediaBlock: (String) -> Unit
): List<SlashMenuSectionData> = buildSlashMenuSections(
    onChangeBlockType, onToggleFormat, onAdjustIndentation, onSetAlignment, onInsertMediaBlock
).map { section ->
    section.copy(items = section.items.filter { item ->
        query.isBlank() || item.label.contains(query, ignoreCase = true)
    })
}.filter { it.items.isNotEmpty() }

@Composable
fun DesktopSlashMenuContent(
    query: String,
    onChangeBlockType: (String) -> Unit,
    onToggleFormat: (String) -> Unit,
    onAdjustIndentation: (Boolean) -> Unit,
    onSetAlignment: (TextAlignment) -> Unit,
    onInsertMediaBlock: (String) -> Unit,
    selectedIndex: Int = -1
) {
    val filteredSections = remember(
        query,
        onChangeBlockType,
        onToggleFormat,
        onAdjustIndentation,
        onSetAlignment,
        onInsertMediaBlock
    ) {
        filteredSlashMenuSections(query, onChangeBlockType, onToggleFormat, onAdjustIndentation, onSetAlignment, onInsertMediaBlock)
    }

    SlashMenuList(sections = filteredSections, selectedIndex = selectedIndex)
}

@Composable
fun SlashMenuList(sections: List<SlashMenuSectionData>, selectedIndex: Int = -1) {
    val rows = remember(sections) {
        buildList {
            var flatIndex = 0
            sections.forEachIndexed { sectionIndex, section ->
                add(SlashMenuRow.SectionHeader(section.title))
                section.items.forEach { menuItem ->
                    add(SlashMenuRow.Entry(menuItem, flatIndex))
                    flatIndex++
                }
                if (sectionIndex < sections.lastIndex) {
                    add(SlashMenuRow.SectionSpacer)
                }
            }
        }
    }

    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    val itemBoundsByFlatIndex = remember(sections) { mutableStateMapOf<Int, ClosedFloatingPointRange<Float>>() }

    LaunchedEffect(selectedIndex, sections) {
        val bounds = itemBoundsByFlatIndex[selectedIndex] ?: return@LaunchedEffect
        val viewportStart = scrollState.value.toFloat()
        val viewportEnd = viewportStart + viewportHeightPx

        if (bounds.endInclusive > viewportEnd) {
            scrollState.animateScrollTo((scrollState.value + (bounds.endInclusive - viewportEnd)).roundToInt())
        } else if (bounds.start < viewportStart) {
            scrollState.animateScrollTo(bounds.start.roundToInt())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .onGloballyPositioned { viewportHeightPx = it.size.height.toFloat() }
            .verticalScroll(scrollState)
            .padding(vertical = 4.dp)
    ) {
        if (sections.isEmpty()) {
            Text(
                text = "No results found",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            rows.forEach { row ->
                when (row) {
                    is SlashMenuRow.SectionHeader -> SlashMenuHeader(row.title)
                    is SlashMenuRow.Entry -> SlashMenuItem(
                        text = row.item.label,
                        icon = row.item.icon,
                        isSelected = row.flatIndex == selectedIndex,
                        onClick = row.item.action,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val top = coordinates.positionInParent().y
                            val bottom = top + coordinates.size.height
                            itemBoundsByFlatIndex[row.flatIndex] = top..bottom
                        }
                    )
                    SlashMenuRow.SectionSpacer -> Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

private sealed interface SlashMenuRow {
    data class SectionHeader(val title: String) : SlashMenuRow
    data class Entry(val item: SlashMenuItemData, val flatIndex: Int) : SlashMenuRow
    data object SectionSpacer : SlashMenuRow
}

@Composable
private fun SlashMenuItem(
    text: String,
    icon: SlashMenuIcon,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(SlashMenuIconSlot),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is SlashMenuIcon.Drawable -> Icon(
                    painterResource(icon.resource),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(icon.size)
                )
                is SlashMenuIcon.Vector -> Icon(
                    icon.image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(icon.size)
                )
                is SlashMenuIcon.Label -> Text(
                    icon.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SlashMenuHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
fun NoteLinkMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    allLinkableNotes: List<NoteMetadataEntity>,
    onNoteSelected: (noteId: String) -> Unit,
    onCreateNote: (title: String) -> Unit,
    onCreateBlankNote: () -> Unit
) {
    if (!expanded) return

    EmberrDesktopMenu(
        expanded = true,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
        modifier = Modifier
            .width(290.dp)
            .heightIn(max = 400.dp)
    ) {
        NoteLinkMenuContent(
            allLinkableNotes = allLinkableNotes,
            onNoteSelected = onNoteSelected,
            onCreateNote = onCreateNote,
            onCreateBlankNote = onCreateBlankNote,
            onDismissRequest = onDismissRequest
        )
    }
}

@Composable
fun NoteLinkMenuContent(
    allLinkableNotes: List<NoteMetadataEntity>,
    onNoteSelected: (noteId: String) -> Unit,
    onCreateNote: (title: String) -> Unit,
    onCreateBlankNote: () -> Unit,
    onDismissRequest: () -> Unit = {},
    autoFocusSearch: Boolean = true
) {
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember(query) { mutableIntStateOf(0) }

    val filteredNotes = remember(query, allLinkableNotes) {
        allLinkableNotes.filter { it.title.contains(query, ignoreCase = true) }
    }

    val entries = remember(query, filteredNotes) {
        buildList {
            add(
                SlashMenuItemData("Create new note", SlashMenuIcon.Vector(Icons.AutoMirrored.Filled.NoteAdd)) {
                    onCreateBlankNote()
                }
            )
            filteredNotes.forEach { note ->
                val icon = note.icon?.let { SlashMenuIcon.Label(it) } ?: SlashMenuIcon.Vector(Icons.Default.Description)
                add(SlashMenuItemData(note.title.ifEmpty { "Untitled" }, icon) { onNoteSelected(note.noteId) })
            }
            if (query.isNotBlank()) {
                add(
                    SlashMenuItemData("New \"$query\" note", SlashMenuIcon.Vector(Icons.Default.Add)) {
                        onCreateNote(query.trim())
                    }
                )
            }
        }
    }

    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) searchFocusRequester.requestFocus()
    }

    EmberrTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = "Search notes...",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .focusRequester(searchFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        if (entries.isNotEmpty()) selectedIndex = (selectedIndex + 1) % entries.size
                        true
                    }
                    Key.DirectionUp -> {
                        if (entries.isNotEmpty()) selectedIndex = (selectedIndex - 1 + entries.size) % entries.size
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        entries.getOrNull(selectedIndex)?.action?.invoke()
                        true
                    }
                    Key.Escape -> {
                        onDismissRequest()
                        true
                    }
                    else -> false
                }
            }
    )
    SlashMenuList(
        sections = listOf(SlashMenuSectionData("Link to Note", entries)),
        selectedIndex = selectedIndex
    )
}

@Composable
private fun TaskBadge(icon: ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun SelectionModeObserver(isSelectionMode: Boolean, onSelectionModeChange: (Boolean) -> Unit) {
    LaunchedEffect(isSelectionMode) { onSelectionModeChange(isSelectionMode) }
}

@Composable
fun BlockSelectionPill(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onAddBlockAbove: () -> Unit,
    onAddBlockBelow: () -> Unit,
    onTogglePin: () -> Unit,
    isSelectionPinned: Boolean = false,
    selectedBlocks: List<NoteBlock> = emptyList(),
    onUpdateLinkedNoteOptions: (id: String, showIcon: Boolean, showCoverImage: Boolean) -> Unit = { _, _, _ -> },
    showStyleButton: Boolean = false,
    isStyleBarOpen: Boolean = false,
    onToggleStyleBar: () -> Unit = {},
    hazeState: HazeState
) {
    val selectedLinkedNoteBlock = selectedBlocks.singleOrNull() as? LinkedNoteBlock
    var showLinkedNoteOptions by remember { mutableStateOf(false) }

    val tint = MaterialTheme.colorScheme.primary

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.padding(horizontal = 24.dp)
    ) {
        Surface(
            shape = DefaultCornerShape,
            color = Color.Transparent,
            modifier = Modifier
                .padding(bottom = 32.dp)
                .customEmberrShadow(DefaultCornerShape)
                .clip(DefaultCornerShape)
                .emberrBlur(hazeState, EmberrBlur.Regular)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = DefaultCornerShape
                )
        ) {
            val scrollState = rememberScrollState()
            val divider = @Composable {
                Box(Modifier.width(1.dp).height(18.dp).background(tint.copy(alpha = 0.2f)))
            }

            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                val iconSize = 18.dp
                Icon(painterResource(Res.drawable.x), null, modifier = Modifier.size(iconSize).clickable { onClearSelection() }, tint = tint)
                Text("$selectedCount", style = MaterialTheme.typography.bodyLarge, color = tint)
                divider()

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelectionPinned) tint.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { onTogglePin() }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(Res.drawable.thumbtack),
                        if (isSelectionPinned) "Unpin Block" else "Pin Block",
                        modifier = Modifier.size(iconSize),
                        tint = tint
                    )
                }
                divider()

                if (selectedLinkedNoteBlock != null) {
                    Box {
                        Icon(
                            painterResource(Res.drawable.eye3),
                            "Preview",
                            modifier = Modifier.size(iconSize + 3.dp).clickable { showLinkedNoteOptions = true },
                            tint = tint
                        )
                        LinkedNoteOptionsMenu(
                            expanded = showLinkedNoteOptions,
                            onDismiss = { showLinkedNoteOptions = false },
                            block = selectedLinkedNoteBlock,
                            onUpdateOptions = { showIcon, showCoverImage ->
                                onUpdateLinkedNoteOptions(selectedLinkedNoteBlock.id, showIcon, showCoverImage)
                            }
                        )
                    }
                    divider()
                }

                if (showStyleButton) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isStyleBarOpen) tint.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onToggleStyleBar() }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(painterResource(Res.drawable.text_tool_2), "Style Selected Blocks", modifier = Modifier.size(iconSize + 3.dp), tint = tint)
                    }
                    divider()
                }

                Icon(Icons.Default.SelectAll, "Select All", modifier = Modifier.size(iconSize + 3.dp).clickable { onSelectAll() }, tint = tint)
                Icon(painterResource(Res.drawable.copy), "Copy", modifier = Modifier.size(iconSize).clickable { onCopy() }, tint = tint)
                Icon(painterResource(Res.drawable.scissor2), "Cut", modifier = Modifier.size(iconSize).clickable { onCut() }, tint = tint)
                divider()
                Icon(painterResource(Res.drawable.arrow_up), "Add above", modifier = Modifier.size(iconSize).clickable { onAddBlockAbove() }, tint = tint)
                Icon(painterResource(Res.drawable.arrow_down), "Add below", modifier = Modifier.size(iconSize).clickable { onAddBlockBelow() }, tint = tint)
                divider()
                Icon(painterResource(Res.drawable.trash), "Delete", modifier = Modifier.size(iconSize).clickable { onDelete() }, tint = tint)
            }
        }
    }
}

// Bulk style bar shown above BlockSelectionPill once the user taps its "Style" button
@Composable
fun BlockStyleBar(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    onChangeBlockType: (String) -> Unit,
    onToggleFormat: (String) -> Unit,
    onAdjustIndentation: (Boolean) -> Unit,
    onSetAlignment: (TextAlignment) -> Unit,
    hazeState: HazeState
) {
    val tint = MaterialTheme.colorScheme.primary
    val iconSize = 19.dp
    val customIconSize = 18.dp
    var isChoosingHighlightColor by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.padding(horizontal = 24.dp)
    ) {
        Column {
            if (isChoosingHighlightColor) {
                FloatingHighlightColors(
                    onColorChosen = onToggleFormat,
                    onClose = { isChoosingHighlightColor = false }
                )
            }

            Surface(
                shape = DefaultCornerShape,
                color = Color.Transparent,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .customEmberrShadow(DefaultCornerShape)
                    .clip(DefaultCornerShape)
                    .emberrBlur(hazeState, EmberrBlur.Regular)
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        shape = DefaultCornerShape
                    )
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ToolbarButton(onClick = { onChangeBlockType("text") }) {
                            Icon(Icons.AutoMirrored.Filled.Subject, "Text", tint = tint, modifier = Modifier.size(iconSize))
                        }
                        ToolbarLabel("H1", tint) { onChangeBlockType("h1") }
                        ToolbarLabel("H2", tint) { onChangeBlockType("h2") }
                        ToolbarButton(onClick = { onChangeBlockType("checkbox") }) {
                            Icon(painterResource(Res.drawable.check_square), "Checkbox", tint = tint, modifier = Modifier.size(customIconSize - 2.dp))
                        }
                        ToolbarButton(onClick = { onChangeBlockType("bullet") }) {
                            Icon(painterResource(Res.drawable.unordered_list), "Bulleted list", tint = tint, modifier = Modifier.size(customIconSize - 2.dp))
                        }
                        ToolbarButton(onClick = { onChangeBlockType("number") }) {
                            Icon(painterResource(Res.drawable.ordered_list), "Numbered list", tint = tint, modifier = Modifier.size(customIconSize - 3.dp))
                        }
                        ToolbarButton(onClick = { onChangeBlockType("toggle") }) {
                            Icon(painterResource(Res.drawable.arrow_right2), "Toggle list", tint = tint, modifier = Modifier.size(customIconSize))
                        }
                        ToolbarButton(onClick = { onChangeBlockType("quote") }) {
                            Icon(painterResource(Res.drawable.quote_down2), "Quote", tint = tint, modifier = Modifier.size(customIconSize - 3.dp))
                        }
                        ToolbarButton(onClick = { onChangeBlockType("code") }) {
                            Icon(painterResource(Res.drawable.code), "Code", tint = tint, modifier = Modifier.size(customIconSize))
                        }

                        ToolbarDivider(tint)

                        InlineFormatButtons(
                            tint = tint,
                            iconSize = customIconSize,
                            onToggleFormat = onToggleFormat,
                            onHighlightClick = { isChoosingHighlightColor = !isChoosingHighlightColor }
                        )

                        ToolbarDivider(tint)

                        ToolbarButton(onClick = { onSetAlignment(TextAlignment.LEFT) }) {
                            Icon(painterResource(Res.drawable.textalign_left2), "Align left", tint = tint, modifier = Modifier.size(customIconSize))
                        }
                        ToolbarButton(onClick = { onSetAlignment(TextAlignment.RIGHT) }) {
                            Icon(painterResource(Res.drawable.textalign_right2), "Align right", tint = tint, modifier = Modifier.size(customIconSize))
                        }
                        ToolbarButton(onClick = { onSetAlignment(TextAlignment.CENTER) }) {
                            Icon(painterResource(Res.drawable.textalign_center2), "Align center", tint = tint, modifier = Modifier.size(customIconSize))
                        }
                        ToolbarButton(onClick = { onSetAlignment(TextAlignment.JUSTIFY) }) {
                            Icon(painterResource(Res.drawable.textalign_justifycenter2), "Justify", tint = tint, modifier = Modifier.size(customIconSize))
                        }

                        ToolbarDivider(tint)

                        ToolbarButton(onClick = { onAdjustIndentation(false) }) {
                            Icon(painterResource(Res.drawable.indent_right), "Decrease indent", tint = tint, modifier = Modifier.size(customIconSize))
                        }
                        ToolbarButton(onClick = { onAdjustIndentation(true) }) {
                            Icon(painterResource(Res.drawable.indent_left), "Increase indent", tint = tint, modifier = Modifier.size(customIconSize))
                        }
                    }
                }
            }
        }
    }
}