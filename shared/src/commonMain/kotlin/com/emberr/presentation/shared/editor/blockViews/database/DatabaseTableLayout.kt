package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

internal val DatabaseSelectionColumnWidth = 40.dp
internal val DatabaseTableBorderWidth = 0.6.dp
private const val FrozenCellZIndex = 2f

internal fun Modifier.staysInPlaceWhileScrolling(
    isFrozen: Boolean,
    scrollState: ScrollState,
    background: Color,
    leftBorderColor: Color? = null
): Modifier =
    if (!isFrozen) {
        this
    } else {
        this
            .zIndex(FrozenCellZIndex)
            .graphicsLayer { translationX = (scrollState.value - DatabaseSidePadding.toPx()).coerceAtLeast(0f) }
            .background(background)
            .then(if (leftBorderColor == null) Modifier else Modifier.leftBorderOnceScrolled(scrollState, leftBorderColor))
    }

private fun Modifier.leftBorderOnceScrolled(scrollState: ScrollState, color: Color): Modifier = drawWithContent {
    drawContent()
    if (scrollState.value <= DatabaseSidePadding.toPx()) return@drawWithContent
    val lineWidth = DatabaseTableBorderWidth.toPx()
    drawLine(color, Offset(lineWidth / 2, 0f), Offset(lineWidth / 2, size.height), lineWidth)
}

@Stable
internal class DatabaseRowSelection {
    var selectedRowIds by mutableStateOf(emptySet<String>())
        private set

    val isSelecting: Boolean get() = selectedRowIds.isNotEmpty()

    fun isSelected(rowNoteId: String): Boolean = rowNoteId in selectedRowIds

    fun select(rowNoteId: String) {
        selectedRowIds = selectedRowIds + rowNoteId
    }

    fun toggle(rowNoteId: String) {
        selectedRowIds = if (rowNoteId in selectedRowIds) selectedRowIds - rowNoteId else selectedRowIds + rowNoteId
    }

    fun keepOnly(rowNoteIds: List<String>) {
        val stillShown = selectedRowIds.intersect(rowNoteIds.toSet())
        if (stillShown.size != selectedRowIds.size) selectedRowIds = stillShown
    }

    fun selectAll(rowNoteIds: List<String>) {
        selectedRowIds = rowNoteIds.toSet()
    }

    fun clear() {
        selectedRowIds = emptySet()
    }
}
