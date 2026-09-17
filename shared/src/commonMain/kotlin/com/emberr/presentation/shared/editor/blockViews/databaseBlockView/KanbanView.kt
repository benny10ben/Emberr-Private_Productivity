package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.data.local.room.TagEntity
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseRow
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DEFAULT_STATUS_OPTIONS
import com.emberr.domain.model.displayText
import com.emberr.domain.util.system.triggerHapticFeedback
import com.emberr.presentation.shared.editor.EditorActions
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.file_text
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource
import com.emberr.presentation.shared.components.EmberrHorizontalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll

const val NO_STATUS_BUCKET = "No Status"

fun bucketKeysFor(groupColumn: DatabaseColumn): List<String> = when (groupColumn.type) {
    ColumnType.CHECKBOX -> listOf("Unchecked", "Checked")
    ColumnType.STATUS -> listOf(NO_STATUS_BUCKET) + DEFAULT_STATUS_OPTIONS
    else -> emptyList()
}

fun orderedBucketKeys(defaultKeys: List<String>, order: List<String>): List<String> {
    val known = order.filter { it in defaultKeys }
    val missing = defaultKeys.filter { it !in order }
    return known + missing
}

private val STATUS_ACCENT_COLORS = mapOf(
    "Not Started" to Color(0xFFE58A7A),
    "In Progress" to Color(0xFFF2C14E),
    "Done" to Color(0xFF6FCF97)
)

fun statusAccentColor(status: String): Color? = STATUS_ACCENT_COLORS[status]

private val CHECKBOX_ACCENT_COLORS = mapOf(
    "Unchecked" to Color(0xFFB39DDB),
    "Checked" to Color(0xFF81C995)
)

fun checkboxAccentColor(bucket: String): Color? = CHECKBOX_ACCENT_COLORS[bucket]

private fun bucketKeyForRow(row: DatabaseRow, groupColumn: DatabaseColumn): String = when (groupColumn.type) {
    ColumnType.CHECKBOX -> if ((row.cells[groupColumn.id] as? CellData.Boolean)?.value == true) "Checked" else "Unchecked"
    ColumnType.STATUS -> {
        val value = (row.cells[groupColumn.id] as? CellData.Text)?.value?.trim().orEmpty()
        if (value in DEFAULT_STATUS_OPTIONS) value else NO_STATUS_BUCKET
    }
    else -> NO_STATUS_BUCKET
}

private fun cellDataForBucket(groupColumn: DatabaseColumn, bucketKey: String): CellData = when (groupColumn.type) {
    ColumnType.CHECKBOX -> CellData.Boolean(bucketKey == "Checked")
    ColumnType.STATUS -> CellData.Text(if (bucketKey == NO_STATUS_BUCKET) "" else bucketKey)
    else -> CellData.Text(bucketKey)
}

fun cardCellText(
    cell: CellData?,
    columnType: ColumnType,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>
): String = when (columnType) {
    ColumnType.TAGS -> (cell as? CellData.TagList)?.tagIds
        ?.mapNotNull { id -> globalTags.find { it.tagId == id }?.name }
        ?.joinToString(", ")
        .orEmpty()
    ColumnType.NOTES -> (cell as? CellData.NoteRelation)?.noteIds?.firstOrNull()
        ?.let { noteId -> allLinkableNotes.find { it.noteId == noteId }?.title }
        .orEmpty()
    else -> cell.displayText()
}

val NOTE_LINK_REGEX = """\[([^\]]+)\]\(emberr://note/([^)]+)\)""".toRegex()
private const val NOTE_LINK_TAG = "NOTE_LINK"

fun buildNoteLinkAnnotatedString(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in NOTE_LINK_REGEX.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        val (title, noteId) = match.destructured
        pushStringAnnotation(NOTE_LINK_TAG, noteId)
        withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold)) {
            append("@$title")
        }
        pop()
        lastIndex = match.range.last + 1
    }
    append(text.substring(lastIndex))
}

