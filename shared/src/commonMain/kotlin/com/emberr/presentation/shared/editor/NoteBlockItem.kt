package com.emberr.presentation.shared.editor

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.util.system.triggerHapticFeedback
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.VoiceBlock
import com.emberr.domain.util.system.isDesktopPlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.animateFloatAsState
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.ThreeDotDividerBlock
import com.emberr.domain.model.inlineSpansOrEmpty
import com.emberr.domain.model.textAlignmentOrNull
import com.emberr.presentation.shared.components.MinimalDatePickerDialog
import com.emberr.presentation.shared.components.MinimalTimePickerDialog
import com.emberr.presentation.shared.components.ReminderPresetMenu
import com.emberr.presentation.shared.components.TimePresetMenu
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import com.emberr.presentation.shared.editor.blockViews.databaseBlockView.DatabaseBlockView
import com.emberr.presentation.shared.editor.blockViews.DocumentBlockView
import com.emberr.presentation.shared.editor.blockViews.ImageBlockView
import com.emberr.presentation.shared.editor.blockViews.AudioBlockView
import com.emberr.presentation.shared.editor.blockViews.BookmarkBlockView
import com.emberr.presentation.shared.editor.blockViews.LinkedNoteBlockView
import com.emberr.presentation.shared.editor.blockViews.TableBlockView
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.ui.theme.LocalEmberrFontStyle
import com.emberr.ui.theme.fontFamilyFor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.calendar_add
import emberr.shared.generated.resources.clock_circle
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

val DefaultBlockShape = RoundedCornerShape(12.dp)

private const val NOTE_LINK_MARKER = "](emberr://note/"
private val NoteLinkRegex = """\[([^\]]+)\]\(emberr://note/([^)]+)\)""".toRegex()
private val TrailingNoteLinkRegex = """\[([^\]]+)\]\(emberr://note/([^)]+)\)$""".toRegex()

private fun TextAlignment.toComposeTextAlign(): TextAlign = when (this) {
    TextAlignment.LEFT -> TextAlign.Left
    TextAlignment.RIGHT -> TextAlign.Right
    TextAlignment.CENTER -> TextAlign.Center
    TextAlignment.JUSTIFY -> TextAlign.Justify
}

