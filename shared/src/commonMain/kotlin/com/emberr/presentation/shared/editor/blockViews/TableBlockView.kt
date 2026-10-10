package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.TableCellContentType
import com.emberr.domain.model.TableCellStyle
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrDesktopMenu
import com.emberr.presentation.shared.components.MenuAtTap
import com.emberr.presentation.shared.components.menuTapAnchor
import com.emberr.presentation.shared.components.rememberMenuTapAnchor
import com.emberr.presentation.shared.components.rememberKeyboardHandoff
import com.emberr.presentation.shared.editor.WebLinkVisualTransformation
import com.emberr.presentation.shared.editor.GlobalEditorState
import com.emberr.presentation.shared.editor.rememberWebLinkActions
import com.emberr.presentation.shared.editor.LinkContextMenu
import com.emberr.presentation.shared.editor.LinkHoverCard
import com.emberr.presentation.shared.editor.linkHover
import com.emberr.presentation.shared.editor.openLinksOnPress
import com.emberr.presentation.shared.editor.rememberLinkHoverState
import com.emberr.presentation.shared.editor.HoveredLink
import com.emberr.presentation.shared.editor.hoveredLinkAt
import com.emberr.presentation.shared.editor.components.DesktopCursor
import com.emberr.presentation.shared.editor.components.desktopPointerCursor
import com.emberr.presentation.shared.components.EmberrHorizontalScrollbar
import com.emberr.presentation.shared.components.horizontalScrollFromBackAndForwardButtons
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseAlignmentOptions
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseMenuLayer
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseMenuOption
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseMenuSectionDivider
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseMenuSectionLabel
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseOptionIcon
import com.emberr.presentation.shared.editor.blockViews.database.DatabaseStyleColorChoices
import com.emberr.presentation.shared.editor.blockViews.database.databaseBackgroundColorNamed
import com.emberr.presentation.shared.editor.blockViews.database.databaseTextColorNamed
import com.emberr.ui.theme.HighlightColor
import com.emberr.ui.theme.LocalAppIsDark
import com.emberr.ui.theme.tableGridLineColor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.arrow_down
import emberr.shared.generated.resources.arrow_left
import emberr.shared.generated.resources.arrow_right
import emberr.shared.generated.resources.arrow_up
import emberr.shared.generated.resources.code
import emberr.shared.generated.resources.format_bold
import emberr.shared.generated.resources.italic
import emberr.shared.generated.resources.link
import emberr.shared.generated.resources.mail
import emberr.shared.generated.resources.minus
import emberr.shared.generated.resources.move_left
import emberr.shared.generated.resources.move_right
import emberr.shared.generated.resources.palette
import emberr.shared.generated.resources.phone
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.text_x
import emberr.shared.generated.resources.trash
import emberr.shared.generated.resources.underline
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds

private const val DefaultColumnWidth = 140
private val CellMinHeight = 44.dp
private val CellHorizontalPadding = 12.dp
private val CellVerticalPadding = 9.dp
private val GutterSize = 44.dp
private val SidePadding = 18.dp
private val MenuMinWidth = 240.dp
private val MenuMaxWidth = 300.dp
private val TableLineWidth = 0.8.dp

private val MenuRowInset: Dp
    get() = if (isDesktopPlatform) 8.dp else 0.dp

private val MenuRowInnerPadding: Dp
    get() = if (isDesktopPlatform) 12.dp else 0.dp

private val MenuTextInset: Dp
    get() = if (isDesktopPlatform) 20.dp else 0.dp

private fun String.toColorOrNull(): Color? = try {
    Color(this.removePrefix("#").toLong(16) or 0xFF000000)
} catch (_: Exception) {
    null
}

private fun Modifier.tableOutline(color: Color): Modifier = drawWithContent {
    drawContent()
    val lineWidth = TableLineWidth.toPx()
    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), lineWidth)
    drawLine(color, Offset(0f, 0f), Offset(0f, size.height), lineWidth)
    drawLine(color, Offset(size.width, 0f), Offset(size.width, size.height), lineWidth)
}

