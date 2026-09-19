package com.emberr.presentation.shared.editor

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable

private val formatsByMenuLabel = listOf(
    "Bold" to "bold",
    "Italic" to "italic",
    "Strikethrough" to "strike",
    "Underline" to "underline",
    "Highlight" to "highlight"
)

@Composable
actual fun TextFormatContextMenu(
    onToggleFormat: (String) -> Unit,
    content: @Composable () -> Unit
) {
    ContextMenuDataProvider(
        items = {
            val selectionWhenMenuOpened = GlobalEditorState.currentSelection
            val cellKeyWhenMenuOpened = GlobalEditorState.currentlyFocusedTableCellKey
            formatsByMenuLabel.map { (label, format) ->
                ContextMenuItem(label) {
                    GlobalEditorState.currentSelection = selectionWhenMenuOpened
                    GlobalEditorState.currentlyFocusedTableCellKey = cellKeyWhenMenuOpened
                    onToggleFormat(format)
                }
            }
        },
        content = content
    )
}