@Composable
fun NoteLinkText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    maxLines: Int,
    onNoteLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration? = null
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) { buildNoteLinkAnnotatedString(text, linkColor) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize, fontWeight = fontWeight),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textDecoration = textDecoration,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures { tapOffset ->
                val result = layoutResult ?: return@detectTapGestures
                val charOffset = result.getOffsetForPosition(tapOffset)
                result.layoutInput.text
                    .getStringAnnotations(NOTE_LINK_TAG, charOffset, charOffset)
                    .firstOrNull()
                    ?.let { onNoteLinkClick(it.item) }
            }
        }
    )
}

@Composable
fun NoteRelationChip(
    noteId: String,
    allLinkableNotes: List<NoteMetadataEntity>,
    getNoteTitle: suspend (String) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reactiveNote = allLinkableNotes.find { it.noteId == noteId }
    var noteTitle by remember(noteId, reactiveNote) {
        mutableStateOf(reactiveNote?.title?.ifBlank { "Untitled Note" } ?: "Loading...")
    }

    LaunchedEffect(noteId, reactiveNote) {
        if (reactiveNote == null) {
            noteTitle = getNoteTitle(noteId).ifBlank { "Untitled Note" }
        }
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        modifier = modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.file_text),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = noteTitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CheckboxCellValue(
    checked: Boolean,
    inSelectionMode: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                if (!inSelectionMode) {
                    triggerHapticFeedback()
                    onCheckedChange(it)
                }
            },
            modifier = modifier.scale(0.9f).size(18.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.surface,
                checkmarkColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

private data class KanbanDragState(
    val isDragging: Boolean = false,
    val draggedRowId: String? = null,
    val sourceBucketKey: String? = null,
    val hoveredBucketKey: String? = null,
    val pointerPositionInWindow: Offset = Offset.Zero,
    val grabOffsetInCard: Offset = Offset.Zero,
    val cardSize: IntSize = IntSize.Zero
)

@Composable
fun KanbanView(
    blockId: String,
    activeView: DatabaseView,
    visibleColumns: List<DatabaseColumn>,
    visibleRows: List<DatabaseRow>,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions,
    onOpenGroupBySheet: () -> Unit
) {
    val groupColumn = remember(visibleColumns, activeView.groupByColumnId) {
        visibleColumns.find { it.id == activeView.groupByColumnId }
            ?.takeIf { it.type == ColumnType.CHECKBOX || it.type == ColumnType.STATUS }
    }

    if (groupColumn == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .clickable(enabled = !inSelectionMode, onClick = onOpenGroupBySheet),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Choose a column to group cards by",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    val rowsByBucket = remember(visibleRows, groupColumn.id, groupColumn.type) {
        visibleRows.groupBy { row -> bucketKeyForRow(row, groupColumn) }
    }
    val bucketKeys = remember(groupColumn, activeView.hiddenGroups, activeView.groupOrder) {
        orderedBucketKeys(bucketKeysFor(groupColumn), activeView.groupOrder)
            .filter { it !in activeView.hiddenGroups }
    }

    var dragState by remember { mutableStateOf(KanbanDragState()) }
    val bucketBounds = remember { mutableStateMapOf<String, Rect>() }
    var boardPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    fun hitTestBucket(pointerXInWindow: Float): String? =
        bucketBounds.entries.firstOrNull { (_, rect) -> pointerXInWindow in rect.left..rect.right }?.key

    val boardScrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { boardPositionInWindow = it.boundsInWindow().topLeft }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(boardScrollState)
                .smoothWheelScroll(boardScrollState, horizontal = true)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            bucketKeys.forEach { bucketKey ->
                val rowsInBucket = rowsByBucket[bucketKey].orEmpty()
                val isHovered = dragState.isDragging && dragState.hoveredBucketKey == bucketKey &&
                        dragState.sourceBucketKey != bucketKey
                val accentColor = statusAccentColor(bucketKey) ?: checkboxAccentColor(bucketKey)

                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .onGloballyPositioned { bucketBounds[bucketKey] = it.boundsInWindow() }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isHovered && accentColor != null -> accentColor.copy(alpha = 0.18f)
                                isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                accentColor != null -> accentColor.copy(alpha = 0.08f)
                                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            }
                        )
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (accentColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(accentColor)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = bucketKey,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${rowsInBucket.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowsInBucket.forEach { row ->
                            KanbanCard(
                                blockId = blockId,
                                row = row,
                                columns = visibleColumns,
                                groupColumnId = groupColumn.id,
                                inSelectionMode = inSelectionMode,
                                globalTags = globalTags,
                                allLinkableNotes = allLinkableNotes,
                                actions = actions,
                                isBeingDragged = dragState.draggedRowId == row.id,
                                dragEnabled = !inSelectionMode,
                                onDragStart = { grabOffset, cardBoundsInWindow ->
                                    dragState = KanbanDragState(
                                        isDragging = true,
                                        draggedRowId = row.id,
                                        sourceBucketKey = bucketKey,
                                        hoveredBucketKey = bucketKey,
                                        pointerPositionInWindow = cardBoundsInWindow.topLeft + grabOffset,
                                        grabOffsetInCard = grabOffset,
                                        cardSize = IntSize(cardBoundsInWindow.width.roundToInt(), cardBoundsInWindow.height.roundToInt())
                                    )
                                },
                                onDrag = { dragAmount ->
                                    val newPointer = dragState.pointerPositionInWindow + dragAmount
                                    dragState = dragState.copy(
                                        pointerPositionInWindow = newPointer,
                                        hoveredBucketKey = hitTestBucket(newPointer.x)
                                    )
                                },
                                onDragEnd = {
                                    val target = dragState.hoveredBucketKey
                                    val source = dragState.sourceBucketKey
                                    if (target != null && target != source) {
                                        actions.onUpdateDbCell(blockId, row.id, groupColumn.id, cellDataForBucket(groupColumn, target))
                                    }
                                    dragState = KanbanDragState()
                                },
                                onDragCancel = { dragState = KanbanDragState() }
                            )
                        }
                    }
                }
            }
        }

        if (dragState.isDragging) {
            val draggedRow = visibleRows.find { it.id == dragState.draggedRowId }
            if (draggedRow != null) {
                val localOffset = dragState.pointerPositionInWindow - dragState.grabOffsetInCard - boardPositionInWindow
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .offset { IntOffset(localOffset.x.roundToInt(), localOffset.y.roundToInt()) }
                        .width(with(density) { dragState.cardSize.width.toDp() })
                        .graphicsLayer { alpha = 0.95f; shadowElevation = 12f }
                        .zIndex(10f)
                ) {
                    KanbanCardSurface(
                        blockId = blockId,
                        row = draggedRow,
                        columns = visibleColumns,
                        groupColumnId = groupColumn.id,
                        inSelectionMode = false,
                        globalTags = globalTags,
                        allLinkableNotes = allLinkableNotes,
                        actions = actions
                    )
                }
            }
        }

        EmberrHorizontalScrollbar(
            scrollState = boardScrollState,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp)
        )
    }
}

