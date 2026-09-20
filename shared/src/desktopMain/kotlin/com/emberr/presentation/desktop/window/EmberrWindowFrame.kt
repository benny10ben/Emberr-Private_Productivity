package com.emberr.presentation.desktop.window

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import java.awt.Cursor
import javax.swing.SwingUtilities
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures

private val AmbientShadowBlur = 7.dp
private val AmbientShadowDrop = 4.dp
private val ContactShadowBlur = 2.dp
private val ContactShadowDrop = 1.dp
private val CloseHoverColor = Color(0xFFE04B4B)
private val TitleBarRevealBand = 22.dp
private const val TitleBarRevealMillis = 150
private const val DoubleTapTimeoutMillis = 500L
private val DoubleTapSlop = 16.dp
private val DragThreshold = 4.dp

enum class WindowFrameSize {
    Regular,
    Compact
}

private val WindowFrameSize.shadowMargin: Dp
    get() = if (this == WindowFrameSize.Compact) 10.dp else 18.dp

private val WindowFrameSize.cornerRadius: Dp
    get() = if (this == WindowFrameSize.Compact) 10.dp else 12.dp

private val WindowFrameSize.titleBarHeight: Dp
    get() = if (this == WindowFrameSize.Compact) 32.dp else 40.dp

private val WindowFrameSize.resizeBand: Dp
    get() = if (this == WindowFrameSize.Compact) 10.dp else 16.dp

