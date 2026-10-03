package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.emberr.domain.database.DatabaseRow
import com.emberr.domain.database.FormulaValue
import com.emberr.domain.database.dropMovesRow
import com.emberr.domain.database.visibleColumns
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCardSize
import com.emberr.domain.model.DatabaseNumberFormat
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.numberOrNull
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.presentation.shared.editor.blockViews.property.PropertyTagChip
import com.emberr.presentation.shared.editor.blockViews.property.formatPropertyDateRange
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.check
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.io.File
import kotlin.math.roundToInt

private val CardGap = 12.dp
private val CardShape = RoundedCornerShape(10.dp)

internal data class CardDimensions(val width: Dp, val coverHeight: Dp, val titleMaxLines: Int)

internal fun DatabaseCardSize.dimensions(): CardDimensions = when (this) {
    DatabaseCardSize.SMALL -> CardDimensions(width = 130.dp, coverHeight = 72.dp, titleMaxLines = 1)
    DatabaseCardSize.MEDIUM -> CardDimensions(width = 210.dp, coverHeight = 120.dp, titleMaxLines = 2)
    DatabaseCardSize.LARGE -> CardDimensions(width = 320.dp, coverHeight = 190.dp, titleMaxLines = 2)
}

@Composable
internal fun DatabaseGallery(
    block: DatabaseBlock,
    view: DatabaseView,
    rows: List<DatabaseRow>,
    inSelectionMode: Boolean,
    canReorder: Boolean,
    onOpenRow: (String) -> Unit,
    onMoveRow: (rowNoteId: String, nextToRowId: String, isAfter: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = view.cardSize.dimensions()
    val dragState = remember { GalleryDragState() }
    val density = LocalDensity.current
    var galleryOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val autoScroll = rememberVerticalDragAutoScroll(isDragging = dragState.isDragging, pointerInRoot = { dragState.pointerInRoot })
    val shownRowIds = rows.map { it.noteId }

    fun dropDraggedCard() {
        val draggedRowId = dragState.draggedRowNoteId
        val spot = dragState.dropSpot()
        if (draggedRowId != null && spot != null && dropMovesRow(shownRowIds, draggedRowId, spot.nextToRowId, spot.isAfter)) {
            onMoveRow(draggedRowId, spot.nextToRowId, spot.isAfter)
        }
        dragState.reset()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .verticalDragAutoScroll(autoScroll)
            .onGloballyPositioned { galleryOriginInRoot = it.positionInRoot() }
    ) {
        EqualHeightCardGrid(minimumCardWidth = dimensions.width, gap = CardGap, modifier = Modifier.fillMaxWidth()) {
            rows.forEach { row ->
                key(row.noteId) {
                    DatabaseGalleryCard(
                        block = block,
                        view = view,
                        row = row,
                        dimensions = dimensions,
                        inSelectionMode = inSelectionMode,
                        canReorder = canReorder,
                        dragState = dragState,
                        onOpen = { onOpenRow(row.noteId) },
                        onDrop = ::dropDraggedCard
                    )
                }
            }
        }

        val draggedRow = dragState.draggedRowNoteId?.let { rowNoteId -> rows.firstOrNull { it.noteId == rowNoteId } }
        if (draggedRow != null) {
            val spot = dragState.dropSpot()
            val spotBounds = spot?.let { dragState.cardBoundsInRoot[it.nextToRowId] }
            if (spot != null && spotBounds != null && dropMovesRow(shownRowIds, draggedRow.noteId, spot.nextToRowId, spot.isAfter)) {
                val halfGapPx = with(density) { (CardGap / 2).toPx() }
                val lineX = if (spot.isAfter) spotBounds.right + halfGapPx else spotBounds.left - halfGapPx
                DatabaseDropIndicator(
                    offset = Offset(lineX, spotBounds.top) - galleryOriginInRoot,
                    width = DropIndicatorThickness,
                    height = with(density) { spotBounds.height.toDp() }
                )
            }

            val cardOffset = dragState.pointerInRoot - galleryOriginInRoot - dragState.grabOffset
            DatabaseRowCard(
                block = block,
                view = view,
                row = draggedRow,
                dimensions = dimensions,
                inSelectionMode = true,
                onOpen = {},
                modifier = Modifier
                    .zIndex(1f)
                    .offset { IntOffset(cardOffset.x.roundToInt(), cardOffset.y.roundToInt()) }
                    .size(with(density) { dragState.draggedCardSize.width.toDp() }, with(density) { dragState.draggedCardSize.height.toDp() })
                    .shadow(elevation = 8.dp, shape = CardShape)
            )
        }
    }
}

@Stable
private class GalleryDragState {
    var draggedRowNoteId by mutableStateOf<String?>(null)
    var pointerInRoot by mutableStateOf(Offset.Zero)
    var grabOffset by mutableStateOf(Offset.Zero)
    var draggedCardSize by mutableStateOf(IntSize.Zero)
    val cardBoundsInRoot = HashMap<String, Rect>()

    val isDragging: Boolean get() = draggedRowNoteId != null

    fun startDrag(rowNoteId: String, pointer: Offset, grabOffsetInCard: Offset, cardSize: IntSize) {
        draggedRowNoteId = rowNoteId
        pointerInRoot = pointer
        grabOffset = grabOffsetInCard
        draggedCardSize = cardSize
    }

    fun dropSpot(): GalleryDropSpot? {
        val (nearestRowId, nearestBounds) = cardBoundsInRoot.entries
            .minByOrNull { (_, bounds) -> (bounds.center - pointerInRoot).getDistanceSquared() }
            ?.toPair() ?: return null
        return GalleryDropSpot(nextToRowId = nearestRowId, isAfter = pointerInRoot.x > nearestBounds.center.x)
    }

    fun reset() {
        draggedRowNoteId = null
    }
}

private data class GalleryDropSpot(val nextToRowId: String, val isAfter: Boolean)

@Composable
private fun DatabaseGalleryCard(
    block: DatabaseBlock,
    view: DatabaseView,
    row: DatabaseRow,
    dimensions: CardDimensions,
    inSelectionMode: Boolean,
    canReorder: Boolean,
    dragState: GalleryDragState,
    onOpen: () -> Unit,
    onDrop: () -> Unit
) {
    val gestureCoordinates = remember { GestureCoordinates() }
    val latestOnDrop by rememberUpdatedState(onDrop)

    DisposableEffect(row.noteId) {
        onDispose { dragState.cardBoundsInRoot.remove(row.noteId) }
    }

    DatabaseRowCard(
        block = block,
        view = view,
        row = row,
        dimensions = dimensions,
        inSelectionMode = inSelectionMode,
        onOpen = onOpen,
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                gestureCoordinates.coordinates = coordinates
                dragState.cardBoundsInRoot[row.noteId] = coordinates.boundsInRoot()
            }
            .alpha(if (dragState.draggedRowNoteId == row.noteId) DraggedItemSourceAlpha else 1f),
        gestureModifier = Modifier.boardDragGesture(
            key = row.noteId,
            isEnabled = canReorder,
            gestureCoordinates = gestureCoordinates,
            onStart = { pointerInRoot, pointerInCard, cardSize -> dragState.startDrag(row.noteId, pointerInRoot, pointerInCard, cardSize) },
            onMove = { dragState.pointerInRoot = it },
            onDrop = { latestOnDrop() },
            onCancel = { dragState.reset() }
        )
    )
}

