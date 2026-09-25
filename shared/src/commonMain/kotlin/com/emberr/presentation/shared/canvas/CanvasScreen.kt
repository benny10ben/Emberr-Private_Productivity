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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeShape
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.isGroup
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.domain.util.system.triggerHapticFeedback
import com.emberr.domain.canvas.membersOf
import com.emberr.presentation.shared.StickyNoteWindowBus
import com.emberr.presentation.shared.components.KmpBackHandler
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.group
import emberr.shared.generated.resources.square
import emberr.shared.generated.resources.trash
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
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
private val KEYBOARD_CLEARANCE = 24.dp
private const val ZOOM_BUTTON_STEP = 1.25f
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
    onNavigateBack: () -> Unit = {},
    viewModel: CanvasViewModel = koinViewModel(key = "canvas:$noteId")
) {
    LaunchedEffect(noteId) { viewModel.loadCanvas(noteId) }

    val canvas by viewModel.canvas.collectAsState()
    var viewport by remember(noteId) { mutableStateOf(CanvasViewport()) }
    var selection by remember(noteId) { mutableStateOf<CanvasSelection>(CanvasSelection.None) }
    var editingNodeId by remember(noteId) { mutableStateOf<String?>(null) }
    var hoveredNodeId by remember(noteId) { mutableStateOf<String?>(null) }
    var dragPreview by remember(noteId) { mutableStateOf<CanvasDragPreview?>(null) }
    var contextMenuRequest by remember(noteId) { mutableStateOf<CanvasContextMenuRequest?>(null) }
    var isSelectingMultiple by remember(noteId) { mutableStateOf(false) }
    var isShapePickerOpen by remember(noteId) { mutableStateOf(false) }
    var pointerIcon by remember(noteId) { mutableStateOf(PointerIcon.Default) }
    var boardSize by remember(noteId) { mutableStateOf(IntSize.Zero) }
    val zoomAnimation = remember(noteId) { Animatable(1f) }
    val panAnimation = remember(noteId) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val zoomScope = rememberCoroutineScope()
    val canvasFocusRequester = remember { FocusRequester() }
    val hazeState = remember { HazeState() }
    val nodeScrollStates = remember(noteId) { mutableMapOf<String, ScrollState>() }
    LaunchedEffect(editingNodeId) { viewModel.finishTextEditStep() }
    LaunchedEffect(isActive) {
        if (!isEmbedded) return@LaunchedEffect
        if (isActive) {
            canvasFocusRequester.requestFocus()
        } else {
            editingNodeId = null
            selection = CanvasSelection.None
            isSelectingMultiple = false
            isShapePickerOpen = false
            hoveredNodeId = null
            pointerIcon = PointerIcon.Default
        }
    }

    val pixelDensity = LocalDensity.current.density
    val backgroundColor = MaterialTheme.colorScheme.background
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DOT_ALPHA)
    val edgeColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    val accentColor = MaterialTheme.colorScheme.primary
    val handleFillColor = MaterialTheme.colorScheme.surface
    val handleBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

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

    KmpBackHandler(enabled = !isDesktopPlatform && isShapePickerOpen) {
        isShapePickerOpen = false
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
                        event.key == Key.Escape && isShapePickerOpen -> {
                            isShapePickerOpen = false
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
                        isShapePickerOpen = false
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
                                            } else {
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
                                            val newBounds = startBounds.resizedForShape(nodeToResize.shape, grabbedEdges, pointerTravel)
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
                                hoveredNodeId = screenPoint?.let { nodeAt(it, margin = HANDLE_HIT_RADIUS.toPx())?.nodeId }
                                val resizeEdges = screenPoint
                                    ?.takeIf { handleAt(it) == null }
                                    ?.let { resizeZoneAt(it)?.second }
                                pointerIcon = resizeEdges?.let { canvasResizePointerIcon(it) } ?: PointerIcon.Default
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
                        isShapePickerOpen = false

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
                                else -> return@awaitEachGesture
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
                                val newBounds = startBounds.resizedForShape(nodeToResize.shape, grabbedEdges, pointerTravel)
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
                                    } else {
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
                drawDotGrid(viewport, pixelDensity, dotColor)
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
                    val looseSide = if (snappedNode != null) snapTarget?.side else null
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
        if (selectedNode != null && dragPreview == null && !isSelectingMultiple) {
            val pillAnchorWorldRect = if (selectedNode.isGroup) selectedNode.groupTitleWorldRect else selectedNode.worldRect
            val pillAnchorRect = viewport.worldRectToScreen(pillAnchorWorldRect, pixelDensity)
            CanvasSelectionPill(
                boxTopCenterOnScreen = Offset(pillAnchorRect.center.x, pillAnchorRect.top),
                currentColorName = selectedNode.color,
                onDelete = { deleteItems(CanvasSelection.Nodes(setOf(selectedNode.nodeId))) },
                onColorSelected = { colorName -> viewModel.setNodeColor(selectedNode.nodeId, colorName) }
            )
        }

        contextMenuRequest?.let { request ->
            CanvasContextMenu(
                anchorOnScreen = request.anchorOnScreen,
                options = contextMenuOptionsFor(request.target),
                onDismiss = { contextMenuRequest = null }
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
                if (!isEmbedded) {
                    CanvasUndoRedoButtons(
                        hazeState = hazeState,
                        onUndo = { undoCanvasStep() },
                        onRedo = { redoCanvasStep() }
                    )
                }
            }
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

            AnimatedVisibility(
                visible = isActive && !isSelectionBarVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .then(
                        if (isDesktopPlatform) Modifier.padding(start = 22.dp, bottom = 20.dp)
                        else Modifier.padding(start = 16.dp, bottom = 16.dp)
                    )
            ) {
                CanvasUndoRedoButtons(
                    hazeState = hazeState,
                    onUndo = { undoCanvasStep() },
                    onRedo = { redoCanvasStep() },
                    isVertical = false
                )
            }
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

        if (isDesktopPlatform || (!isSelectionBarVisible && editingNodeId == null)) {
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(
                        when {
                            isDesktopPlatform -> Modifier.padding(end = 22.dp, bottom = 20.dp)
                            isEmbedded -> Modifier.padding(end = 16.dp, bottom = 16.dp)
                            else -> Modifier.navigationBarsPadding().padding(end = 16.dp, bottom = 16.dp)
                        }
                    )
            ) {
                CanvasAddShapeButton(
                    isOpen = isShapePickerOpen,
                    hazeState = hazeState,
                    onToggle = { isShapePickerOpen = !isShapePickerOpen },
                    onShapeSelected = { shape ->
                        isShapePickerOpen = false
                        val boardCenter = Offset(boardSize.width / 2f, boardSize.height / 2f)
                        createBoxAt(viewport.screenToWorld(boardCenter, pixelDensity), groupToGrowId = null, shape = shape)
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
    val unitDirection = directionIntoTarget / directionLength
    val perpendicular = Offset(-unitDirection.y, unitDirection.x)
    val arrowBase = curve.end - unitDirection * arrowSize

    val linePath = Path().apply {
        moveTo(curve.start.x, curve.start.y)
        cubicTo(curve.startControl.x, curve.startControl.y, curve.endControl.x, curve.endControl.y, arrowBase.x, arrowBase.y)
    }
    drawPath(linePath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

    val arrowPath = Path().apply {
        moveTo(curve.end.x, curve.end.y)
        val leftWing = arrowBase + perpendicular * (arrowSize * 0.5f)
        val rightWing = arrowBase - perpendicular * (arrowSize * 0.5f)
        lineTo(leftWing.x, leftWing.y)
        lineTo(rightWing.x, rightWing.y)
        close()
    }
    drawPath(arrowPath, color)
}
