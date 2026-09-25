package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.CanvasBlock
import com.emberr.presentation.shared.canvas.CanvasScreen
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.editor.GlobalEditorState

private val CanvasBlockHeight = 520.dp
private val CanvasBlockShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasBlockView(
    block: CanvasBlock,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit
) {
    var isActive by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun stopEditingCanvas() {
        isActive = false
        focusManager.clearFocus()
    }

    DisposableEffect(block.id) {
        onDispose {
            if (GlobalEditorState.focusedCanvasBlockId == block.id) GlobalEditorState.focusedCanvasBlockId = null
        }
    }

    KmpBackHandler(enabled = isActive) { stopEditingCanvas() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CanvasBlockHeight)
            .clip(CanvasBlockShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CanvasBlockShape)
            .onFocusChanged { focusState ->
                if (focusState.hasFocus) {
                    GlobalEditorState.focusedCanvasBlockId = block.id
                } else {
                    if (GlobalEditorState.focusedCanvasBlockId == block.id) GlobalEditorState.focusedCanvasBlockId = null
                    isActive = false
                }
            }
            .onKeyEvent { event ->
                val isEscapePressed = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (isActive && isEscapePressed) {
                    stopEditingCanvas()
                    true
                } else {
                    false
                }
            }
    ) {
        CanvasScreen(noteId = block.canvasNoteId, isEmbedded = true, isActive = isActive)

        if (!isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (inSelectionMode) onToggleSelection() else isActive = true },
                        onLongClick = onToggleSelection
                    )
            )
        }
    }
}
