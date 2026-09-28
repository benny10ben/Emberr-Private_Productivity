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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.emberr.domain.database.DatabaseRow
import com.emberr.domain.database.visibleColumns
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCardSize
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.presentation.shared.editor.blockViews.PropertyTagChip
import com.emberr.presentation.shared.editor.blockViews.formatPropertyDate
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.check
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.io.File

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
    onOpenRow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = view.cardSize.dimensions()

    EqualHeightCardGrid(cardWidth = dimensions.width, gap = CardGap, modifier = modifier.fillMaxWidth()) {
        rows.forEach { row ->
            key(row.noteId) {
                DatabaseRowCard(
                    block = block,
                    view = view,
                    row = row,
                    dimensions = dimensions,
                    inSelectionMode = inSelectionMode,
                    onOpen = { onOpenRow(row.noteId) }
                )
            }
        }
    }
}

@Composable
private fun EqualHeightCardGrid(
    cardWidth: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { cards, constraints ->
        val cardWidthPx = cardWidth.roundToPx().coerceAtMost(constraints.maxWidth)
        val gapPx = gap.roundToPx()
        val tallestCardHeight = cards.maxOfOrNull { it.maxIntrinsicHeight(cardWidthPx) } ?: 0
        val placedCards = cards.map { it.measure(Constraints.fixed(cardWidthPx, tallestCardHeight)) }
        val cardsPerLine = maxOf(1, (constraints.maxWidth + gapPx) / (cardWidthPx + gapPx))
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
            block.visibleColumns()
                .mapNotNull { column -> row.cell(column) }
                .filter { it.hasValueToShowOnCard() }
                .forEach { cell -> DatabaseCardValue(cell = cell) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatabaseCardValue(cell: PropertyBlock) {
    val valueColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    when {
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
        cell.valueType.holdsDate -> cell.date?.let { date ->
            Text(text = formatPropertyDate(date), style = MaterialTheme.typography.bodyMedium, color = valueColor, maxLines = 1)
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