private val WindowFrameSize.windowButtonSize: Dp
    get() = if (this == WindowFrameSize.Compact) 22.dp else 26.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowScope.EmberrWindowFrame(
    windowState: WindowState,
    isEnabled: Boolean,
    windowTitle: String,
    frameSize: WindowFrameSize = WindowFrameSize.Regular,
    autoHideTitleBar: Boolean = true,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!isEnabled) {
        content()
        return
    }

    val isMaximized = windowState.placement == WindowPlacement.Maximized
    val frameShape: Shape = if (isMaximized) RectangleShape else RoundedCornerShape(frameSize.cornerRadius)

    val isWindowFocused = LocalWindowInfo.current.isWindowFocused
    val shadowStrength = if (isWindowFocused) 1f else 0.45f
    val canResizeNatively = !isMaximized && NativeWindowActions.isAvailable

    LaunchedEffect(window) {
        NativeWindowActions.makeUncoveredAreaTransparentWhileResizing(window)
    }

    val invisibleBorderPx = with(LocalDensity.current) {
        if (isMaximized) 0 else frameSize.shadowMargin.roundToPx()
    }
    LaunchedEffect(window, invisibleBorderPx) {
        NativeWindowActions.reportInvisibleBorderWidth(window, invisibleBorderPx)
    }

    var frameSizeInPixels by remember { mutableStateOf(IntSize.Zero) }
    var hoveredResizeEdge by remember { mutableStateOf<WindowResizeEdge?>(null) }
    var isTitleBarVisible by remember { mutableStateOf(false) }
    val resizeBandPx = with(LocalDensity.current) { frameSize.resizeBand.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { frameSizeInPixels = it }
            .then(
                if (canResizeNatively) {
                    Modifier
                        .onPointerEvent(PointerEventType.Move) { event ->
                            hoveredResizeEdge = resizeEdgeAt(
                                position = event.changes.first().position,
                                frameSize = frameSizeInPixels,
                                bandWidth = resizeBandPx
                            )
                        }
                        .onPointerEvent(PointerEventType.Exit) { hoveredResizeEdge = null }
                        .onPointerEvent(PointerEventType.Press) { event ->
                            val edge = hoveredResizeEdge ?: return@onPointerEvent
                            val mouseEvent = event.awtEventOrNull ?: return@onPointerEvent
                            NativeWindowActions.beginResize(
                                window = window,
                                edge = edge,
                                screenX = mouseEvent.xOnScreen,
                                screenY = mouseEvent.yOnScreen
                            )
                        }
                        .pointerHoverIcon(cursorForResizeEdge(hoveredResizeEdge))
                } else {
                    Modifier
                }
            )
            .padding(if (isMaximized) 0.dp else frameSize.shadowMargin)
    ) {
        if (!isMaximized) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawWindowShadow(frameSize.cornerRadius.toPx(), shadowStrength)
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = frameShape,
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp
        ) {
            val titleBar: @Composable () -> Unit = {
                EmberrTitleBar(
                    windowTitle = windowTitle,
                    frameSize = frameSize,
                    isMaximized = isMaximized,
                    onMinimize = { windowState.isMinimized = true },
                    onToggleMaximize = {
                        windowState.placement =
                            if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
                    },
                    onClose = onCloseRequest
                )
            }

            if (!autoHideTitleBar) {
                Column(modifier = Modifier.fillMaxSize()) {
                    titleBar()
                    Box(modifier = Modifier.fillMaxSize()) { content() }
                }
                return@Surface
            }

            val titleBarHeightPx = with(LocalDensity.current) { frameSize.titleBarHeight.toPx() }
            val revealBandPx = with(LocalDensity.current) { TitleBarRevealBand.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPointerEvent(PointerEventType.Move, pass = PointerEventPass.Initial) { event ->
                        val pointerY = event.changes.first().position.y
                        val revealLimit = if (isTitleBarVisible) titleBarHeightPx else revealBandPx
                        isTitleBarVisible = pointerY <= revealLimit
                    }
                    .onPointerEvent(PointerEventType.Exit, pass = PointerEventPass.Initial) {
                        isTitleBarVisible = false
                    }
            ) {
                content()

                AnimatedVisibility(
                    visible = isTitleBarVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = slideInVertically(tween(TitleBarRevealMillis)) { -it } +
                        fadeIn(tween(TitleBarRevealMillis)),
                    exit = slideOutVertically(tween(TitleBarRevealMillis)) { -it } +
                        fadeOut(tween(TitleBarRevealMillis))
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp
                    ) {
                        titleBar()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowScope.EmberrTitleBar(
    windowTitle: String,
    frameSize: WindowFrameSize,
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    var previousTapTimeMillis by remember { mutableStateOf(0L) }
    var previousTapPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(frameSize.titleBarHeight)
            .pointerInput(window) {
                val doubleTapSlopPx = DoubleTapSlop.toPx()
                val dragThresholdPx = DragThreshold.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val downMouseEvent = currentEvent.awtEventOrNull

                    if (downMouseEvent != null && SwingUtilities.isRightMouseButton(downMouseEvent)) {
                        NativeWindowActions.showWindowMenu(
                            window = window,
                            screenX = downMouseEvent.xOnScreen,
                            screenY = downMouseEvent.yOnScreen
                        )
                        return@awaitEachGesture
                    }

                    val tapTimeMillis = downMouseEvent?.`when` ?: 0L
                    val isDoubleTap =
                        tapTimeMillis - previousTapTimeMillis <= DoubleTapTimeoutMillis &&
                            (down.position - previousTapPosition).getDistance() <= doubleTapSlopPx

                    if (isDoubleTap) {
                        previousTapTimeMillis = 0L
                        onToggleMaximize()
                        return@awaitEachGesture
                    }

                    previousTapTimeMillis = tapTimeMillis
                    previousTapPosition = down.position

                    while (true) {
                        val dragEvent = awaitPointerEvent()
                        val dragChange = dragEvent.changes.firstOrNull { it.id == down.id } ?: break
                        if (!dragChange.pressed) break
                        if ((dragChange.position - down.position).getDistance() <= dragThresholdPx) {
                            continue
                        }

                        val dragMouseEvent = dragEvent.awtEventOrNull
                        if (dragMouseEvent != null) {
                            previousTapTimeMillis = 0L
                            NativeWindowActions.beginMove(
                                window = window,
                                screenX = dragMouseEvent.xOnScreen,
                                screenY = dragMouseEvent.yOnScreen
                            )
                        }
                        break
                    }
                }
            }
    ) {
        val buttonLayout = remember { DesktopWindowButtonLayout.current }

        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WindowButtonGroup(
                buttons = buttonLayout.leadingButtons,
                frameSize = frameSize,
                isMaximized = isMaximized,
                onMinimize = onMinimize,
                onToggleMaximize = onToggleMaximize,
                onClose = onClose
            )
            Text(
                text = windowTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp).weight(1f)
            )
            WindowButtonGroup(
                buttons = buttonLayout.trailingButtons,
                frameSize = frameSize,
                isMaximized = isMaximized,
                onMinimize = onMinimize,
                onToggleMaximize = onToggleMaximize,
                onClose = onClose
            )
        }
    }
}

@Composable
private fun WindowButton(
    frameSize: WindowFrameSize,
    onClick: () -> Unit,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    drawGlyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isCloseButton = hoverColor == CloseHoverColor

    val backgroundColor = when {
        isHovered -> hoverColor
        else -> Color.Transparent
    }
    val glyphColor = when {
        isHovered && isCloseButton -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(frameSize.windowButtonSize)
            .clip(RoundedCornerShape(percent = 50))
            .background(backgroundColor)
            .hoverable(interactionSource)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(10.dp)) { drawGlyph(glyphColor) }
    }
}

