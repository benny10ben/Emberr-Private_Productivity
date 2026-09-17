package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.domain.model.TableCellStyle
import com.emberr.presentation.shared.editor.GlobalEditorState
import com.emberr.domain.model.ColumnType
import com.emberr.presentation.shared.editor.rememberWebLinkActions
import com.emberr.presentation.shared.editor.LinkContextMenu
import com.emberr.presentation.shared.editor.LinkHoverCard
import com.emberr.presentation.shared.editor.linkHover
import com.emberr.presentation.shared.editor.openLinksOnPress
import com.emberr.presentation.shared.editor.rememberLinkHoverState
import com.emberr.presentation.shared.editor.webLinkAtPosition
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.list_sort_descending
import emberr.shared.generated.resources.plus
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

private val NOTE_LINK_BEFORE_CURSOR = """\[([^\]]+)\]\(emberr://note/([^)]+)\)$""".toRegex()

@Composable
fun TableCellTextEditor(
    modifier: Modifier = Modifier,
    initialText: String,
    columnType: ColumnType,
    inSelectionMode: Boolean,
    cellKey: String,
    cellStyle: TableCellStyle,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onNoteLinkClick: (String) -> Unit = {},
    allLinkableNotes: List<NoteMetadataEntity>,
    onCreateLinkedNote: (String) -> String,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var tfv by remember { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    var lastSentText by remember { mutableStateOf(initialText) }

    var mentionQuery by remember { mutableStateOf<String?>(null) }
    var mentionAnchorRect by remember { mutableStateOf(Rect.Zero) }
    var mentionStartIndex by remember { mutableIntStateOf(-1) }
    var isFocused by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val validNoteIds = remember(allLinkableNotes) { allLinkableNotes.map { it.noteId }.toSet() }
    val webLinkActions = rememberWebLinkActions()
    val linkHoverState = rememberLinkHoverState()

    LaunchedEffect(initialText) {
        if (tfv.text != initialText && initialText != lastSentText) {
            val safeStart = tfv.selection.start.coerceAtMost(initialText.length)
            val safeEnd = tfv.selection.end.coerceAtMost(initialText.length)
            tfv = tfv.copy(text = initialText, selection = TextRange(safeStart, safeEnd))
        }
    }

    LaunchedEffect(tfv.text) {
        if (tfv.text != initialText) {
            if (mentionQuery == null) delay(400L.milliseconds)
            lastSentText = tfv.text
            onValueChange(tfv.text)
        }
    }

    fun replaceMentionWithLink(title: String, noteId: () -> String) {
        val cursor = tfv.selection.start.coerceIn(0, tfv.text.length)
        val lastAt = tfv.text.substring(0, cursor).lastIndexOf('@')

        if (lastAt != -1) {
            val markdownLink = "[$title](emberr://note/${noteId()}) "
            val textBefore = tfv.text.substring(0, lastAt)
            val newText = textBefore + markdownLink + tfv.text.substring(cursor)

            tfv = tfv.copy(
                text = newText,
                selection = TextRange(textBefore.length + markdownLink.length),
                composition = null
            )
            onValueChange(newText)
        }
        mentionQuery = null
        mentionStartIndex = -1
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .linkHover(linkHoverState, validNoteIds) { textLayoutResult }
            .openLinksOnPress(
                validNoteIds = validNoteIds,
                onOpenWebLink = { webLinkActions.openLink(it) },
                onOpenNoteLink = onNoteLinkClick,
                onRightClickLink = { link, at -> linkHoverState.openMenuFor(link, at) },
                currentTextLayout = { textLayoutResult }
            )
    ) {
        BasicTextField(
            value = tfv,
            onValueChange = { newValue ->
                val newText = newValue.text
                val cursor = newValue.selection.start

                val activeMention = if (cursor > 0 && cursor <= newText.length) {
                    val textUpToCursor = newText.substring(0, cursor)
                    val lastAt = textUpToCursor.lastIndexOf('@')
                    val isValidAt = lastAt != -1 &&
                            (lastAt == 0 || textUpToCursor[lastAt - 1] == ' ' || textUpToCursor[lastAt - 1] == '\n')
                    if (isValidAt && !textUpToCursor.substring(lastAt).contains(" ")) {
                        lastAt to textUpToCursor.substring(lastAt + 1)
                    } else null
                } else null

                mentionStartIndex = activeMention?.first ?: -1
                mentionQuery = activeMention?.second

                tfv = newValue
                GlobalEditorState.currentSelection = newValue.selection
            },
            onTextLayout = { result ->
                textLayoutResult = result
                if (mentionStartIndex != -1) {
                    val transformedText = visualTransformation.filter(AnnotatedString(tfv.text))
                    val mappedIndex = transformedText.offsetMapping.originalToTransformed(mentionStartIndex)
                    mentionAnchorRect = result.getCursorRect(mappedIndex.coerceIn(0, transformedText.text.length))
                }
            },
            enabled = !inSelectionMode,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (columnType.rendersAsLink() && tfv.text.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (cellStyle.isBold) FontWeight.Bold else null,
                fontStyle = if (cellStyle.isItalic) FontStyle.Italic else null,
                textDecoration = cellTextDecoration(cellStyle, columnType.rendersAsLink() && tfv.text.isNotBlank())
            ),
            visualTransformation = visualTransformation,
            keyboardOptions = when (columnType) {
                ColumnType.NUMBER, ColumnType.MONEY -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                ColumnType.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone)
                ColumnType.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email)
                ColumnType.URL -> KeyboardOptions(keyboardType = KeyboardType.Uri)
                else -> KeyboardOptions.Default
            },
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = columnType != ColumnType.TEXT,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChanged(it.isFocused)
                    if (it.isFocused) {
                        GlobalEditorState.currentlyFocusedTableCellKey = cellKey
                        GlobalEditorState.currentSelection = tfv.selection
                    } else if (GlobalEditorState.currentlyFocusedTableCellKey == cellKey) {
                        GlobalEditorState.currentlyFocusedTableCellKey = null
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown) {
                        val cursor = tfv.selection.start
                        if (cursor > 0 && tfv.selection.collapsed) {
                            val textBeforeCursor = tfv.text.substring(0, cursor)
                            val match = NOTE_LINK_BEFORE_CURSOR.find(textBeforeCursor)

                            if (match != null) {
                                val textBeforeLink = textBeforeCursor.substring(0, match.range.first)
                                val newText = textBeforeLink + tfv.text.substring(cursor)

                                tfv = tfv.copy(text = newText, selection = TextRange(textBeforeLink.length))
                                onValueChange(newText)
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                }
        )

        if (columnType == ColumnType.TEXT && !isFocused && !inSelectionMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(tfv.text) {
                        detectTapGestures(
                            onTap = { position ->
                                val tappedWebLink = textLayoutResult?.webLinkAtPosition(position)
                                val tappedNoteId = textLayoutResult?.let { layout ->
                                    val offset = layout.getOffsetForPosition(position)
                                    layout.layoutInput.text.getStringAnnotations(
                                        "NOTE_LINK",
                                        maxOf(0, offset - 1),
                                        minOf(layout.layoutInput.text.length, offset + 1)
                                    ).firstOrNull()?.item
                                }

                                when {
                                    tappedWebLink != null -> webLinkActions.openLink(tappedWebLink)
                                    tappedNoteId != null && validNoteIds.contains(tappedNoteId) ->
                                        onNoteLinkClick(tappedNoteId)
                                    else -> {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                }
                            },
                            onLongPress = { position ->
                                val pressedWebLink = textLayoutResult?.webLinkAtPosition(position)
                                if (pressedWebLink != null) {
                                    webLinkActions.copyLink(pressedWebLink)
                                } else {
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            }
                        )
                    }
            )
        }

        LinkHoverCard(
            hoverState = linkHoverState,
            onOpenNoteLink = onNoteLinkClick,
            findNote = { noteId -> allLinkableNotes.find { it.noteId == noteId } }
        )

        LinkContextMenu(
            hoverState = linkHoverState,
            onOpenNoteLink = onNoteLinkClick,
            findNote = { noteId -> allLinkableNotes.find { it.noteId == noteId } }
        )

        val currentQuery = mentionQuery
        if (currentQuery != null) {
            NoteMentionPopup(
                query = currentQuery,
                anchorRect = mentionAnchorRect,
                allLinkableNotes = allLinkableNotes,
                onSelectNote = { note ->
                    val safeTitle = note.title.replace("[", "").replace("]", "").ifEmpty { "Untitled" }
                    replaceMentionWithLink(safeTitle) { note.noteId }
                },
                onCreateNote = {
                    val safeTitle = currentQuery.replace("[", "").replace("]", "").trim().ifEmpty { "Untitled" }
                    replaceMentionWithLink(safeTitle) { onCreateLinkedNote(safeTitle) }
                }
            )
        }
    }
}

private fun ColumnType.rendersAsLink() =
    this == ColumnType.EMAIL || this == ColumnType.PHONE || this == ColumnType.URL

@Composable
private fun NoteMentionPopup(
    query: String,
    anchorRect: Rect,
    allLinkableNotes: List<NoteMetadataEntity>,
    onSelectNote: (NoteMetadataEntity) -> Unit,
    onCreateNote: () -> Unit
) {
    val density = LocalDensity.current
    val filteredNotes = allLinkableNotes.filter { it.title.contains(query, ignoreCase = true) }

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { anchorRect.left.toDp() },
                y = with(density) { anchorRect.top.toDp() }
            )
            .size(width = 1.dp, height = with(density) { anchorRect.height.toDp() })
    ) {
        val positionProvider = remember {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset {
                    var y = anchorBounds.bottom + 8
                    if (y + popupContentSize.height > windowSize.height - 16) {
                        y = anchorBounds.top - popupContentSize.height - 8
                    }
                    var x = anchorBounds.left
                    if (x + popupContentSize.width > windowSize.width - 16) {
                        x = windowSize.width - popupContentSize.width - 16
                    }
                    return IntOffset(x, 0.coerceAtLeast(y))
                }
            }
        }

        Popup(
            popupPositionProvider = positionProvider,
            properties = PopupProperties(focusable = false)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .width(260.dp)
                    .heightIn(max = 300.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = "LINK TO NOTE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    filteredNotes.forEach { note ->
                        MentionPopupRow(
                            iconRes = Res.drawable.list_sort_descending,
                            label = note.title.ifEmpty { "Untitled" },
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            onClick = { onSelectNote(note) }
                        )
                    }

                    if (query.isNotBlank()) {
                        if (filteredNotes.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            )
                        }
                        MentionPopupRow(
                            iconRes = Res.drawable.plus,
                            label = "New \"$query\" note",
                            labelColor = MaterialTheme.colorScheme.primary,
                            onClick = onCreateNote
                        )
                    } else if (filteredNotes.isEmpty()) {
                        Text(
                            text = "Start typing to search...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MentionPopupRow(
    iconRes: DrawableResource,
    label: String,
    labelColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun cellTextDecoration(cellStyle: TableCellStyle, rendersAsLink: Boolean): TextDecoration {
    val underline = cellStyle.isUnderlined || rendersAsLink
    return when {
        cellStyle.isStrikeThrough && underline -> TextDecoration.LineThrough + TextDecoration.Underline
        cellStyle.isStrikeThrough -> TextDecoration.LineThrough
        underline -> TextDecoration.Underline
        else -> TextDecoration.None
    }
}