@Composable
private fun KanbanCard(
    blockId: String,
    row: DatabaseRow,
    columns: List<DatabaseColumn>,
    groupColumnId: String,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions,
    isBeingDragged: Boolean,
    dragEnabled: Boolean,
    onDragStart: (grabOffset: Offset, cardBoundsInWindow: Rect) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    var cardBoundsInWindow by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isBeingDragged) 0f else 1f }
            .onGloballyPositioned { cardBoundsInWindow = it.boundsInWindow() }
            .then(
                if (dragEnabled) {
                    Modifier.pointerInput(row.id) {
                        detectDragGestures(
                            onDragStart = { offset -> onDragStart(offset, cardBoundsInWindow) },
                            onDrag = { change, dragAmount -> change.consume(); onDrag(dragAmount) },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel
                        )
                    }
                } else Modifier
            )
    ) {
        KanbanCardSurface(
            blockId = blockId,
            row = row,
            columns = columns,
            groupColumnId = groupColumnId,
            inSelectionMode = inSelectionMode,
            globalTags = globalTags,
            allLinkableNotes = allLinkableNotes,
            actions = actions
        )
    }
}

@Composable
private fun KanbanCardSurface(
    blockId: String,
    row: DatabaseRow,
    columns: List<DatabaseColumn>,
    groupColumnId: String,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp))
        {
            val primaryColumn = columns.firstOrNull()
            val primaryCell = primaryColumn?.let { row.cells[it.id] }

            when {
                primaryColumn != null && primaryColumn.type == ColumnType.CHECKBOX -> {
                    CheckboxCellValue(
                        checked = (primaryCell as? CellData.Boolean)?.value ?: false,
                        inSelectionMode = inSelectionMode,
                        onCheckedChange = { actions.onUpdateDbCell(blockId, row.id, primaryColumn.id, CellData.Boolean(it)) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                primaryColumn != null && primaryColumn.type == ColumnType.NOTES && primaryCell is CellData.NoteRelation -> {
                    primaryCell.noteIds.firstOrNull()?.let { noteId ->
                        NoteRelationChip(
                            noteId = noteId,
                            allLinkableNotes = allLinkableNotes,
                            getNoteTitle = actions::getNoteTitle,
                            onClick = { actions.onOpenDatabaseNote(blockId, row.id, primaryColumn.id, noteId) }
                        )
                    }
                }
                primaryCell is CellData.Text && NOTE_LINK_REGEX.containsMatchIn(primaryCell.value) -> {
                    NoteLinkText(
                        text = primaryCell.value,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        onNoteLinkClick = actions::onNoteLinkClick
                    )
                }
                else -> {
                    val primaryText = primaryColumn
                        ?.let { cardCellText(row.cells[it.id], it.type, globalTags, allLinkableNotes) }
                        .orEmpty()
                    Text(
                        text = primaryText.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val secondaryColumns = remember(columns, groupColumnId, primaryColumn?.id) {
                columns.filter { it.id != groupColumnId && it.id != primaryColumn?.id }
            }
            secondaryColumns.forEach { col ->
                val cell = row.cells[col.id]
                when {
                    col.type == ColumnType.CHECKBOX -> {
                        Spacer(Modifier.height(3.dp))
                        CheckboxCellValue(
                            checked = (cell as? CellData.Boolean)?.value ?: false,
                            inSelectionMode = inSelectionMode,
                            onCheckedChange = {
                                actions.onUpdateDbCell(
                                    blockId,
                                    row.id,
                                    col.id,
                                    CellData.Boolean(it)
                                )
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    col.type == ColumnType.NOTES && cell is CellData.NoteRelation && cell.noteIds.isNotEmpty() -> {
                        val noteId = cell.noteIds.first()
                        Spacer(Modifier.height(3.dp))
                        NoteRelationChip(
                            noteId = noteId,
                            allLinkableNotes = allLinkableNotes,
                            getNoteTitle = actions::getNoteTitle,
                            onClick = {
                                actions.onOpenDatabaseNote(
                                    blockId,
                                    row.id,
                                    col.id,
                                    noteId
                                )
                            }
                        )
                    }

                    cell is CellData.Text && cell.value.isNotBlank() && NOTE_LINK_REGEX.containsMatchIn(
                        cell.value
                    ) -> {
                        Spacer(Modifier.height(3.dp))
                        NoteLinkText(
                            text = cell.value,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            fontWeight = null,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            onNoteLinkClick = actions::onNoteLinkClick
                        )
                    }

                    else -> {
                        val value = cardCellText(cell, col.type, globalTags, allLinkableNotes)
                        if (value.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
