package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseRow
import com.emberr.domain.model.GalleryCardSize
import com.emberr.presentation.shared.editor.EditorActions
import kotlin.math.max

private data class CardMetrics(
    val minCellWidth: Dp,
    val padding: Dp,
    val propertySpacing: Dp
)

private fun metricsFor(size: GalleryCardSize): CardMetrics = when (size) {
    GalleryCardSize.SMALL -> CardMetrics(130.dp, 8.dp, 4.dp)
    GalleryCardSize.MEDIUM -> CardMetrics(220.dp, 12.dp, 6.dp)
    GalleryCardSize.LARGE -> CardMetrics(300.dp, 16.dp, 8.dp)
}

private val GalleryCardSpacing = 12.dp

@Composable
fun GalleryView(
    blockId: String,
    cardSize: GalleryCardSize,
    visibleColumns: List<DatabaseColumn>,
    visibleRows: List<DatabaseRow>,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions
) {
    if (visibleRows.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No rows yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    val metrics = remember(cardSize) { metricsFor(cardSize) }

    AdaptiveCardGrid(
        minCellWidth = metrics.minCellWidth,
        spacing = GalleryCardSpacing,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        visibleRows.forEach { row ->
            GalleryCard(
                blockId = blockId,
                row = row,
                columns = visibleColumns,
                metrics = metrics,
                inSelectionMode = inSelectionMode,
                globalTags = globalTags,
                allLinkableNotes = allLinkableNotes,
                actions = actions
            )
        }
    }
}

@Composable
private fun AdaptiveCardGrid(
    minCellWidth: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, 0) {}

        val spacingPx = spacing.roundToPx()
        val minCellPx = minCellWidth.roundToPx()
        val availableWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else minCellPx
        val columns = max(1, (availableWidth + spacingPx) / (minCellPx + spacingPx))
        val cellWidth = (if (columns == 1) availableWidth else (availableWidth - spacingPx * (columns - 1)) / columns)
            .coerceAtLeast(0)
        val cellConstraints = Constraints.fixedWidth(cellWidth)

        val placeables = measurables.map { it.measure(cellConstraints) }
        val rowCount = (placeables.size + columns - 1) / columns
        val rowHeights = IntArray(rowCount)
        placeables.forEachIndexed { index, placeable ->
            val row = index / columns
            rowHeights[row] = max(rowHeights[row], placeable.height)
        }
        val totalHeight = rowHeights.sum() + spacingPx * (rowCount - 1).coerceAtLeast(0)

        layout(availableWidth, totalHeight) {
            var y = 0
            for (row in 0 until rowCount) {
                var x = 0
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index >= placeables.size) break
                    placeables[index].placeRelative(x, y)
                    x += cellWidth + spacingPx
                }
                y += rowHeights[row] + spacingPx
            }
        }
    }
}

@Composable
private fun GalleryCard(
    blockId: String,
    row: DatabaseRow,
    columns: List<DatabaseColumn>,
    metrics: CardMetrics,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions
) {
    val primaryColumn = remember(columns) { columns.firstOrNull() }
    val secondaryColumns = remember(columns, primaryColumn?.id) {
        columns.filter { it.id != primaryColumn?.id }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),

    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(metrics.padding).padding(horizontal = 4.dp, vertical = 2.dp)) {
            primaryColumn?.let { col ->
                GalleryCellValue(
                    blockId = blockId,
                    row = row,
                    column = col,
                    inSelectionMode = inSelectionMode,
                    globalTags = globalTags,
                    allLinkableNotes = allLinkableNotes,
                    actions = actions,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    emptyFallback = "Untitled"
                )
            }
            secondaryColumns.forEach { col ->
                if (!cellIsEmpty(row.cells[col.id], col.type, globalTags, allLinkableNotes)) {
                    Box(modifier = Modifier.padding(top = metrics.propertySpacing)) {
                        GalleryCellValue(
                            blockId = blockId,
                            row = row,
                            column = col,
                            inSelectionMode = inSelectionMode,
                            globalTags = globalTags,
                            allLinkableNotes = allLinkableNotes,
                            actions = actions,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = null,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            emptyFallback = ""
                        )
                    }
                }
            }
        }
    }
}

private fun cellIsEmpty(
    cell: CellData?,
    columnType: ColumnType,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>
): Boolean = when {
    columnType == ColumnType.NOTES -> (cell as? CellData.NoteRelation)?.noteIds.isNullOrEmpty()
    else -> cardCellText(cell, columnType, globalTags, allLinkableNotes).isBlank()
}

@Composable
private fun GalleryCellValue(
    blockId: String,
    row: DatabaseRow,
    column: DatabaseColumn,
    inSelectionMode: Boolean,
    globalTags: List<TagEntity>,
    allLinkableNotes: List<NoteMetadataEntity>,
    actions: EditorActions,
    style: TextStyle,
    fontWeight: FontWeight?,
    color: Color,
    emptyFallback: String
) {
    val cell = row.cells[column.id]
    when {
        column.type == ColumnType.CHECKBOX -> {
            CheckboxCellValue(
                checked = (cell as? CellData.Boolean)?.value ?: false,
                inSelectionMode = inSelectionMode,
                onCheckedChange = { actions.onUpdateDbCell(blockId, row.id, column.id, CellData.Boolean(it)) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        column.type == ColumnType.NOTES && cell is CellData.NoteRelation && cell.noteIds.isNotEmpty() -> {
            val noteId = cell.noteIds.first()
            NoteRelationChip(
                noteId = noteId,
                allLinkableNotes = allLinkableNotes,
                getNoteTitle = actions::getNoteTitle,
                onClick = { actions.onOpenDatabaseNote(blockId, row.id, column.id, noteId) }
            )
        }
        cell is CellData.Text && NOTE_LINK_REGEX.containsMatchIn(cell.value) -> {
            NoteLinkText(
                text = cell.value,
                fontSize = style.fontSize,
                fontWeight = fontWeight,
                color = color,
                maxLines = 3,
                onNoteLinkClick = actions::onNoteLinkClick
            )
        }
        else -> {
            val text = cardCellText(cell, column.type, globalTags, allLinkableNotes).ifBlank { emptyFallback }
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = style,
                    fontWeight = fontWeight,
                    color = color,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
