package com.emberr.presentation.mobile.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.Dialog
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.VoiceBlock
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.inlineSpansOrEmpty
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.LocalImageOverlay
import com.emberr.presentation.shared.components.EmberrBlur
import com.emberr.presentation.shared.components.TopBarIconButton
import com.emberr.presentation.shared.components.emberrBlur
import com.emberr.presentation.shared.components.fullScreenDialogProperties
import com.emberr.presentation.shared.editor.EditorActions
import com.emberr.presentation.shared.editor.blockViews.AudioBlockView
import com.emberr.presentation.shared.editor.blockViews.BookmarkBlockView
import com.emberr.presentation.shared.editor.blockViews.DocumentBlockView
import com.emberr.presentation.shared.editor.blockViews.ImageBlockView
import com.emberr.presentation.shared.editor.blockViews.LinkedNoteBlockView
import com.emberr.presentation.shared.editor.blockViews.TableBlockView
import com.emberr.presentation.shared.editor.blockViews.databaseBlockView.DatabaseBlockView
import com.emberr.presentation.shared.editor.blockViews.databaseBlockView.buildNoteLinkAnnotatedString
import com.emberr.ui.theme.LocalAppIsDark
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.code
import emberr.shared.generated.resources.pen
import emberr.shared.generated.resources.search
import emberr.shared.generated.resources.square
import emberr.shared.generated.resources.square_check
import emberr.shared.generated.resources.x
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

private const val NoteLinkPrefix = "](emberr://note/"
private val DialogShape = RoundedCornerShape(24.dp)
private val BubbleShape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
private val IndentationStep = 14.dp
private val TableBlockSideInsetCancellation = 16.dp
private val DatabaseBlockSideInsetCancellation = 18.dp

private fun Modifier.reduceSideInset(amount: Dp): Modifier = this.layout { measurable, constraints ->
    val amountPx = amount.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = amountPx * 2))
    layout(placeable.width - amountPx * 2, placeable.height) {
        placeable.placeRelative(-amountPx, 0)
    }
}

// One scrollable row of the timeline.
private sealed interface TimelineRow {
    val date: LocalDate
    val key: String

    data class DayHeader(override val date: LocalDate) : TimelineRow {
        override val key get() = "header_$date"
    }

    data class EmptyDay(override val date: LocalDate) : TimelineRow {
        override val key get() = "empty_$date"
    }

    data class BlockRow(override val date: LocalDate, val block: NoteBlock) : TimelineRow {
        override val key get() = "block_${date}_${block.id}"
    }
}

@Composable
fun DailyTimelineDialog(
    days: List<DailyTimelineDay>,
    isLoading: Boolean,
    anchorDate: LocalDate,
    today: LocalDate,
    editorActions: EditorActions,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    onDismiss: () -> Unit,
    onBlockClick: (LocalDate, String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = fullScreenDialogProperties()
    ) {
        var fullScreenOverlayContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

        CompositionLocalProvider(
            LocalImageOverlay provides { content -> fullScreenOverlayContent = content }
        ) {
            val hazeState = remember { HazeState() }
            val dialogBackgroundColor = if (LocalAppIsDark.current) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.background
            }
            var searchQuery by remember { mutableStateOf("") }

            val rows = remember(days) { buildTimelineRows(days) }
            val trimmedQuery = searchQuery.trim()
            val matchRowIndices = remember(rows, trimmedQuery, today) {
                findMatchingRowIndices(rows, trimmedQuery, today)
            }

            val nearestMatchPosition = remember(rows, matchRowIndices, anchorDate) {
                matchRowIndices
                    .indices
                    .minByOrNull { abs(anchorDate.daysUntil(rows[matchRowIndices[it]].date)) }
                    ?: 0
            }
            var matchStepOffset by remember(matchRowIndices) { mutableIntStateOf(0) }
            val currentMatchPosition =
                stepMatchPosition(nearestMatchPosition, matchRowIndices.size, matchStepOffset)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isDesktopPlatform) 0.55f else 0.94f)
                        .widthIn(max = 720.dp)
                        .fillMaxHeight(0.92f)
                        .safeDrawingPadding()
                        .clip(DialogShape)
                        .background(dialogBackgroundColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                    ) {
                        TimelineList(
                            rows = rows,
                            isLoading = isLoading,
                            anchorDate = anchorDate,
                            today = today,
                            matchRowIndices = matchRowIndices,
                            targetRowIndex = matchRowIndices.getOrNull(currentMatchPosition),
                            editorActions = editorActions,
                            globalTags = globalTags,
                            allLinkableNotes = allLinkableNotes,
                            onBlockClick = onBlockClick
                        )
                    }

                    TimelineHeader(
                        hazeState = hazeState,
                        onDismiss = onDismiss,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    TimelineSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        matchCount = matchRowIndices.size,
                        currentMatchPosition = currentMatchPosition,
                        onPreviousMatch = { matchStepOffset -= 1 },
                        onNextMatch = { matchStepOffset += 1 },
                        hazeState = hazeState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                fullScreenOverlayContent?.invoke()
            }
        }
    }
}

