package com.emberr.presentation.mobile.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.presentation.shared.components.AnimatedFolderIcon
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.file_text
import emberr.shared.generated.resources.folder_plus
import emberr.shared.generated.resources.plus
import emberr.shared.generated.resources.star
import org.jetbrains.compose.resources.painterResource

private val TREE_INDENT_STEP        = 26.dp
private val TREE_ROW_MIN_HEIGHT     = 42.dp
private val TREE_ROW_SPACING        = 2.dp
private val TREE_ICON_SIZE          = 24.dp
private val TREE_INSERT_LINE_HEIGHT = 2.dp
private val TREE_ROW_INNER_PADDING  = 8.dp
private val TREE_GUIDE_COLUMN_START = 12.dp
private val TREE_GUIDE_WIDTH        = 1.5.dp
private val TREE_GUIDE_END_GAP      = 3.dp
private val TREE_GUIDE_CORNER       = 6.dp

private val TreeColorSpec   = tween<Color>(durationMillis = 180, easing = FastOutSlowInEasing)
private val TreeFloatSpec   = tween<Float>(durationMillis = 160, easing = FastOutSlowInEasing)

@Composable
fun MobileTreeFolderRow(
    folder: FolderEntity,
    level: Int,
    guideLines: TreeGuideLines = ROOT_TREE_GUIDE_LINES,
    isExpanded: Boolean,
    isSelected: Boolean,
    noteCount: Int,
    dragState: MobileTreeDragState,
    showAddNoteAction: Boolean,
    onAddNote: () -> Unit,
    showAddSubfolderAction: Boolean = false,
    onAddSubfolder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rowKey = HomeItemKey.forFolder(folder.folderId)
    val isIntoTarget = dragState.isIntoTarget(rowKey)

    MobileTreeRowFrame(
        level = level,
        guideLines = guideLines,
        isDragged = dragState.isDragged(rowKey),
        isSelected = isSelected,
        isIntoTarget = isIntoTarget,
        isInsertBefore = dragState.isInsertBefore(rowKey),
        isInsertAfter = dragState.isInsertAfter(rowKey),
        modifier = modifier
    ) {
        AnimatedFolderIcon(
            isExpanded = isExpanded,
            tint = if (isIntoTarget) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.size(TREE_ICON_SIZE - 1.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (isIntoTarget) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (noteCount > 0) {
            Text(
                text = "$noteCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        if (isSelected) {
            MobileTreeTrailingCheck()
        } else {
            if (showAddSubfolderAction) {
                MobileTreeRowAction(
                    painter = painterResource(Res.drawable.folder_plus),
                    description = "New subfolder in ${folder.name}",
                    onClick = onAddSubfolder
                )
            }
            if (showAddNoteAction) {
                MobileTreeRowAction(
                    painter = painterResource(Res.drawable.plus),
                    description = "New note in ${folder.name}",
                    onClick = onAddNote
                )
            }
        }
    }
}

@Composable
fun MobileTreeNoteRow(
    note: NoteMetadataEntity,
    level: Int,
    guideLines: TreeGuideLines = ROOT_TREE_GUIDE_LINES,
    isSelected: Boolean,
    dragState: MobileTreeDragState,
    modifier: Modifier = Modifier
) {
    val rowKey = HomeItemKey.forNote(note.noteId)

    MobileTreeRowFrame(
        level = level,
        guideLines = guideLines,
        isDragged = dragState.isDragged(rowKey),
        isSelected = isSelected,
        isIntoTarget = false,
        isInsertBefore = dragState.isInsertBefore(rowKey),
        isInsertAfter = dragState.isInsertAfter(rowKey),
        modifier = modifier
    ) {
        Box(Modifier.size(TREE_ICON_SIZE), contentAlignment = Alignment.Center) {
            if (!note.icon.isNullOrEmpty()) {
                Text(text = note.icon, fontSize = 18.sp, textAlign = TextAlign.Center)
            } else {
                Icon(
                    painter = painterResource(Res.drawable.file_text),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(TREE_ICON_SIZE - 1.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = note.title.ifEmpty { "Untitled" },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (note.isFavorite) {
            Icon(
                painter = painterResource(Res.drawable.star),
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 6.dp).size(14.dp)
            )
        }
        if (isSelected) MobileTreeTrailingCheck()
    }
}

@Composable
private fun MobileTreeRowFrame(
    level: Int,
    guideLines: TreeGuideLines,
    isDragged: Boolean,
    isSelected: Boolean,
    isIntoTarget: Boolean,
    isInsertBefore: Boolean,
    isInsertAfter: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val indent = TREE_INDENT_STEP * level

    val backgroundTarget = when {
        isIntoTarget -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        isSelected   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        else         -> Color.Transparent
    }
    val backgroundColor by animateColorAsState(backgroundTarget, TreeColorSpec, label = "tree_row_background")
    val borderAlpha by animateFloatAsState(if (isIntoTarget) 1f else 0f, TreeFloatSpec, label = "tree_row_border")
    val beforeAlpha by animateFloatAsState(if (isInsertBefore) 1f else 0f, TreeFloatSpec, label = "tree_row_insert_before")
    val afterAlpha by animateFloatAsState(if (isInsertAfter) 1f else 0f, TreeFloatSpec, label = "tree_row_insert_after")
    val contentAlpha by animateFloatAsState(if (isDragged) 0f else 1f, TreeFloatSpec, label = "tree_row_content")
    val guideColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentAlpha }
            .drawBehind { drawTreeGuideLines(level, guideLines, guideColor) }
    ) {
        if (beforeAlpha > 0f) {
            MobileTreeInsertLine(
                alpha = beforeAlpha,
                startPadding = indent,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indent, top = TREE_ROW_SPACING, bottom = TREE_ROW_SPACING)
                .clip(shape)
                .background(backgroundColor)
                .then(
                    if (borderAlpha > 0f) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha),
                        shape
                    ) else Modifier
                )
                .heightIn(min = TREE_ROW_MIN_HEIGHT)
                .padding(horizontal = TREE_ROW_INNER_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        if (afterAlpha > 0f) {
            MobileTreeInsertLine(
                alpha = afterAlpha,
                startPadding = indent,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private fun DrawScope.drawTreeGuideLines(level: Int, guideLines: TreeGuideLines, color: Color) {
    if (level == 0) return

    val indentStep = TREE_INDENT_STEP.toPx()
    val guideColumnStart = TREE_GUIDE_COLUMN_START.toPx()
    val lineWidth = TREE_GUIDE_WIDTH.toPx()

    guideLines.ancestorVerticalLines.forEachIndexed { depth, isVisible ->
        if (!isVisible) return@forEachIndexed
        val x = depth * indentStep + guideColumnStart
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round
        )
    }

    val elbowX = (level - 1) * indentStep + guideColumnStart
    val middleY = size.height / 2f
    val rowIconStartX = level * indentStep + TREE_ROW_INNER_PADDING.toPx() - TREE_GUIDE_END_GAP.toPx()
    val cornerRadius = minOf(TREE_GUIDE_CORNER.toPx(), middleY, rowIconStartX - elbowX)

    if (!guideLines.isLastChildOfParent) {
        drawLine(
            color = color,
            start = Offset(elbowX, 0f),
            end = Offset(elbowX, size.height),
            strokeWidth = lineWidth,
            cap = StrokeCap.Round
        )
    }

    val elbowStartY = if (guideLines.isLastChildOfParent) 0f else middleY - cornerRadius
    val elbow = Path().apply {
        moveTo(elbowX, elbowStartY)
        lineTo(elbowX, middleY - cornerRadius)
        quadraticTo(elbowX, middleY, elbowX + cornerRadius, middleY)
        lineTo(rowIconStartX, middleY)
    }
    drawPath(path = elbow, color = color, style = Stroke(width = lineWidth, cap = StrokeCap.Round))
}

@Composable
private fun MobileTreeInsertLine(alpha: Float, startPadding: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startPadding)
            .height(TREE_INSERT_LINE_HEIGHT)
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            .zIndex(10f)
    )
}

@Composable
private fun MobileTreeRowAction(painter: Painter, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun MobileTreeTrailingCheck() {
    Box(
        modifier = Modifier
            .padding(horizontal = 7.dp)
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp)
        )
    }
}
