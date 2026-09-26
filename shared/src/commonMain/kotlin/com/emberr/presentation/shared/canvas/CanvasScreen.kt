package com.emberr.presentation.shared.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeShape
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.data.local.room.entity.CanvasStrokeTool
import com.emberr.domain.canvas.CanvasLineStyle
import com.emberr.domain.canvas.CanvasStrokePoint
import com.emberr.domain.canvas.CanvasStrokeStyle
import com.emberr.domain.canvas.CanvasViewPosition
import com.emberr.domain.canvas.isFreeText
import com.emberr.domain.canvas.textStyle
import com.emberr.domain.canvas.isGroup
import com.emberr.domain.canvas.isImage
import com.emberr.domain.util.media.ImageClipboard
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.domain.util.system.triggerHapticFeedback
import com.emberr.domain.canvas.membersOf
import com.emberr.presentation.LocalImagePicker
import com.emberr.presentation.shared.StickyNoteWindowBus
import com.emberr.presentation.shared.components.KmpBackHandler
import com.emberr.ui.theme.LocalAppIsDark
import com.emberr.ui.theme.highlightBackgroundFor
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.group
import emberr.shared.generated.resources.image
import emberr.shared.generated.resources.square
import emberr.shared.generated.resources.trash
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private const val GRID_SPACING_UNITS = 20f
private const val WHEEL_PAN_PIXELS_PER_NOTCH = 48f
private const val WHEEL_ZOOM_STEP = 1.1f
private const val EDGE_MIN_CONTROL_UNITS = 30f
private const val EDGE_MAX_CONTROL_UNITS = 150f
private val HANDLE_RADIUS = 5.dp
private val HANDLE_HIT_RADIUS = 10.dp
private val EDGE_SNAP_RADIUS = 24.dp
private const val SNAPPED_HANDLE_SCALE = 1.5f
private val RESIZE_GRAB_DISTANCE = 6.dp
private val TOUCH_HANDLE_RADIUS = 7.dp
private val TOUCH_HANDLE_HIT_RADIUS = 22.dp
private val TOUCH_RESIZE_GRAB_DISTANCE = 14.dp
private val TOUCH_EDGE_HIT_DISTANCE = 14.dp
private val TOUCH_EDGE_SNAP_RADIUS = 32.dp
private val FREE_TEXT_MIN_TEXT_WIDTH = 8.dp
private val TOOL_PANEL_BOTTOM_CLEARANCE = 76.dp
private val TOOL_PANEL_MIN_HEIGHT = 120.dp
private const val FREE_TEXT_SIZE_CHANGE_TO_SAVE = 0.5f
private val KEYBOARD_CLEARANCE = 24.dp
private const val ZOOM_BUTTON_STEP = 1.25f
private const val VIEW_POSITION_SAVE_DELAY_MILLIS = 500L
private const val TOOL_SETTINGS_SAVE_DELAY_MILLIS = 300L
private val EDGE_HIT_DISTANCE = 6.dp
private val EDGE_STROKE_WIDTH = 1.5.dp
private val SELECTED_EDGE_STROKE_WIDTH = 2.5.dp
private val ARROW_SIZE = 9.dp
private val CARD_CORNER_RADIUS = 6.dp
private val GROUP_CORNER_RADIUS = 10.dp
private const val DOT_ALPHA = 0.18f
private const val COLORED_GROUP_FILL_ALPHA = 0.35f
private const val GROUP_TITLE_ALPHA = 0.75f
private const val GROUP_BORDER_ALPHA = 0.35f
private const val SHAPE_LINE_ALPHA = 0.45f

private sealed interface CanvasSelection {
    data object None : CanvasSelection
    data class Nodes(val nodeIds: Set<String>) : CanvasSelection
    data class Edge(val edgeId: String) : CanvasSelection
}

private val CanvasSelection.selectedNodeIds: Set<String>
    get() = (this as? CanvasSelection.Nodes)?.nodeIds.orEmpty()

private val CanvasSelection.singleSelectedNodeId: String?
    get() = selectedNodeIds.singleOrNull()

private data class CanvasContextMenuRequest(val target: CanvasSelection, val anchorOnScreen: Offset)

private data class CanvasClick(val targetKey: String, val uptimeMillis: Long, val position: Offset)

private sealed interface CanvasDragPreview {
    data class DrawingBox(val startWorld: Offset, val currentWorld: Offset) : CanvasDragPreview
    data class DraggingEdgeEnd(
        val anchoredNodeId: String,
        val anchoredSide: CanvasSide,
        val isDraggingArrowHead: Boolean,
        val looseEndWorld: Offset,
        val snapTarget: CanvasHandle? = null,
        val detachedEdgeId: String? = null
    ) : CanvasDragPreview
}