private fun DrawScope.drawMinimizeGlyph(color: Color) {
    drawLine(
        color = color,
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = 1.4.dp.toPx(),
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawMaximizeGlyph(color: Color) {
    drawRect(color = color, style = Stroke(width = 1.4.dp.toPx()))
}

private fun DrawScope.drawRestoreGlyph(color: Color) {
    val inset = size.width * 0.25f
    val stroke = Stroke(width = 1.4.dp.toPx())
    drawRect(
        color = color,
        topLeft = Offset(inset, 0f),
        size = Size(size.width - inset, size.height - inset),
        style = stroke
    )
    drawRect(
        color = color,
        topLeft = Offset(0f, inset),
        size = Size(size.width - inset, size.height - inset),
        style = stroke
    )
}

private fun DrawScope.drawCloseGlyph(color: Color) {
    val stroke = 1.4.dp.toPx()
    drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
}

private fun DrawScope.drawWindowShadow(cornerRadius: Float, strength: Float) {
    drawBlurredRoundedRect(cornerRadius, AmbientShadowBlur, AmbientShadowDrop, 0.30f * strength)
    drawBlurredRoundedRect(cornerRadius, ContactShadowBlur, ContactShadowDrop, 0.22f * strength)
}

private fun DrawScope.drawBlurredRoundedRect(
    cornerRadius: Float,
    blur: Dp,
    drop: Dp,
    alpha: Float
) {
    val paint = org.jetbrains.skia.Paint().apply {
        color = org.jetbrains.skia.Color.makeARGB((alpha * 255f).toInt(), 0, 0, 0)
        maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(
            org.jetbrains.skia.FilterBlurMode.NORMAL,
            blur.toPx()
        )
    }
    val shadowRect = org.jetbrains.skia.RRect.makeXYWH(
        0f,
        drop.toPx(),
        size.width,
        size.height,
        cornerRadius
    )
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawRRect(shadowRect, paint) }
    paint.close()
}

private fun resizeEdgeAt(position: Offset, frameSize: IntSize, bandWidth: Float): WindowResizeEdge? {
    if (frameSize.width <= 0 || frameSize.height <= 0) return null

    val isNearLeft = position.x <= bandWidth
    val isNearRight = position.x >= frameSize.width - bandWidth
    val isNearTop = position.y <= bandWidth
    val isNearBottom = position.y >= frameSize.height - bandWidth

    return when {
        isNearTop && isNearLeft -> WindowResizeEdge.TopLeft
        isNearTop && isNearRight -> WindowResizeEdge.TopRight
        isNearBottom && isNearLeft -> WindowResizeEdge.BottomLeft
        isNearBottom && isNearRight -> WindowResizeEdge.BottomRight
        isNearTop -> WindowResizeEdge.Top
        isNearBottom -> WindowResizeEdge.Bottom
        isNearLeft -> WindowResizeEdge.Left
        isNearRight -> WindowResizeEdge.Right
        else -> null
    }
}

private fun cursorForResizeEdge(edge: WindowResizeEdge?): PointerIcon {
    val cursorType = when (edge) {
        WindowResizeEdge.TopLeft -> Cursor.NW_RESIZE_CURSOR
        WindowResizeEdge.Top -> Cursor.N_RESIZE_CURSOR
        WindowResizeEdge.TopRight -> Cursor.NE_RESIZE_CURSOR
        WindowResizeEdge.Right -> Cursor.E_RESIZE_CURSOR
        WindowResizeEdge.BottomRight -> Cursor.SE_RESIZE_CURSOR
        WindowResizeEdge.Bottom -> Cursor.S_RESIZE_CURSOR
        WindowResizeEdge.BottomLeft -> Cursor.SW_RESIZE_CURSOR
        WindowResizeEdge.Left -> Cursor.W_RESIZE_CURSOR
        null -> Cursor.DEFAULT_CURSOR
    }
    return PointerIcon(Cursor(cursorType))
}

@Composable
private fun WindowButtonGroup(
    buttons: List<WindowButtonKind>,
    frameSize: WindowFrameSize,
    isMaximized: Boolean,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit
) {
    if (buttons.isEmpty()) return

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        buttons.forEach { button ->
            when (button) {
                WindowButtonKind.Minimize ->
                    WindowButton(frameSize = frameSize, onClick = onMinimize) { color -> drawMinimizeGlyph(color) }

                WindowButtonKind.Maximize ->
                    WindowButton(frameSize = frameSize, onClick = onToggleMaximize) { color ->
                        if (isMaximized) drawRestoreGlyph(color) else drawMaximizeGlyph(color)
                    }

                WindowButtonKind.Close ->
                    WindowButton(frameSize = frameSize, onClick = onClose, hoverColor = CloseHoverColor) { color ->
                        drawCloseGlyph(color)
                    }
            }
        }
    }
}
