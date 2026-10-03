package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.emberr.domain.database.DatabaseBoardGroup
import com.emberr.domain.database.DatabaseBoardGroupValue
import com.emberr.domain.database.DatabaseRow
import com.emberr.domain.database.boardGroups
import com.emberr.domain.database.calculate
import com.emberr.domain.database.calculationsFor
import com.emberr.domain.database.columnWithKey
import com.emberr.domain.database.groupByColumn
import com.emberr.domain.database.hiddenIn
import com.emberr.domain.database.inManualOrder
import com.emberr.domain.database.manualRowOrderAfterDrop
import com.emberr.domain.database.shownIn
import com.emberr.domain.database.withItemMovedBefore
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCardSize
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseDateGrouping
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.cleanPropertyTagName
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.tagPoolKey
import com.emberr.domain.model.valueTypeOf
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrHorizontalScrollbar
import com.emberr.presentation.shared.components.EmberrTextField
import com.emberr.presentation.shared.components.MenuAtTap
import com.emberr.presentation.shared.components.customEmberrShadow
import com.emberr.presentation.shared.components.menuTapAnchor
import com.emberr.presentation.shared.components.rememberMenuTapAnchor
import com.emberr.presentation.shared.editor.DatabaseBlockEditor
import com.emberr.presentation.shared.editor.blockViews.PropertyTagChip
import com.emberr.presentation.shared.editor.blockViews.PropertyTagColorChoices
import com.emberr.presentation.shared.editor.blockViews.formatPropertyDate
import com.emberr.presentation.shared.editor.blockViews.rememberPropertyTagColor
import com.emberr.presentation.shared.editor.blockViews.rememberPropertyTagColorName
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.chevron_right
import emberr.shared.generated.resources.eye3
import emberr.shared.generated.resources.minimize_2
import emberr.shared.generated.resources.palette
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.square
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private val BoardColumnGap = 12.dp
private val BoardColumnPadding = 6.dp
private val CollapsedColumnWidth = 48.dp
private val BoardEdgeScrollZone = 56.dp
private val VerticalLabelMaxLength = 240.dp
private val BoardColumnShape = RoundedCornerShape(10.dp)
private const val BoardEdgeScrollStepPixels = 14f
private const val DraggedItemSourceAlpha = 0.35f
private val BoardCardGap = 8.dp
private val DropIndicatorThickness = 2.dp
private const val OptionGroupBackgroundAlpha = 0.18f
private const val NeutralGroupBackgroundAlpha = 0.04f
private const val DropTargetShadeAlpha = 0.08f

private fun DatabaseCardSize.boardColumnWidth(): Dp = when (this) {
    DatabaseCardSize.SMALL -> 200.dp
    DatabaseCardSize.MEDIUM -> 260.dp
    DatabaseCardSize.LARGE -> 320.dp
}

@Stable
private class BoardDragState {
    var draggedRowNoteId by mutableStateOf<String?>(null)
    var draggedFromGroupKey by mutableStateOf<String?>(null)
    var draggedColumnKey by mutableStateOf<String?>(null)
    var pointerInRoot by mutableStateOf(Offset.Zero)
    var grabOffset by mutableStateOf(Offset.Zero)
    var draggedItemSize by mutableStateOf(IntSize.Zero)
    val columnBoundsInRoot = mutableStateMapOf<String, Rect>()
    val cardBoundsInRoot = HashMap<String, Rect>()

    val isDraggingCard: Boolean get() = draggedRowNoteId != null
    val isDraggingColumn: Boolean get() = draggedColumnKey != null
    val isDragging: Boolean get() = isDraggingCard || isDraggingColumn

    fun columnUnderPointer(): String? =
        columnBoundsInRoot.entries.firstOrNull { (_, bounds) -> pointerInRoot.x in bounds.left..bounds.right }?.key

    fun startCardDrag(rowNoteId: String, groupKey: String, pointer: Offset, grabOffsetInCard: Offset, cardSize: IntSize) {
        draggedRowNoteId = rowNoteId
        draggedFromGroupKey = groupKey
        pointerInRoot = pointer
        grabOffset = grabOffsetInCard
        draggedItemSize = cardSize
    }