@Composable
fun CanvasScreen(
    noteId: String,
    modifier: Modifier = Modifier,
    isStickyNote: Boolean = false,
    isEmbedded: Boolean = false,
    isActive: Boolean = true,
    showBackButton: Boolean = true,
    onNavigateBack: () -> Unit = {},
    viewModel: CanvasViewModel = koinViewModel(key = "canvas:$noteId")
) {
    LaunchedEffect(noteId) { viewModel.loadCanvas(noteId) }

    val canvas by viewModel.canvas.collectAsState()
    val isAddingImage by viewModel.isAddingImage.collectAsState()
    var viewport by remember(noteId) { mutableStateOf(CanvasViewport()) }
    var selection by remember(noteId) { mutableStateOf<CanvasSelection>(CanvasSelection.None) }
    var editingNodeId by remember(noteId) { mutableStateOf<String?>(null) }
    var hoveredNodeId by remember(noteId) { mutableStateOf<String?>(null) }
    var dragPreview by remember(noteId) { mutableStateOf<CanvasDragPreview?>(null) }
    var contextMenuRequest by remember(noteId) { mutableStateOf<CanvasContextMenuRequest?>(null) }
    var pastePillAnchor by remember(noteId) { mutableStateOf<Offset?>(null) }
    var isSelectingMultiple by remember(noteId) { mutableStateOf(false) }
    var isDotGridVisible by remember(noteId) { mutableStateOf(viewModel.isDotGridVisible(noteId)) }
    var activeTool by remember(noteId) { mutableStateOf<CanvasTool?>(null) }
    val liveStrokePoints = remember(noteId) { mutableStateListOf<CanvasStrokePoint>() }
    var liveStrokeTool by remember(noteId) { mutableStateOf(CanvasStrokeTool.PEN) }
    var penStyle by remember { mutableStateOf(viewModel.savedStrokeStyle(CanvasStrokeTool.PEN)) }
    var highlighterStyle by remember { mutableStateOf(viewModel.savedStrokeStyle(CanvasStrokeTool.HIGHLIGHTER)) }
    var eraserRadius by remember { mutableStateOf(viewModel.savedEraserRadius()) }
    var textToolStyle by remember { mutableStateOf(viewModel.savedTextStyle()) }
    var lineStyle by remember { mutableStateOf(viewModel.savedLineStyle()) }
    var lineStrokeStyle by remember { mutableStateOf(viewModel.savedStrokeStyle(CanvasStrokeTool.LINE)) }
    var openLineSettingsCategory by remember(noteId) { mutableStateOf<CanvasLineSettingsCategory?>(null) }
    var hasSeenStylus by remember { mutableStateOf(false) }
    var liveLine by remember(noteId) { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var openStrokeSettingsCategory by remember(noteId) { mutableStateOf<CanvasStrokeSettingsCategory?>(null) }
    var openTextSettingsCategory by remember(noteId) { mutableStateOf<CanvasTextSettingsCategory?>(null) }
    var isFinishedStrokeWaitingToAppear by remember(noteId) { mutableStateOf(false) }
    var eraserScreenPosition by remember(noteId) { mutableStateOf<Offset?>(null) }
    val strokeCache = remember(noteId) { CanvasStrokeCache() }
    var pointerIcon by remember(noteId) { mutableStateOf(PointerIcon.Default) }
    var boardSize by remember(noteId) { mutableStateOf(IntSize.Zero) }
    var hasRestoredViewPosition by remember(noteId) { mutableStateOf(false) }
    val zoomAnimation = remember(noteId) { Animatable(1f) }
    val panAnimation = remember(noteId) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val zoomScope = rememberCoroutineScope()
    val pasteScope = rememberCoroutineScope()
    val pickImage = LocalImagePicker.current
    val canvasFocusRequester = remember { FocusRequester() }
    val hazeState = remember { HazeState() }
    val nodeScrollStates = remember(noteId) { mutableMapOf<String, ScrollState>() }
    var previousEditingNodeId by remember(noteId) { mutableStateOf<String?>(null) }
    LaunchedEffect(editingNodeId) {
        val finishedNodeId = previousEditingNodeId
        previousEditingNodeId = editingNodeId
        if (finishedNodeId != null && finishedNodeId != editingNodeId) viewModel.finishTextEditing(finishedNodeId)
    }
    LaunchedEffect(isActive) {
        if (!isEmbedded) return@LaunchedEffect
        if (isActive) {
            canvasFocusRequester.requestFocus()
        } else {
            editingNodeId = null
            selection = CanvasSelection.None
            isSelectingMultiple = false
            hoveredNodeId = null
            pointerIcon = PointerIcon.Default
            activeTool = null
            eraserScreenPosition = null
            openStrokeSettingsCategory = null
            openTextSettingsCategory = null
            openLineSettingsCategory = null
            pastePillAnchor = null
        }
    }

    val pixelDensity = LocalDensity.current.density

    fun currentViewPosition(): CanvasViewPosition {
        val worldCenter = viewport.screenToWorld(Offset(boardSize.width / 2f, boardSize.height / 2f), pixelDensity)
        return CanvasViewPosition(centerX = worldCenter.x, centerY = worldCenter.y, zoom = viewport.zoom)
    }

    LaunchedEffect(noteId, boardSize) {
        if (hasRestoredViewPosition || boardSize == IntSize.Zero) return@LaunchedEffect
        viewModel.savedViewPosition(noteId)?.let { saved ->
            viewport = canvasViewportCenteredOn(
                worldCenter = Offset(saved.centerX, saved.centerY),
                zoom = saved.zoom,
                screenCenter = Offset(boardSize.width / 2f, boardSize.height / 2f),
                density = pixelDensity
            )
        }
        hasRestoredViewPosition = true
    }

    LaunchedEffect(noteId, hasRestoredViewPosition) {
        if (!hasRestoredViewPosition) return@LaunchedEffect
        snapshotFlow { currentViewPosition() }.collectLatest { position ->
            delay(VIEW_POSITION_SAVE_DELAY_MILLIS)
            viewModel.saveViewPosition(noteId, position)
        }
    }

    DisposableEffect(noteId) {
        onDispose {
            if (hasRestoredViewPosition) viewModel.saveViewPosition(noteId, currentViewPosition())
        }
    }
    val backgroundColor = MaterialTheme.colorScheme.background
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DOT_ALPHA)
    val edgeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val accentColor = MaterialTheme.colorScheme.primary
    val handleFillColor = MaterialTheme.colorScheme.surface
    val handleBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val strokeInkColor = MaterialTheme.colorScheme.onSurface
    val isDarkTheme = LocalAppIsDark.current

    LaunchedEffect(penStyle, highlighterStyle, eraserRadius, textToolStyle, lineStyle, lineStrokeStyle) {
        delay(TOOL_SETTINGS_SAVE_DELAY_MILLIS)
        viewModel.saveStrokeStyle(CanvasStrokeTool.PEN, penStyle)
        viewModel.saveStrokeStyle(CanvasStrokeTool.HIGHLIGHTER, highlighterStyle)
        viewModel.saveEraserRadius(eraserRadius)
        viewModel.saveTextStyle(textToolStyle)
        viewModel.saveLineStyle(lineStyle)
        viewModel.saveStrokeStyle(CanvasStrokeTool.LINE, lineStrokeStyle)
    }

    fun styleFor(tool: CanvasStrokeTool): CanvasStrokeStyle = when (tool) {
        CanvasStrokeTool.PEN -> penStyle
        CanvasStrokeTool.HIGHLIGHTER -> highlighterStyle
        CanvasStrokeTool.LINE -> lineStrokeStyle
    }

    fun eraserRadiusInPixels(): Float = eraserRadius * pixelDensity

    fun penInkColorFor(colorName: String?, opacity: Float): Color =
        (CanvasInkColor.named(colorName)?.color ?: strokeInkColor).copy(alpha = opacity)

    LaunchedEffect(canvas.strokes) {
        if (isFinishedStrokeWaitingToAppear) {
            liveStrokePoints.clear()
            liveLine = null
            isFinishedStrokeWaitingToAppear = false
        }
    }

    fun addLiveStrokePoint(screenPosition: Offset, pressure: Float?) {
        val worldPosition = viewport.screenToWorld(screenPosition, pixelDensity)
        val point = CanvasStrokePoint(worldPosition.x, worldPosition.y, pressure)
        val minimumSpacing = MINIMUM_STROKE_POINT_SPACING_PIXELS / viewport.pixelsPerUnit(pixelDensity)
        if (point.isFarEnoughFrom(liveStrokePoints.lastOrNull(), minimumSpacing)) liveStrokePoints.add(point)
    }

    fun startLiveStroke(screenPosition: Offset, pressure: Float?, tool: CanvasStrokeTool) {
        liveStrokePoints.clear()
        liveStrokeTool = tool
        isFinishedStrokeWaitingToAppear = false
        addLiveStrokePoint(screenPosition, pressure)
    }

    fun finishLiveStroke() {
        val style = styleFor(liveStrokeTool)
        viewModel.addStroke(
            tool = liveStrokeTool,
            worldPoints = liveStrokePoints.toList(),
            width = style.width,
            color = style.colorName,
            opacity = style.opacity,
            usesPressure = style.usesPressure
        )
        isFinishedStrokeWaitingToAppear = true
    }

    fun dropActiveTool() {
        activeTool = null
        eraserScreenPosition = null
        pointerIcon = PointerIcon.Default
    }

    fun startTextAt(screenPosition: Offset) {
        val pressedFreeText = CanvasHitTester(canvas, viewport, pixelDensity).textNodeAt(screenPosition)?.takeIf { it.isFreeText }
        val textNodeId = pressedFreeText?.nodeId ?: run {
            val pressedWorld = viewport.screenToWorld(screenPosition, pixelDensity)
            val startingHeight = freeTextStartingHeight(textToolStyle.fontSize)
            viewModel.createFreeText(pressedWorld - Offset(CANVAS_FREE_TEXT_PADDING, startingHeight / 2), startingHeight, textToolStyle)
        }
        selection = CanvasSelection.Nodes(setOf(textNodeId))
        editingNodeId = textNodeId
        activeTool = null
        pointerIcon = PointerIcon.Default
    }

    fun startLiveLine(screenPosition: Offset) {
        liveStrokePoints.clear()
        isFinishedStrokeWaitingToAppear = false
        val worldPosition = viewport.screenToWorld(screenPosition, pixelDensity)
        liveLine = worldPosition to worldPosition
    }

    fun moveLiveLineEnd(screenPosition: Offset) {
        val lineStart = liveLine?.first ?: return
        liveLine = lineStart to viewport.screenToWorld(screenPosition, pixelDensity)
    }

    fun finishLiveLine(minimumScreenLength: Float) {
        val (lineStart, lineEnd) = liveLine ?: return
        val screenLength = (lineEnd - lineStart).getDistance() * viewport.pixelsPerUnit(pixelDensity)
        if (screenLength < minimumScreenLength) {
            liveLine = null
            return
        }
        viewModel.addLine(lineStart, lineEnd, lineStyle, lineStrokeStyle)
        isFinishedStrokeWaitingToAppear = true
    }

    fun eraseStrokesAlong(fromScreen: Offset, toScreen: Offset) {
        eraserScreenPosition = toScreen
        val eraserWorldRadius = eraserRadiusInPixels() / viewport.pixelsPerUnit(pixelDensity)
        val eraserPositions = eraserPositionsBetween(
            from = viewport.screenToWorld(fromScreen, pixelDensity),
            to = viewport.screenToWorld(toScreen, pixelDensity),
            spacing = eraserWorldRadius / 2
        )
        val touchedStrokeIds = canvas.strokes
            .filter { stroke -> eraserPositions.any { strokeCache.isTouchedByEraser(stroke, it, eraserWorldRadius) } }
            .mapTo(HashSet()) { it.strokeId }
        if (touchedStrokeIds.isNotEmpty()) viewModel.eraseStrokes(touchedStrokeIds)
    }

    fun animateZoom(zoomFactor: Float, anchorOnScreen: Offset) {
        val startingZoom = if (zoomAnimation.isRunning) zoomAnimation.targetValue else viewport.zoom
        val targetZoom = (startingZoom * zoomFactor).coerceIn(CANVAS_MIN_ZOOM, CANVAS_MAX_ZOOM)
        zoomScope.launch {
            if (!zoomAnimation.isRunning) zoomAnimation.snapTo(viewport.zoom)
            zoomAnimation.animateTo(targetZoom, spring(stiffness = Spring.StiffnessMediumLow)) {
                viewport = viewport.zoomedAround(anchorOnScreen, value / viewport.zoom, pixelDensity)
            }
        }
    }

    fun zoomAroundBoardCenter(zoomFactor: Float) =
        animateZoom(zoomFactor, Offset(boardSize.width / 2f, boardSize.height / 2f))

    fun animatePanTo(targetPanOffset: Offset) {
        zoomScope.launch {
            panAnimation.snapTo(viewport.panOffset)
            panAnimation.animateTo(targetPanOffset, spring(stiffness = Spring.StiffnessMediumLow)) {
                viewport = viewport.copy(panOffset = value)
            }
        }
    }

    fun showBusiestArea() {
        val textBoxes = canvas.nodes.filter { !it.isGroup }
        val boxesToConsider = textBoxes.ifEmpty { canvas.nodes }
        val busiestCenter = busiestAreaCenter(boxesToConsider.map { it.worldRect }, CANVAS_CLUSTER_RADIUS) ?: return
        val boardCenter = Offset(boardSize.width / 2f, boardSize.height / 2f)
        animatePanTo(viewport.panOffsetCentering(busiestCenter, boardCenter, pixelDensity))
    }

    fun leaveEditingAndSelection() {
        editingNodeId = null
        selection = CanvasSelection.None
        isSelectingMultiple = false
        canvasFocusRequester.requestFocus()
    }

    val keyboardHeightPx = WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboardClearancePx = with(LocalDensity.current) { KEYBOARD_CLEARANCE.toPx() }
    LaunchedEffect(editingNodeId, keyboardHeightPx, boardSize) {
        if (isDesktopPlatform || isEmbedded || keyboardHeightPx == 0) return@LaunchedEffect
        val editingNode = canvas.nodes.firstOrNull { it.nodeId == editingNodeId } ?: return@LaunchedEffect
        val editingRect = viewport.worldRectToScreen(editingNode.worldRect, pixelDensity)
        val visibleBottom = boardSize.height - keyboardHeightPx - keyboardClearancePx
        val hiddenBelowKeyboard = editingRect.bottom - visibleBottom
        if (hiddenBelowKeyboard <= 0f) return@LaunchedEffect
        val roomAboveBox = (editingRect.top - keyboardClearancePx).coerceAtLeast(0f)
        animatePanTo(viewport.panOffset - Offset(0f, minOf(hiddenBelowKeyboard, roomAboveBox)))
    }

    KmpBackHandler(enabled = !isDesktopPlatform && (editingNodeId != null || selection != CanvasSelection.None)) {
        leaveEditingAndSelection()
    }

    KmpBackHandler(enabled = !isDesktopPlatform && activeTool != null) {
        dropActiveTool()
    }

    fun deleteItems(target: CanvasSelection) {
        when (target) {
            is CanvasSelection.Nodes -> viewModel.deleteNodes(target.nodeIds)
            is CanvasSelection.Edge -> viewModel.deleteEdge(target.edgeId)
            CanvasSelection.None -> Unit
        }
        editingNodeId = null
        selection = CanvasSelection.None
    }

    fun createBoxAt(worldCenter: Offset, groupToGrowId: String?, shape: CanvasNodeShape = CanvasNodeShape.RECTANGLE) {
        val shapeSize = shape.defaultWorldSize
        val newNodeId = viewModel.createNode(
            worldRect = rectCenteredOn(worldCenter, shapeSize.width, shapeSize.height),
            groupToGrowId = groupToGrowId,
            shape = shape
        )
        selection = CanvasSelection.Nodes(setOf(newNodeId))
        editingNodeId = newNodeId
    }

    fun placeShapeAtBoardCenter(shape: CanvasNodeShape) {
        dropActiveTool()
        val boardCenter = Offset(boardSize.width / 2f, boardSize.height / 2f)
        createBoxAt(viewport.screenToWorld(boardCenter, pixelDensity), groupToGrowId = null, shape = shape)
    }

    fun boardCenterInWorld(): Offset = viewport.screenToWorld(Offset(boardSize.width / 2f, boardSize.height / 2f), pixelDensity)

    fun showPasteOptionIfClipboardHasImage(anchorOnScreen: Offset) {
        pasteScope.launch {
            if (!ImageClipboard.hasImage()) return@launch
            if (isDesktopPlatform) {
                contextMenuRequest = CanvasContextMenuRequest(CanvasSelection.None, anchorOnScreen)
            } else {
                triggerHapticFeedback()
                pastePillAnchor = anchorOnScreen
            }
        }
    }

    fun contextMenuOptionsFor(target: CanvasSelection): List<CanvasMenuOption> {
        val deleteOption = CanvasMenuOption("Delete", Res.drawable.trash, isDestructive = true) { deleteItems(target) }
        if (target !is CanvasSelection.Nodes) return listOf(deleteOption)
        val clickedGroup = target.nodeIds.singleOrNull()?.let { nodeId ->
            canvas.nodes.firstOrNull { it.nodeId == nodeId && it.isGroup }
        }
        val groupingOption = if (clickedGroup != null) {
            CanvasMenuOption("Ungroup", Res.drawable.square) {
                viewModel.ungroup(clickedGroup.nodeId)
                selection = CanvasSelection.None
            }
        } else {
            CanvasMenuOption("Create group", Res.drawable.group) {
                val newGroupId = viewModel.createGroup(target.nodeIds)
                selection = newGroupId?.let { CanvasSelection.Nodes(setOf(it)) } ?: CanvasSelection.None
            }
        }
        return listOf(groupingOption, deleteOption)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(backgroundColor)
                .onSizeChanged { boardSize = it }
                .pointerHoverIcon(pointerIcon)
                .focusRequester(canvasFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val isEditingGroupTitle = canvas.nodes.any { it.nodeId == editingNodeId && it.isGroup }
                    val isCommandPressed = event.isCtrlPressed || event.isMetaPressed
                    when {
                        event.key == Key.Escape && activeTool != null -> {
                            dropActiveTool()
                            true
                        }
                        editingNodeId == null && isCommandPressed && event.key == Key.Z && !event.isShiftPressed -> {
                            viewModel.undo()
                            true
                        }
                        editingNodeId == null && isCommandPressed &&
                            (event.key == Key.Y || (event.key == Key.Z && event.isShiftPressed)) -> {
                            viewModel.redo()
                            true
                        }
                        event.key == Key.Escape && editingNodeId != null -> {
                            editingNodeId = null
                            canvasFocusRequester.requestFocus()
                            true
                        }
                        event.key == Key.Enter && isEditingGroupTitle -> {
                            editingNodeId = null
                            canvasFocusRequester.requestFocus()
                            true
                        }
                        editingNodeId == null && isCommandPressed && event.key == Key.V -> {
                            viewModel.pasteImageFromClipboard(boardCenterInWorld())
                            true
                        }
                        editingNodeId == null && (event.key == Key.Delete || event.key == Key.Backspace) -> {
                            deleteItems(selection)
                            true
                        }
                        else -> false
                    }
                }
                .pointerInput(noteId) {
                    if (isDesktopPlatform) return@pointerInput
                    var lastTap: CanvasClick? = null

                    fun registerTap(targetKey: String, uptimeMillis: Long, position: Offset): Boolean {
                        val previousTap = lastTap
                        val isDoubleTap = previousTap != null &&
                            previousTap.targetKey == targetKey &&
                            uptimeMillis - previousTap.uptimeMillis <= viewConfiguration.doubleTapTimeoutMillis &&
                            (position - previousTap.position).getDistance() <= viewConfiguration.touchSlop * 2
                        lastTap = if (isDoubleTap) null else CanvasClick(targetKey, uptimeMillis, position)
                        return isDoubleTap
                    }

                    fun hitTester() = CanvasHitTester(canvas, viewport, density)

                    fun movedLooseEnd(dragging: CanvasDragPreview.DraggingEdgeEnd, screenPoint: Offset) = dragging.copy(
                        looseEndWorld = viewport.screenToWorld(screenPoint, density),
                        snapTarget = hitTester().snapTargetNear(screenPoint, dragging.anchoredNodeId, TOUCH_EDGE_SNAP_RADIUS.toPx())
                    )

                    fun startPositionsOfSelectionAndGroupContents(): Map<String, Offset> {
                        val selectedIds = selection.selectedNodeIds
                        val selectedGroups = canvas.nodes.filter { it.nodeId in selectedIds && it.isGroup }
                        val movingNodeIds = selectedIds + selectedGroups.flatMap { canvas.membersOf(it) }.map { it.nodeId }
                        return canvas.nodes.filter { it.nodeId in movingNodeIds }.associate { it.nodeId to Offset(it.x, it.y) }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        pastePillAnchor = null
                        if (activeTool == CanvasTool.SHAPES) dropActiveTool()
                        if (down.isFromStylus) hasSeenStylus = true
                        if (activeTool.drawsOnBoard && hasSeenStylus && !down.isFromStylus) {
                            down.consume()
                            trackPinchAndPan { centroid, pan, zoom ->
                                viewport = viewport.pannedBy(pan).zoomedAround(centroid, zoom, density)
                            }
                            return@awaitEachGesture
                        }
                        if (activeTool == CanvasTool.LINE) {
                            down.consume()
                            startLiveLine(down.position)
                            val gestureEnd = if (currentEvent.changes.count { it.pressed } >= 2) {
                                OnePointerGestureEnd.SecondFingerDown
                            } else {
                                trackOnePointerUntilLift(down.id) { position, _ -> moveLiveLineEnd(position) }
                            }
                            if (gestureEnd == OnePointerGestureEnd.Lifted) {
                                finishLiveLine(viewConfiguration.touchSlop)
                            } else {
                                liveLine = null
                                trackPinchAndPan { centroid, pan, zoom ->
                                    viewport = viewport.pannedBy(pan).zoomedAround(centroid, zoom, density)
                                }
                            }
                            return@awaitEachGesture
                        }
                        if (activeTool.drawsOrErases) {
                            down.consume()
                            val strokeTool = activeTool.strokeTool
                            var previousPosition = down.position
                            if (strokeTool != null) {
                                startLiveStroke(down.position, down.penPressure, strokeTool)
                            } else {
                                viewModel.beginUndoStep()
                                eraseStrokesAlong(down.position, down.position)
                            }
                            val gestureEnd = if (currentEvent.changes.count { it.pressed } >= 2) {
                                OnePointerGestureEnd.SecondFingerDown
                            } else {
                                trackOnePointerUntilLift(down.id) { position, pressure ->
                                    if (strokeTool != null) {
                                        addLiveStrokePoint(position, pressure)
                                    } else {
                                        eraseStrokesAlong(previousPosition, position)
                                        previousPosition = position
                                    }
                                }
                            }
                            eraserScreenPosition = null
                            if (strokeTool != null && gestureEnd == OnePointerGestureEnd.Lifted) finishLiveStroke() else liveStrokePoints.clear()
                            if (gestureEnd == OnePointerGestureEnd.SecondFingerDown) {
                                trackPinchAndPan { centroid, pan, zoom ->
                                    viewport = viewport.pannedBy(pan).zoomedAround(centroid, zoom, density)
                                }
                            }
                            return@awaitEachGesture
                        }
                        val downPosition = down.position
                        val tester = hitTester()
                        val editingArea = tester.editingAreaOf(editingNodeId, TOUCH_RESIZE_GRAB_DISTANCE.toPx())
                        if (editingArea != null && editingArea.contains(downPosition)) return@awaitEachGesture

                        down.consume()
                        val downWorld = viewport.screenToWorld(downPosition, density)
                        val singleSelectedIds = if (isSelectingMultiple) emptyList() else listOfNotNull(selection.singleSelectedNodeId)
                        val handle = tester.handleAt(downPosition, singleSelectedIds, TOUCH_HANDLE_HIT_RADIUS.toPx())
                        val resizeZone = if (handle == null && singleSelectedIds.isNotEmpty()) {
                            tester.resizeZoneAt(downPosition, TOUCH_RESIZE_GRAB_DISTANCE.toPx(), onlyNodeIds = singleSelectedIds.toSet())
                        } else {
                            null
                        }
                        val pressedTextNode = tester.textNodeAt(downPosition)
                        val pressedEdge = if (pressedTextNode == null) tester.edgeAt(downPosition, TOUCH_EDGE_HIT_DISTANCE.toPx()) else null
                        val pressedGroup = if (pressedTextNode == null && pressedEdge == null) tester.groupAt(downPosition) else null
                        val pressedNode = pressedTextNode ?: pressedGroup

                        val touchStart = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            awaitTouchStart(down.id, downPosition)
                        } ?: CanvasTouchStart.LongPress

                        when (touchStart) {
                            CanvasTouchStart.Pinch -> trackPinchAndPan { centroid, pan, zoom ->
                                viewport = viewport.pannedBy(pan).zoomedAround(centroid, zoom, density)
                            }

                            CanvasTouchStart.Tap -> {
                                if (activeTool == CanvasTool.TEXT) {
                                    startTextAt(downPosition)
                                    return@awaitEachGesture
                                }
                                if (isSelectingMultiple) {
                                    if (pressedNode != null) {
                                        val currentlySelected = selection.selectedNodeIds
                                        val toggledSelection = if (pressedNode.nodeId in currentlySelected) {
                                            currentlySelected - pressedNode.nodeId
                                        } else {
                                            currentlySelected + pressedNode.nodeId
                                        }
                                        selection = if (toggledSelection.isEmpty()) CanvasSelection.None else CanvasSelection.Nodes(toggledSelection)
                                        if (toggledSelection.isEmpty()) isSelectingMultiple = false
                                    } else {
                                        selection = CanvasSelection.None
                                        isSelectingMultiple = false
                                    }
                                    return@awaitEachGesture
                                }
                                editingNodeId = null
                                canvasFocusRequester.requestFocus()
                                when {
                                    pressedNode != null -> {
                                        selection = CanvasSelection.Nodes(setOf(pressedNode.nodeId))
                                        val pressedGroupTitle = pressedNode.isGroup && tester.groupTitleScreenRectOf(pressedNode).contains(downPosition)
                                        val tapTargetKey = when {
                                            !pressedNode.isGroup -> "box:${pressedNode.nodeId}"
                                            pressedGroupTitle -> "group-title:${pressedNode.nodeId}"
                                            else -> "group-body:${pressedNode.nodeId}"
                                        }
                                        if (registerTap(tapTargetKey, down.uptimeMillis, downPosition)) {
                                            if (pressedNode.isGroup && !pressedGroupTitle) {
                                                createBoxAt(downWorld, groupToGrowId = pressedNode.nodeId)
                                            } else if (!pressedNode.isImage) {
                                                editingNodeId = pressedNode.nodeId
                                            }
                                        }
                                    }
                                    pressedEdge != null -> selection = CanvasSelection.Edge(pressedEdge.edgeId)
                                    else -> {
                                        selection = CanvasSelection.None
                                        if (registerTap("empty", down.uptimeMillis, downPosition)) {
                                            createBoxAt(downWorld, groupToGrowId = null)
                                        }
                                    }
                                }
                            }

                            CanvasTouchStart.LongPress -> {
                                when {
                                    pressedNode != null -> {
                                        triggerHapticFeedback()
                                        editingNodeId = null
                                        val alreadySelected = if (isSelectingMultiple) selection.selectedNodeIds else emptySet()
                                        selection = CanvasSelection.Nodes(alreadySelected + pressedNode.nodeId)
                                        isSelectingMultiple = true
                                        val startPositions = startPositionsOfSelectionAndGroupContents()
                                        var hasMoved = false
                                        viewModel.beginUndoStep()
                                        trackDragUntilRelease(isStillHeld = { event -> event.changes.any { it.pressed } }) { position ->
                                            if ((position - downPosition).getDistance() > viewConfiguration.touchSlop) hasMoved = true
                                            if (hasMoved) {
                                                viewModel.moveNodes(startPositions, viewport.screenToWorld(position, density) - downWorld)
                                            }
                                        }
                                    }
                                    pressedEdge != null -> {
                                        triggerHapticFeedback()
                                        editingNodeId = null
                                        selection = CanvasSelection.Edge(pressedEdge.edgeId)
                                        isSelectingMultiple = true
                                    }
                                    else -> showPasteOptionIfClipboardHasImage(downPosition)
                                }
                            }

                            CanvasTouchStart.Drag -> {
                                editingNodeId = null
                                canvasFocusRequester.requestFocus()
                                when {
                                    handle != null -> {
                                        val newEdgeEnd = CanvasDragPreview.DraggingEdgeEnd(
                                            anchoredNodeId = handle.nodeId,
                                            anchoredSide = handle.side,
                                            isDraggingArrowHead = true,
                                            looseEndWorld = downWorld
                                        )
                                        dragPreview = newEdgeEnd
                                        trackDragUntilRelease(isStillHeld = { event -> event.changes.any { it.pressed } }) { position ->
                                            dragPreview = movedLooseEnd(newEdgeEnd, position)
                                        }
                                        val snapTarget = (dragPreview as? CanvasDragPreview.DraggingEdgeEnd)?.snapTarget
                                        dragPreview = null
                                        if (snapTarget != null) {
                                            viewModel.createEdge(handle.nodeId, handle.side, snapTarget.nodeId, snapTarget.side)
                                        }
                                    }
                                    resizeZone != null -> {
                                        val (nodeToResize, grabbedEdges) = resizeZone
                                        viewModel.beginUndoStep()
                                        val startBounds = nodeToResize.worldRect
                                        trackDragUntilRelease(isStillHeld = { event -> event.changes.any { it.pressed } }) { position ->
                                            val pointerTravel = viewport.screenToWorld(position, density) - downWorld
                                            val newBounds = startBounds.resizedFor(nodeToResize, grabbedEdges, pointerTravel)
                                            viewModel.setNodeBounds(nodeToResize.nodeId, newBounds)
                                        }
                                    }
                                    pressedNode != null -> {
                                        if (pressedNode.nodeId !in selection.selectedNodeIds) {
                                            selection = CanvasSelection.Nodes(setOf(pressedNode.nodeId))
                                            isSelectingMultiple = false
                                        }
                                        val startPositions = startPositionsOfSelectionAndGroupContents()
                                        viewModel.beginUndoStep()
                                        trackDragUntilRelease(isStillHeld = { event -> event.changes.any { it.pressed } }) { position ->
                                            viewModel.moveNodes(startPositions, viewport.screenToWorld(position, density) - downWorld)
                                        }
                                    }
                                    pressedEdge != null -> {
                                        selection = CanvasSelection.Edge(pressedEdge.edgeId)
                                        isSelectingMultiple = false
                                        val pressedCurve = screenCurveFor(pressedEdge, canvas.nodes.associateBy { it.nodeId }, viewport, density)
                                        val grabbedArrowHead = (pressedCurve?.closestProgressTo(downPosition) ?: 1f) >= 0.5f
                                        val detachedEnd = CanvasDragPreview.DraggingEdgeEnd(
                                            anchoredNodeId = if (grabbedArrowHead) pressedEdge.fromNodeId else pressedEdge.toNodeId,
                                            anchoredSide = if (grabbedArrowHead) pressedEdge.fromSide else pressedEdge.toSide,
                                            isDraggingArrowHead = grabbedArrowHead,
                                            looseEndWorld = downWorld,
                                            detachedEdgeId = pressedEdge.edgeId
                                        )
                                        dragPreview = detachedEnd
                                        trackDragUntilRelease(isStillHeld = { event -> event.changes.any { it.pressed } }) { position ->
                                            dragPreview = movedLooseEnd(detachedEnd, position)
                                        }
                                        val snapTarget = (dragPreview as? CanvasDragPreview.DraggingEdgeEnd)?.snapTarget
                                        dragPreview = null
                                        if (snapTarget != null) {
                                            viewModel.reconnectEdge(pressedEdge.edgeId, grabbedArrowHead, snapTarget.nodeId, snapTarget.side)
                                        }
                                    }
                                    else -> trackPinchAndPan { centroid, pan, zoom ->
                                        viewport = viewport.pannedBy(pan).zoomedAround(centroid, zoom, density)
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(noteId) {
                    if (!isDesktopPlatform) return@pointerInput
                    var lastClick: CanvasClick? = null

                    fun registerClick(targetKey: String, uptimeMillis: Long, position: Offset): Boolean {
                        val previousClick = lastClick
                        val isDoubleClick = previousClick != null &&
                            previousClick.targetKey == targetKey &&
                            uptimeMillis - previousClick.uptimeMillis <= viewConfiguration.doubleTapTimeoutMillis &&
                            (position - previousClick.position).getDistance() <= viewConfiguration.touchSlop * 2
                        lastClick = if (isDoubleClick) null else CanvasClick(targetKey, uptimeMillis, position)
                        return isDoubleClick
                    }

                    fun hitTester() = CanvasHitTester(canvas, viewport, density)

                    fun groupTitleScreenRectOf(group: CanvasNodeEntity): Rect = hitTester().groupTitleScreenRectOf(group)

                    fun textNodeAt(screenPoint: Offset, margin: Float = 0f): CanvasNodeEntity? =
                        hitTester().textNodeAt(screenPoint, margin)

                    fun groupAt(screenPoint: Offset, margin: Float = 0f): CanvasNodeEntity? =
                        hitTester().groupAt(screenPoint, margin)

                    fun nodeAt(screenPoint: Offset, margin: Float = 0f): CanvasNodeEntity? =
                        hitTester().nodeAt(screenPoint, margin)

                    fun handleAt(screenPoint: Offset): CanvasHandle? = hitTester().handleAt(
                        screenPoint,
                        candidateNodeIds = listOfNotNull(hoveredNodeId, selection.singleSelectedNodeId),
                        hitRadius = HANDLE_HIT_RADIUS.toPx()
                    )

                    fun resizeZoneAt(screenPoint: Offset): Pair<CanvasNodeEntity, CanvasResizeEdges>? =
                        hitTester().resizeZoneAt(screenPoint, RESIZE_GRAB_DISTANCE.toPx())

                    fun edgeAt(screenPoint: Offset): CanvasEdgeEntity? =
                        hitTester().edgeAt(screenPoint, EDGE_HIT_DISTANCE.toPx())

                    fun snapTargetNear(screenPoint: Offset, anchoredNodeId: String): CanvasHandle? =
                        hitTester().snapTargetNear(screenPoint, anchoredNodeId, EDGE_SNAP_RADIUS.toPx())

                    fun movedLooseEnd(dragging: CanvasDragPreview.DraggingEdgeEnd, screenPoint: Offset) = dragging.copy(
                        looseEndWorld = viewport.screenToWorld(screenPoint, density),
                        snapTarget = snapTargetNear(screenPoint, dragging.anchoredNodeId)
                    )

                    awaitEachGesture {
                        val pressEvent = awaitPressWhileTrackingHoverAndWheel(
                            onHover = { screenPoint ->
                                if (activeTool.drawsOnBoard) {
                                    hoveredNodeId = null
                                    pointerIcon = PointerIcon.Crosshair
                                    eraserScreenPosition = if (activeTool == CanvasTool.ERASER) screenPoint else null
                                } else if (activeTool == CanvasTool.TEXT) {
                                    hoveredNodeId = null
                                    pointerIcon = PointerIcon.Text
                                } else {
                                    hoveredNodeId = screenPoint?.let { nodeAt(it, margin = HANDLE_HIT_RADIUS.toPx())?.nodeId }
                                    val resizeEdges = screenPoint
                                        ?.takeIf { handleAt(it) == null }
                                        ?.let { resizeZoneAt(it)?.second }
                                    pointerIcon = resizeEdges?.let { canvasResizePointerIcon(it) } ?: PointerIcon.Default
                                }
                            },
                            onWheel = { event ->
                                val wheelChange = event.changes.first()
                                val wheelPixels = wheelChange.scrollDelta * WHEEL_PAN_PIXELS_PER_NOTCH * density
                                val boxUnderPointer = textNodeAt(wheelChange.position)
                                val boxScrollState = boxUnderPointer
                                    ?.let { nodeScrollStates[it.nodeId] }
                                    ?.takeIf { it.maxValue > 0 }
                                when {
                                    event.keyboardModifiers.isCtrlPressed -> {
                                        animateZoom(WHEEL_ZOOM_STEP.pow(-wheelChange.scrollDelta.y), wheelChange.position)
                                        true
                                    }
                                    boxUnderPointer != null && boxUnderPointer.nodeId == editingNodeId -> false
                                    boxScrollState != null && wheelPixels.y != 0f -> {
                                        boxScrollState.dispatchRawDelta(wheelPixels.y)
                                        viewport = viewport.pannedBy(Offset(-wheelPixels.x, 0f))
                                        true
                                    }
                                    else -> {
                                        viewport = viewport.pannedBy(-wheelPixels)
                                        true
                                    }
                                }
                            }
                        )
                        val pressChange = pressEvent.changes.first()
                        val pressPosition = pressChange.position
                        if (activeTool == CanvasTool.SHAPES) dropActiveTool()

                        if (pressEvent.buttons.isBackPressed || pressEvent.buttons.isForwardPressed) {
                            if (horizontalScrollArrivesAsBackAndForwardButtons) {
                                pressChange.consume()
                                val scrollDirection = if (pressEvent.buttons.isBackPressed) -1f else 1f
                                viewport = viewport.pannedBy(Offset(-scrollDirection * WHEEL_PAN_PIXELS_PER_NOTCH * density, 0f))
                            }
                            return@awaitEachGesture
                        }

                        if (pressEvent.buttons.isTertiaryPressed) {
                            var previousPosition = pressPosition
                            pressChange.consume()
                            trackDragUntilRelease(isStillHeld = { it.buttons.isTertiaryPressed }) { position ->
                                viewport = viewport.pannedBy(position - previousPosition)
                                previousPosition = position
                            }
                            return@awaitEachGesture
                        }

                        if (activeTool.drawsOrErases && !pressEvent.buttons.isSecondaryPressed) {
                            pressChange.consume()
                            canvasFocusRequester.requestFocus()
                            val strokeTool = activeTool.strokeTool
                            if (strokeTool != null) {
                                startLiveStroke(pressPosition, pressChange.penPressure, strokeTool)
                                trackOnePointerUntilLift(pressChange.id) { position, pressure -> addLiveStrokePoint(position, pressure) }
                                finishLiveStroke()
                            } else {
                                viewModel.beginUndoStep()
                                var previousPosition = pressPosition
                                eraseStrokesAlong(pressPosition, pressPosition)
                                trackOnePointerUntilLift(pressChange.id) { position, _ ->
                                    eraseStrokesAlong(previousPosition, position)
                                    previousPosition = position
                                }
                            }
                            return@awaitEachGesture
                        }

                        if (activeTool == CanvasTool.LINE && !pressEvent.buttons.isSecondaryPressed) {
                            pressChange.consume()
                            canvasFocusRequester.requestFocus()
                            startLiveLine(pressPosition)
                            trackOnePointerUntilLift(pressChange.id) { position, _ -> moveLiveLineEnd(position) }
                            finishLiveLine(viewConfiguration.touchSlop)
                            return@awaitEachGesture
                        }

                        if (activeTool == CanvasTool.TEXT && !pressEvent.buttons.isSecondaryPressed) {
                            pressChange.consume()
                            trackDragUntilRelease(isStillHeld = { it.changes.first().pressed }) {}
                            startTextAt(pressPosition)
                            return@awaitEachGesture
                        }

                        val editingArea = hitTester().editingAreaOf(editingNodeId, RESIZE_GRAB_DISTANCE.toPx())
                        if (editingArea != null && editingArea.contains(pressPosition)) {
                            return@awaitEachGesture
                        }

                        if (pressEvent.buttons.isSecondaryPressed) {
                            val clickedTextNode = textNodeAt(pressPosition)
                            val clickedEdge = if (clickedTextNode == null) edgeAt(pressPosition) else null
                            val clickedGroup = if (clickedTextNode == null && clickedEdge == null) groupAt(pressPosition) else null
                            val clickedNodeId = (clickedTextNode ?: clickedGroup)?.nodeId
                            val clickedTarget = when {
                                clickedNodeId != null && clickedNodeId in selection.selectedNodeIds -> selection
                                clickedNodeId != null -> CanvasSelection.Nodes(setOf(clickedNodeId))
                                clickedEdge != null -> CanvasSelection.Edge(clickedEdge.edgeId)
                                else -> {
                                    showPasteOptionIfClipboardHasImage(pressPosition)
                                    return@awaitEachGesture
                                }
                            }
                            pressChange.consume()
                            editingNodeId = null
                            selection = clickedTarget
                            contextMenuRequest = CanvasContextMenuRequest(clickedTarget, pressPosition)
                            return@awaitEachGesture
                        }

                        pressChange.consume()
                        editingNodeId = null
                        canvasFocusRequester.requestFocus()
                        val pressWorld = viewport.screenToWorld(pressPosition, density)
                        val isShiftPressed = pressEvent.keyboardModifiers.isShiftPressed

                        val handle = handleAt(pressPosition)
                        if (handle != null) {
                            val newEdgeEnd = CanvasDragPreview.DraggingEdgeEnd(
                                anchoredNodeId = handle.nodeId,
                                anchoredSide = handle.side,
                                isDraggingArrowHead = true,
                                looseEndWorld = pressWorld
                            )
                            dragPreview = newEdgeEnd
                            trackDragUntilRelease(isStillHeld = { it.changes.first().pressed }) { position ->
                                dragPreview = movedLooseEnd(newEdgeEnd, position)
                            }
                            val snapTarget = (dragPreview as? CanvasDragPreview.DraggingEdgeEnd)?.snapTarget
                            dragPreview = null
                            if (snapTarget != null) {
                                viewModel.createEdge(handle.nodeId, handle.side, snapTarget.nodeId, snapTarget.side)
                            }
                            return@awaitEachGesture
                        }

                        val resizeZone = if (isShiftPressed) null else resizeZoneAt(pressPosition)
                        if (resizeZone != null) {
                            val (nodeToResize, grabbedEdges) = resizeZone
                            selection = CanvasSelection.Nodes(setOf(nodeToResize.nodeId))
                            viewModel.beginUndoStep()
                            val startBounds = nodeToResize.worldRect
                            trackDragUntilRelease(isStillHeld = { it.changes.first().pressed }) { position ->
                                val pointerTravel = viewport.screenToWorld(position, density) - pressWorld
                                val newBounds = startBounds.resizedFor(nodeToResize, grabbedEdges, pointerTravel)
                                viewModel.setNodeBounds(nodeToResize.nodeId, newBounds)
                            }
                            return@awaitEachGesture
                        }

                        val pressedTextNode = textNodeAt(pressPosition)
                        val pressedEdge = if (pressedTextNode == null) edgeAt(pressPosition) else null
                        val pressedGroup = if (pressedTextNode == null && pressedEdge == null) groupAt(pressPosition) else null
                        val pressedNode = pressedTextNode ?: pressedGroup

                        if (pressedNode != null && isShiftPressed) {
                            val currentlySelected = selection.selectedNodeIds
                            val toggledSelection = if (pressedNode.nodeId in currentlySelected) {
                                currentlySelected - pressedNode.nodeId
                            } else {
                                currentlySelected + pressedNode.nodeId
                            }
                            selection = if (toggledSelection.isEmpty()) CanvasSelection.None else CanvasSelection.Nodes(toggledSelection)
                            trackDragUntilRelease(isStillHeld = { it.changes.first().pressed }) {}
                            return@awaitEachGesture
                        }

                        if (pressedNode != null) {
                            if (pressedNode.nodeId !in selection.selectedNodeIds) {
                                selection = CanvasSelection.Nodes(setOf(pressedNode.nodeId))
                            }
                            val selectedIds = selection.selectedNodeIds
                            val selectedGroups = canvas.nodes.filter { it.nodeId in selectedIds && it.isGroup }
                            val movingNodeIds = selectedIds + selectedGroups.flatMap { canvas.membersOf(it) }.map { it.nodeId }
                            val startPositions = canvas.nodes
                                .filter { it.nodeId in movingNodeIds }
                                .associate { it.nodeId to Offset(it.x, it.y) }
                            var hasMoved = false
                            viewModel.beginUndoStep()
                            trackDragUntilRelease(isStillHeld = { it.changes.first().pressed }) { position ->
                                if ((position - pressPosition).getDistance() > viewConfiguration.touchSlop) hasMoved = true
                                if (hasMoved) {
                                    viewModel.moveNodes(startPositions, viewport.screenToWorld(position, density) - pressWorld)
                                }
                            }
                            if (!hasMoved) {
                                selection = CanvasSelection.Nodes(setOf(pressedNode.nodeId))
                                val pressedGroupTitle = pressedNode.isGroup && groupTitleScreenRectOf(pressedNode).contains(pressPosition)
                                val clickTargetKey = when {
                                    !pressedNode.isGroup -> "box:${pressedNode.nodeId}"
                                    pressedGroupTitle -> "group-title:${pressedNode.nodeId}"
                                    else -> "group-body:${pressedNode.nodeId}"
                                }
                                if (registerClick(clickTargetKey, pressChange.uptimeMillis, pressPosition)) {
                                    if (pressedNode.isGroup && !pressedGroupTitle) {
                                        createBoxAt(pressWorld, groupToGrowId = pressedNode.nodeId)
                                    } else if (!pressedNode.isImage) {
                                        editingNodeId = pressedNode.nodeId
                                    }
                                }
                            }
                            return@awaitEachGesture
                        }

                        if (pressedEdge != null) {
                            selection = CanvasSelection.Edge(pressedEdge.edgeId)
                            val pressedCurve = screenCurveFor(pressedEdge, canvas.nodes.associateBy { it.nodeId }, viewport, density)
                            val grabbedArrowHead = (pressedCurve?.closestProgressTo(pressPosition) ?: 1f) >= 0.5f
                            var detachedEnd: CanvasDragPreview.DraggingEdgeEnd? = null
                            trackDragUntilRelease(isStillHeld = { it.changes.first().pressed }) { position ->
                                if (detachedEnd == null && (position - pressPosition).getDistance() > viewConfiguration.touchSlop) {
                                    detachedEnd = CanvasDragPreview.DraggingEdgeEnd(
                                        anchoredNodeId = if (grabbedArrowHead) pressedEdge.fromNodeId else pressedEdge.toNodeId,
                                        anchoredSide = if (grabbedArrowHead) pressedEdge.fromSide else pressedEdge.toSide,
                                        isDraggingArrowHead = grabbedArrowHead,
                                        looseEndWorld = pressWorld,
                                        detachedEdgeId = pressedEdge.edgeId
                                    )
                                }
                                detachedEnd?.let { dragging -> dragPreview = movedLooseEnd(dragging, position) }
                            }
                            val snapTarget = (dragPreview as? CanvasDragPreview.DraggingEdgeEnd)?.snapTarget
                            dragPreview = null
                            if (snapTarget != null) {
                                viewModel.reconnectEdge(pressedEdge.edgeId, grabbedArrowHead, snapTarget.nodeId, snapTarget.side)
                            }
                            return@awaitEachGesture
                        }

                        if (!isShiftPressed) selection = CanvasSelection.None
                        trackDragUntilRelease(isStillHeld = { it.changes.first().pressed }) { position ->
                            if (dragPreview != null || (position - pressPosition).getDistance() > viewConfiguration.touchSlop) {
                                dragPreview = CanvasDragPreview.DrawingBox(pressWorld, viewport.screenToWorld(position, density))
                            }
                        }
                        val drawnBox = dragPreview as? CanvasDragPreview.DrawingBox
                        dragPreview = null
                        if (drawnBox != null) {
                            val newNodeId = viewModel.createNode(rectBetween(drawnBox.startWorld, drawnBox.currentWorld))
                            selection = CanvasSelection.Nodes(setOf(newNodeId))
                            editingNodeId = newNodeId
                        } else if (registerClick("empty", pressChange.uptimeMillis, pressPosition)) {
                            createBoxAt(pressWorld, groupToGrowId = null)
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (isDotGridVisible) drawDotGrid(viewport, pixelDensity, dotColor)
            }

            canvas.nodes.filter { it.isGroup }.sortedByDescending { it.width * it.height }.forEach { group ->
                key(group.nodeId) {
                    CanvasGroupCard(
                        group = group,
                        viewport = viewport,
                        isSelected = group.nodeId in selection.selectedNodeIds,
                        isEditingTitle = editingNodeId == group.nodeId,
                        cursorColor = accentColor,
                        onTitleChange = { title -> viewModel.updateNodeText(group.nodeId, title) }
                    )
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                val nodesById = canvas.nodes.associateBy { it.nodeId }
                val draggingEdgeEnd = dragPreview as? CanvasDragPreview.DraggingEdgeEnd
                canvas.edges.forEach { edge ->
                    if (edge.edgeId == draggingEdgeEnd?.detachedEdgeId) return@forEach
                    val curve = screenCurveFor(edge, nodesById, viewport, pixelDensity) ?: return@forEach
                    val isSelected = selection == CanvasSelection.Edge(edge.edgeId)
                    drawCanvasEdge(
                        curve = curve,
                        color = if (isSelected) accentColor else edgeColor,
                        strokeWidth = (if (isSelected) SELECTED_EDGE_STROKE_WIDTH else EDGE_STROKE_WIDTH).toPx(),
                        arrowSize = ARROW_SIZE.toPx()
                    )
                }
                draggingEdgeEnd?.let { preview ->
                    val anchoredNode = nodesById[preview.anchoredNodeId] ?: return@let
                    val anchoredPoint = anchoredNode.anchorOn(preview.anchoredSide)
                    val snapTarget = preview.snapTarget
                    val snappedNode = snapTarget?.let { nodesById[it.nodeId] }
                    val loosePoint = if (snapTarget != null && snappedNode != null) snappedNode.anchorOn(snapTarget.side) else preview.looseEndWorld
                    val looseSide = if (snappedNode != null) snapTarget.side else null
                    val controlDistance = edgeControlDistance(anchoredPoint, loosePoint, EDGE_MIN_CONTROL_UNITS, EDGE_MAX_CONTROL_UNITS)
                    val worldCurve = if (preview.isDraggingArrowHead) {
                        edgeCurve(anchoredPoint, preview.anchoredSide, loosePoint, looseSide, controlDistance)
                    } else {
                        edgeCurve(loosePoint, looseSide, anchoredPoint, preview.anchoredSide, controlDistance)
                    }
                    drawCanvasEdge(worldCurve.toScreen(viewport, pixelDensity), CanvasSelectionColor, SELECTED_EDGE_STROKE_WIDTH.toPx(), ARROW_SIZE.toPx())
                }
            }

            canvas.nodes.filter { !it.isGroup }.forEach { node ->
                key(node.nodeId) {
                    if (node.isFreeText) {
                        CanvasFreeTextCard(
                            node = node,
                            viewport = viewport,
                            isSelected = node.nodeId in selection.selectedNodeIds,
                            isEditing = editingNodeId == node.nodeId,
                            cursorColor = accentColor,
                            onTextChange = { text -> viewModel.updateNodeText(node.nodeId, text) },
                            onSizeMeasured = { width, height -> viewModel.setFreeTextSize(node.nodeId, width, height) }
                        )
                    } else if (node.isImage) {
                        CanvasImageCard(
                            node = node,
                            viewport = viewport,
                            isSelected = node.nodeId in selection.selectedNodeIds
                        )
                    } else {
                        CanvasNodeCard(
                            node = node,
                            viewport = viewport,
                            isSelected = node.nodeId in selection.selectedNodeIds,
                            isEditing = editingNodeId == node.nodeId,
                            scrollState = nodeScrollStates.getOrPut(node.nodeId) { ScrollState(initial = 0) },
                            cursorColor = accentColor,
                            onTextChange = { text -> viewModel.updateNodeText(node.nodeId, text) }
                        )
                    }
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                strokeCache.forgetStrokesNotIn(canvas.strokes)
                val pixelsPerUnit = viewport.pixelsPerUnit(pixelDensity)
                val visibleBoardArea = Rect(
                    topLeft = viewport.screenToWorld(Offset.Zero, pixelDensity),
                    bottomRight = viewport.screenToWorld(Offset(size.width, size.height), pixelDensity)
                )
                val (highlighterStrokes, penStrokes) = canvas.strokes
                    .filter { stroke -> strokeCache.boundsOnBoard(stroke).overlaps(visibleBoardArea) }
                    .partition { it.tool == CanvasStrokeTool.HIGHLIGHTER }

                val highlighterAlpha = if (isDarkTheme) HIGHLIGHTER_ALPHA_IN_DARK_THEME else HIGHLIGHTER_ALPHA_IN_LIGHT_THEME

                fun highlighterColorFor(colorName: String?, opacity: Float) =
                    highlightBackgroundFor(colorName, isDarkTheme).copy(alpha = highlighterAlpha * opacity)

                fun DrawScope.drawLiveStrokeIfUsing(tool: CanvasStrokeTool, color: Color) {
                    if (liveStrokeTool != tool || liveStrokePoints.isEmpty()) return
                    val style = styleFor(tool)
                    val outline = canvasStrokeOutline(liveStrokePoints, style.width, style.usesPressure, isComplete = false)
                    drawPath(outline.toSmoothPath(), color)
                }

                withTransform({
                    translate(viewport.panOffset.x, viewport.panOffset.y)
                    scale(pixelsPerUnit, pixelsPerUnit, pivot = Offset.Zero)
                }) {
                    highlighterStrokes.forEach { stroke ->
                        translate(stroke.x, stroke.y) { drawPath(strokeCache.pathFor(stroke), highlighterColorFor(stroke.color, stroke.opacity)) }
                    }
                    drawLiveStrokeIfUsing(CanvasStrokeTool.HIGHLIGHTER, highlighterColorFor(highlighterStyle.colorName, highlighterStyle.opacity))
                    penStrokes.forEach { stroke ->
                        val inkColor = penInkColorFor(stroke.color, stroke.opacity)
                        if (stroke.tool == CanvasStrokeTool.LINE) {
                            strokeCache.lineEndsOf(stroke)?.let { (lineStart, lineEnd) ->
                                drawCanvasLine(lineStart, lineEnd, stroke.width, inkColor, stroke.linePattern, stroke.hasArrowHead)
                            }
                        } else {
                            translate(stroke.x, stroke.y) { drawPath(strokeCache.pathFor(stroke), inkColor) }
                        }
                    }
                    liveLine?.let { (lineStart, lineEnd) ->
                        drawCanvasLine(
                            start = lineStart,
                            end = lineEnd,
                            width = lineStrokeStyle.width,
                            color = penInkColorFor(lineStrokeStyle.colorName, lineStrokeStyle.opacity),
                            pattern = lineStyle.pattern,
                            hasArrowHead = lineStyle.hasArrowHead
                        )
                    }
                    drawLiveStrokeIfUsing(CanvasStrokeTool.PEN, penInkColorFor(penStyle.colorName, penStyle.opacity))
                }
                eraserScreenPosition?.let { center ->
                    drawCircle(color = handleBorderColor, radius = eraserRadiusInPixels(), center = center, style = Stroke(width = 1.dp.toPx()))
                }
            }

            Canvas(Modifier.fillMaxSize()) {
                (dragPreview as? CanvasDragPreview.DrawingBox)?.let { preview ->
                    val screenRect = viewport.worldRectToScreen(rectBetween(preview.startWorld, preview.currentWorld), pixelDensity)
                    drawRect(
                        color = accentColor,
                        topLeft = screenRect.topLeft,
                        size = screenRect.size,
                        style = Stroke(width = EDGE_STROKE_WIDTH.toPx())
                    )
                }
                val draggingEdgeEnd = dragPreview as? CanvasDragPreview.DraggingEdgeEnd
                val handleRadius = (if (isDesktopPlatform) HANDLE_RADIUS else TOUCH_HANDLE_RADIUS).toPx()
                val handleNodeIds = listOfNotNull(hoveredNodeId, selection.singleSelectedNodeId.takeUnless { isSelectingMultiple })
                val nodesShowingHandles = if (draggingEdgeEnd != null) {
                    canvas.nodes.filter { it.nodeId != draggingEdgeEnd.anchoredNodeId }
                } else {
                    canvas.nodes.filter { it.nodeId in handleNodeIds && it.nodeId != editingNodeId }
                }
                nodesShowingHandles.forEach { node ->
                    CanvasSide.entries.forEach { side ->
                        val center = viewport.worldToScreen(node.anchorOn(side), pixelDensity)
                        drawCircle(color = handleFillColor, radius = handleRadius, center = center)
                        drawCircle(color = handleBorderColor, radius = handleRadius, center = center, style = Stroke(width = 1.dp.toPx()))
                    }
                }
                draggingEdgeEnd?.snapTarget?.let { snapTarget ->
                    val snappedNode = canvas.nodes.firstOrNull { it.nodeId == snapTarget.nodeId } ?: return@let
                    val center = viewport.worldToScreen(snappedNode.anchorOn(snapTarget.side), pixelDensity)
                    drawCircle(color = CanvasSelectionColor, radius = handleRadius * SNAPPED_HANDLE_SCALE, center = center)
                }
            }
        }

        val selectedNode = selection.singleSelectedNodeId?.let { nodeId -> canvas.nodes.firstOrNull { it.nodeId == nodeId } }
        val isTypingInFreeText = selectedNode != null && selectedNode.isFreeText && editingNodeId == selectedNode.nodeId
        if (selectedNode != null && dragPreview == null && !isSelectingMultiple && !isTypingInFreeText) {
            val pillAnchorWorldRect = if (selectedNode.isGroup) selectedNode.groupTitleWorldRect else selectedNode.worldRect
            val pillAnchorRect = viewport.worldRectToScreen(pillAnchorWorldRect, pixelDensity)
            CanvasSelectionPill(
                boxTopCenterOnScreen = Offset(pillAnchorRect.center.x, pillAnchorRect.top),
                currentColorName = selectedNode.color,
                onDelete = { deleteItems(CanvasSelection.Nodes(setOf(selectedNode.nodeId))) },
                onColorSelected = { colorName -> viewModel.setNodeColor(selectedNode.nodeId, colorName) },
                showsColorOption = !selectedNode.isFreeText && !selectedNode.isImage
            )
        }

        contextMenuRequest?.let { request ->
            CanvasContextMenu(
                anchorOnScreen = request.anchorOnScreen,
                options = if (request.target == CanvasSelection.None) {
                    val pasteWorldCenter = viewport.screenToWorld(request.anchorOnScreen, pixelDensity)
                    listOf(CanvasMenuOption("Paste image", Res.drawable.image) { viewModel.pasteImageFromClipboard(pasteWorldCenter) })
                } else {
                    contextMenuOptionsFor(request.target)
                },
                onDismiss = { contextMenuRequest = null }
            )
        }

        if (isAddingImage) CanvasAddingImageOverlay()

        pastePillAnchor?.let { anchor ->
            CanvasPastePill(
                anchorOnScreen = anchor,
                onPaste = {
                    pastePillAnchor = null
                    viewModel.pasteImageFromClipboard(viewport.screenToWorld(anchor, pixelDensity))
                }
            )
        }

        val areControlsVisible = !isEmbedded || isActive
        val isSelectionBarVisible = !isDesktopPlatform && isSelectingMultiple && selection != CanvasSelection.None

        fun undoCanvasStep() {
            editingNodeId = null
            viewModel.undo()
        }

        fun redoCanvasStep() {
            editingNodeId = null
            viewModel.redo()
        }

        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(
                    when {
                        isDesktopPlatform -> Modifier.padding(top = 20.dp, end = 22.dp)
                        isEmbedded -> Modifier.padding(top = 10.dp, end = 16.dp)
                        else -> Modifier.statusBarsPadding().padding(top = 10.dp, end = 16.dp)
                    }
                )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CanvasOptionsButton(
                    hazeState = hazeState,
                    showStickyNoteOption = !isStickyNote && isDesktopPlatform,
                    showMoveToTrashOption = !isEmbedded,
                    loadCurrentTitle = { viewModel.currentTitle() },
                    onRename = { newTitle -> viewModel.renameCanvas(newTitle) },
                    onOpenAsStickyNote = { StickyNoteWindowBus.open(noteId) },
                    onMoveToTrash = { viewModel.moveCanvasToTrash(onMoved = onNavigateBack) }
                )
                CanvasZoomButtons(
                    hazeState = hazeState,
                    onZoomIn = { zoomAroundBoardCenter(ZOOM_BUTTON_STEP) },
                    onShowBusiestArea = { showBusiestArea() },
                    onZoomOut = { zoomAroundBoardCenter(1f / ZOOM_BUTTON_STEP) }
                )
                CanvasUndoRedoButtons(
                    hazeState = hazeState,
                    onUndo = { undoCanvasStep() },
                    onRedo = { redoCanvasStep() }
                )
                CanvasDotGridButton(
                    hazeState = hazeState,
                    isDotGridVisible = isDotGridVisible,
                    onToggle = {
                        isDotGridVisible = !isDotGridVisible
                        viewModel.saveDotGridVisible(noteId, isDotGridVisible)
                    }
                )
            }
        }

        val isDesktopBackButtonVisible = isDesktopPlatform && !isEmbedded && !isStickyNote && showBackButton
        if (isDesktopBackButtonVisible) {
            CanvasBackButton(
                hazeState = hazeState,
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 20.dp, start = 22.dp)
            )
        }

        val hasControlAboveToolPanel = isEmbedded || isDesktopBackButtonVisible
        val toolPanelTop = if (hasControlAboveToolPanel) 76.dp else 20.dp
        val boardHeight = with(LocalDensity.current) { boardSize.height.toDp() }
        val toolPanelMaxHeight = (boardHeight - toolPanelTop - TOOL_PANEL_BOTTOM_CLEARANCE).coerceAtLeast(TOOL_PANEL_MIN_HEIGHT)
        val toolPanelModifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 22.dp, top = toolPanelTop)
            .heightIn(max = toolPanelMaxHeight)
        AnimatedVisibility(
            visible = isDesktopPlatform && activeTool == CanvasTool.PEN,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = toolPanelModifier
        ) {
            CanvasStrokeStylePanel(
                tool = CanvasStrokeTool.PEN,
                style = penStyle,
                hazeState = hazeState,
                onStyleChange = { newStyle -> penStyle = newStyle },
                onInteractionFinished = { canvasFocusRequester.requestFocus() }
            )
        }
        AnimatedVisibility(
            visible = isDesktopPlatform && activeTool == CanvasTool.HIGHLIGHTER,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = toolPanelModifier
        ) {
            CanvasStrokeStylePanel(
                tool = CanvasStrokeTool.HIGHLIGHTER,
                style = highlighterStyle,
                hazeState = hazeState,
                onStyleChange = { newStyle -> highlighterStyle = newStyle },
                onInteractionFinished = { canvasFocusRequester.requestFocus() }
            )
        }
        val selectedFreeText = selection.singleSelectedNodeId?.let { nodeId ->
            canvas.nodes.firstOrNull { it.nodeId == nodeId && it.isFreeText }
        }
        AnimatedVisibility(
            visible = isDesktopPlatform && (activeTool == CanvasTool.TEXT || selectedFreeText != null),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = toolPanelModifier
        ) {
            CanvasTextStylePanel(
                style = selectedFreeText?.textStyle ?: textToolStyle,
                hazeState = hazeState,
                onStyleChange = { newStyle ->
                    textToolStyle = newStyle
                    selectedFreeText?.let { viewModel.setFreeTextStyle(it.nodeId, newStyle) }
                }
            )
        }
        AnimatedVisibility(
            visible = isDesktopPlatform && activeTool == CanvasTool.LINE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = toolPanelModifier
        ) {
            CanvasLinePanel(
                selectedStyle = lineStyle,
                strokeStyle = lineStrokeStyle,
                hazeState = hazeState,
                onStyleChange = { newStyle -> lineStyle = newStyle },
                onStrokeStyleChange = { newStyle -> lineStrokeStyle = newStyle }
            )
        }
        AnimatedVisibility(
            visible = isDesktopPlatform && activeTool == CanvasTool.SHAPES,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = toolPanelModifier
        ) {
            CanvasShapePanel(hazeState = hazeState, onShapeSelected = { shape -> placeShapeAtBoardCenter(shape) })
        }
        AnimatedVisibility(
            visible = isDesktopPlatform && activeTool == CanvasTool.ERASER,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = toolPanelModifier
        ) {
            CanvasEraserSizePanel(
                radius = eraserRadius,
                hazeState = hazeState,
                onRadiusChange = { newRadius -> eraserRadius = newRadius },
                onInteractionFinished = { canvasFocusRequester.requestFocus() }
            )
        }

        if (isEmbedded) {
            val canvasTitle by viewModel.title.collectAsState()
            CanvasTitlePill(
                title = canvasTitle,
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .then(
                        if (isDesktopPlatform) Modifier.padding(start = 22.dp, top = 20.dp, end = 82.dp)
                        else Modifier.padding(start = 16.dp, top = 10.dp, end = 76.dp)
                    )
            )
        }

        if (!isDesktopPlatform) {
            if (!isEmbedded) {
                CanvasBackButton(
                    hazeState = hazeState,
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(top = 10.dp, start = 16.dp)
                )
            }

            CanvasSelectionActionBar(
                isVisible = isSelectionBarVisible,
                selectedCount = if (selection is CanvasSelection.Edge) 1 else selection.selectedNodeIds.size,
                options = contextMenuOptionsFor(selection).map { option ->
                    option.copy(onClick = {
                        option.onClick()
                        isSelectingMultiple = false
                    })
                },
                onClose = { leaveEditingAndSelection() },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter).then(if (isEmbedded) Modifier else Modifier.navigationBarsPadding())
            )
        }

        val areMobileToolSettingsAllowed = !isDesktopPlatform && areControlsVisible && !isSelectionBarVisible && editingNodeId == null
        val mobileToolSettingsModifier = Modifier
            .align(Alignment.BottomStart)
            .then(if (isEmbedded) Modifier else Modifier.navigationBarsPadding())
            .padding(start = 16.dp, bottom = 72.dp)

        fun toggleStrokeSettings(category: CanvasStrokeSettingsCategory) {
            openStrokeSettingsCategory = if (openStrokeSettingsCategory == category) null else category
        }

        AnimatedVisibility(
            visible = areMobileToolSettingsAllowed && activeTool == CanvasTool.PEN,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = mobileToolSettingsModifier
        ) {
            CanvasMobileStrokeSettings(
                tool = CanvasStrokeTool.PEN,
                style = penStyle,
                openCategory = openStrokeSettingsCategory,
                hazeState = hazeState,
                onCategoryClick = { category -> toggleStrokeSettings(category) },
                onStyleChange = { newStyle -> penStyle = newStyle }
            )
        }
        AnimatedVisibility(
            visible = areMobileToolSettingsAllowed && activeTool == CanvasTool.HIGHLIGHTER,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = mobileToolSettingsModifier
        ) {
            CanvasMobileStrokeSettings(
                tool = CanvasStrokeTool.HIGHLIGHTER,
                style = highlighterStyle,
                openCategory = openStrokeSettingsCategory,
                hazeState = hazeState,
                onCategoryClick = { category -> toggleStrokeSettings(category) },
                onStyleChange = { newStyle -> highlighterStyle = newStyle }
            )
        }
        AnimatedVisibility(
            visible = areMobileToolSettingsAllowed && activeTool == CanvasTool.LINE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = mobileToolSettingsModifier
        ) {
            CanvasMobileLineSettings(
                selectedStyle = lineStyle,
                strokeStyle = lineStrokeStyle,
                openCategory = openLineSettingsCategory,
                hazeState = hazeState,
                onCategoryClick = { category ->
                    openLineSettingsCategory = if (openLineSettingsCategory == category) null else category
                },
                onStyleChange = { newStyle -> lineStyle = newStyle },
                onStrokeStyleChange = { newStyle -> lineStrokeStyle = newStyle }
            )
        }
        AnimatedVisibility(
            visible = areMobileToolSettingsAllowed && activeTool == CanvasTool.SHAPES,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = mobileToolSettingsModifier.padding(end = 16.dp)
        ) {
            CanvasMobileShapeSettings(hazeState = hazeState, onShapeSelected = { shape -> placeShapeAtBoardCenter(shape) })
        }
        AnimatedVisibility(
            visible = areMobileToolSettingsAllowed && activeTool == CanvasTool.ERASER,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = mobileToolSettingsModifier
        ) {
            CanvasMobileEraserSettings(
                radius = eraserRadius,
                hazeState = hazeState,
                onRadiusChange = { newRadius -> eraserRadius = newRadius }
            )
        }
        AnimatedVisibility(
            visible = areMobileToolSettingsAllowed && (activeTool == CanvasTool.TEXT || selectedFreeText != null),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = mobileToolSettingsModifier
        ) {
            CanvasMobileTextSettings(
                style = selectedFreeText?.textStyle ?: textToolStyle,
                openCategory = openTextSettingsCategory,
                hazeState = hazeState,
                onCategoryClick = { category ->
                    openTextSettingsCategory = if (openTextSettingsCategory == category) null else category
                },
                onStyleChange = { newStyle ->
                    textToolStyle = newStyle
                    selectedFreeText?.let { viewModel.setFreeTextStyle(it.nodeId, newStyle) }
                }
            )
        }

        if (isDesktopPlatform || (!isSelectionBarVisible && editingNodeId == null)) {
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .then(
                        when {
                            isDesktopPlatform -> Modifier.padding(start = 22.dp, bottom = 20.dp)
                            isEmbedded -> Modifier.padding(start = 16.dp, bottom = 16.dp)
                            else -> Modifier.navigationBarsPadding().padding(start = 16.dp, bottom = 16.dp)
                        }
                    )
            ) {
                CanvasToolbar(
                    hazeState = hazeState,
                    activeTool = activeTool,
                    onToolClick = { tool ->
                        activeTool = if (activeTool == tool || tool == CanvasTool.IMAGE) null else tool
                        openStrokeSettingsCategory = null
                        openTextSettingsCategory = null
                        openLineSettingsCategory = null
                        editingNodeId = null
                        selection = CanvasSelection.None
                        isSelectingMultiple = false
                        hoveredNodeId = null
                        eraserScreenPosition = null
                        pointerIcon = when {
                            activeTool.drawsOnBoard -> PointerIcon.Crosshair
                            activeTool == CanvasTool.TEXT -> PointerIcon.Text
                            else -> PointerIcon.Default
                        }
                        canvasFocusRequester.requestFocus()
                        if (tool == CanvasTool.IMAGE) {
                            pickImage { path -> viewModel.addImageFromFile(path, boardCenterInWorld()) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CanvasGroupCard(
    group: CanvasNodeEntity,
    viewport: CanvasViewport,
    isSelected: Boolean,
    isEditingTitle: Boolean,
    cursorColor: Color,
    onTitleChange: (String) -> Unit
) {
    val baseDensity = LocalDensity.current
    val screenTopLeft = viewport.worldToScreen(group.groupTitleWorldRect.topLeft, baseDensity.density)
    val zoomedDensity = Density(baseDensity.density * viewport.zoom, baseDensity.fontScale)
    val groupShape = RoundedCornerShape(GROUP_CORNER_RADIUS)
    val selectionBorderWidth = (2f / viewport.zoom).dp
    val groupBorderWidth = (1f / viewport.zoom).dp
    val groupBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = GROUP_BORDER_ALPHA)
    val fillColor = if (group.color == null) {
        MaterialTheme.colorScheme.background
    } else {
        canvasNodeBackgroundFor(group.color).copy(alpha = COLORED_GROUP_FILL_ALPHA)
    }
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = GROUP_TITLE_ALPHA),
        fontWeight = FontWeight.Medium
    )

    Box(
        Modifier
            .offset { IntOffset(screenTopLeft.x.roundToInt(), screenTopLeft.y.roundToInt()) }
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
    ) {
        CompositionLocalProvider(LocalDensity provides zoomedDensity) {
            Column {
                Box(
                    modifier = Modifier
                        .size(group.width.dp, CANVAS_GROUP_TITLE_HEIGHT.dp)
                        .padding(start = 4.dp, bottom = 4.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    if (isEditingTitle) {
                        CanvasNodeTextField(
                            text = group.text,
                            textStyle = titleStyle,
                            cursorColor = cursorColor,
                            onTextChange = onTitleChange,
                            singleLine = true
                        )
                    } else {
                        Text(text = group.text, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box(
                    Modifier
                        .size(group.width.dp, group.height.dp)
                        .background(fillColor, groupShape)
                        .then(
                            if (isSelected) Modifier.border(selectionBorderWidth, CanvasSelectionColor, groupShape)
                            else Modifier.border(groupBorderWidth, groupBorderColor, groupShape)
                        )
                )
            }
        }
    }
}

@Composable
private fun CanvasNodeCard(
    node: CanvasNodeEntity,
    viewport: CanvasViewport,
    isSelected: Boolean,
    isEditing: Boolean,
    scrollState: ScrollState,
    cursorColor: Color,
    onTextChange: (String) -> Unit
) {
    val baseDensity = LocalDensity.current
    val screenTopLeft = viewport.worldToScreen(Offset(node.x, node.y), baseDensity.density)
    val zoomedDensity = Density(baseDensity.density * viewport.zoom, baseDensity.fontScale)
    val shape = node.shape
    val cardShape = remember(shape) {
        if (shape.isPlainCard) RoundedCornerShape(CARD_CORNER_RADIUS) else CanvasNodeOutlineShape(shape, CARD_CORNER_RADIUS)
    }
    val selectionBorderWidth = (2f / viewport.zoom).dp
    val shapeLineWidth = (1f / viewport.zoom).dp
    val shapeLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = SHAPE_LINE_ALPHA)
    val backgroundColor = canvasNodeBackgroundFor(node.color)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = if (shape.hasRectangularTextArea) TextAlign.Start else TextAlign.Center
    )
    val textPadding = shape.textPadding(node.width, node.height)

    Box(
        Modifier
            .offset { IntOffset(screenTopLeft.x.roundToInt(), screenTopLeft.y.roundToInt()) }
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
    ) {
        CompositionLocalProvider(LocalDensity provides zoomedDensity) {
            Box(
                modifier = Modifier
                    .size(node.width.dp, node.height.dp)
                    .then(if (isSelected) Modifier.border(selectionBorderWidth, CanvasSelectionColor, cardShape) else Modifier)
                    .clip(cardShape)
                    .background(backgroundColor)
                    .then(
                        if (shape.isPlainCard) Modifier
                        else Modifier.drawBehind { drawShapeLines(shape, shapeLineColor, shapeLineWidth.toPx()) }
                    )
                    .padding(
                        start = textPadding.start.dp,
                        top = textPadding.top.dp,
                        end = textPadding.end.dp,
                        bottom = textPadding.bottom.dp
                    ),
                contentAlignment = if (shape.hasRectangularTextArea) Alignment.TopStart else Alignment.Center
            ) {
                if (isEditing) {
                    CanvasNodeTextField(
                        text = node.text,
                        textStyle = textStyle,
                        cursorColor = cursorColor,
                        onTextChange = onTextChange,
                        modifier = if (shape.hasRectangularTextArea) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
                    )
                } else {
                    Text(text = node.text, style = textStyle, modifier = Modifier.verticalScroll(scrollState))
                }
            }
        }
    }
}

@Composable
private fun CanvasFreeTextCard(
    node: CanvasNodeEntity,
    viewport: CanvasViewport,
    isSelected: Boolean,
    isEditing: Boolean,
    cursorColor: Color,
    onTextChange: (String) -> Unit,
    onSizeMeasured: (width: Float, height: Float) -> Unit
) {
    val baseDensity = LocalDensity.current
    val screenTopLeft = viewport.worldToScreen(Offset(node.x, node.y), baseDensity.density)
    val zoomedDensity = Density(baseDensity.density * viewport.zoom, baseDensity.fontScale)
    val selectionBorderWidth = (1f / viewport.zoom).dp
    val style = node.textStyle
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = CanvasInkColor.named(style.textColor)?.color ?: MaterialTheme.colorScheme.onSurface,
        fontFamily = CanvasTextFont.named(style.fontFamily).fontFamily(),
        fontWeight = FontWeight(style.fontWeight),
        fontSize = style.fontSize.sp,
        lineHeight = (style.fontSize * CANVAS_FREE_TEXT_LINE_HEIGHT_RATIO).sp,
        textAlign = CanvasTextAlignment.named(style.textAlign).textAlign
    )
    val backgroundColor = style.backgroundColor?.let { canvasNodeBackgroundFor(it) }

    Box(
        Modifier
            .offset { IntOffset(screenTopLeft.x.roundToInt(), screenTopLeft.y.roundToInt()) }
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
    ) {
        CompositionLocalProvider(LocalDensity provides zoomedDensity) {
            Box(
                modifier = Modifier
                    .onSizeChanged { size ->
                        val measuredWidth = size.width / zoomedDensity.density
                        val measuredHeight = size.height / zoomedDensity.density
                        val sizeChanged = abs(measuredWidth - node.width) > FREE_TEXT_SIZE_CHANGE_TO_SAVE ||
                            abs(measuredHeight - node.height) > FREE_TEXT_SIZE_CHANGE_TO_SAVE
                        if ((isEditing || isSelected) && sizeChanged) onSizeMeasured(measuredWidth, measuredHeight)
                    }
                    .then(
                        if (isSelected && !isEditing) Modifier.border(selectionBorderWidth, CanvasSelectionColor, RoundedCornerShape(CARD_CORNER_RADIUS))
                        else Modifier
                    )
                    .then(
                        if (backgroundColor != null) Modifier.background(backgroundColor, RoundedCornerShape(CARD_CORNER_RADIUS))
                        else Modifier
                    )
                    .padding(CANVAS_FREE_TEXT_PADDING.dp)
            ) {
                if (isEditing) {
                    CanvasNodeTextField(
                        text = node.text,
                        textStyle = textStyle,
                        cursorColor = cursorColor,
                        onTextChange = onTextChange,
                        modifier = Modifier.widthIn(min = FREE_TEXT_MIN_TEXT_WIDTH).width(IntrinsicSize.Max)
                    )
                } else {
                    Text(text = node.text, style = textStyle, softWrap = false)
                }
            }
        }
    }
}

private fun DrawScope.drawShapeLines(shape: CanvasNodeShape, color: Color, lineWidth: Float) {
    val cornerRadius = CARD_CORNER_RADIUS.toPx()
    drawPath(shape.outlinePath(size, cornerRadius), color, style = Stroke(width = lineWidth * 2f))
    shape.detailLinePath(size, CANVAS_DOUBLE_LINE_GAP.dp.toPx(), cornerRadius)?.let { detailLine ->
        drawPath(detailLine, color, style = Stroke(width = lineWidth))
    }
}

@Composable
private fun CanvasNodeTextField(
    text: String,
    textStyle: TextStyle,
    cursorColor: Color,
    onTextChange: (String) -> Unit,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            if (newValue.text != text) onTextChange(newValue.text)
        },
        textStyle = textStyle,
        singleLine = singleLine,
        cursorBrush = SolidColor(cursorColor),
        modifier = modifier.focusRequester(focusRequester)
    )
}

private suspend fun AwaitPointerEventScope.awaitPressWhileTrackingHoverAndWheel(
    onHover: (Offset?) -> Unit,
    onWheel: (PointerEvent) -> Boolean
): PointerEvent {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        when (event.type) {
            PointerEventType.Press -> return event
            PointerEventType.Scroll -> {
                if (onWheel(event)) event.changes.forEach { it.consume() }
            }
            PointerEventType.Exit -> onHover(null)
            else -> onHover(event.changes.first().position)
        }
    }
}

private enum class CanvasTouchStart { Tap, Drag, LongPress, Pinch }

private enum class OnePointerGestureEnd { Lifted, SecondFingerDown }

private val CanvasTool?.strokeTool: CanvasStrokeTool?
    get() = when (this) {
        CanvasTool.PEN -> CanvasStrokeTool.PEN
        CanvasTool.HIGHLIGHTER -> CanvasStrokeTool.HIGHLIGHTER
        else -> null
    }

private val CanvasTool?.drawsOrErases: Boolean
    get() = strokeTool != null || this == CanvasTool.ERASER

private val CanvasTool?.drawsOnBoard: Boolean
    get() = drawsOrErases || this == CanvasTool.LINE

private val PointerInputChange.isFromStylus: Boolean
    get() = type == PointerType.Stylus || type == PointerType.Eraser

private val PointerInputChange.penPressure: Float?
    get() = if (type == PointerType.Stylus) pressure else null

private suspend fun AwaitPointerEventScope.trackOnePointerUntilLift(
    pointerId: PointerId,
    onMove: (position: Offset, pressure: Float?) -> Unit
): OnePointerGestureEnd {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        if (event.changes.count { it.pressed } >= 2) return OnePointerGestureEnd.SecondFingerDown
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return OnePointerGestureEnd.Lifted
        change.historical.forEach { onMove(it.position, change.penPressure) }
        onMove(change.position, change.penPressure)
        if (!change.pressed) return OnePointerGestureEnd.Lifted
    }
}

private suspend fun AwaitPointerEventScope.awaitTouchStart(downId: PointerId, downPosition: Offset): CanvasTouchStart {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        if (event.changes.count { it.pressed } >= 2) return CanvasTouchStart.Pinch
        val change = event.changes.firstOrNull { it.id == downId } ?: return CanvasTouchStart.Tap
        if (!change.pressed) return CanvasTouchStart.Tap
        if ((change.position - downPosition).getDistance() > viewConfiguration.touchSlop) return CanvasTouchStart.Drag
    }
}

private suspend fun AwaitPointerEventScope.trackPinchAndPan(onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.none { it.pressed }) return
        val centroid = event.calculateCentroid(useCurrent = true)
        if (centroid != Offset.Unspecified) onTransform(centroid, event.calculatePan(), event.calculateZoom())
        event.changes.forEach { it.consume() }
    }
}

private suspend fun AwaitPointerEventScope.trackDragUntilRelease(
    isStillHeld: (PointerEvent) -> Boolean,
    onMove: (Offset) -> Unit
): Offset {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.first()
        change.consume()
        if (!isStillHeld(event)) return change.position
        onMove(change.position)
    }
}

