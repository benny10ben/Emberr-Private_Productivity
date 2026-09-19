package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import com.emberr.domain.util.system.triggerHapticFeedback
import com.emberr.presentation.shared.components.EmberrHorizontalScrollbar
import com.emberr.presentation.shared.components.smoothWheelScroll
import com.emberr.presentation.shared.editor.EditorActions
import com.emberr.presentation.shared.editor.GlobalEditorState
import com.emberr.presentation.shared.editor.LinkContextMenu
import com.emberr.presentation.shared.editor.LinkHoverCard
import com.emberr.presentation.shared.editor.RichTextVisualTransformation
import com.emberr.presentation.shared.editor.components.DesktopCursor
import com.emberr.presentation.shared.editor.components.desktopPointerCursor
import com.emberr.presentation.shared.editor.linkHover
import com.emberr.presentation.shared.editor.openLinksOnPress
import com.emberr.presentation.shared.editor.rememberLinkHoverState
import com.emberr.presentation.shared.editor.rememberWebLinkActions
import com.emberr.presentation.shared.editor.webLinkAtPosition
import com.emberr.ui.theme.highlightBackgroundColor
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
import emberr.shared.generated.resources.list_sort_descending
import emberr.shared.generated.resources.mail
import emberr.shared.generated.resources.microphone
import emberr.shared.generated.resources.paperclip
import emberr.shared.generated.resources.phone
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.sigma
import emberr.shared.generated.resources.square_arrow_out_up_right
import emberr.shared.generated.resources.square_check
import emberr.shared.generated.resources.tags
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TableView(
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TableCell(
    cell: CellData?,
    cellKey: String,
    cellStyle: TableCellStyle,
    cellSpans: List<InlineSpan>,
    columnType: ColumnType,
    cellWidth: Dp,
    globalTags: List<TagEntity>,
    inSelectionMode: Boolean,
    currencySymbol: String = "$",
    isFormulaCurrency: Boolean = false,
    onValueChange: (CellData) -> Unit,
    onDateClick: () -> Unit,
    onTagClick: () -> Unit,
    onFileClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onStatusClick: () -> Unit,
    onNoteClick: () -> Unit,
    onNoteLinkClick: (String) -> Unit,
    onGetNoteTitle: suspend (String) -> String,
    allLinkableNotes: List<NoteMetadataEntity>,
    onCreateLinkedNote: (String) -> String,
    onLongPress: () -> Unit = {}
) {
    when (columnType) {
        ColumnType.TEXT, ColumnType.NUMBER, ColumnType.PHONE, ColumnType.EMAIL, ColumnType.URL, ColumnType.MONEY ->
            EditableTextCell(
                cell = cell,
                cellKey = cellKey,
                cellStyle = cellStyle,
                cellSpans = cellSpans,
                columnType = columnType,
                cellWidth = cellWidth,
                inSelectionMode = inSelectionMode,
                currencySymbol = currencySymbol,
                allLinkableNotes = allLinkableNotes,
                onValueChange = onValueChange,
                onNoteLinkClick = onNoteLinkClick,
                onCreateLinkedNote = onCreateLinkedNote
            )

        ColumnType.CHECKBOX -> CheckboxCell(cell, inSelectionMode, onValueChange)
        ColumnType.DATE -> DateCell(cell, inSelectionMode, onDateClick)
        ColumnType.FORMULA -> FormulaCell(cell, currencySymbol, isFormulaCurrency)
        ColumnType.TAGS -> TagsCell(cell, globalTags, inSelectionMode, onTagClick, onLongPress)
        ColumnType.FILES, ColumnType.AUDIO -> MediaCell(cell, columnType, inSelectionMode, onFileClick, onLongPress)
        ColumnType.PRIORITY -> ColoredChipCell(cell, inSelectionMode, ::priorityAccentColor, onPriorityClick, onLongPress)
        ColumnType.STATUS -> ColoredChipCell(cell, inSelectionMode, ::statusAccentColor, onStatusClick, onLongPress)
        ColumnType.NOTES -> NoteRelationCell(
            cell = cell,
            cellWidth = cellWidth,
            inSelectionMode = inSelectionMode,
            allLinkableNotes = allLinkableNotes,
            onGetNoteTitle = onGetNoteTitle,
            onNoteClick = onNoteClick,
            onLongPress = onLongPress
        )
    }
}

private fun ColumnType.isExternallyOpenable() =
    this == ColumnType.EMAIL || this == ColumnType.PHONE || this == ColumnType.URL

@Composable
private fun EditableTextCell(
    cell: CellData?,
    cellKey: String,
    cellStyle: TableCellStyle,
    cellSpans: List<InlineSpan>,
    columnType: ColumnType,
    cellWidth: Dp,
    inSelectionMode: Boolean,
    currencySymbol: String,
    allLinkableNotes: List<NoteMetadataEntity>,
    onValueChange: (CellData) -> Unit,
    onNoteLinkClick: (String) -> Unit,
    onCreateLinkedNote: (String) -> String
) {
    val uriHandler = LocalUriHandler.current
    val validNoteIds = remember(allLinkableNotes) { allLinkableNotes.map { it.noteId }.toSet() }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val isNumeric = columnType == ColumnType.NUMBER || columnType == ColumnType.MONEY
    val value = if (isNumeric) {
        (cell as? CellData.Number)?.value
            ?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            ?: ""
    } else {
        (cell as? CellData.Text)?.value ?: ""
    }

    Box(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            if (!inSelectionMode) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (columnType == ColumnType.MONEY && (value.isNotBlank() || isFocused)) {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            TableCellTextEditor(
                initialText = value,
                columnType = columnType,
                allLinkableNotes = allLinkableNotes,
                visualTransformation = if (columnType == ColumnType.TEXT) {
                    RichTextVisualTransformation(
                        linkColor = MaterialTheme.colorScheme.primary,
                        fadedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        highlightColor = highlightBackgroundColor,
                        validNoteIds = validNoteIds,
                        inlineSpans = cellSpans
                    )
                } else VisualTransformation.None,
                inSelectionMode = inSelectionMode,
                cellKey = cellKey,
                cellStyle = cellStyle,
                focusRequester = focusRequester,
                onValueChange = { raw ->
                    onValueChange(
                        if (isNumeric) CellData.Number(raw.toDoubleOrNull()) else CellData.Text(raw)
                    )
                },
                onFocusChanged = { isFocused = it },
                onNoteLinkClick = onNoteLinkClick,
                onCreateLinkedNote = onCreateLinkedNote,
                modifier = Modifier.weight(1f).defaultMinSize(minWidth = cellWidth - 24.dp)
            )

            if (columnType.isExternallyOpenable() && value.isNotBlank() && !isFocused) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(Res.drawable.square_arrow_out_up_right),
                    contentDescription = "Open Link",
                    modifier = Modifier.size(16.dp).clickable {
                        val uri = when (columnType) {
                            ColumnType.EMAIL -> "mailto:$value"
                            ColumnType.PHONE -> "tel:$value"
                            ColumnType.URL ->
                                if (!value.startsWith("http://") && !value.startsWith("https://")) "https://$value"
                                else value
                            else -> null
                        }
                        if (uri != null) {
                            try {
                                uriHandler.openUri(uri)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CheckboxCell(cell: CellData?, inSelectionMode: Boolean, onValueChange: (CellData) -> Unit) {
    val isChecked = (cell as? CellData.Boolean)?.value ?: false
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = {
                    if (!inSelectionMode) {
                        triggerHapticFeedback()
                        onValueChange(CellData.Boolean(it))
                    }
                },
                modifier = Modifier.scale(0.9f).size(18.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.surface,
                    checkmarkColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}

@Composable
private fun DateCell(cell: CellData?, inSelectionMode: Boolean, onDateClick: () -> Unit) {
    val value = cell.displayText()
    Text(
        text = value.ifEmpty { "—" },
        style = MaterialTheme.typography.bodyLarge,
        color = if (value.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !inSelectionMode) { onDateClick() }
    )
}

@Composable
private fun FormulaCell(cell: CellData?, currencySymbol: String, isFormulaCurrency: Boolean) {
    val value = (cell as? CellData.Formula)?.result ?: ""
    val formulaScrollState = rememberScrollState()
    val isInvalid = value.equals("NaN", ignoreCase = true) || value.startsWith("Error", ignoreCase = true)

    val displayValue = when {
        isInvalid -> ""
        isFormulaCurrency && value.toDoubleOrNull() != null -> {
            val number = value.toDouble()
            val formatted =
                if (number == number.toLong().toDouble()) number.toLong().toString()
                else ((number * 100.0).toLong() / 100.0).toString()
            "$currencySymbol$formatted"
        }
        else -> value
    }

    Text(
        text = displayValue,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        modifier = Modifier.horizontalScroll(formulaScrollState).smoothWheelScroll(formulaScrollState, horizontal = true)
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TagsCell(
    cell: CellData?,
    globalTags: List<TagEntity>,
    inSelectionMode: Boolean,
    onTagClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val activeTagIds = (cell as? CellData.TagList)?.tagIds ?: emptyList()
    val activeTags = activeTagIds.mapNotNull { id -> globalTags.find { it.tagId == id } }

    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(
            onClick = { if (!inSelectionMode) onTagClick() },
            onLongClick = { if (!inSelectionMode) onLongPress() }
        )
    ) {
        if (activeTags.isEmpty()) {
            EmptyCellHint("Empty")
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = if (isDesktopPlatform) 12.dp else 0.dp)
            ) {
                activeTags.forEach { tag ->
                    val tagColor = parseTagColor(tag.colorHex)
                    Surface(shape = RoundedCornerShape(4.dp), color = tagColor.copy(alpha = 0.15f)) {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = tagColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MediaCell(
    cell: CellData?,
    columnType: ColumnType,
    inSelectionMode: Boolean,
    onFileClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val resources = (cell as? CellData.MediaList)?.files ?: emptyList()

    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(
            onClick = { if (!inSelectionMode) onFileClick() },
            onLongClick = { if (!inSelectionMode) onLongPress() }
        )
    ) {
        if (resources.isEmpty()) {
            EmptyCellHint("Empty")
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                resources.forEach { resourceEntry ->
                    val cleanFileName = resourceEntry.fileName.substringAfterLast("/")
                    val resourceName = resourceEntry.originalName.ifBlank { cleanFileName }

                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painterResource(
                                    if (columnType == ColumnType.AUDIO) Res.drawable.microphone
                                    else Res.drawable.paperclip
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = resourceName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 100.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColoredChipCell(
    cell: CellData?,
    inSelectionMode: Boolean,
    accentColorFor: (String) -> Color?,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val value = (cell as? CellData.Text)?.value ?: ""

    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(
            onClick = { if (!inSelectionMode) onClick() },
            onLongClick = { if (!inSelectionMode) onLongPress() }
        )
    ) {
        if (value.isBlank()) {
            EmptyCellHint("—", alpha = 0.65f)
        } else {
            val chipColor = accentColorFor(value) ?: MaterialTheme.colorScheme.outline
            Surface(shape = RoundedCornerShape(4.dp), color = chipColor.copy(alpha = 0.15f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = chipColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRelationCell(
    cell: CellData?,
    cellWidth: Dp,
    inSelectionMode: Boolean,
    allLinkableNotes: List<NoteMetadataEntity>,
    onGetNoteTitle: suspend (String) -> String,
    onNoteClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val noteId = (cell as? CellData.NoteRelation)?.noteIds?.firstOrNull() ?: ""
    val reactiveNote = allLinkableNotes.find { it.noteId == noteId }
    var noteTitle by remember(noteId) { mutableStateOf("Loading...") }

    LaunchedEffect(noteId, reactiveNote) {
        if (noteId.isNotBlank()) {
            noteTitle = (reactiveNote?.title ?: onGetNoteTitle(noteId)).ifBlank { "Untitled Note" }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp).combinedClickable(
            onClick = { if (!inSelectionMode) onNoteClick() },
            onLongClick = { if (!inSelectionMode) onLongPress() }
        ),
        contentAlignment = Alignment.CenterStart
    ) {
        if (noteId.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(Res.drawable.plus),
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(4.dp))
                EmptyCellHint("New Note", alpha = 0.6f)
            }
        } else {
            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painterResource(Res.drawable.file_text),
                        null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = noteTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = cellWidth - 45.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCellHint(text: String, alpha: Float = 0.5f) {
    Text(
        text,
        color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
        style = MaterialTheme.typography.labelSmall
    )
}

private val NOTE_LINK_BEFORE_CURSOR = """\[([^\]]+)\]\(emberr://note/([^)]+)\)$""".toRegex()

@Composable
private fun TableCellTextEditor(
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
                        GlobalEditorState.currentSelection = TextRange.Zero
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
