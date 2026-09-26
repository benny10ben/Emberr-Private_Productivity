package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.emberr.domain.model.CanvasBlock
import com.emberr.presentation.LocalCanvasFullScreenOverlay
import com.emberr.presentation.LocalImageOverlay
import com.emberr.presentation.shared.canvas.CanvasScreen
import com.emberr.presentation.shared.canvas.CanvasViewModel
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.presentation.shared.editor.GlobalEditorState
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

private val CanvasBlockHeight = 520.dp
private val CanvasBlockCornerRadius = 12.dp
private val CanvasBlockShape = RoundedCornerShape(CanvasBlockCornerRadius)
private const val FullScreenAnimationMillis = 350

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasBlockView(
    block: CanvasBlock,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit
) {
    var isActive by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }
    var isLeavingFullScreen by remember { mutableStateOf(false) }
    var blockBoundsInRoot by remember { mutableStateOf(Rect.Zero) }
    val focusManager = LocalFocusManager.current
    val setFullScreenOverlay = LocalCanvasFullScreenOverlay.current ?: LocalImageOverlay.current
    val canvasViewModel: CanvasViewModel = koinViewModel(key = "canvas:${block.canvasNoteId}")

    val canvas = remember(block.canvasNoteId) {
        movableContentOf { onToggleFullScreen: (() -> Unit)? ->
            CanvasScreen(
                noteId = block.canvasNoteId,
                isEmbedded = true,
                isActive = isActive || isFullScreen,
                isFullScreen = isFullScreen,
                onToggleFullScreen = onToggleFullScreen,
                viewModel = canvasViewModel
            )
        }
    }

    fun stopEditingCanvas() {
        isActive = false
        focusManager.clearFocus()
    }

    fun finishLeavingFullScreen() {
        setFullScreenOverlay?.invoke(null)
        isFullScreen = false
        isLeavingFullScreen = false
        isActive = true
    }

    fun toggleFullScreen() {
        if (isFullScreen) {
            isLeavingFullScreen = !isLeavingFullScreen
            return
        }
        isFullScreen = true
        setFullScreenOverlay?.invoke {
            FullScreenCanvasFrame(
                blockBoundsInRoot = { blockBoundsInRoot },
                isLeaving = isLeavingFullScreen,
                onLeaveRequest = { isLeavingFullScreen = true },
                onLeft = { finishLeavingFullScreen() }
            ) {
                canvas { toggleFullScreen() }
            }
        }
    }

    DisposableEffect(block.id) {
        onDispose {
            if (GlobalEditorState.focusedCanvasBlockId == block.id) GlobalEditorState.focusedCanvasBlockId = null
        }
    }

    KmpBackHandler(enabled = isActive) { stopEditingCanvas() }
    KmpBackHandler(enabled = isFullScreen && !isLeavingFullScreen) { isLeavingFullScreen = true }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CanvasBlockHeight)
            .onGloballyPositioned { coordinates ->
                blockBoundsInRoot = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
            }
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
            .focusGroup()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    GlobalEditorState.pressStartedOnCanvasBlock = true
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
        if (!isFullScreen) {
            canvas(if (setFullScreenOverlay != null) ({ toggleFullScreen() }) else null)

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
}

@Composable
private fun FullScreenCanvasFrame(
    blockBoundsInRoot: () -> Rect,
    isLeaving: Boolean,
    onLeaveRequest: () -> Unit,
    onLeft: () -> Unit,
    content: @Composable () -> Unit
) {
    val expandProgress = remember { Animatable(0f) }
    var framePositionInRoot by remember { mutableStateOf<Offset?>(null) }
    val systemBarInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val backgroundColor = MaterialTheme.colorScheme.background

    LaunchedEffect(isLeaving) {
        expandProgress.animateTo(
            targetValue = if (isLeaving) 0f else 1f,
            animationSpec = tween(FullScreenAnimationMillis, easing = FastOutSlowInEasing)
        )
        if (isLeaving) onLeft()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> framePositionInRoot = coordinates.positionInRoot() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = expandProgress.value }
                .background(backgroundColor)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent()
                    }
                }
        )
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val startBounds = blockBoundsInRoot().translate(-(framePositionInRoot ?: Offset.Zero))
                    val fullScreenBounds = Rect(
                        left = systemBarInsets.getLeft(this, layoutDirection).toFloat(),
                        top = systemBarInsets.getTop(this).toFloat(),
                        right = (constraints.maxWidth - systemBarInsets.getRight(this, layoutDirection)).toFloat(),
                        bottom = (constraints.maxHeight - systemBarInsets.getBottom(this)).toFloat()
                    )
                    val currentBounds = lerp(startBounds, fullScreenBounds, expandProgress.value)
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            currentBounds.width.roundToInt().coerceAtLeast(0),
                            currentBounds.height.roundToInt().coerceAtLeast(0)
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(currentBounds.left.roundToInt(), currentBounds.top.roundToInt())
                    }
                }
                .graphicsLayer {
                    alpha = if (framePositionInRoot == null) 0f else 1f
                    shape = RoundedCornerShape(CanvasBlockCornerRadius.toPx() * (1f - expandProgress.value))
                    clip = true
                }
                .onKeyEvent { event ->
                    val isEscapePressed = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                    if (isEscapePressed) onLeaveRequest()
                    isEscapePressed
                }
        ) {
            content()
        }
    }
}