internal fun screenCurveFor(
    edge: CanvasEdgeEntity,
    nodesById: Map<String, CanvasNodeEntity>,
    viewport: CanvasViewport,
    density: Float
): CanvasCurve? {
    val fromNode = nodesById[edge.fromNodeId] ?: return null
    val toNode = nodesById[edge.toNodeId] ?: return null
    val startWorld = fromNode.anchorOn(edge.fromSide)
    val endWorld = toNode.anchorOn(edge.toSide)
    return edgeCurve(
        start = startWorld,
        startSide = edge.fromSide,
        end = endWorld,
        endSide = edge.toSide,
        controlDistance = edgeControlDistance(startWorld, endWorld, EDGE_MIN_CONTROL_UNITS, EDGE_MAX_CONTROL_UNITS)
    ).toScreen(viewport, density)
}

private fun CanvasCurve.toScreen(viewport: CanvasViewport, density: Float): CanvasCurve = CanvasCurve(
    start = viewport.worldToScreen(start, density),
    startControl = viewport.worldToScreen(startControl, density),
    endControl = viewport.worldToScreen(endControl, density),
    end = viewport.worldToScreen(end, density)
)

private fun DrawScope.drawDotGrid(viewport: CanvasViewport, density: Float, color: Color) {
    var spacing = GRID_SPACING_UNITS * viewport.pixelsPerUnit(density)
    while (spacing < 14f * density) spacing *= 2f
    val dots = mutableListOf<Offset>()
    var x = viewport.panOffset.x.mod(spacing)
    while (x < size.width) {
        var y = viewport.panOffset.y.mod(spacing)
        while (y < size.height) {
            dots.add(Offset(x, y))
            y += spacing
        }
        x += spacing
    }
    drawPoints(dots, PointMode.Points, color, strokeWidth = 2f * density, cap = StrokeCap.Round)
}

private fun DrawScope.drawCanvasEdge(curve: CanvasCurve, color: Color, strokeWidth: Float, arrowSize: Float) {
    val directionIntoTarget = (curve.end - curve.endControl).takeIf { it.getDistance() > 0.01f } ?: (curve.end - curve.start)
    val directionLength = directionIntoTarget.getDistance()
    if (directionLength < 0.01f) return

    val linePath = Path().apply {
        moveTo(curve.start.x, curve.start.y)
        cubicTo(curve.startControl.x, curve.startControl.y, curve.endControl.x, curve.endControl.y, curve.end.x, curve.end.y)
    }
    drawPath(linePath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    drawOpenArrowHead(
        tip = curve.end,
        directionIntoTip = directionIntoTarget / directionLength,
        wingLength = arrowSize,
        color = color,
        strokeWidth = strokeWidth
    )
}
