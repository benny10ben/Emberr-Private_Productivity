package com.emberr.presentation.shared.editor

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import com.emberr.presentation.shared.components.EmberrDesktopMenu

private val formatsByMenuLabel = listOf(
    "Bold" to "bold",
    "Italic" to "italic",
    "Strikethrough" to "strike",
    "Underline" to "underline"
)

@Composable
actual fun TextFormatContextMenu(
    onToggleFormat: (String) -> Unit,
    content: @Composable () -> Unit
) {
    var isChoosingHighlightColor by remember { mutableStateOf(false) }
    var selectionBeforeChoosing by remember { mutableStateOf(TextRange.Zero) }
    var cellKeyBeforeChoosing by remember { mutableStateOf<String?>(null) }

    Box {
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
                } + ContextMenuItem("Highlight") {
                    selectionBeforeChoosing = selectionWhenMenuOpened
                    cellKeyBeforeChoosing = cellKeyWhenMenuOpened
                    isChoosingHighlightColor = true
                }
            },
            content = content
        )

        EmberrDesktopMenu(
            expanded = isChoosingHighlightColor,
            onDismissRequest = { isChoosingHighlightColor = false },
            modifier = Modifier.width(HighlightColorMenuWidth)
        ) {
            HighlightColorCircles(
                onColorChosen = { formatWithColor ->
                    GlobalEditorState.currentSelection = selectionBeforeChoosing
                    GlobalEditorState.currentlyFocusedTableCellKey = cellKeyBeforeChoosing
                    onToggleFormat(formatWithColor)
                },
                onClose = { isChoosingHighlightColor = false }
            )
        }
    }
}