    fun startColumnDrag(groupKey: String, pointer: Offset, grabOffsetInHeader: Offset, headerSize: IntSize) {
        draggedColumnKey = groupKey
        pointerInRoot = pointer
        grabOffset = grabOffsetInHeader
        draggedItemSize = headerSize
    }

    fun reset() {
        draggedRowNoteId = null
        draggedFromGroupKey = null
        draggedColumnKey = null
    }
}

private fun cardBoundsKey(groupKey: String, rowNoteId: String): String = "$groupKey/$rowNoteId"

private class GestureCoordinates {
    var coordinates: LayoutCoordinates? = null
}

private data class CardDropSpot(
    val group: DatabaseBoardGroup,
    val beforeRowId: String?,
    val lastOtherRowId: String?
)

private fun Modifier.boardDragGesture(
    key: Any,
    isEnabled: Boolean,
    gestureCoordinates: GestureCoordinates,
    onStart: (pointerInRoot: Offset, pointerInItem: Offset, itemSize: IntSize) -> Unit,
    onMove: (pointerInRoot: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit
): Modifier = pointerInput(key, isEnabled) {
    if (!isEnabled) return@pointerInput
    val startDrag = { pointerInItem: Offset ->
        gestureCoordinates.coordinates?.let { coordinates ->
            onStart(coordinates.localToRoot(pointerInItem), pointerInItem, coordinates.size)
        }
        Unit
    }
    val followPointer = { change: PointerInputChange, _: Offset ->
        change.consume()
        gestureCoordinates.coordinates?.let { onMove(it.localToRoot(change.position)) }
        Unit
    }
    if (isDesktopPlatform) {
        detectDragGestures(onDragStart = startDrag, onDragEnd = onDrop, onDragCancel = onCancel, onDrag = followPointer)
    } else {
        detectDragGesturesAfterLongPress(onDragStart = startDrag, onDragEnd = onDrop, onDragCancel = onCancel, onDrag = followPointer)
    }
}

@Composable
internal fun DatabaseBoard(
    block: DatabaseBlock,
    view: DatabaseView,
    rows: List<DatabaseRow>,
    inSelectionMode: Boolean,
    isLocked: Boolean,
    editor: DatabaseBlockEditor,
    onOpenRow: (String) -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val blocksEdits = inSelectionMode || isLocked
    val groupColumn = block.groupByColumn(view)
    val valueType = groupColumn?.let { block.valueTypeOf(it) }
    if (groupColumn == null || valueType == null) {
        Text(
            text = "Add a Status, Tags, Checkbox or Date column to group this board by.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            modifier = modifier.padding(vertical = 12.dp)
        )
        return
    }

    val tagPoolKey = groupColumn.tagPoolKey
    val savedOptions by remember(tagPoolKey, valueType) {
        if (valueType.holdsTags && tagPoolKey != null) editor.savedTagsOf(tagPoolKey) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val usesManualOrder = view.sorts.isEmpty()
    val orderedRows = remember(rows, usesManualOrder, view.manualRowOrder) {
        if (usesManualOrder) rows.inManualOrder(view.manualRowOrder) else rows
    }
    val groups = remember(orderedRows, groupColumn, valueType, savedOptions, view.dateGrouping) {
        boardGroups(orderedRows, groupColumn, valueType, savedOptions.map { it.name }, view.dateGrouping)
    }
    val groupsByKey = groups.associateBy { it.key }
    val shownGroups = groups.shownIn(view)
    val hiddenGroups = groups.hiddenIn(view)
    val shownOptionGroups = shownGroups.filter { it.value is DatabaseBoardGroupValue.Option }
    val canReorderColumns = tagPoolKey != null && valueType.holdsTags && !blocksEdits
    val columnLabel = block.labelOf(groupColumn)
    val columnWidth = view.cardSize.boardColumnWidth()
    val cardDimensions = view.cardSize.dimensions().copy(width = columnWidth - BoardColumnPadding * 2)
    val calculationLabel = rememberGroupCalculationLabel(block, view)

    val dragState = remember { BoardDragState() }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var boardOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }

    val latestGroups by rememberUpdatedState(groups)
    val latestShownOptionGroups by rememberUpdatedState(shownOptionGroups)
    val latestOrderedRows by rememberUpdatedState(orderedRows)
    val latestView by rememberUpdatedState(view)
    val latestGroupColumn by rememberUpdatedState(groupColumn)
    val latestTagPoolKey by rememberUpdatedState(tagPoolKey)
    val latestUsesManualOrder by rememberUpdatedState(usesManualOrder)

    fun latestGroupWithKey(groupKey: String?): DatabaseBoardGroup? = latestGroups.firstOrNull { it.key == groupKey }

    fun cardDropSpot(): CardDropSpot? {
        val draggedRowId = dragState.draggedRowNoteId ?: return null
        val targetGroup = latestGroupWithKey(dragState.columnUnderPointer()) ?: return null
        val otherRows = targetGroup.rows.filter { it.noteId != draggedRowId }
        val beforeRow = otherRows.firstOrNull { row ->
            val bounds = dragState.cardBoundsInRoot[cardBoundsKey(targetGroup.key, row.noteId)]
            bounds != null && bounds.center.y > dragState.pointerInRoot.y
        }
        return CardDropSpot(group = targetGroup, beforeRowId = beforeRow?.noteId, lastOtherRowId = otherRows.lastOrNull()?.noteId)
    }

    fun columnDropBeforeKey(): String? {
        val draggedKey = dragState.draggedColumnKey
        return latestShownOptionGroups.filter { it.key != draggedKey }.firstOrNull { group ->
            val bounds = dragState.columnBoundsInRoot[group.key]
            bounds != null && bounds.center.x > dragState.pointerInRoot.x
        }?.key
    }

    fun dropDraggedCard() {
        val rowNoteId = dragState.draggedRowNoteId
        val fromGroup = latestGroupWithKey(dragState.draggedFromGroupKey)
        val spot = cardDropSpot()
        if (rowNoteId != null && fromGroup != null && spot != null) {
            val currentView = latestView
            val newManualRowOrder = if (latestUsesManualOrder) {
                val shownRowIds = latestOrderedRows.map { it.noteId }
                val shownRowIdSet = shownRowIds.toSet()
                val currentOrder = shownRowIds + currentView.manualRowOrder.filterNot { it in shownRowIdSet }
                manualRowOrderAfterDrop(shownRowIds, currentView.manualRowOrder, rowNoteId, spot.beforeRowId, spot.lastOtherRowId)
                    .takeIf { it != currentOrder }
            } else {
                null
            }
            editor.moveCard(block.id, currentView.id, rowNoteId, latestGroupColumn, fromGroup.value, spot.group.value, newManualRowOrder)
        }
        dragState.reset()
    }

    fun dropDraggedColumn() {
        val draggedKey = dragState.draggedColumnKey
        val draggedName = (latestGroupWithKey(draggedKey)?.value as? DatabaseBoardGroupValue.Option)?.name
        val currentTagPoolKey = latestTagPoolKey
        if (draggedName != null && currentTagPoolKey != null) {
            val optionNames = latestGroups.mapNotNull { (it.value as? DatabaseBoardGroupValue.Option)?.name }
            val beforeName = (latestGroupWithKey(columnDropBeforeKey())?.value as? DatabaseBoardGroupValue.Option)?.name
            val anchorName = beforeName ?: run {
                val lastShownName = (latestShownOptionGroups.lastOrNull { it.key != draggedKey }?.value as? DatabaseBoardGroupValue.Option)?.name
                val namesWithoutDragged = optionNames - draggedName
                lastShownName?.let { namesWithoutDragged.getOrNull(namesWithoutDragged.indexOf(it) + 1) }
            }
            val newOrder = optionNames.withItemMovedBefore(draggedName, anchorName)
            if (newOrder != optionNames) editor.reorderGroupOptions(currentTagPoolKey, newOrder)
        }
        dragState.reset()
    }

    LaunchedEffect(dragState.isDragging) {
        val edgeZonePx = with(density) { BoardEdgeScrollZone.toPx() }
        while (dragState.isDragging) {
            val pointerX = dragState.pointerInRoot.x - boardOriginInRoot.x
            when {
                pointerX < edgeZonePx -> scrollState.scrollBy(-BoardEdgeScrollStepPixels)
                pointerX > boardSize.width - edgeZonePx -> scrollState.scrollBy(BoardEdgeScrollStepPixels)
            }
            withFrameNanos { }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                boardOriginInRoot = coordinates.positionInRoot()
                boardSize = coordinates.size
            }
    ) {
        Column {
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(BoardColumnGap),
                verticalAlignment = Alignment.Top
            ) {
                shownGroups.forEach { group ->
                    key(group.key) {
                        val isDropTarget = dragState.isDraggingCard &&
                            dragState.draggedFromGroupKey != group.key &&
                            dragState.columnUnderPointer() == group.key
                        if (group.key in view.collapsedGroupKeys) {
                            DatabaseCollapsedBoardColumn(
                                group = group,
                                columnLabel = columnLabel,
                                tagPoolKey = tagPoolKey,
                                isDropTarget = isDropTarget,
                                inSelectionMode = blocksEdits,
                                dragState = dragState,
                                onExpand = { editor.changeView(block.id, view.id) { it.copy(collapsedGroupKeys = it.collapsedGroupKeys - group.key) } }
                            )
                        } else {
                            DatabaseBoardColumn(
                                block = block,
                                view = view,
                                group = group,
                                groupColumn = groupColumn,
                                columnLabel = columnLabel,
                                tagPoolKey = tagPoolKey,
                                width = columnWidth,
                                cardDimensions = cardDimensions,
                                calculationLabel = calculationLabel(group.rows),
                                isDropTarget = isDropTarget,
                                canBeDragged = canReorderColumns && group.value is DatabaseBoardGroupValue.Option,
                                inSelectionMode = blocksEdits,
                                canOpenCards = !inSelectionMode,
                                dragState = dragState,
                                editor = editor,
                                onOpenRow = onOpenRow,
                                onDropCard = ::dropDraggedCard,
                                onDropColumn = ::dropDraggedColumn,
                                runAfterKeyboardCloses = runAfterKeyboardCloses
                            )
                        }
                    }
                }

                if (valueType.holdsTags && tagPoolKey != null && !blocksEdits) {
                    DatabaseAddGroupButton(
                        existingNames = groups.mapNotNull { (it.value as? DatabaseBoardGroupValue.Option)?.name },
                        onAdd = { name -> editor.addGroupOption(tagPoolKey, name) },
                        runAfterKeyboardCloses = runAfterKeyboardCloses
                    )
                }

                if (hiddenGroups.isNotEmpty()) {
                    DatabaseHiddenGroupsColumn(
                        hiddenGroups = hiddenGroups,
                        columnLabel = columnLabel,
                        tagPoolKey = tagPoolKey,
                        width = columnWidth,
                        inSelectionMode = blocksEdits,
                        onShowGroup = { groupKey ->
                            editor.changeView(block.id, view.id) { it.copy(hiddenGroupKeys = it.hiddenGroupKeys - groupKey) }
                        }
                    )
                }
            }
            EmberrHorizontalScrollbar(
                scrollState = scrollState,
                modifier = Modifier.fillMaxWidth().padding(start = BoardColumnPadding, end = BoardColumnPadding, top = 4.dp),
                showsWhenScrollbarsAreOff = true
            )
        }

        if (dragState.isDraggingCard) {
            val spot = cardDropSpot()
            val movesToAnotherColumn = spot != null && spot.group.key != dragState.draggedFromGroupKey
            if (spot != null && (usesManualOrder || movesToAnotherColumn) && spot.group.key !in view.collapsedGroupKeys) {
                val columnBounds = dragState.columnBoundsInRoot[spot.group.key]
                val beforeBounds = spot.beforeRowId?.let { dragState.cardBoundsInRoot[cardBoundsKey(spot.group.key, it)] }
                val lastBounds = spot.lastOtherRowId?.let { dragState.cardBoundsInRoot[cardBoundsKey(spot.group.key, it)] }
                val halfGapPx = with(density) { (BoardCardGap / 2).toPx() }
                val lineY = beforeBounds?.let { it.top - halfGapPx } ?: lastBounds?.let { it.bottom + halfGapPx }
                if (columnBounds != null && lineY != null) {
                    DatabaseDropIndicator(
                        offset = Offset(columnBounds.left, lineY) - boardOriginInRoot,
                        width = with(density) { columnBounds.width.toDp() },
                        height = DropIndicatorThickness
                    )
                }
            }
        }

        if (dragState.isDraggingColumn) {
            val beforeBounds = columnDropBeforeKey()?.let { dragState.columnBoundsInRoot[it] }
            val lastBounds = shownOptionGroups.lastOrNull { it.key != dragState.draggedColumnKey }
                ?.let { dragState.columnBoundsInRoot[it.key] }
            val halfGapPx = with(density) { (BoardColumnGap / 2).toPx() }
            val lineX = beforeBounds?.let { it.left - halfGapPx } ?: lastBounds?.let { it.right + halfGapPx }
            if (lineX != null) {
                DatabaseDropIndicator(
                    offset = Offset(lineX, boardOriginInRoot.y) - boardOriginInRoot,
                    width = DropIndicatorThickness,
                    height = with(density) { boardSize.height.toDp() }
                )
            }
        }

        val draggedRow = dragState.draggedRowNoteId?.let { rowNoteId -> orderedRows.firstOrNull { it.noteId == rowNoteId } }
        if (draggedRow != null) {
            val cardOffset = dragState.pointerInRoot - boardOriginInRoot - dragState.grabOffset
            DatabaseRowCard(
                block = block,
                view = view,
                row = draggedRow,
                dimensions = cardDimensions,
                inSelectionMode = true,
                onOpen = {},
                modifier = Modifier
                    .zIndex(1f)
                    .offset { IntOffset(cardOffset.x.roundToInt(), cardOffset.y.roundToInt()) }
                    .width(with(density) { dragState.draggedItemSize.width.toDp() })
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(10.dp))
            )
        }

        val draggedColumnGroup = dragState.draggedColumnKey?.let(groupsByKey::get)
        if (draggedColumnGroup != null) {
            val headerOffset = dragState.pointerInRoot - boardOriginInRoot - dragState.grabOffset
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .offset { IntOffset(headerOffset.x.roundToInt(), headerOffset.y.roundToInt()) }
                    .width(with(density) { dragState.draggedItemSize.width.toDp() })
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                DatabaseBoardGroupLabel(value = draggedColumnGroup.value, columnLabel = columnLabel, tagPoolKey = tagPoolKey)
            }
        }
    }
}