private enum class TableStyleScope { CELL, ROW, COLUMN }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableBlockView(
    block: TableBlock,
    inSelectionMode: Boolean,
    onUpdateTable: (rows: List<List<String>>) -> Unit,
    onUpdateTableStyle: (
        cellStyles: Map<String, TableCellStyle>,
        rowStyles: Map<String, TableCellStyle>,
        columnStyles: Map<String, TableCellStyle>
    ) -> Unit,
    onUpdateColumnWidth: (columnIndex: Int, width: Int) -> Unit
) {
    val rows = block.rows
    val columnCount = rows.firstOrNull()?.size ?: 0
    val borderColor = tableGridLineColor
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardHandoff = rememberKeyboardHandoff()

    var activeRowIndex by remember { mutableStateOf(-1) }
    var activeColIndex by remember { mutableStateOf(-1) }
    var showCellActions by remember { mutableStateOf(false) }

    fun closeMenu() {
        showCellActions = false
        activeRowIndex = -1
        activeColIndex = -1
    }

    fun openCellActions(rowIndex: Int, colIndex: Int) {
        activeRowIndex = rowIndex
        activeColIndex = colIndex
        keyboardHandoff.run { showCellActions = true }
    }

    fun updateCell(rowIndex: Int, colIndex: Int, newValue: String) {
        onUpdateTable(
            rows.mapIndexed { r, row ->
                if (r != rowIndex) row else row.mapIndexed { c, cell -> if (c == colIndex) newValue else cell }
            }
        )
    }

    fun addRow() = onUpdateTable(rows + listOf(List(columnCount) { "" }))
    fun addColumn() {
        onUpdateTable(rows.map { it + "" })
        coroutineScope.launch {
            delay(150.milliseconds)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    fun insertRowAt(index: Int) = onUpdateTable(rows.toMutableList().apply { add(index, List(columnCount) { "" }) })
    fun insertColumnAt(index: Int) = onUpdateTable(rows.map { row -> row.toMutableList().apply { add(index, "") } })

    fun deleteRowAt(index: Int) {
        if (rows.size <= 1) return
        onUpdateTable(rows.filterIndexed { i, _ -> i != index })
    }

    fun deleteColumnAt(index: Int) {
        if (columnCount <= 1) return
        onUpdateTable(rows.map { row -> row.filterIndexed { i, _ -> i != index } })
    }

    // Overrides block.columnWidths while a drag is in progress, so the column resizes instantly
    // on every pointer delta instead of waiting on a full modifyBlocks -> StateFlow -> recompose
    // round trip per pixel. Re-keyed (and thus cleared) whenever the persisted widths change -
    // i.e. once this same drag's own onDragStopped commit lands, or a sync update arrives.
    val liveColumnWidths = remember(block.columnWidths) { mutableStateMapOf<Int, Int>() }

    fun columnWidthFor(colIndex: Int): Int =
        liveColumnWidths[colIndex] ?: block.columnWidths["$colIndex"] ?: DefaultColumnWidth

    fun updateColumnWidthBy(colIndex: Int, delta: Int) {
        onUpdateColumnWidth(colIndex, (columnWidthFor(colIndex) + delta).coerceIn(40, 600))
    }

    fun moveRow(index: Int, targetIndex: Int) {
        if (targetIndex !in rows.indices) return
        onUpdateTable(
            rows.toMutableList().apply {
                val tmp = this[index]
                this[index] = this[targetIndex]
                this[targetIndex] = tmp
            }
        )

        val newRowStyles = block.rowStyles.toMutableMap()
        val a = newRowStyles.remove("$index")
        val b = newRowStyles.remove("$targetIndex")
        if (b != null) newRowStyles["$index"] = b
        if (a != null) newRowStyles["$targetIndex"] = a

        val newCellStyles = block.cellStyles.toMutableMap()
        for (c in 0 until columnCount) {
            val keyA = "$index:$c"
            val keyB = "$targetIndex:$c"
            val sa = newCellStyles.remove(keyA)
            val sb = newCellStyles.remove(keyB)
            if (sb != null) newCellStyles[keyA] = sb
            if (sa != null) newCellStyles[keyB] = sa
        }
        onUpdateTableStyle(newCellStyles, newRowStyles, block.columnStyles)
    }

    fun moveColumn(index: Int, targetIndex: Int) {
        if (targetIndex !in 0 until columnCount) return
        onUpdateTable(
            rows.map { row ->
                row.toMutableList().apply {
                    val tmp = this[index]
                    this[index] = this[targetIndex]
                    this[targetIndex] = tmp
                }
            }
        )

        val newColumnStyles = block.columnStyles.toMutableMap()
        val a = newColumnStyles.remove("$index")
        val b = newColumnStyles.remove("$targetIndex")
        if (b != null) newColumnStyles["$index"] = b
        if (a != null) newColumnStyles["$targetIndex"] = a

        val newCellStyles = block.cellStyles.toMutableMap()
        for (r in rows.indices) {
            val keyA = "$r:$index"
            val keyB = "$r:$targetIndex"
            val sa = newCellStyles.remove(keyA)
            val sb = newCellStyles.remove(keyB)
            if (sb != null) newCellStyles[keyA] = sb
            if (sa != null) newCellStyles[keyB] = sa
        }
        onUpdateTableStyle(newCellStyles, block.rowStyles, newColumnStyles)
    }

    fun styleKeyFor(scope: TableStyleScope): String = when (scope) {
        TableStyleScope.CELL -> "$activeRowIndex:$activeColIndex"
        TableStyleScope.ROW -> "$activeRowIndex"
        TableStyleScope.COLUMN -> "$activeColIndex"
    }

    fun currentStyleFor(scope: TableStyleScope): TableCellStyle {
        val key = styleKeyFor(scope)
        return when (scope) {
            TableStyleScope.CELL -> block.cellStyles[key]
            TableStyleScope.ROW -> block.rowStyles[key]
            TableStyleScope.COLUMN -> block.columnStyles[key]
        } ?: TableCellStyle()
    }

    fun applyStyle(scope: TableStyleScope, newStyle: TableCellStyle) {
        val key = styleKeyFor(scope)
        when (scope) {
            TableStyleScope.CELL -> onUpdateTableStyle(block.cellStyles + (key to newStyle), block.rowStyles, block.columnStyles)
            TableStyleScope.ROW -> onUpdateTableStyle(block.cellStyles, block.rowStyles + (key to newStyle), block.columnStyles)
            TableStyleScope.COLUMN -> onUpdateTableStyle(block.cellStyles, block.rowStyles, block.columnStyles + (key to newStyle))
        }
    }

    fun resetStyle(scope: TableStyleScope) {
        val key = styleKeyFor(scope)
        when (scope) {
            TableStyleScope.CELL -> onUpdateTableStyle(block.cellStyles - key, block.rowStyles, block.columnStyles)
            TableStyleScope.ROW -> onUpdateTableStyle(block.cellStyles, block.rowStyles - key, block.columnStyles)
            TableStyleScope.COLUMN -> onUpdateTableStyle(block.cellStyles, block.rowStyles, block.columnStyles - key)
        }
    }

    fun effectiveStyle(rowIndex: Int, colIndex: Int): TableCellStyle {
        val layersNearestFirst = listOfNotNull(
            block.cellStyles["$rowIndex:$colIndex"],
            block.rowStyles["$rowIndex"],
            block.columnStyles["$colIndex"]
        )
        if (layersNearestFirst.isEmpty()) return TableCellStyle()

        return TableCellStyle(
            backgroundColorHex = layersNearestFirst.firstNotNullOfOrNull { it.backgroundColorHex },
            textColorHex = layersNearestFirst.firstNotNullOfOrNull { it.textColorHex },
            isBold = layersNearestFirst.any { it.isBold },
            isItalic = layersNearestFirst.any { it.isItalic },
            isUnderlined = layersNearestFirst.any { it.isUnderlined },
            isStrikeThrough = layersNearestFirst.any { it.isStrikeThrough },
            isCode = layersNearestFirst.any { it.isCode },
            contentType = layersNearestFirst
                .firstOrNull { it.contentType != TableCellContentType.NONE }
                ?.contentType ?: TableCellContentType.NONE,
            alignment = layersNearestFirst.firstNotNullOfOrNull { it.alignment }
        )
    }

    val cellActionsBody = @Composable {
        val rowIndex = activeRowIndex
        val colIndex = activeColIndex
        if (rowIndex in rows.indices && colIndex in 0 until columnCount) {
            SheetMenuRow(painterResource(Res.drawable.arrow_up), "Insert Row Above") {
                insertRowAt(rowIndex); closeMenu()
            }
            SheetMenuRow(painterResource(Res.drawable.arrow_down), "Insert Row Below") {
                insertRowAt(rowIndex + 1); closeMenu()
            }
            SheetMenuRow(painterResource(Res.drawable.arrow_left), "Insert Column Left") {
                insertColumnAt(colIndex); closeMenu()
            }
            SheetMenuRow(painterResource(Res.drawable.arrow_right), "Insert Column Right") {
                insertColumnAt(colIndex + 1); closeMenu()
            }

            TableMenuDivider()

            if (rowIndex > 0) {
                SheetMenuRow(rememberVectorPainter(Icons.Default.ArrowUpward), "Move Row Up") {
                    moveRow(rowIndex, rowIndex - 1); closeMenu()
                }
            }
            if (rowIndex < rows.lastIndex) {
                SheetMenuRow(rememberVectorPainter(Icons.Default.ArrowDownward), "Move Row Down") {
                    moveRow(rowIndex, rowIndex + 1); closeMenu()
                }
            }
            if (colIndex > 0) {
                SheetMenuRow(painterResource(Res.drawable.move_left), "Move Column Left") {
                    moveColumn(colIndex, colIndex - 1); closeMenu()
                }
            }
            if (colIndex < columnCount - 1) {
                SheetMenuRow(painterResource(Res.drawable.move_right), "Move Column Right") {
                    moveColumn(colIndex, colIndex + 1); closeMenu()
                }
            }

            if (!isDesktopPlatform) {
                TableMenuDivider()

                StyleSectionLabel("Column Width")
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.clickable { updateColumnWidthBy(colIndex, -20) }
                    ) {
                        Icon(
                            painterResource(Res.drawable.minus),
                            contentDescription = "Decrease column width",
                            modifier = Modifier.padding(8.dp).size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "${columnWidthFor(colIndex)} px",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.widthIn(min = 50.dp),
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.clickable { updateColumnWidthBy(colIndex, 20) }
                    ) {
                        Icon(
                            painterResource(Res.drawable.plus),
                            contentDescription = "Increase column width",
                            modifier = Modifier.padding(8.dp).size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            TableMenuDivider()

            listOf(
                "Style Cell" to TableStyleScope.CELL,
                "Style Row" to TableStyleScope.ROW,
                "Style Column" to TableStyleScope.COLUMN
            ).forEach { (label, scope) ->
                TableStyleMenuLayer(
                    label = label,
                    style = currentStyleFor(scope),
                    onStyleChange = { applyStyle(scope, it) },
                    onReset = { resetStyle(scope); closeMenu() }
                )
            }

            TableMenuDivider()

            SheetMenuRow(painterResource(Res.drawable.trash), "Delete Row", MaterialTheme.colorScheme.error) {
                deleteRowAt(rowIndex); closeMenu()
            }
            SheetMenuRow(painterResource(Res.drawable.trash), "Delete Column", MaterialTheme.colorScheme.error) {
                deleteColumnAt(colIndex); closeMenu()
            }
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScrollFromBackAndForwardButtons(scrollState)
                .horizontalScroll(scrollState)
        ) {
            Column(modifier = Modifier.padding(horizontal = SidePadding)) {
                Surface(
                    shape = RoundedCornerShape(0.dp),
                    color = Color.Transparent,
                    modifier = Modifier.tableOutline(borderColor)
                ) {
                    Column {
                        rows.forEachIndexed { rowIndex, row ->
                            Row(modifier = Modifier.height(IntrinsicSize.Max).defaultMinSize(minHeight = CellMinHeight)) {
                                row.forEachIndexed { columnIndex, cellValue ->
                                    val isActiveCell = activeRowIndex == rowIndex && activeColIndex == columnIndex
                                    val isHighlighted = isActiveCell && showCellActions
                                    val cellStyle = effectiveStyle(rowIndex, columnIndex)
                                    val cellKey = "$rowIndex:$columnIndex"
                                    val tapAnchor = rememberMenuTapAnchor()

                                    Box(modifier = Modifier.menuTapAnchor(tapAnchor)) {
                                        TableGridCell(
                                            value = cellValue,
                                            cellKey = cellKey,
                                            spans = block.cellSpans[cellKey].orEmpty(),
                                            style = cellStyle,
                                            width = columnWidthFor(columnIndex).dp,
                                            inSelectionMode = inSelectionMode,
                                            isHighlighted = isHighlighted,
                                            borderColor = borderColor,
                                            onValueChange = { updateCell(rowIndex, columnIndex, it) },
                                            onLongPress = { openCellActions(rowIndex, columnIndex) }
                                        )

                                        if (isDesktopPlatform) {
                                            MenuAtTap(tapAnchor, menuWidth = MenuMaxWidth) {
                                                EmberrDesktopMenu(
                                                    expanded = isActiveCell && showCellActions,
                                                    onDismissRequest = { closeMenu() }
                                                ) {
                                                    TableMenuPopupContent(title = "Cell Actions") { cellActionsBody() }
                                                }
                                            }

                                            if (rowIndex == 0) {
                                                val density = LocalDensity.current
                                                var widthDragAccumulator by remember(columnIndex) { mutableStateOf(0f) }
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
                                                                    val newWidth = (columnWidthFor(columnIndex) + wholePixels).coerceIn(40, 600)
                                                                    liveColumnWidths[columnIndex] = newWidth
                                                                }
                                                            },
                                                            onDragStopped = {
                                                                liveColumnWidths[columnIndex]?.let { onUpdateColumnWidth(columnIndex, it) }
                                                            }
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(GutterSize)
                                        .fillMaxHeight()
                                        .defaultMinSize(minHeight = CellMinHeight)
                                        .drawBehind {
                                            val px = TableLineWidth.toPx()
                                            drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), px)
                                        }
                                        .then(
                                            if (rowIndex == 0 && !inSelectionMode) Modifier.clickable { addColumn() } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (rowIndex == 0 && !inSelectionMode) {
                                        Icon(painterResource(Res.drawable.plus), contentDescription = "Add column", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!inSelectionMode) {
                    Row(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { addRow() }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(Res.drawable.plus), contentDescription = "Add row", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(7.dp))
                        Text(text = "New Row", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        EmberrHorizontalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth().padding(start = SidePadding, end = SidePadding, top = 4.dp)
        )
    }

    if (!isDesktopPlatform) {
        EmberrBottomSheet(
            expanded = showCellActions,
            onDismiss = { closeMenu() },
            title = "Cell Actions"
        ) { _ ->
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                cellActionsBody()
                EmberrButtonPrimary(
                    text = "Close",
                    onClick = { closeMenu() },
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun TableMenuPopupContent(title: String, content: @Composable () -> Unit) {
    Box(modifier = Modifier.widthIn(min = MenuMinWidth, max = MenuMaxWidth).padding(vertical = 4.dp)) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 10.dp, top = 4.dp).padding(horizontal = MenuTextInset)
            )
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableGridCell(
    value: String,
    cellKey: String,
    spans: List<InlineSpan>,
    style: TableCellStyle,
    width: Dp,
    inSelectionMode: Boolean,
    isHighlighted: Boolean,
    borderColor: Color,
    onValueChange: (String) -> Unit,
    onLongPress: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val webLinkActions = rememberWebLinkActions()
    val linkHoverState = rememberLinkHoverState()
    val isDarkTheme = LocalAppIsDark.current
    val webLinkTransformation = remember(spans, linkHoverState.hoveredLink, isDarkTheme) {
        WebLinkVisualTransformation(spans, linkHoverState.hoveredLink, isDarkTheme)
    }

    var editorValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val textsSentButNotYetEchoed = remember { mutableListOf<String>() }

    LaunchedEffect(value) {
        if (editorValue.text == value) {
            textsSentButNotYetEchoed.clear()
        } else if (value !in textsSentButNotYetEchoed) {
            textsSentButNotYetEchoed.clear()
            val caret = editorValue.selection.start.coerceAtMost(value.length)
            editorValue = editorValue.copy(text = value, selection = TextRange(caret))
        }
    }

    val backgroundColor = highlightColorForHex(style.backgroundColorHex)?.backgroundFor(isDarkTheme)
        ?: style.backgroundColorHex?.toColorOrNull()
        ?: Color.Transparent
    val textColor = highlightColorForHex(style.textColorHex)?.backgroundFor(!isDarkTheme)
        ?: style.textColorHex?.toColorOrNull()
        ?: MaterialTheme.colorScheme.onBackground
    val textDecoration = when {
        style.isStrikeThrough && style.isUnderlined -> TextDecoration.LineThrough + TextDecoration.Underline
        style.isStrikeThrough -> TextDecoration.LineThrough
        style.isUnderlined -> TextDecoration.Underline
        else -> TextDecoration.None
    }
    val textAlign = when (style.alignment) {
        TextAlignment.LEFT -> TextAlign.Left
        TextAlignment.RIGHT -> TextAlign.Right
        TextAlignment.CENTER -> TextAlign.Center
        else -> TextAlign.Start
    }

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .defaultMinSize(minHeight = CellMinHeight)
            .background(backgroundColor)
            .drawBehind {
                val px = TableLineWidth.toPx()
                drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, size.height), px)
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), px)
            }
            .then(if (isHighlighted) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .pointerInput(inSelectionMode) {
                val textAreaStart = Offset(CellHorizontalPadding.toPx(), CellVerticalPadding.toPx())
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
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
                        val pressedLink = if (isFocused) {
                            null
                        } else {
                            textLayoutResult?.hoveredLinkAt(down.position - textAreaStart, emptySet())
                        }

                        when (pressedLink) {
                            is HoveredLink.Web -> webLinkActions.copyLink(pressedLink.url)
                            is HoveredLink.Email -> webLinkActions.copyLink(pressedLink.email)
                            is HoveredLink.Phone -> webLinkActions.copyLink(pressedLink.phone)
                            else -> onLongPress()
                        }
                    }
                }
            }
            .padding(horizontal = CellHorizontalPadding, vertical = CellVerticalPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = editorValue,
            onValueChange = { newValue ->
                editorValue = newValue
                GlobalEditorState.currentSelection = newValue.selection
                if (newValue.text != value) {
                    textsSentButNotYetEchoed += newValue.text
                    onValueChange(newValue.text)
                }
            },
            enabled = !inSelectionMode,
            visualTransformation = webLinkTransformation,
            onTextLayout = { textLayoutResult = it },
            textStyle = TextStyle(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                fontFamily = if (style.isCode) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (style.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.isItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = textDecoration,
                textAlign = textAlign,
                color = textColor
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .linkHover(linkHoverState) { textLayoutResult }
                .openLinksOnPress(
                    onOpenWebLink = { webLinkActions.openLink(it) },
                    onOpenEmail = { webLinkActions.openEmail(it) },
                    onOpenPhone = { webLinkActions.openPhone(it) },
                    onRightClickLink = { link, at -> linkHoverState.openMenuFor(link, at) },
                    currentTextLayout = { textLayoutResult }
                )
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        GlobalEditorState.currentlyFocusedTableCellKey = cellKey
                        GlobalEditorState.currentSelection = editorValue.selection
                    } else if (GlobalEditorState.currentlyFocusedTableCellKey == cellKey) {
                        GlobalEditorState.currentlyFocusedTableCellKey = null
                        GlobalEditorState.currentSelection = TextRange.Zero
                    }
                }
        )

        LinkHoverCard(linkHoverState)

        LinkContextMenu(linkHoverState)

        // Raw touches only reach the BasicTextField once it's already focused - while unfocused,
        // this overlay claims the tap so the field's own long-press-to-select gesture (which would
        // otherwise fire alongside the gesture above and pop the system copy/paste/autofill toolbar)
        // never gets a chance to start.
        if (!isFocused && !inSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .linkHover(linkHoverState) { textLayoutResult }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { position ->
                            when (val tappedLink = textLayoutResult?.hoveredLinkAt(position, emptySet())) {
                                is HoveredLink.Web -> webLinkActions.openLink(tappedLink.url)
                                is HoveredLink.Email -> webLinkActions.openEmail(tappedLink.email)
                                is HoveredLink.Phone -> webLinkActions.openPhone(tappedLink.phone)
                                else -> {
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                }
                            }
                        })
                    }
            )
        }
    }
}

@Composable
private fun TableStyleMenuLayer(
    label: String,
    style: TableCellStyle,
    onStyleChange: (TableCellStyle) -> Unit,
    onReset: () -> Unit
) {
    DatabaseMenuLayer(
        title = label,
        anchor = { openLayer -> SheetMenuRow(painterResource(Res.drawable.palette), label, onClick = openLayer) }
    ) { _ ->
        TableStyleSheetContent(style = style, onStyleChange = onStyleChange, onReset = onReset)
    }
}

@Composable
private fun TableStyleSheetContent(
    style: TableCellStyle,
    onStyleChange: (TableCellStyle) -> Unit,
    onReset: () -> Unit
) {
    DatabaseMenuSectionLabel(text = "Text color")
    DatabaseStyleColorChoices(
        selectedColorName = colorChoiceNameFor(style.textColorHex),
        colorOf = { databaseTextColorNamed(it.storageName) },
        onColorChosen = { onStyleChange(style.copy(textColorHex = cellHexFor(it))) }
    )

    DatabaseMenuSectionDivider()
    DatabaseMenuSectionLabel(text = "Background color")
    DatabaseStyleColorChoices(
        selectedColorName = colorChoiceNameFor(style.backgroundColorHex),
        colorOf = { databaseBackgroundColorNamed(it.storageName) },
        onColorChosen = { onStyleChange(style.copy(backgroundColorHex = cellHexFor(it))) }
    )

    DatabaseMenuSectionDivider()
    DatabaseMenuSectionLabel(text = "Alignment")
    DatabaseAlignmentOptions(selectedAlignment = style.alignment) { onStyleChange(style.copy(alignment = it)) }

    DatabaseMenuSectionDivider()
    DatabaseMenuSectionLabel(text = "Format")
    TableStyleToggleOption("Bold", Res.drawable.format_bold, style.isBold) { onStyleChange(style.copy(isBold = !style.isBold)) }
    TableStyleToggleOption("Italic", Res.drawable.italic, style.isItalic) { onStyleChange(style.copy(isItalic = !style.isItalic)) }
    TableStyleToggleOption("Underline", Res.drawable.underline, style.isUnderlined) { onStyleChange(style.copy(isUnderlined = !style.isUnderlined)) }
    TableStyleToggleOption("Strikethrough", Res.drawable.text_x, style.isStrikeThrough) { onStyleChange(style.copy(isStrikeThrough = !style.isStrikeThrough)) }
    TableStyleToggleOption("Code", Res.drawable.code, style.isCode) { onStyleChange(style.copy(isCode = !style.isCode)) }

    DatabaseMenuSectionDivider()
    DatabaseMenuSectionLabel(text = "Content type")
    TableContentTypeOption("Link", Res.drawable.link, TableCellContentType.LINK, style, onStyleChange)
    TableContentTypeOption("Phone", Res.drawable.phone, TableCellContentType.PHONE, style, onStyleChange)
    TableContentTypeOption("Email", Res.drawable.mail, TableCellContentType.EMAIL, style, onStyleChange)

    DatabaseMenuSectionDivider()
    DatabaseMenuOption(
        label = "Reset style",
        icon = { DatabaseOptionIcon(Res.drawable.trash, tint = MaterialTheme.colorScheme.error) },
        labelColor = MaterialTheme.colorScheme.error,
        onClick = onReset
    )
}

@Composable
private fun TableStyleToggleOption(label: String, icon: DrawableResource, isOn: Boolean, onClick: () -> Unit) {
    DatabaseMenuOption(label = label, isSelected = isOn, icon = { DatabaseOptionIcon(icon) }, onClick = onClick)
}

@Composable
private fun TableContentTypeOption(
    label: String,
    icon: DrawableResource,
    contentType: TableCellContentType,
    style: TableCellStyle,
    onStyleChange: (TableCellStyle) -> Unit
) {
    val isSelected = style.contentType == contentType
    TableStyleToggleOption(label, icon, isSelected) {
        onStyleChange(style.copy(contentType = if (isSelected) TableCellContentType.NONE else contentType))
    }
}

private fun highlightColorForHex(hex: String?): HighlightColor? =
    HighlightColor.entries.firstOrNull { it.cellBackgroundHex == hex }

private fun colorChoiceNameFor(hex: String?): String? = hex?.let { highlightColorForHex(it)?.storageName ?: it }

private fun cellHexFor(colorName: String?): String? = colorName?.let { HighlightColor.named(it).cellBackgroundHex }

@Composable
private fun StyleSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(
            start = MenuTextInset,
            end = MenuTextInset,
            top = if (isDesktopPlatform) 8.dp else 14.dp,
            bottom = if (isDesktopPlatform) 4.dp else 8.dp
        )
    )
}

@Composable
private fun TableMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = MenuTextInset, vertical = if (isDesktopPlatform) 8.dp else 12.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    )
}

@Composable
private fun SheetMenuRow(
    icon: Painter,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MenuRowInset, vertical = 2.dp)
            .then(if (!isDesktopPlatform) Modifier.heightIn(min = 48.dp) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = MenuRowInnerPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(if (isDesktopPlatform) 18.dp else 20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
    }
}
