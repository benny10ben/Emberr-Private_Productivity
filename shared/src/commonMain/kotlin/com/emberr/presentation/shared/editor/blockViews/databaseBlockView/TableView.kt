package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseRow
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.TableCellStyle
import com.emberr.domain.model.displayText
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.editor.components.DesktopCursor
import com.emberr.presentation.shared.editor.components.desktopPointerCursor
import com.emberr.presentation.shared.editor.EditorActions
import com.emberr.presentation.shared.components.EmberrHorizontalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.badge_dollar_sign
import emberr.shared.generated.resources.calendar
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.file_text
import emberr.shared.generated.resources.flag
import emberr.shared.generated.resources.hash
import emberr.shared.generated.resources.link_2
import emberr.shared.generated.resources.mail
import emberr.shared.generated.resources.microphone
import emberr.shared.generated.resources.paperclip
import emberr.shared.generated.resources.phone
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.sigma
import emberr.shared.generated.resources.square_check
import emberr.shared.generated.resources.tags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableView(
    block: DatabaseBlock,
    activeView: DatabaseView,
    visibleColumns: List<DatabaseColumn>,
    visibleRows: List<DatabaseRow>,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions,
    hazeState: HazeState,
    scrollState: ScrollState,
    coroutineScope: CoroutineScope,
    focusManager: FocusManager,
    currentSheet: DatabaseSheet,
    activeColId: String?,
    activeRowId: String?,
    onOpenSheet: (sheet: DatabaseSheet, rowId: String?, colId: String?) -> Unit,
    onOpenDatePicker: (rowId: String, colId: String) -> Unit,
    desktopDropdown: @Composable (Boolean) -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    val borderColor1 = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    val orderedColumns = remember(visibleColumns) { mutableStateListOf(*visibleColumns.toTypedArray()) }
    var draggedColId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragPointerX by remember { mutableStateOf(0f) }
    val colBoundsInWindow = remember { mutableStateMapOf<String, Rect>() }

    Column(modifier = Modifier.fillMaxWidth()) {
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)
        .smoothWheelScroll(scrollState, horizontal = true).hazeSource(state = hazeState)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            Surface(
                shape = RoundedCornerShape(0.dp),
                color = Color.Transparent,
                border = BorderStroke(0.6.dp, borderColor1)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                            .height(IntrinsicSize.Max)
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        orderedColumns.forEach { col ->
                          key(col.id) {
                            val activeSort = activeView.activeSorts.find { it.columnId == col.id }
                            val isDragged = draggedColId == col.id

                            val typeIcon = when (col.type) {
                                ColumnType.TEXT     -> rememberVectorPainter(Icons.AutoMirrored.Filled.Subject)
                                ColumnType.NUMBER   -> painterResource(Res.drawable.hash)
                                ColumnType.CHECKBOX -> painterResource(Res.drawable.square_check)
                                ColumnType.DATE     -> painterResource(Res.drawable.calendar)
                                ColumnType.FORMULA  -> painterResource(Res.drawable.sigma)
                                ColumnType.PHONE    -> painterResource(Res.drawable.phone)
                                ColumnType.EMAIL    -> painterResource(Res.drawable.mail)
                                ColumnType.TAGS     -> painterResource(Res.drawable.tags)
                                ColumnType.URL      -> painterResource(Res.drawable.link_2)
                                ColumnType.FILES    -> painterResource(Res.drawable.paperclip)
                                ColumnType.PRIORITY -> painterResource(Res.drawable.flag)
                                ColumnType.MONEY    -> painterResource(Res.drawable.badge_dollar_sign)
                                ColumnType.AUDIO    -> painterResource(Res.drawable.microphone)
                                ColumnType.NOTES    -> painterResource(Res.drawable.file_text)
                                ColumnType.STATUS   -> painterResource(Res.drawable.check_square)
                            }

                            Box(
                                modifier = Modifier
                                    .onGloballyPositioned { colBoundsInWindow[col.id] = it.boundsInWindow() }
                                    .graphicsLayer { alpha = if (isDragged) 0.6f else 1f }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(col.width.dp)
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = 44.dp)
                                        .drawBehind {
                                            val px = 0.5.dp.toPx()
                                            drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, size.height), px)
                                            drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), px)
                                        }
                                        .clickable(enabled = !inSelectionMode) {
                                            onOpenSheet(DatabaseSheet.COLUMN_OPTIONS, null, col.id)
                                        }
                                        .pointerInput(col.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    draggedColId = col.id
                                                    dragStartIndex = visibleColumns.indexOfFirst { it.id == col.id }
                                                    dragPointerX = colBoundsInWindow[col.id]?.center?.x ?: 0f
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragPointerX += dragAmount.x
                                                    val hovered = colBoundsInWindow.entries
                                                        .firstOrNull { (_, rect) -> dragPointerX in rect.left..rect.right }
                                                        ?.key
                                                    if (hovered != null && hovered != col.id) {
                                                        val from = orderedColumns.indexOfFirst { it.id == col.id }
                                                        val to = orderedColumns.indexOfFirst { it.id == hovered }
                                                        if (from != -1 && to != -1) {
                                                            orderedColumns.add(to, orderedColumns.removeAt(from))
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggedColId = null
                                                    val finalIndex = orderedColumns.indexOfFirst { it.id == col.id }
                                                    if (dragStartIndex != -1 && finalIndex != -1 && dragStartIndex != finalIndex) {
                                                        actions.onReorderDbColumns(block.id, dragStartIndex, finalIndex)
                                                    }
                                                    dragStartIndex = -1
                                                },
                                                onDragCancel = { draggedColId = null; dragStartIndex = -1 }
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = typeIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            text = col.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        if (activeSort != null) {
                                            if (activeView.activeSorts.size > 1) {
                                                val layerIndex = activeView.activeSorts.indexOfFirst { it.columnId == col.id } + 1
                                                Text(
                                                    text = "$layerIndex",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(end = 2.dp)
                                                )
                                            }
                                            Icon(
                                                if (activeSort.isAscending) painterResource(Res.drawable.arrow_up) else painterResource(Res.drawable.arrow_down),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                desktopDropdown(activeColId == col.id && currentSheet in listOf(
                                    DatabaseSheet.COLUMN_OPTIONS, DatabaseSheet.RENAME, DatabaseSheet.FORMULA))

                                if (isDesktopPlatform) {
                                    val density = LocalDensity.current
                                    var widthDragAccumulator by remember { mutableStateOf(0f) }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight()
                                            .width(8.dp)
                                            .desktopPointerCursor(DesktopCursor.RESIZE_HORIZONTAL)
                                            .draggable(
                                                orientation = Orientation.Horizontal,
                                                state = rememberDraggableState { delta ->
                                                    widthDragAccumulator += with(density) { delta.toDp().value }
                                                    val wholePixels = widthDragAccumulator.toInt()
                                                    if (wholePixels != 0) {
                                                        widthDragAccumulator -= wholePixels
                                                        val idx = orderedColumns.indexOfFirst { it.id == col.id }
                                                        if (idx != -1) {
                                                            val current = orderedColumns[idx]
                                                            val newWidth = (current.width + wholePixels).coerceIn(40, 600)
                                                            orderedColumns[idx] = current.copy(width = newWidth)
                                                        }
                                                    }
                                                },
                                                onDragStopped = {
                                                    val idx = orderedColumns.indexOfFirst { it.id == col.id }
                                                    if (idx != -1) {
                                                        actions.onUpdateDbColumnWidth(block.id, col.id, orderedColumns[idx].width)
                                                    }
                                                }
                                            )
                                    )
                                }
                            }
                          }
                        }

                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .defaultMinSize(minHeight = 47.dp)
                                .drawBehind {
                                    val px = 0.5.dp.toPx()
                                    drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), px)
                                }
                                .clickable(enabled = !inSelectionMode) {
                                    actions.onAddDbColumn(block.id)
                                    coroutineScope.launch { delay(150.milliseconds); scrollState.animateScrollTo(scrollState.maxValue) }
                                }
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(painterResource(Res.drawable.plus), contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                    }

                    visibleRows.forEach { row ->
                        Row(modifier = Modifier.height(IntrinsicSize.Max).defaultMinSize(minHeight = 44.dp)) {
                            orderedColumns.forEach { col ->
                                val cellData = row.cells[col.id]
                                val isHighlighted = currentSheet == DatabaseSheet.CELL_OPTIONS && activeRowId == row.id && activeColId == col.id

                                Box {
                                    Box(
                                        modifier = Modifier
                                            .width(col.width.dp)
                                            .fillMaxHeight()
                                            .defaultMinSize(minHeight = 44.dp)
                                            .drawBehind {
                                                val px = 0.5.dp.toPx()
                                                drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, size.height), px)
                                                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), px)
                                            }
                                            .then(if (isHighlighted) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary) else Modifier)
                                            .pointerInput(inSelectionMode) {
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    var isLongPress = false
                                                    try {
                                                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                                            waitForUpOrCancellation()
                                                        }
                                                    } catch (_: PointerEventTimeoutCancellationException) {
                                                        isLongPress = true
                                                        currentEvent.changes.forEach { it.consume() }
                                                    }
                                                    if (isLongPress && !inSelectionMode) {
                                                        focusManager.clearFocus()
                                                        onOpenSheet(DatabaseSheet.CELL_OPTIONS, row.id, col.id)
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        TableCell(
                                            cell = cellData,
                                            cellKey = "${row.id}:${col.id}",
                                            cellStyle = block.cellStyles["${row.id}:${col.id}"] ?: TableCellStyle(),
                                            cellSpans = block.cellSpans["${row.id}:${col.id}"].orEmpty(),
                                            allLinkableNotes = allLinkableNotes,
                                            columnType = col.type,
                                            cellWidth = col.width.dp,
                                            globalTags = globalTags,
                                            inSelectionMode = inSelectionMode,
                                            currencySymbol = col.currencySymbol ?: "$",
                                            isFormulaCurrency = col.isFormulaCurrency,
                                            onValueChange = {
                                                actions.onUpdateDbCell(
                                                    block.id,
                                                    row.id,
                                                    col.id,
                                                    it
                                                )
                                            },
                                            onDateClick = {
                                                if (!inSelectionMode) {
                                                    focusManager.clearFocus()
                                                    onOpenDatePicker(row.id, col.id)
                                                }
                                            },
                                            onTagClick = {
                                                if (!inSelectionMode) {
                                                    focusManager.clearFocus()
                                                    onOpenSheet(
                                                        DatabaseSheet.TAG_SELECTION,
                                                        row.id,
                                                        col.id
                                                    )
                                                }
                                            },
                                            onFileClick = {
                                                if (!inSelectionMode) {
                                                    focusManager.clearFocus()
                                                    onOpenSheet(
                                                        DatabaseSheet.FILE_OPTIONS,
                                                        row.id,
                                                        col.id
                                                    )
                                                }
                                            },
                                            onPriorityClick = {
                                                if (!inSelectionMode) {
                                                    focusManager.clearFocus()
                                                    onOpenSheet(
                                                        DatabaseSheet.PRIORITY_SELECTION,
                                                        row.id,
                                                        col.id
                                                    )
                                                }
                                            },
                                            onStatusClick = {
                                                if (!inSelectionMode) {
                                                    focusManager.clearFocus()
                                                    onOpenSheet(
                                                        DatabaseSheet.STATUS_SELECTION,
                                                        row.id,
                                                        col.id
                                                    )
                                                }
                                            },
                                            onNoteClick = {
                                                if (!inSelectionMode) {
                                                    val existingNoteId =
                                                        (cellData as? CellData.NoteRelation)?.noteIds?.firstOrNull()
                                                    actions.onOpenDatabaseNote(
                                                        block.id,
                                                        row.id,
                                                        col.id,
                                                        existingNoteId
                                                    )
                                                }
                                            },
                                            onNoteLinkClick = { noteId ->
                                                actions.onNoteLinkClick(
                                                    noteId
                                                )
                                            },
                                            onGetNoteTitle = { id -> actions.getNoteTitle(id) },
                                            onCreateLinkedNote = { title ->
                                                actions.onCreateLinkedNote(
                                                    title
                                                )
                                            },
                                            onLongPress = {
                                                if (!inSelectionMode) {
                                                    focusManager.clearFocus()
                                                    onOpenSheet(
                                                        DatabaseSheet.CELL_OPTIONS,
                                                        row.id,
                                                        col.id
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    desktopDropdown(activeRowId == row.id && activeColId == col.id && currentSheet in listOf(
                                        DatabaseSheet.CELL_OPTIONS, DatabaseSheet.TAG_SELECTION, DatabaseSheet.FILE_OPTIONS, DatabaseSheet.PRIORITY_SELECTION, DatabaseSheet.STATUS_SELECTION))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .fillMaxHeight()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .drawBehind {
                                        val px = 0.5.dp.toPx()
                                        drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), px)
                                    }
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.height(IntrinsicSize.Max).defaultMinSize(minHeight = 36.dp)) {
                orderedColumns.forEach { col ->
                    val aggType = col.aggregationType
                    val isActivelyEditing = currentSheet == DatabaseSheet.AGGREGATION && activeColId == col.id
                    val isCurr  = col.type == ColumnType.MONEY || (col.type == ColumnType.FORMULA && col.isFormulaCurrency)
                    val prefix  = if (isCurr) (col.currencySymbol ?: "$") else ""

                    val displayValue = if (aggType == null) {
                        if (isActivelyEditing) "Calculate" else ""
                    } else {
                        val values  = visibleRows.map { it.cells[col.id].displayText() }
                        val numbers = values.mapNotNull { it.toDoubleOrNull() }
                        fun Double.fmt() = if (this == this.toLong().toDouble()) this.toLong().toString() else ((this * 100.0).toLong() / 100.0).toString()

                        val result = when (aggType) {
                            "Count all"         -> visibleRows.size.toString()
                            "Count values"      -> values.count { it.isNotBlank() }.toString()
                            "Count unique"      -> values.filter { it.isNotBlank() }.distinct().size.toString()
                            "Count empty"       -> visibleRows.count { it.cells[col.id].displayText().isBlank() }.toString()
                            "Count not empty"   -> values.count { it.isNotBlank() }.toString()
                            "Percent empty"     -> if (visibleRows.isEmpty()) "0%" else "${(visibleRows.count { it.cells[col.id].displayText().isBlank() } * 100 / visibleRows.size)}%"
                            "Percent not empty" -> if (visibleRows.isEmpty()) "0%" else "${(values.count { it.isNotBlank() } * 100 / visibleRows.size)}%"
                            "Sum"     -> if (numbers.isEmpty()) "" else "$prefix${numbers.sum().fmt()}"
                            "Average" -> if (numbers.isEmpty()) "" else "$prefix${(numbers.sum() / numbers.size).fmt()}"
                            "Min"     -> if (numbers.isEmpty()) "" else "$prefix${numbers.minOrNull()?.fmt() ?: ""}"
                            "Max"     -> if (numbers.isEmpty()) "" else "$prefix${numbers.maxOrNull()?.fmt() ?: ""}"
                            "Median"  -> {
                                if (numbers.isEmpty()) ""
                                else {
                                    val sorted = numbers.sorted()
                                    if (sorted.size % 2 == 0) "$prefix${((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2).fmt()}"
                                    else "$prefix${sorted[sorted.size / 2].fmt()}"
                                }
                            }
                            "Range" -> if (numbers.isEmpty()) "" else "$prefix${(numbers.maxOrNull()!! - numbers.minOrNull()!!).fmt()}"
                            else    -> ""
                        }
                        if (result.isEmpty()) aggType else "$aggType $result"
                    }

                    Box(
                        modifier = Modifier
                            .width(col.width.dp)
                            .fillMaxHeight()
                            .defaultMinSize(minHeight = 36.dp)
                            .clickable(enabled = !inSelectionMode) {
                                onOpenSheet(DatabaseSheet.AGGREGATION, null, col.id)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (aggType == null) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    desktopDropdown(activeColId == col.id && currentSheet == DatabaseSheet.AGGREGATION)
                }
                Box(modifier = Modifier.width(44.dp).fillMaxHeight().defaultMinSize(minHeight = 36.dp))
            }

            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !inSelectionMode) { actions.onAddDbRow(block.id) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(Res.drawable.plus), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(7.dp))
                Text(text = "New Row", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

        EmberrHorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 4.dp)
        )
    }
}