@Composable
private fun TimelineHeader(
    hazeState: HazeState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Timeline",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        ) {
            TopBarIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Close timeline",
                bgColor = Color.Transparent,
                tint = MaterialTheme.colorScheme.onSurface,
                hazeState = hazeState,
                hazeStyle = EmberrBlur.Regular,
                shadowElevation = 0.dp,
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun TimelineSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentMatchPosition: Int,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 14.dp)
            .height(50.dp)
            .clip(CircleShape)
            .emberrBlur(hazeState, EmberrBlur.Regular)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.primary
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search the timeline",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (matchCount > 1) {
                Text(
                    text = "${currentMatchPosition + 1}/$matchCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 4.dp)
                )
                SearchBarActionIcon(
                    icon = painterResource(Res.drawable.arrow_up),
                    contentDescription = "Previous match",
                    onClick = onPreviousMatch
                )
                SearchBarActionIcon(
                    icon = painterResource(Res.drawable.arrow_down),
                    contentDescription = "Next match",
                    onClick = onNextMatch
                )
            }

            if (query.isNotEmpty()) {
                SearchBarActionIcon(
                    icon = painterResource(Res.drawable.x),
                    contentDescription = "Clear search",
                    onClick = { onQueryChange("") }
                )
            }
        }
    }
}