@Composable
private fun rememberBoardGroupBackground(value: DatabaseBoardGroupValue, tagPoolKey: String?): Color =
    if (value is DatabaseBoardGroupValue.Option) {
        rememberPropertyTagColor(tagPoolKey, value.name).copy(alpha = OptionGroupBackgroundAlpha)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = NeutralGroupBackgroundAlpha)
    }

@Composable
private fun DatabaseDropIndicator(offset: Offset, width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .zIndex(1f)
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(width = width, height = height)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun rememberGroupCalculationLabel(block: DatabaseBlock, view: DatabaseView): (List<DatabaseRow>) -> String? {
    val calculationColumn = block.columnWithKey(view.groupCalculationColumnKey)
    val calculationValueType = calculationColumn?.let { block.valueTypeOf(it) }
    val calculation = view.groupCalculation?.takeIf { calculationValueType != null && it in calculationsFor(calculationValueType) }
    val numberFormat = calculationColumn?.let { block.numberFormats[it.columnKey] }
    return remember(calculationColumn, calculationValueType, calculation, numberFormat) {
        { groupRows ->
            if (calculationColumn == null || calculationValueType == null || calculation == null) {
                null
            } else {
                "${calculation.shortLabel} ${calculate(groupRows, calculationColumn, calculationValueType, calculation).displayTextIn(numberFormat)}"
            }
        }
    }
}

@Composable
private fun DatabaseBoardColumn(
    block: DatabaseBlock,
    view: DatabaseView,
    group: DatabaseBoardGroup,
    groupColumn: DatabaseColumnTarget,
    columnLabel: String,
    tagPoolKey: String?,
    width: Dp,
    cardDimensions: CardDimensions,
    calculationLabel: String?,
    isDropTarget: Boolean,
    canBeDragged: Boolean,
    inSelectionMode: Boolean,
    canOpenCards: Boolean,
    dragState: BoardDragState,
    editor: DatabaseBlockEditor,
    onOpenRow: (String) -> Unit,
    onDropCard: () -> Unit,
    onDropColumn: () -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    DisposableEffect(group.key) {
        onDispose { dragState.columnBoundsInRoot.remove(group.key) }
    }
    val isBeingDragged = dragState.draggedColumnKey == group.key

    Column(
        modifier = Modifier
            .width(width)
            .onGloballyPositioned { dragState.columnBoundsInRoot[group.key] = it.boundsInRoot() }
            .alpha(if (isBeingDragged) DraggedItemSourceAlpha else 1f)
            .background(rememberBoardGroupBackground(group.value, tagPoolKey), BoardColumnShape)
            .background(if (isDropTarget) MaterialTheme.colorScheme.onSurface.copy(alpha = DropTargetShadeAlpha) else Color.Transparent, BoardColumnShape)
            .padding(BoardColumnPadding),
        verticalArrangement = Arrangement.spacedBy(BoardCardGap)
    ) {
        DatabaseBoardColumnHeader(
            group = group,
            columnLabel = columnLabel,
            tagPoolKey = tagPoolKey,
            calculationLabel = calculationLabel,
            canBeDragged = canBeDragged,
            inSelectionMode = inSelectionMode,
            dragState = dragState,
            onCollapse = { editor.changeView(block.id, view.id) { it.copy(collapsedGroupKeys = it.collapsedGroupKeys + group.key) } },
            onHide = { editor.changeView(block.id, view.id) { it.copy(hiddenGroupKeys = it.hiddenGroupKeys + group.key) } },
            onColorChosen = { optionName, colorName -> tagPoolKey?.let { editor.setGroupOptionColor(it, optionName, colorName) } },
            onDrop = onDropColumn,
            runAfterKeyboardCloses = runAfterKeyboardCloses
        )
        group.rows.forEach { row ->
            key(row.noteId) {
                DatabaseBoardCard(
                    block = block,
                    view = view,
                    row = row,
                    group = group,
                    cardDimensions = cardDimensions,
                    inSelectionMode = inSelectionMode,
                    canOpen = canOpenCards,
                    dragState = dragState,
                    onOpen = { editor.openRow(row.noteId, onOpenRow) },
                    onDrop = onDropCard
                )
            }
        }
        if (!inSelectionMode) {
            DatabaseAddRowButton(onClick = { editor.addRowInGroup(block.id, groupColumn, group.value) })
        }
    }
}

@Composable
private fun DatabaseBoardCard(
    block: DatabaseBlock,
    view: DatabaseView,
    row: DatabaseRow,
    group: DatabaseBoardGroup,
    cardDimensions: CardDimensions,
    inSelectionMode: Boolean,
    canOpen: Boolean,
    dragState: BoardDragState,
    onOpen: () -> Unit,
    onDrop: () -> Unit
) {
    val gestureCoordinates = remember { GestureCoordinates() }
    val latestOnDrop by rememberUpdatedState(onDrop)
    val boundsKey = cardBoundsKey(group.key, row.noteId)
    val isBeingDragged = dragState.draggedRowNoteId == row.noteId && dragState.draggedFromGroupKey == group.key

    DisposableEffect(boundsKey) {
        onDispose { dragState.cardBoundsInRoot.remove(boundsKey) }
    }

    DatabaseRowCard(
        block = block,
        view = view,
        row = row,
        dimensions = cardDimensions,
        inSelectionMode = !canOpen,
        onOpen = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                gestureCoordinates.coordinates = coordinates
                dragState.cardBoundsInRoot[boundsKey] = coordinates.boundsInRoot()
            }
            .alpha(if (isBeingDragged) DraggedItemSourceAlpha else 1f)
            .customEmberrShadow(RoundedCornerShape(10.dp)),
        gestureModifier = Modifier.boardDragGesture(
            key = boundsKey,
            isEnabled = !inSelectionMode,
            gestureCoordinates = gestureCoordinates,
            onStart = { pointerInRoot, pointerInCard, cardSize ->
                dragState.startCardDrag(row.noteId, group.key, pointerInRoot, pointerInCard, cardSize)
            },
            onMove = { dragState.pointerInRoot = it },
            onDrop = { latestOnDrop() },
            onCancel = { dragState.reset() }
        )
    )
}