// All NoteBlock subtypes are @Immutable: never mutate in place, always .copy()
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteBlockItem(
    block: NoteBlock,
    globalTags: ImmutableList<TagEntity>,
    actions: EditorActions,
    focusRequest: FocusRequest?,
    selectedBlockIds: ImmutableSet<String>,
    inSelectionMode: Boolean,
    activeBlockId: String?,
    onFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
    showSlashMenu: Boolean = false,
    slashQuery: String = "",
    allLinkableNotes: List<NoteMetadataEntity> = emptyList(),
    onDismissSlashMenu: () -> Unit = {},
    showNoteLinkMenu: Boolean = false,
    onDismissNoteLinkMenu: () -> Unit = {},
    isFirstToggleChild: Boolean = false,
    selectionRequest: SelectionRequest? = null,
    validNoteIds: Set<String> = emptySet(),
    onRightClick: ((Offset) -> Unit)? = null,
    onShiftClick: ((String) -> Unit)? = null,
) {
    // STANDARD BLOCK LOGIC
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val isActiveBlock = block.id == activeBlockId
    val isSelected = selectedBlockIds.contains(block.id)

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var blockHeight by remember { mutableFloatStateOf(50f) }
    val imeBottom = WindowInsets.ime.getBottom(density)

    val text = when (block) {
        is CodeBlock -> block.code
        is QuoteBlock -> block.text
        is TextBlock -> block.text
        is HeadingBlock -> block.text
        is CheckboxBlock -> block.text
        is BulletedListBlock -> block.text
        is NumberedListBlock -> block.text
        is ToggleBlock -> block.text
        is BookmarkBlock, is ImageBlock, is DocumentBlock, is DatabaseBlock, is TableBlock, is VoiceBlock -> ""
        else -> ""
    }

    val placeholderText = when {
        block is CheckboxBlock && block.reminderTimestamp != null -> "Untitled event"
        isFirstToggleChild -> "Type something..."
        else -> ""
    }

    // LOCAL STATE FOR INSTANT TYPING
    var showPresetMenu by remember { mutableStateOf(false) }
    var showTimePresetMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var isReminderPickerOpening by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val webLinkActions = rememberWebLinkActions()
    val linkHoverState = rememberLinkHoverState()
    val imeInsets = WindowInsets.ime
    val isKeyboardOpen = imeInsets.getBottom(density) > 0

    val afterKeyboardCloses: (action: () -> Unit) -> Unit = { action ->
        if (!isKeyboardOpen) {
            action()
        } else {
            isReminderPickerOpening = true
            focusManager.clearFocus()
            keyboardController?.hide()
            scope.launch {
                withTimeoutOrNull(600.milliseconds) {
                    snapshotFlow { imeInsets.getBottom(density) }.first { it == 0 }
                }
                delay(50L.milliseconds)
                action()
                isReminderPickerOpening = false
            }
        }
    }

    val isDatabase = block is DatabaseBlock

    var lastTappedYInBlock by remember { mutableFloatStateOf(0f) }

// Handles bring-into-view focus positioning for database blocks when the IME keyboard opens.
    // Text blocks manage their own bring-into-view behavior relative to text cursor lines.
    LaunchedEffect(isFocused, imeBottom, blockHeight) {
        if (isFocused && imeBottom > 0 && isDatabase) {
            // Debounces IME animation frame updates until the keyboard stabilizes.
            delay(150.milliseconds)
            val bufferPx = with(density) { 120.dp.toPx() }
            val targetY = if (lastTappedYInBlock > 0f) lastTappedYInBlock else blockHeight / 2f
            val targetRect = Rect(
                left = 0f,
                top = 0f,
                right = 1f,
                bottom = targetY + bufferPx
            )
            bringIntoViewRequester.bringIntoView(targetRect)
        }
    }

    val isTextBased = block !is BookmarkBlock && block !is ImageBlock && block !is DocumentBlock && block !is DatabaseBlock && block !is TableBlock && block !is VoiceBlock && block !is SolidDividerBlock && block !is ThreeDotDividerBlock && block !is LinkedNoteBlock
    LaunchedEffect(focusRequest?.nonce) {
        if (focusRequest == null || focusRequest.id != block.id) return@LaunchedEffect

        if (!isTextBased) {
            // No text field in this branch, so no FocusRequester is attached. Consume the
            // request and leave the existing input session alone rather than failing into
            // a state where nothing is focused.
            actions.onClearFocusRequest()
            return@LaunchedEffect
        }

        val grabbed = runCatching { focusRequester.requestFocus() }.isSuccess
        if (!grabbed) {
            withFrameNanos {}
            runCatching { focusRequester.requestFocus() }
        }
        if (!isKeyboardOpen) keyboardController?.show()
        actions.onClearFocusRequest()
    }

    // STYLING
    val baseStyle = when (block) {
        is HeadingBlock -> TextStyle(
            fontFamily = fontFamilyFor(LocalEmberrFontStyle.current),
            fontSize = if (block.level == 1) {
                if (isDesktopPlatform) 32.sp else 26.sp
            } else {
                if (isDesktopPlatform) 24.sp else 20.sp
            },
            lineHeight = if (block.level == 1) {
                if (isDesktopPlatform) 40.sp else 32.sp
            } else {
                if (isDesktopPlatform) 32.sp else 26.sp
            },
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        is CodeBlock -> MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        is QuoteBlock -> MaterialTheme.typography.bodyLarge.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface
        )
        else -> MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onBackground
        )
    }

    val isCheckboxChecked = block is CheckboxBlock && block.isChecked
    val applyStrikeThrough = block.isStrikeThrough || isCheckboxChecked

    val textStyle = baseStyle.copy(
        fontWeight = if (block.isBold) FontWeight.Bold else baseStyle.fontWeight,
        fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = when {
            applyStrikeThrough && block.isUnderlined -> TextDecoration.LineThrough + TextDecoration.Underline
            applyStrikeThrough -> TextDecoration.LineThrough
            block.isUnderlined -> TextDecoration.Underline
            else -> TextDecoration.None
        },
        textAlign = block.textAlignmentOrNull()?.toComposeTextAlign() ?: TextAlign.Unspecified,
        color = if (isCheckboxChecked) MaterialTheme.colorScheme.outline else baseStyle.color
    )

    val internalVerticalPadding = when (block) {
        is HeadingBlock -> 8.dp
        is CodeBlock -> 4.dp
        else -> 4.dp
    }

    val selectionBg = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent

    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.surface
    )

    val desktopExtraPadding = if (isDesktopPlatform) 16.dp else 0.dp
    val startPadding = when {
        isDatabase || block is TableBlock -> (block.indentationLevel * 28).dp + desktopExtraPadding
        block is CheckboxBlock -> (18 + (block.indentationLevel * 28)).dp + desktopExtraPadding
        block is BulletedListBlock -> (18 + (block.indentationLevel * 28)).dp + desktopExtraPadding
        block is NumberedListBlock -> (18 + (block.indentationLevel * 28)).dp + desktopExtraPadding
        block is ToggleBlock -> (18 + (block.indentationLevel * 28)).dp + desktopExtraPadding
        else -> (16 + (block.indentationLevel * 28)).dp + desktopExtraPadding
    }
    val endPadding = (if (isDatabase || block is TableBlock) 0.dp else 16.dp) + desktopExtraPadding

    val isSlashMenuActiveHere = isDesktopPlatform && isActiveBlock && showSlashMenu
    val isNoteLinkMenuActiveHere = isDesktopPlatform && isActiveBlock && showNoteLinkMenu

    val slashMenuSections = remember(slashQuery, isSlashMenuActiveHere) {
        if (isSlashMenuActiveHere) {
            filteredSlashMenuSections(
                query = slashQuery,
                onChangeBlockType = { actions.onChangeBlockType(it) },
                onToggleFormat = { actions.onToggleFormat(it) },
                onAdjustIndentation = { actions.onAdjustIndentation(it) },
                onSetAlignment = { actions.onSetBlockAlignment(it) },
                onInsertMediaBlock = { actions.onInsertMediaBlock(it) }
            )
        } else {
            emptyList()
        }
    }
    val slashMenuFilteredItems = remember(slashMenuSections) { slashMenuSections.flatMap { it.items } }
    var slashMenuSelectedIndex by remember(slashQuery) { mutableIntStateOf(0) }

    var blockTopLeftInWindow by remember { mutableStateOf(Offset.Zero) }
    val handleRightClick: (Offset) -> Unit = { pressPositionInBlock ->
        if (!isSelected) actions.onToggleSelection(block.id)
        onRightClick?.invoke(blockTopLeftInWindow + pressPositionInBlock)
    }
    val latestRightClickHandler by rememberUpdatedState(handleRightClick)
    val latestShiftClickHandler by rememberUpdatedState(onShiftClick)
    val latestInSelectionMode by rememberUpdatedState(inSelectionMode)

    val blockMouseModifier = if (isDesktopPlatform && (onRightClick != null || onShiftClick != null)) {
        Modifier
            .onGloballyPositioned { blockTopLeftInWindow = it.positionInWindow() }
            .pointerInput(block.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Press) continue
                        val press = event.changes.firstOrNull() ?: continue
                        if (press.isConsumed || isFocused) continue

                        val opensContextMenu = event.buttons.isSecondaryPressed &&
                            linkHoverState.hoveredLink == null
                        val extendsSelection = event.buttons.isPrimaryPressed &&
                            event.keyboardModifiers.isShiftPressed &&
                            latestInSelectionMode

                        if (opensContextMenu) {
                            press.consume()
                            latestRightClickHandler(press.position)
                        } else if (extendsSelection) {
                            press.consume()
                            latestShiftClickHandler?.invoke(block.id)
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    // RENDER BLOCK CONTENT
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(blockMouseModifier)
            .background(selectionBg)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (inSelectionMode) actions.onToggleSelection(block.id) },
                onLongClick = { actions.onToggleSelection(block.id) }
            )
    ) {
        // Desktop slash menu
        if (isSlashMenuActiveHere) {
            EmberrDesktopMenu(
                expanded = true,
                onDismissRequest = onDismissSlashMenu,
                properties = PopupProperties(focusable = false),
                modifier = Modifier
                    .width(290.dp)
                    .heightIn(max = 400.dp)
            ) {
                SlashMenuList(
                    sections = slashMenuSections,
                    selectedIndex = slashMenuSelectedIndex
                )
            }
        }

        if (isNoteLinkMenuActiveHere) {
            NoteLinkMenu(
                expanded = true,
                onDismissRequest = onDismissNoteLinkMenu,
                allLinkableNotes = allLinkableNotes,
                onNoteSelected = { noteId ->
                    actions.onInsertLinkedNoteBlock(noteId)
                    onDismissNoteLinkMenu()
                },
                onCreateNote = { title ->
                    val newNoteId = actions.onCreateLinkedNote(title)
                    actions.onInsertLinkedNoteBlock(newNoteId)
                    onDismissNoteLinkMenu()
                },
                onCreateBlankNote = {
                    val newNoteId = actions.onCreateLinkedNote("Untitled")
                    actions.onInsertLinkedNoteBlock(newNoteId)
                    onDismissNoteLinkMenu()
                    actions.onNoteLinkClick(newNoteId)
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPadding, end = endPadding)
                .padding(vertical = internalVerticalPadding),
            verticalAlignment = Alignment.Top
        ) {
            val iconOffset = when (block) {
                is HeadingBlock -> if (block.level == 1) 4.dp else 2.dp
                is CodeBlock -> 12.dp
                else -> (-2).dp
            }

            if (block is CheckboxBlock || block is BulletedListBlock || block is NumberedListBlock || block is ToggleBlock) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .offset(y = iconOffset)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (block) {
                        is CheckboxBlock -> CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides 0.dp
                        ) {
                            Checkbox(
                                checked = block.isChecked,
                                onCheckedChange = {
                                    triggerHapticFeedback()
                                    actions.onToggleCheckbox(block.id, it)
                                },
                                modifier = Modifier.scale(0.9f).size(16.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.surface,
                                    checkmarkColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                        is BulletedListBlock -> Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(textStyle.color))
                        is NumberedListBlock -> Text("${block.number}.", style = textStyle.copy(fontSize = 17.sp))
                        is ToggleBlock -> {
                            val rotation by animateFloatAsState(if (block.isExpanded) 90f else 0f, label = "toggleRotation")
                            Icon(
                                Icons.Default.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp).rotate(rotation).clickable { actions.onToggleExpand(block.id) }
                            )
                        }
                    }
                }
            }

            val primaryColor = MaterialTheme.colorScheme.primary
            val quoteAccentColor = Color(0xFFFFD54F)
            val DefaultBlockShape = RoundedCornerShape(12.dp)
            val textFieldWrapperModifier = (if (block is CodeBlock) {
                Modifier.weight(1f).padding(horizontal = 4.dp)
                    .background(MaterialTheme.colorScheme.surface, DefaultBlockShape).padding(12.dp)
            } else if (block is QuoteBlock) {
                Modifier.weight(1f).padding(horizontal = 4.dp).drawBehind {
                    drawLine(color = quoteAccentColor, start = Offset(0f, 0f), end = Offset(0f, size.height), strokeWidth = 2.dp.toPx())
                }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            } else if (isDatabase || block is TableBlock) {
                Modifier.weight(1f)
            } else {
                Modifier.weight(1f).padding(horizontal = 4.dp)
            })
                .bringIntoViewRequester(bringIntoViewRequester)
                .onSizeChanged { blockHeight = it.height.toFloat() }
                .onFocusChanged { focusState ->
                    val currentlyFocused = focusState.isFocused || focusState.hasFocus
                    isFocused = currentlyFocused
                    if (currentlyFocused) onFocus(block.id) else GlobalEditorState.currentSelection = TextRange.Zero
                }

            val linkColor = MaterialTheme.colorScheme.primary
            val fadedLinkColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            val inlineSpans = block.inlineSpansOrEmpty()
            val noteTitlesById = remember(allLinkableNotes) { allLinkableNotes.associate { it.noteId to it.title } }
            val richTextTransformation: VisualTransformation = remember(
                block is CodeBlock, linkColor, fadedLinkColor, validNoteIds, inlineSpans, linkHoverState.hoveredLink, noteTitlesById
            ) {
                if (block is CodeBlock) VisualTransformation.None
                else RichTextVisualTransformation(linkColor, fadedLinkColor, validNoteIds, inlineSpans, linkHoverState.hoveredLink, noteTitlesById)
            }

            Column(modifier = textFieldWrapperModifier) {
                if (isTextBased) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .linkHover(linkHoverState, validNoteIds) { textLayoutResult }
                            .openLinksOnPress(
                                validNoteIds = validNoteIds,
                                onOpenWebLink = { webLinkActions.openLink(it) },
                                onOpenNoteLink = { actions.onNoteLinkClick(it) },
                                onOpenEmail = { webLinkActions.openEmail(it) },
                                onOpenPhone = { webLinkActions.openPhone(it) },
                                onRightClickLink = { link, at -> linkHoverState.openMenuFor(link, at) },
                                currentTextLayout = { textLayoutResult }
                            )
                    ) {
                        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {

                            IsolatedEditorTextField(
                                initialText = text,
                                placeholderText = placeholderText,
                                blockId = block.id,
                                isCodeBlock = block is CodeBlock,
                                textStyle = textStyle,
                                inSelectionMode = inSelectionMode,
                                focusRequester = focusRequester,
                                onUpdateText = { id, newText -> actions.onUpdateText(id, newText) },
                                onEnterPressed = { id, before, after -> actions.onEnterPressed(id, before, after) },
                                onBackspaceOnEmpty = { id -> actions.onBackspaceOnEmpty(id) },
                                allLinkableNotes = allLinkableNotes,
                                onCreateLinkedNote = { actions.onCreateLinkedNote(it) },
                                onOpenNote = { actions.onNoteLinkClick(it) },
                                visualTransformation = richTextTransformation,
                                selectionRequest = selectionRequest,
                                focusRequest = focusRequest,
                                onTextLayout = { textLayoutResult = it },
                                isSlashMenuOpen = isSlashMenuActiveHere,
                                onSlashMenuNavigate = { delta ->
                                    val count = slashMenuFilteredItems.size
                                    if (count > 0) {
                                        slashMenuSelectedIndex = ((slashMenuSelectedIndex + delta) % count + count) % count
                                    }
                                },
                                onSlashMenuConfirm = {
                                    slashMenuFilteredItems.getOrNull(slashMenuSelectedIndex)?.action?.invoke()
                                },
                                onSlashMenuDismiss = onDismissSlashMenu
                            )

                        }

                        if (!isFocused && !inSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .pointerInput(block.id) {
                                        detectTapGestures(
                                            onTap = { pos ->
                                                val layoutResult = textLayoutResult
                                                val tappedLink = layoutResult?.hoveredLinkAt(pos, validNoteIds)

                                                when (tappedLink) {
                                                    is HoveredLink.Web -> webLinkActions.openLink(tappedLink.url)
                                                    is HoveredLink.Email -> webLinkActions.openEmail(tappedLink.email)
                                                    is HoveredLink.Phone -> webLinkActions.openPhone(tappedLink.phone)
                                                    is HoveredLink.Note -> actions.onNoteLinkClick(tappedLink.noteId)
                                                    null -> {
                                                        focusRequester.requestFocus()
                                                        keyboardController?.show()
                                                        layoutResult?.getOffsetForPosition(pos)?.let {
                                                            actions.onRequestCursorPosition(block.id, it)
                                                        }
                                                    }
                                                }
                                            },
                                            onDoubleTap = { pos ->
                                                focusRequester.requestFocus()
                                                keyboardController?.show()
                                                textLayoutResult?.getOffsetForPosition(pos)?.let { actions.onRequestCursorPosition(block.id, it) }
                                            },
                                            onLongPress = { pos ->
                                                when (val pressedLink = textLayoutResult?.hoveredLinkAt(pos, validNoteIds)) {
                                                    is HoveredLink.Web -> webLinkActions.copyLink(pressedLink.url)
                                                    is HoveredLink.Email -> webLinkActions.copyLink(pressedLink.email)
                                                    is HoveredLink.Phone -> webLinkActions.copyLink(pressedLink.phone)
                                                    else -> actions.onToggleSelection(block.id)
                                                }
                                            }
                                        )
                                    }
                            )
                        }

                        LinkHoverCard(
                            hoverState = linkHoverState,
                            onOpenNoteLink = { actions.onNoteLinkClick(it) },
                            findNote = { noteId -> allLinkableNotes.find { it.noteId == noteId } }
                        )

                        LinkContextMenu(
                            hoverState = linkHoverState,
                            onOpenNoteLink = { actions.onNoteLinkClick(it) },
                            findNote = { noteId -> allLinkableNotes.find { it.noteId == noteId } }
                        )
                    }

                    if (block is CheckboxBlock) {
                        val hasReminder = block.reminderTimestamp != null
                        val isPickerOpen = showPresetMenu || showTimePresetMenu || showDatePicker ||
                            showTimePicker || isReminderPickerOpening
                        AnimatedVisibility(
                            visible = isActiveBlock || hasReminder || isPickerOpen,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasReminder) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            val occurrenceDate = block.reminderTimestamp.let {
                                                Instant.fromEpochMilliseconds(it)
                                                    .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                                            }
                                            afterKeyboardCloses {
                                                actions.onOpenEventOptions(block.id, occurrenceDate)
                                            }
                                        }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert, "Event options",
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable {
                                                isReminderPickerOpening = true
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                scope.launch {
                                                    if (isKeyboardOpen) delay(500L.milliseconds) else delay(
                                                        50L.milliseconds
                                                    )
                                                    showPresetMenu = true
                                                    isReminderPickerOpening = false
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painterResource(Res.drawable.calendar_add), "Date",
                                            modifier = Modifier.size(15.dp),
                                            tint = if (hasReminder) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    ReminderPresetMenu(
                                        expanded = showPresetMenu,
                                        onDismiss = { showPresetMenu = false },
                                        onPresetSelected = {
                                            actions.onUpdateReminder(
                                                block.id,
                                                it
                                            )
                                        },
                                        onCustomSelected = { showDatePicker = true },
                                        onRemove = if (hasReminder) {
                                            { actions.onUpdateReminder(block.id, null) }
                                        } else null
                                    )
                                }
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable {
                                                isReminderPickerOpening = true
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                                scope.launch {
                                                    if (isKeyboardOpen) delay(500L.milliseconds) else delay(
                                                        50L.milliseconds
                                                    )
                                                    showTimePresetMenu = true
                                                    isReminderPickerOpening = false
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painterResource(Res.drawable.clock_circle), "Time",
                                            modifier = Modifier.size(16.dp),
                                            tint = if (hasReminder) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TimePresetMenu(
                                        expanded = showTimePresetMenu,
                                        onDismiss = { showTimePresetMenu = false },
                                        onPresetSelected = {
                                            actions.onUpdateReminder(
                                                block.id,
                                                it
                                            )
                                        },
                                        onCustomSelected = { showTimePicker = true }
                                    )
                                }
                                if (hasReminder) {
                                    val timeText = remember(block.reminderTimestamp) {
                                        val instant = Instant.fromEpochMilliseconds(block.reminderTimestamp)
                                        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                                        val amPm = if (dt.hour >= 12) "PM" else "AM"
                                        val hour12 = if (dt.hour % 12 == 0) 12 else dt.hour % 12
                                        val minStr = dt.minute.toString().padStart(2, '0')
                                        "${dt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${dt.day}, $hour12:$minStr $amPm"
                                    }
                                    Text(
                                        text = timeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    key(block.id) {
                        when (block) {
                            is BookmarkBlock -> BookmarkBlockView(
                                block, inSelectionMode,
                                { actions.onToggleSelection(block.id) },
                                { actions.onUrlSubmit(block.id, it) }
                            )
                            is LinkedNoteBlock -> LinkedNoteBlockView(
                                block = block,
                                inSelectionMode = inSelectionMode,
                                onToggleSelection = { actions.onToggleSelection(block.id) },
                                onOpenNote = { actions.onNoteLinkClick(block.linkedNoteId) },
                                getNoteMetadata = { actions.getNoteMetadata(it) }
                            )
                            is ImageBlock -> ImageBlockView(
                                block, inSelectionMode,
                                onToggleSelection = { actions.onToggleSelection(block.id) },
                                onRequestPicker = { actions.onRequestImagePicker(block.id) },
                                onRequestCamera = { actions.onRequestCamera(block.id) },
                                onDelete = { actions.onDeleteImageBlock(block.id) }
                            )
                            is DocumentBlock -> DocumentBlockView(
                                block = block,
                                inSelectionMode = inSelectionMode,
                                onToggleSelection = { actions.onToggleSelection(block.id) },
                                onRequestPicker = { actions.onRequestDocumentPicker(block.id) },
                                onOpenFile = { filePath, mimeType -> actions.onOpenFile(filePath, mimeType) }
                            )
                            is DatabaseBlock -> Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                                if (event.type == PointerEventType.Press) {
                                                    lastTappedYInBlock = event.changes.firstOrNull()?.position?.y ?: 0f
                                                }
                                            }
                                        }
                                    }
                            ) {
                                DatabaseBlockView(
                                    block = block,
                                    inSelectionMode = inSelectionMode,
                                    globalTags = globalTags,
                                    actions = actions,
                                    allLinkableNotes = allLinkableNotes,
                                )
                            }
                            is TableBlock -> TableBlockView(
                                block = block,
                                inSelectionMode = inSelectionMode,
                                onUpdateTable = { actions.onUpdateTable(block.id, it) },
                                onUpdateTableStyle = { cellStyles, rowStyles, columnStyles ->
                                    actions.onUpdateTableStyle(block.id, cellStyles, rowStyles, columnStyles)
                                },
                                onUpdateColumnWidth = { columnIndex, width ->
                                    actions.onUpdateTableColumnWidth(block.id, columnIndex, width)
                                }
                            )
                            is VoiceBlock -> AudioBlockView(
                                block = block,
                                inSelectionMode = inSelectionMode,
                                onToggleSelection = { actions.onToggleSelection(block.id) },
                                onRemoveVoice = { actions.onRemoveVoice(block.id) },
                                onStartRecording = { actions.onStartRecording() },
                                onStopRecording = { cancel -> actions.onStopRecording(block.id, cancel) },
                                onPlayAudio = { path, onComplete -> actions.onPlayAudio(path, onComplete) },
                                onStopAudio = { actions.onStopAudio() }
                            )
                            is SolidDividerBlock -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .height(1.5.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(1.dp))
                                )
                            }
                            is ThreeDotDividerBlock -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val dotSize = 6.dp
                                    val dotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    Box(Modifier.size(dotSize).clip(CircleShape).background(dotColor))
                                    Spacer(Modifier.width(16.dp))
                                    Box(Modifier.size(dotSize).clip(CircleShape).background(dotColor))
                                    Spacer(Modifier.width(16.dp))
                                    Box(Modifier.size(dotSize).clip(CircleShape).background(dotColor))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (block.isPinned) {
            Icon(
                Icons.Default.PushPin, "Pinned",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 16.dp)
                    .size(15.dp)
            )
        }

        if (block is CheckboxBlock) {
            if (showDatePicker) {
                MinimalDatePickerDialog(
                    initialTimestamp = block.reminderTimestamp,
                    onDismiss = { showDatePicker = false },
                    onConfirm = { timestamp ->
                        actions.onUpdateReminder(block.id, timestamp)
                        showDatePicker = false
                    }
                )
            }
            if (showTimePicker) {
                MinimalTimePickerDialog(
                    initialTimestamp = block.reminderTimestamp,
                    onDismiss = { showTimePicker = false },
                    onConfirm = { hour, minute ->
                        val tz = TimeZone.currentSystemDefault()
                        val currentInstant = block.reminderTimestamp?.let {
                            Instant.fromEpochMilliseconds(it)
                        } ?: Clock.System.now()
                        val currentDt = currentInstant.toLocalDateTime(tz)
                        val newDt = LocalDateTime(
                            currentDt.year, currentDt.month.number, currentDt.day,
                            hour, minute, 0, 0
                        )
                        actions.onUpdateReminder(
                            block.id,
                            newDt.toInstant(tz).toEpochMilliseconds()
                        )
                        showTimePicker = false
                    }
                )
            }
        }

        if (inSelectionMode) {
            Box(Modifier.matchParentSize().clickable(onClick = { actions.onToggleSelection(block.id) }))
        }
    }
}