@Composable
private fun SearchBarActionIcon(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun TimelineList(
    rows: List<TimelineRow>,
    isLoading: Boolean,
    anchorDate: LocalDate,
    today: LocalDate,
    matchRowIndices: List<Int>,
    targetRowIndex: Int?,
    editorActions: EditorActions,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    onBlockClick: (LocalDate, String) -> Unit
) {
    val listState = rememberLazyListState()
    var isAnchorCentered by remember { mutableStateOf(false) }
    val matchRowIndexSet = remember(matchRowIndices) { matchRowIndices.toSet() }

    val viewportHeight by remember {
        derivedStateOf { listState.layoutInfo.viewportSize.height }
    }

    LaunchedEffect(rows, anchorDate) {
        isAnchorCentered = false
        if (rows.isEmpty()) return@LaunchedEffect
        val anchorIndex = rows.indexOfFirst { it is TimelineRow.DayHeader && it.date == anchorDate }
        if (anchorIndex < 0) {
            isAnchorCentered = true
            return@LaunchedEffect
        }
        val measuredHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        listState.scrollToItem(anchorIndex, -(measuredHeight / 2))
        isAnchorCentered = true
    }

    LaunchedEffect(targetRowIndex, viewportHeight, isAnchorCentered) {
        if (!isAnchorCentered || targetRowIndex == null || viewportHeight <= 0) return@LaunchedEffect
        listState.animateScrollToItem(targetRowIndex, -(viewportHeight / 2))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (isAnchorCentered) 1f else 0f),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 72.dp, bottom = 400.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            val isSearchMatch = index in matchRowIndexSet
            when (row) {
                is TimelineRow.DayHeader -> {
                    if (index != 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = timelineDayLabel(row.date, today),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSearchMatch) FontWeight.Bold else null,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is TimelineRow.EmptyDay -> Text(
                    text = "Nothing on this day yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )

                is TimelineRow.BlockRow -> {
                    val indentModifier = Modifier
                        .padding(start = IndentationStep * row.block.indentationLevel)
                        .fillMaxWidth()
                    val clickModifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onBlockClick(row.date, row.block.id) }
                    )
                    val rowModifier = if (row.block is TableBlock || row.block is DatabaseBlock) {
                        indentModifier.then(clickModifier)
                    } else {
                        indentModifier
                            .clip(BubbleShape)
                            .then(clickModifier)
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    }
                    Box(modifier = rowModifier) {
                        TimelineBlockContent(
                            block = row.block,
                            isSearchMatch = isSearchMatch,
                            editorActions = editorActions,
                            globalTags = globalTags,
                            allLinkableNotes = allLinkableNotes,
                            onNavigate = { onBlockClick(row.date, row.block.id) }
                        )
                    }
                }
            }
        }
    }

    if (isLoading || !isAnchorCentered) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onSurface,
                strokeWidth = 2.dp,
                modifier = Modifier.size(26.dp)
            )
        }
    } else if (rows.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Nothing written yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun TimelineBlockContent(
    block: NoteBlock,
    isSearchMatch: Boolean,
    editorActions: EditorActions,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    onNavigate: () -> Unit
) {
    when (block) {
        is HeadingBlock -> TimelineText(
            block = block,
            text = block.text,
            baseWeight = FontWeight.Bold,
            isSearchMatch = isSearchMatch
        )

        is TextBlock -> TimelineText(
            block = block,
            text = block.text,
            isSearchMatch = isSearchMatch
        )

        is QuoteBlock -> Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Spacer(modifier = Modifier.width(10.dp))
            TimelineText(
                block = block,
                text = block.text,
                color = MaterialTheme.colorScheme.primary,
                baseStyle = FontStyle.Italic,
                isSearchMatch = isSearchMatch
            )
        }

        is CheckboxBlock -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(if (block.isChecked) Res.drawable.square_check else Res.drawable.square),
                contentDescription = if (block.isChecked) "Completed" else "Not completed",
                tint = if (block.isChecked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            TimelineText(
                block = block,
                text = block.text,
                color = if (block.isChecked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                forceStrikeThrough = block.isChecked,
                isSearchMatch = isSearchMatch
            )
        }

        is BulletedListBlock -> TimelinePrefixedText(
            block = block,
            prefix = "•",
            text = block.text,
            isSearchMatch = isSearchMatch
        )

        is NumberedListBlock -> TimelinePrefixedText(
            block = block,
            prefix = "${block.number}.",
            text = block.text,
            isSearchMatch = isSearchMatch
        )

        is ToggleBlock -> TimelinePrefixedText(
            block = block,
            prefix = "›",
            text = block.text,
            isSearchMatch = isSearchMatch
        )

        is CodeBlock -> Row(verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(Res.drawable.code),
                contentDescription = "Code",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isSearchMatch) FontWeight.Bold else null,
                color = MaterialTheme.colorScheme.primary
            )
        }

        is BookmarkBlock -> BookmarkBlockView(
            block = block,
            inSelectionMode = false,
            onToggleSelection = {},
            onUrlSubmit = {}
        )

        is LinkedNoteBlock -> LinkedNoteBlockView(
            block = block,
            inSelectionMode = false,
            onToggleSelection = {},
            // Tapping this card normally opens the linked note itself, but here it should behave
            // like every other block in the timeline and just jump to this block's own day.
            onOpenNote = onNavigate,
            getNoteMetadata = { editorActions.getNoteMetadata(it) }
        )

        is ImageBlock -> ImageBlockView(
            block = block,
            inSelectionMode = false,
            onToggleSelection = {},
            onRequestPicker = {},
            onRequestCamera = {},
            onDelete = {}
        )

        is DocumentBlock -> DocumentBlockView(
            block = block,
            inSelectionMode = false,
            onToggleSelection = {},
            onRequestPicker = {},
            onOpenFile = { filePath, mimeType -> editorActions.onOpenFile(filePath, mimeType) }
        )

        is VoiceBlock -> AudioBlockView(
            block = block,
            inSelectionMode = false,
            onToggleSelection = {},
            onRemoveVoice = {},
            onStartRecording = {},
            onStopRecording = {},
            onPlayAudio = { filePath, onComplete -> editorActions.onPlayAudio(filePath, onComplete) },
            onStopAudio = { editorActions.onStopAudio() }
        )

        is TableBlock -> Box(modifier = Modifier.reduceSideInset(TableBlockSideInsetCancellation)) {
            TableBlockView(
                block = block,
                inSelectionMode = true,
                onUpdateTable = { editorActions.onUpdateTable(block.id, it) },
                onUpdateTableStyle = { cellStyles, rowStyles, columnStyles ->
                    editorActions.onUpdateTableStyle(block.id, cellStyles, rowStyles, columnStyles)
                },
                onUpdateColumnWidth = { columnIndex, width ->
                    editorActions.onUpdateTableColumnWidth(block.id, columnIndex, width)
                }
            )
        }

        is DatabaseBlock -> Box(modifier = Modifier.reduceSideInset(DatabaseBlockSideInsetCancellation)) {
            DatabaseBlockView(
                block = block,
                inSelectionMode = true,
                globalTags = globalTags,
                allLinkableNotes = allLinkableNotes,
                actions = object : EditorActions by editorActions {
                    override fun onToggleSelection(id: String) = onNavigate()
                }
            )
        }

        else -> Unit
    }
}

