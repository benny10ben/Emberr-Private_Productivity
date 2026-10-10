package com.emberr.presentation.shared.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.CalloutBlock
import com.emberr.domain.model.CalloutType
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.editor.blockViews.backgroundColor

data class CalloutFrame(
    val calloutType: CalloutType,
    val displayedIndentationLevel: Int,
    val nestingDepth: Int,
    val isFirstRow: Boolean,
    val isLastRow: Boolean
)

private val CalloutFrameSideInset = 20.dp
private val CalloutFrameIndentStep = 28.dp
private val CalloutFrameCornerRadius = 12.dp
private val CalloutFrameEndGap = 4.dp
private val CalloutContentSidePadding = 24.dp
private val CalloutContentEdgePadding = 16.dp
private val CalloutHeaderToBodyGap = 10.dp

fun calloutFramesByRow(blocks: List<NoteBlock>): List<List<CalloutFrame>> {
    val framesByRow = List(blocks.size) { mutableListOf<CalloutFrame>() }
    blocks.forEachIndexed { firstRow, block ->
        if (block !is CalloutBlock) return@forEachIndexed
        var lastRow = firstRow
        while (lastRow + 1 < blocks.size && blocks[lastRow + 1].indentationLevel > block.indentationLevel) lastRow++
        val enclosingCalloutCount = framesByRow[firstRow].size
        for (row in firstRow..lastRow) {
            framesByRow[row] += CalloutFrame(
                calloutType = block.calloutType,
                displayedIndentationLevel = block.indentationLevel - enclosingCalloutCount,
                nestingDepth = enclosingCalloutCount,
                isFirstRow = row == firstRow,
                isLastRow = row == lastRow
            )
        }
    }
    return framesByRow
}

fun displayedIndentationLevelOf(block: NoteBlock, frames: List<CalloutFrame>): Int =
    block.indentationLevel - frames.count { !it.isFirstRow }

fun Modifier.drawCalloutFrames(frames: List<CalloutFrame>, surfaceColor: Color): Modifier {
    if (frames.isEmpty()) return this
    val sideInset = CalloutFrameSideInset + if (isDesktopPlatform) 16.dp else 0.dp
    return drawBehind {
        frames.forEach { frame ->
            val nestingInset = CalloutContentSidePadding * frame.nestingDepth
            val left = (sideInset + CalloutFrameIndentStep * frame.displayedIndentationLevel + nestingInset).toPx()
            val right = size.width - (sideInset + nestingInset).toPx()
            val top = if (frame.isFirstRow) CalloutFrameEndGap.toPx() else 0f
            val bottom = if (frame.isLastRow) size.height - CalloutFrameEndGap.toPx() else size.height
            if (right <= left || bottom <= top) return@forEach
            val topCorner = if (frame.isFirstRow) CornerRadius(CalloutFrameCornerRadius.toPx()) else CornerRadius.Zero
            val bottomCorner = if (frame.isLastRow) CornerRadius(CalloutFrameCornerRadius.toPx()) else CornerRadius.Zero
            val framePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        topLeftCornerRadius = topCorner,
                        topRightCornerRadius = topCorner,
                        bottomRightCornerRadius = bottomCorner,
                        bottomLeftCornerRadius = bottomCorner
                    )
                )
            }
            drawPath(framePath, frame.calloutType.backgroundColor(surfaceColor))
        }
    }
}

fun Modifier.calloutContentPadding(frames: List<CalloutFrame>): Modifier {
    if (frames.isEmpty()) return this
    val isHeaderAboveABody = frames.any { it.isFirstRow && !it.isLastRow }
    return padding(
        start = CalloutContentSidePadding * frames.size,
        end = CalloutContentSidePadding * frames.size,
        top = if (frames.any { it.isFirstRow }) CalloutContentEdgePadding else 0.dp,
        bottom = when {
            frames.any { it.isLastRow } -> CalloutContentEdgePadding
            isHeaderAboveABody -> CalloutHeaderToBodyGap
            else -> 0.dp
        }
    )
}