@Composable
fun IsolatedEditorTextField(
    modifier: Modifier = Modifier,
    initialText: String,
    placeholderText: String = "",
    blockId: String,
    isCodeBlock: Boolean,
    textStyle: TextStyle,
    inSelectionMode: Boolean,
    focusRequester: FocusRequester,
    onUpdateText: (String, String) -> Unit,
    onEnterPressed: (String, String, String) -> Unit,
    onBackspaceOnEmpty: (String) -> Unit,
    onTextLayout: (TextLayoutResult) -> Unit,
    allLinkableNotes: List<NoteMetadataEntity>,
    onCreateLinkedNote: (String) -> String,
    onOpenNote: (String) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    selectionRequest: SelectionRequest? = null,
    focusRequest: FocusRequest? = null,
    isSlashMenuOpen: Boolean = false,
    onSlashMenuNavigate: (delta: Int) -> Unit = {},
    onSlashMenuConfirm: () -> Unit = {},
    onSlashMenuDismiss: () -> Unit = {}
) {
    var tfv by remember { mutableStateOf(TextFieldValue(initialText, TextRange.Zero)) }
    var lastSentText by remember { mutableStateOf(initialText) }

    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var mentionStartIndex by remember { mutableIntStateOf(-1) }
    var isPendingDeletion by remember { mutableStateOf(false) }

    var localTextLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var isFieldFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    var mentionSelectedIndex by remember(mentionQuery) { mutableIntStateOf(0) }

    LaunchedEffect(isFieldFocused, imeBottom, tfv.selection.end) {
        if (!isFieldFocused || imeBottom <= 0) return@LaunchedEffect
        delay(50.milliseconds)
        val layout = localTextLayoutResult ?: return@LaunchedEffect
        val cursorRect = runCatching {
            layout.getCursorRect(tfv.selection.end.coerceIn(0, layout.layoutInput.text.length))
        }.getOrNull() ?: return@LaunchedEffect
        runCatching { bringIntoViewRequester.bringIntoView(cursorRect) }
    }

    LaunchedEffect(initialText) {
        if (tfv.text == initialText) {
            lastSentText = initialText
            return@LaunchedEffect
        }
        val wasAtEnd = tfv.selection.start == tfv.text.length
        val safeStart = if (wasAtEnd) initialText.length else tfv.selection.start.coerceAtMost(initialText.length)
        val safeEnd = if (wasAtEnd) initialText.length else tfv.selection.end.coerceAtMost(initialText.length)
        tfv = tfv.copy(text = initialText, selection = TextRange(safeStart, safeEnd), composition = null)
        lastSentText = initialText
    }

    // Toolbar "@" press. Mirrors exactly what typing '@' does in onValueChange: insert the
    // character at the cursor, record where the mention starts, open the picker with an empty query.
    LaunchedEffect(EditorEventBus.insertMentionSignal) {
        val (targetId, _) = EditorEventBus.insertMentionSignal ?: return@LaunchedEffect
        if (targetId != blockId || !isFieldFocused) return@LaunchedEffect

        val cursor = tfv.selection.start.coerceIn(0, tfv.text.length)
        // onValueChange only treats '@' as a mention at index 0 or after whitespace,
        // so pad it here or the picker would open and immediately fail to re-match on the
        // next keystroke.
        val needsSpace = cursor > 0 && !tfv.text[cursor - 1].isWhitespace()
        val insert = if (needsSpace) " @" else "@"

        val newText = tfv.text.substring(0, cursor) + insert + tfv.text.substring(cursor)
        val newCursor = cursor + insert.length

        tfv = tfv.copy(text = newText, selection = TextRange(newCursor), composition = null)
        lastSentText = newText          // keeps LaunchedEffect(initialText) from clobbering us
        mentionStartIndex = newCursor - 1
        mentionQuery = ""
        onUpdateText(blockId, newText)
    }

// Updates cursor placement when a FocusRequest targets this block with placeCursorAtEnd = true.
    LaunchedEffect(focusRequest?.nonce) {
        if (focusRequest?.id == blockId && focusRequest.placeCursorAtEnd) {
            tfv = tfv.copy(selection = TextRange(tfv.text.length), composition = null)
        }
    }

    LaunchedEffect(selectionRequest?.nonce) {
        if (selectionRequest?.blockId == blockId) {
            val selection = selectionRequest.selection
            tfv = tfv.copy(
                text = initialText,
                selection = TextRange(
                    selection.start.coerceIn(0, initialText.length),
                    selection.end.coerceIn(0, initialText.length)
                ),
                composition = null
            )
            lastSentText = initialText
        }
    }

    fun insertNoteLink(safeTitle: String, noteId: String) {
        val lastAt = mentionStartIndex
        if (lastAt != -1) {
            val cursor = tfv.selection.start.coerceIn(0, tfv.text.length)
            val markdownLink = "[$safeTitle](emberr://note/$noteId) "

            val textBefore = tfv.text.substring(0, lastAt)
            val textAfter = tfv.text.substring(cursor)

            val newText = textBefore + markdownLink + textAfter
            val newCursor = textBefore.length + markdownLink.length

            tfv = tfv.copy(text = newText, selection = TextRange(newCursor), composition = null)
            onUpdateText(blockId, newText)
        }
        mentionQuery = null
        mentionStartIndex = -1
    }

    LaunchedEffect(blockId) {
        EditorEventBus.confirmMentionEvent.collect { (safeTitle, noteId) ->
            if (mentionQuery == null || !isFieldFocused) return@collect
            insertNoteLink(safeTitle, noteId)
        }
    }

    LaunchedEffect(blockId) {
        EditorEventBus.confirmMentionAndOpenEvent.collect { (safeTitle, noteId) ->
            if (mentionQuery == null || !isFieldFocused) return@collect
            insertNoteLink(safeTitle, noteId)
            onOpenNote(noteId)
        }
    }

    LaunchedEffect(blockId) {
        EditorEventBus.cancelMentionEvent.collect {
            if (mentionQuery == null || !isFieldFocused) return@collect
            mentionQuery = null
            mentionStartIndex = -1
        }
    }

    val filteredMentionNotes = remember(mentionQuery, allLinkableNotes) {
        val query = mentionQuery.orEmpty()
        allLinkableNotes.filter { it.title.contains(query, ignoreCase = true) }
    }

    val mentionMenuEntries = remember(mentionQuery, filteredMentionNotes) {
        val query = mentionQuery.orEmpty()
        buildList {
            add(
                SlashMenuItemData("Create new note", SlashMenuIcon.Vector(Icons.AutoMirrored.Filled.NoteAdd)) {
                    val newNoteId = onCreateLinkedNote("Untitled")
                    insertNoteLink("Untitled", newNoteId)
                    onOpenNote(newNoteId)
                }
            )
            filteredMentionNotes.forEach { note ->
                val icon = note.icon?.let { SlashMenuIcon.Label(it) } ?: SlashMenuIcon.Vector(Icons.Default.Description)
                add(
                    SlashMenuItemData(note.title.ifEmpty { "Untitled" }, icon) {
                        val safeTitle = note.title.replace("[", "").replace("]", "").ifEmpty { "Untitled" }
                        insertNoteLink(safeTitle, note.noteId)
                    }
                )
            }
            if (query.isNotBlank()) {
                add(
                    SlashMenuItemData("New \"$query\" note", SlashMenuIcon.Vector(Icons.Default.Add)) {
                        val safeTitle = query.replace("[", "").replace("]", "").trim().ifEmpty { "Untitled" }
                        val newNoteId = onCreateLinkedNote(safeTitle)
                        insertNoteLink(safeTitle, newNoteId)
                    }
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (tfv.text.isEmpty() && placeholderText.isNotEmpty()) {
            Text(
                text = placeholderText,
                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        BasicTextField(
            value = tfv,
            visualTransformation = visualTransformation,
            onTextLayout = { result ->
                localTextLayoutResult = result
                onTextLayout(result)
            },
            onValueChange = { newValue ->
                GlobalEditorState.currentSelection = newValue.selection
                val newText = newValue.text
                val cursor = newValue.selection.start

                if (cursor > 0 && cursor <= newText.length) {
                    val textUpToCursor = newText.substring(0, cursor)
                    val lastAt = textUpToCursor.lastIndexOf('@')
                    val isValidAt = lastAt != -1 && (lastAt == 0 || textUpToCursor[lastAt - 1] == ' ' || textUpToCursor[lastAt - 1] == '\n')

                    if (isValidAt && !textUpToCursor.substring(lastAt).contains(" ")) {
                        mentionQuery = textUpToCursor.substring(lastAt + 1)
                        mentionStartIndex = lastAt
                    } else {
                        mentionQuery = null
                        mentionStartIndex = -1
                    }
                } else {
                    mentionQuery = null
                    mentionStartIndex = -1
                }

                if (!isCodeBlock && newText.contains('\n')) {
                    val splitIndex = newText.indexOf('\n')
                    val textBefore = newText.substring(0, splitIndex)
                    val textAfter = newText.substring(splitIndex + 1).replace("\n", "")

                    tfv = newValue.copy(text = textBefore, selection = TextRange(textBefore.length))
                    lastSentText = textBefore
                    onEnterPressed(blockId, textBefore, textAfter)
                } else {
                    tfv = newValue
                    lastSentText = newText
                    onUpdateText(blockId, newText)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .bringIntoViewRequester(bringIntoViewRequester)
                .onFocusChanged {
                    isFieldFocused = it.isFocused
                }
                .onPreviewKeyEvent { event ->
                    val isBackspace = event.key == Key.Backspace
                    val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter

                    if (isSlashMenuOpen && event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                onSlashMenuNavigate(1)
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionUp -> {
                                onSlashMenuNavigate(-1)
                                return@onPreviewKeyEvent true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                onSlashMenuConfirm()
                                return@onPreviewKeyEvent true
                            }
                            Key.Escape -> {
                                onSlashMenuDismiss()
                                return@onPreviewKeyEvent true
                            }
                            else -> {}
                        }
                    }

                    if (mentionQuery != null && event.type == KeyEventType.KeyDown) {
                        val entryCount = mentionMenuEntries.size
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (entryCount > 0) mentionSelectedIndex = (mentionSelectedIndex + 1) % entryCount
                                return@onPreviewKeyEvent true
                            }
                            Key.DirectionUp -> {
                                if (entryCount > 0) mentionSelectedIndex = (mentionSelectedIndex - 1 + entryCount) % entryCount
                                return@onPreviewKeyEvent true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                mentionMenuEntries.getOrNull(mentionSelectedIndex)?.action?.invoke()
                                return@onPreviewKeyEvent true
                            }
                            Key.Escape -> {
                                mentionQuery = null
                                mentionStartIndex = -1
                                return@onPreviewKeyEvent true
                            }
                            else -> {}
                        }
                    }

                    if (isBackspace && event.type == KeyEventType.KeyDown) {
                        if (tfv.text.isEmpty()) {
                            if (!isPendingDeletion) {
                                isPendingDeletion = true
                                onBackspaceOnEmpty(blockId)
                            }
                            return@onPreviewKeyEvent true
                        }

                        val cursor = tfv.selection.start
                        if (cursor > 0 && tfv.selection.collapsed) {
                            val textBeforeCursor = tfv.text.substring(0, cursor)
                            val match = TrailingNoteLinkRegex.find(textBeforeCursor)

                            if (match != null) {
                                val textBeforeLink = textBeforeCursor.substring(0, match.range.first)
                                val textAfterCursor = tfv.text.substring(cursor)
                                val newText = textBeforeLink + textAfterCursor

                                tfv = tfv.copy(text = newText, selection = TextRange(textBeforeLink.length))
                                onUpdateText(blockId, newText)
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    if (isEnter && !isCodeBlock) {
                        if (event.type == KeyEventType.KeyDown) {
                            val cursor = tfv.selection.start
                            val textBefore = tfv.text.substring(0, cursor)
                            val textAfter = tfv.text.substring(cursor)
                            onEnterPressed(blockId, textBefore, textAfter)
                        }
                        return@onPreviewKeyEvent true
                    }
                    false
                },
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            enabled = !inSelectionMode
        )

        if (isDesktopPlatform && mentionQuery != null) {
            EmberrDesktopMenu(
                expanded = true,
                onDismissRequest = { mentionQuery = null; mentionStartIndex = -1 },
                properties = PopupProperties(focusable = false),
                modifier = Modifier
                    .width(290.dp)
                    .heightIn(max = 400.dp)
            ) {
                SlashMenuList(
                    sections = listOf(SlashMenuSectionData("Link to Note", mentionMenuEntries)),
                    selectedIndex = mentionSelectedIndex
                )
            }
        }
    }
}

// Replaces raw note link syntax (e.g. "[title](emberr://note/id)") with styled display text ("@title")
// and applies inline rich-text styles (bold, italic, strike, underline). Computes offset mappings
// so cursor movements and tap targets accurately correspond to the underlying text.
data class RichTextVisualTransformation(
    private val linkColor: Color,
    private val fadedColor: Color,
    private val validNoteIds: Set<String>,
    private val inlineSpans: List<InlineSpan> = emptyList(),
    private val hoveredLink: HoveredLink? = null,
    private val noteTitlesById: Map<String, String> = emptyMap()
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text

        if (inlineSpans.isEmpty() && !originalText.contains(NOTE_LINK_MARKER)) {
            return TransformedText(text.withInteractiveLinksHighlighted(hoveredLink), OffsetMapping.Identity)
        }

        val matches = NoteLinkRegex.findAll(originalText).toList()
        if (matches.isEmpty() && inlineSpans.isEmpty()) {
            return TransformedText(text.withInteractiveLinksHighlighted(hoveredLink), OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder()
        val mapping = IntArray(originalText.length * 2 + 50)
        val inverse = IntArray(originalText.length + 1)

        var originalIndex = 0
        var transformedIndex = 0
        data class PendingLink(val noteId: String, val start: Int, val end: Int, val isMissing: Boolean)
        val pendingLinks = mutableListOf<PendingLink>()

        for (match in matches) {
            while (originalIndex < match.range.first) {
                mapping[transformedIndex] = originalIndex
                inverse[originalIndex] = transformedIndex
                builder.append(originalText[originalIndex])
                originalIndex++
                transformedIndex++
            }

            val noteId = match.groupValues[2]
            val title = noteTitlesById[noteId]?.ifEmpty { "Untitled" } ?: match.groupValues[1]
            val linkText = "@$title"
            val isMissing = !validNoteIds.contains(noteId)

            val linkStartTransformed = transformedIndex
            builder.append(linkText)
            pendingLinks.add(PendingLink(noteId, linkStartTransformed, transformedIndex + linkText.length, isMissing))

            for (i in linkText.indices) {
                mapping[transformedIndex] = match.range.last + 1
                transformedIndex++
            }

            for (i in match.range) {
                inverse[i] = linkStartTransformed
            }
            originalIndex = match.range.last + 1
        }

        while (originalIndex < originalText.length) {
            mapping[transformedIndex] = originalIndex
            inverse[originalIndex] = transformedIndex
            builder.append(originalText[originalIndex])
            originalIndex++
            transformedIndex++
        }

        val finalTransformedLength = transformedIndex
        val finalOriginalLength = originalText.length

        mapping[finalTransformedLength] = finalOriginalLength
        inverse[finalOriginalLength] = finalTransformedLength

        pendingLinks.forEach { link ->
            val finalColor = if (link.isMissing) fadedColor else linkColor
            val decoration = if (link.isMissing) TextDecoration.LineThrough else TextDecoration.None
            builder.addStyle(SpanStyle(color = finalColor, fontWeight = FontWeight.SemiBold, textDecoration = decoration), link.start, link.end)
            builder.addStringAnnotation(tag = NOTE_LINK_TAG, annotation = link.noteId, start = link.start, end = link.end)
        }

        inlineSpans.forEach { span ->
            val start = span.start.coerceIn(0, finalOriginalLength)
            val end = span.end.coerceIn(0, finalOriginalLength)
            if (start >= end) return@forEach
            val transformedStart = inverse[start]
            val transformedEnd = inverse[end]
            if (transformedStart >= transformedEnd) return@forEach
            val decoration = when {
                span.strikeThrough && span.underline -> TextDecoration.LineThrough + TextDecoration.Underline
                span.strikeThrough -> TextDecoration.LineThrough
                span.underline -> TextDecoration.Underline
                else -> null
            }
            builder.addStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = decoration
                ),
                transformedStart,
                transformedEnd
            )
        }

        return TransformedText(
            builder.toAnnotatedString().withInteractiveLinksHighlighted(hoveredLink),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    if (offset <= 0) return 0
                    if (offset >= finalOriginalLength) return finalTransformedLength
                    return inverse[offset]
                }
                override fun transformedToOriginal(offset: Int): Int {
                    if (offset <= 0) return 0
                    if (offset >= finalTransformedLength) return finalOriginalLength
                    return mapping[offset]
                }
            }
        )
    }
}