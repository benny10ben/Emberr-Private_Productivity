package com.emberr.presentation.shared.editor.blockViews.database

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

internal val DatabaseSelectionColumnWidth = 40.dp
private const val FrozenCellZIndex = 2f

internal fun Modifier.staysInPlaceWhileScrolling(isFrozen: Boolean, scrollState: ScrollState, background: Color): Modifier =
    if (!isFrozen) {
        this
    } else {
        this
            .zIndex(FrozenCellZIndex)
            .graphicsLayer { translationX = (scrollState.value - DatabaseSidePadding.toPx()).coerceAtLeast(0f) }
            .background(background)
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