@Composable
private fun DatabaseBoardColumnHeader(
    group: DatabaseBoardGroup,
    columnLabel: String,
    tagPoolKey: String?,
    calculationLabel: String?,
    canBeDragged: Boolean,
    inSelectionMode: Boolean,
    dragState: BoardDragState,
    onCollapse: () -> Unit,
    onHide: () -> Unit,
    onColorChosen: (optionName: String, colorName: String?) -> Unit,
    onDrop: () -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val labelText = group.value.labelText(columnLabel)
    val gestureCoordinates = remember { GestureCoordinates() }
    val latestOnDrop by rememberUpdatedState(onDrop)
    val optionName = (group.value as? DatabaseBoardGroupValue.Option)?.name
    val tapAnchor = rememberMenuTapAnchor()

    Box(modifier = Modifier.menuTapAnchor(tapAnchor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { gestureCoordinates.coordinates = it }
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !inSelectionMode) { runAfterKeyboardCloses { showMenu = true } }
                .boardDragGesture(
                    key = group.key,
                    isEnabled = canBeDragged,
                    gestureCoordinates = gestureCoordinates,
                    onStart = { pointerInRoot, pointerInHeader, headerSize ->
                        dragState.startColumnDrag(group.key, pointerInRoot, pointerInHeader, headerSize)
                    },
                    onMove = { dragState.pointerInRoot = it },
                    onDrop = { latestOnDrop() },
                    onCancel = { dragState.reset() }
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f, fill = false)) {
                DatabaseBoardGroupLabel(value = group.value, columnLabel = columnLabel, tagPoolKey = tagPoolKey)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = group.rows.size.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (calculationLabel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = calculationLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        MenuAtTap(tapAnchor, menuWidth = DesktopDatabaseMenuMaxWidth) {
            DatabaseMenu(
                expanded = showMenu,
                title = labelText,
                onDismiss = { showMenu = false }
            ) { closeAnd ->
                if (optionName != null && tagPoolKey != null) {
                    DatabaseMenuLayer(
                        title = "Color",
                        anchor = { openLayer ->
                            DatabaseMenuOption(
                                label = "Color",
                                icon = { DatabaseOptionIcon(Res.drawable.palette) },
                                onClick = openLayer
                            )
                        }
                    ) { _ ->
                        DatabaseMenuContent {
                            PropertyTagColorChoices(
                                tagName = optionName,
                                selectedColorName = rememberPropertyTagColorName(tagPoolKey, optionName),
                                onColorChosen = { colorName -> onColorChosen(optionName, colorName) },
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
                DatabaseMenuOption(
                    label = "Collapse group",
                    icon = { DatabaseOptionIcon(Res.drawable.minimize_2) },
                    onClick = { closeAnd(onCollapse) }
                )
                DatabaseMenuOption(
                    label = "Hide group",
                    icon = { DatabaseOptionIcon(Res.drawable.eye3) },
                    onClick = { closeAnd(onHide) }
                )
            }
        }
    }
}

@Composable
private fun DatabaseBoardGroupLabel(value: DatabaseBoardGroupValue, columnLabel: String, tagPoolKey: String?) {
    when (value) {
        is DatabaseBoardGroupValue.Option -> PropertyTagChip(
            tagName = value.name,
            tagPoolKey = tagPoolKey,
            textStyle = MaterialTheme.typography.bodyMedium
        )
        is DatabaseBoardGroupValue.Checkbox -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(if (value.isChecked) Res.drawable.check_square else Res.drawable.square),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = value.labelText(columnLabel), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        else -> Text(
            text = value.labelText(columnLabel),
            style = MaterialTheme.typography.bodyLarge,
            color = if (value is DatabaseBoardGroupValue.NoValue) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DatabaseCollapsedBoardColumn(
    group: DatabaseBoardGroup,
    columnLabel: String,
    tagPoolKey: String?,
    isDropTarget: Boolean,
    inSelectionMode: Boolean,
    dragState: BoardDragState,
    onExpand: () -> Unit
) {
    DisposableEffect(group.key) {
        onDispose { dragState.columnBoundsInRoot.remove(group.key) }
    }

    Column(
        modifier = Modifier
            .width(CollapsedColumnWidth)
            .onGloballyPositioned { dragState.columnBoundsInRoot[group.key] = it.boundsInRoot() }
            .clip(BoardColumnShape)
            .background(rememberBoardGroupBackground(group.value, tagPoolKey))
            .background(if (isDropTarget) MaterialTheme.colorScheme.onSurface.copy(alpha = DropTargetShadeAlpha) else Color.Transparent)
            .clickable(enabled = !inSelectionMode, onClick = onExpand)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.chevron_right),
            contentDescription = "Expand group",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = group.rows.size.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = group.value.labelText(columnLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.verticalText()
        )
    }
}

private fun Modifier.verticalText(): Modifier = this
    .layout { measurable, _ ->
        val placeable = measurable.measure(Constraints(maxWidth = VerticalLabelMaxLength.roundToPx()))
        layout(width = placeable.height, height = placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2)
            )
        }
    }
    .rotate(90f)

@Composable
private fun DatabaseHiddenGroupsColumn(
    hiddenGroups: List<DatabaseBoardGroup>,
    columnLabel: String,
    tagPoolKey: String?,
    width: Dp,
    inSelectionMode: Boolean,
    onShowGroup: (String) -> Unit
) {
    Column(
        modifier = Modifier.width(width).padding(BoardColumnPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Hidden groups",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
        hiddenGroups.forEach { group ->
            key(group.key) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !inSelectionMode) { onShowGroup(group.key) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        DatabaseBoardGroupLabel(value = group.value, columnLabel = columnLabel, tagPoolKey = tagPoolKey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = group.rows.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun DatabaseAddGroupButton(
    existingNames: List<String>,
    onAdd: (String) -> Unit,
    runAfterKeyboardCloses: (() -> Unit) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .padding(top = BoardColumnPadding)
                .clip(RoundedCornerShape(8.dp))
                .clickable { runAfterKeyboardCloses { showMenu = true } }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Res.drawable.plus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Add a group", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline, maxLines = 1)
        }

        DatabaseMenu(expanded = showMenu, title = "New group", onDismiss = { showMenu = false }, showsCloseButton = false) { closeAnd ->
            DatabaseNewGroupPage(
                existingNames = existingNames,
                onCancel = { closeAnd { } },
                onAdd = { name -> closeAnd { onAdd(name) } }
            )
        }
    }
}

@Composable
private fun DatabaseNewGroupPage(existingNames: List<String>, onCancel: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val cleanedName = cleanPropertyTagName(name)
    val isNameTaken = existingNames.any { it.equals(cleanedName, ignoreCase = true) }
    val addGroup = { if (cleanedName.isNotEmpty() && !isNameTaken) onAdd(cleanedName) }

    DatabaseMenuContent {
        EmberrTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Group name",
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            onSubmit = addGroup
        )
        if (isNameTaken) {
            Text(
                text = "A group with this name already exists",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
    }
    DatabaseMenuButtons(cancelText = "Cancel", onCancel = onCancel, confirmText = "Add", onConfirm = addGroup)
}

internal fun DatabaseBoardGroupValue.labelText(columnLabel: String): String = when (this) {
    is DatabaseBoardGroupValue.Option -> name
    is DatabaseBoardGroupValue.Checkbox -> if (isChecked) "Checked" else "Unchecked"
    is DatabaseBoardGroupValue.DatePeriod -> when (grouping) {
        DatabaseDateGrouping.DAY -> formatPropertyDate(start)
        DatabaseDateGrouping.WEEK -> "Week of ${formatPropertyDate(start)}"
        DatabaseDateGrouping.MONTH -> "${start.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${start.year}"
        DatabaseDateGrouping.YEAR -> start.year.toString()
    }
    DatabaseBoardGroupValue.NoValue -> "No $columnLabel"
}