@Composable
private fun TimelinePrefixedText(
    block: NoteBlock,
    prefix: String,
    text: String,
    isSearchMatch: Boolean
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        TimelineText(block = block, text = text, isSearchMatch = isSearchMatch)
    }
}

@Composable
private fun TimelineMediaLabel(icon: Painter, label: String, isSearchMatch: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSearchMatch) FontWeight.Bold else null,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2
        )
    }
}

@Composable
private fun TimelineText(
    block: NoteBlock,
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    baseWeight: FontWeight? = null,
    baseStyle: FontStyle? = null,
    forceStrikeThrough: Boolean = false,
    isSearchMatch: Boolean = false
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val inlineSpans = block.inlineSpansOrEmpty()
    val annotatedText = remember(text, inlineSpans, linkColor) {
        buildTimelineAnnotatedString(text, inlineSpans, linkColor)
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        fontWeight = if (block.isBold || isSearchMatch) FontWeight.Bold else baseWeight,
        fontStyle = if (block.isItalic) FontStyle.Italic else baseStyle,
        textDecoration = timelineTextDecoration(block, forceStrikeThrough)
    )
}

private fun buildTimelineAnnotatedString(
    text: String,
    inlineSpans: List<InlineSpan>,
    linkColor: Color
): AnnotatedString {
    if (text.contains(NoteLinkPrefix)) return buildNoteLinkAnnotatedString(text, linkColor)
    if (inlineSpans.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        append(text)
        inlineSpans.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start == end) return@forEach
            addStyle(
                style = SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = combineDecorations(span.underline, span.strikeThrough)
                ),
                start = start,
                end = end
            )
        }
    }
}

private fun timelineTextDecoration(block: NoteBlock, forceStrikeThrough: Boolean): TextDecoration? =
    combineDecorations(block.isUnderlined, block.isStrikeThrough || forceStrikeThrough)

private fun combineDecorations(underline: Boolean, strikeThrough: Boolean): TextDecoration? = when {
    underline && strikeThrough -> TextDecoration.combine(
        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
    )
    underline -> TextDecoration.Underline
    strikeThrough -> TextDecoration.LineThrough
    else -> null
}

private fun buildTimelineRows(days: List<DailyTimelineDay>): List<TimelineRow> =
    buildList {
        days.forEach { day ->
            add(TimelineRow.DayHeader(day.date))
            if (day.blocks.isEmpty()) {
                add(TimelineRow.EmptyDay(day.date))
            } else {
                day.blocks.forEach { block -> add(TimelineRow.BlockRow(day.date, block)) }
            }
        }
    }

private fun findMatchingRowIndices(
    rows: List<TimelineRow>,
    query: String,
    today: LocalDate
): List<Int> {
    if (query.isEmpty()) return emptyList()
    return rows.indices.filter { index ->
        when (val row = rows[index]) {
            is TimelineRow.BlockRow -> timelineBlockSearchText(row.block).contains(query, ignoreCase = true)
            is TimelineRow.DayHeader ->
                row.date.toString().contains(query, ignoreCase = true) ||
                    timelineDayLabel(row.date, today).contains(query, ignoreCase = true)
            is TimelineRow.EmptyDay -> false
        }
    }
}

private fun stepMatchPosition(current: Int, matchCount: Int, step: Int): Int {
    if (matchCount <= 0) return 0
    return ((current + step) % matchCount + matchCount) % matchCount
}

private fun timelineBlockSearchText(block: NoteBlock): String = when (block) {
    is TextBlock -> block.text
    is HeadingBlock -> block.text
    is QuoteBlock -> block.text
    is CheckboxBlock -> block.text
    is BulletedListBlock -> block.text
    is NumberedListBlock -> block.text
    is ToggleBlock -> block.text
    is CodeBlock -> block.code
    is BookmarkBlock -> "${block.title.orEmpty()} ${block.url}"
    is DocumentBlock -> block.fileName
    is DatabaseBlock -> block.title
    is TableBlock -> block.rows.joinToString(" ") { row -> row.joinToString(" ") }
    else -> ""
}

private fun timelineDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
    today.plus(-1, DateTimeUnit.DAY) -> "Yesterday"
    else -> {
        val weekday = shortName(date.dayOfWeek.name)
        val month = shortName(date.month.name)
        "$weekday, ${date.day} $month ${date.year}"
    }
}

private fun shortName(enumName: String): String =
    enumName.take(3).lowercase().replaceFirstChar { it.uppercase() }