@Composable
private fun EqualHeightCardGrid(
    minimumCardWidth: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { cards, constraints ->
        val minimumCardWidthPx = minimumCardWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        val gapPx = gap.roundToPx()
        val cardsPerLine = maxOf(1, (constraints.maxWidth + gapPx) / (minimumCardWidthPx + gapPx))
        val cardWidthPx = (constraints.maxWidth - (cardsPerLine - 1) * gapPx) / cardsPerLine
        val tallestCardHeight = cards.maxOfOrNull { it.maxIntrinsicHeight(cardWidthPx) } ?: 0
        val placedCards = cards.map { it.measure(Constraints.fixed(cardWidthPx, tallestCardHeight)) }
        val lineCount = (placedCards.size + cardsPerLine - 1) / cardsPerLine
        val gridHeight = if (lineCount == 0) 0 else lineCount * tallestCardHeight + (lineCount - 1) * gapPx

        layout(width = constraints.maxWidth, height = gridHeight) {
            placedCards.forEachIndexed { index, card ->
                card.place(
                    x = (index % cardsPerLine) * (cardWidthPx + gapPx),
                    y = (index / cardsPerLine) * (tallestCardHeight + gapPx)
                )
            }
        }
    }
}

@Composable
internal fun DatabaseRowCard(
    block: DatabaseBlock,
    view: DatabaseView,
    row: DatabaseRow,
    dimensions: CardDimensions,
    inSelectionMode: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !inSelectionMode, onClick = onOpen)
            .then(gestureModifier)
    ) {
        if (view.showsCoverImage) {
            DatabaseCardCover(coverImagePath = row.coverImagePath, height = dimensions.coverHeight)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = row.icon
                if (view.showsIcon && icon != null) {
                    Text(text = icon, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = row.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (row.title.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    maxLines = dimensions.titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            block.visibleColumns().forEach { column ->
                val formulaResult = row.formulaResult(column)
                val cell = row.cell(column)
                val numberFormat = block.numberFormats[column.columnKey]
                if (formulaResult != null) {
                    DatabaseCardFormulaValue(result = formulaResult, numberFormat = numberFormat)
                } else if (cell != null && cell.hasValueToShowOnCard()) {
                    DatabaseCardValue(cell = cell, numberFormat = numberFormat)
                }
            }
        }
    }
}

@Composable
private fun DatabaseCardCover(coverImagePath: String?, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        if (coverImagePath != null) {
            val mediaStorageHelper: MediaStorageHelper = koinInject()
            val context = LocalPlatformContext.current
            val absolutePath = remember(coverImagePath) { mediaStorageHelper.getAbsoluteMediaPath(coverImagePath) }
            val request = remember(absolutePath) {
                ImageRequest.Builder(context)
                    .data(File(absolutePath))
                    .memoryCacheKey(absolutePath)
                    .diskCacheKey(absolutePath)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun PropertyBlock.hasValueToShowOnCard(): Boolean = when {
    valueType.holdsTags -> tags.isNotEmpty()
    valueType.holdsCheck -> isChecked
    valueType.holdsDate -> date != null
    else -> text.isNotBlank()
}

@Composable
private fun DatabaseCardFormulaValue(result: FormulaValue, numberFormat: DatabaseNumberFormat?) {
    if (result is FormulaValue.Error || result.displayText.isBlank()) return
    if (result is FormulaValue.NumberValue && numberFormat != null) {
        DatabaseCardNumberValue(number = result.number, numberFormat = numberFormat)
        return
    }
    Text(
        text = result.textToShow(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatabaseCardValue(cell: PropertyBlock, numberFormat: DatabaseNumberFormat?) {
    val valueColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val number = cell.numberOrNull()
    when {
        number != null && numberFormat != null -> DatabaseCardNumberValue(number = number, numberFormat = numberFormat)
        cell.valueType.holdsTags -> {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                cell.tags.forEach { tagName ->
                    PropertyTagChip(tagName = tagName, tagPoolKey = cell.tagPoolKey, textStyle = MaterialTheme.typography.labelSmall)
                }
            }
        }
        cell.valueType.holdsCheck -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = cell.label, style = MaterialTheme.typography.bodyMedium, color = valueColor, maxLines = 1)
            }
        }
        cell.valueType.holdsDate -> if (cell.date != null) {
            Text(text = formatPropertyDateRange(cell.dateRange), style = MaterialTheme.typography.bodyMedium, color = valueColor, maxLines = 1)
        }
        else -> {
            Text(
                text = cell.text.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DatabaseCardNumberValue(number: Double, numberFormat: DatabaseNumberFormat) {
    DatabaseNumberValue(
        number = number,
        format = numberFormat,
        textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        alignment = null,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}
