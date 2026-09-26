package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeShape
import com.emberr.data.local.room.entity.CanvasNodeType
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.data.local.room.entity.CanvasStrokeEntity
import com.emberr.data.local.room.entity.CanvasStrokeTool
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.canvas.CanvasLineStyle
import com.emberr.domain.canvas.CanvasStrokeStyle
import com.emberr.domain.canvas.CanvasTextStyle
import com.emberr.domain.canvas.CanvasToolSettingsStore
import com.emberr.domain.canvas.CanvasRepository
import com.emberr.domain.canvas.CanvasStrokePoint
import com.emberr.domain.canvas.CanvasStrokePoints
import com.emberr.domain.canvas.CanvasViewPosition
import com.emberr.domain.canvas.CanvasViewPositionStore
import com.emberr.domain.canvas.isFreeText
import com.emberr.domain.canvas.isGroup
import com.emberr.domain.canvas.isInside
import com.emberr.domain.canvas.membersOf
import com.emberr.domain.canvas.withTextStyle
import com.emberr.domain.model.NoteContent
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.media.ClipboardImage
import com.emberr.domain.util.media.ImageClipboard
import com.emberr.domain.util.media.MediaInfo
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.media.readImagePixelSize
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.domain.util.sync.NoteSyncEvent
import com.emberr.domain.util.sync.SyncEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

private const val SAVE_DELAY_MILLIS = 500L
private const val DEFAULT_GROUP_TITLE = "Group"
private const val NEW_IMAGE_LONGEST_SIDE = 320f

class CanvasViewModel(
    private val canvasRepository: CanvasRepository,
    private val noteRepository: NoteRepository,
    private val viewPositionStore: CanvasViewPositionStore,
    private val toolSettingsStore: CanvasToolSettingsStore,
    private val settingsManager: SettingsManager,
    private val mediaStorageHelper: MediaStorageHelper,
    private val appScope: CoroutineScope
) : ViewModel() {

    private val _canvas = MutableStateFlow(CanvasContent())
    val canvas: StateFlow<CanvasContent> = _canvas.asStateFlow()

    private val _isAddingImage = MutableStateFlow(false)
    val isAddingImage: StateFlow<Boolean> = _isAddingImage.asStateFlow()

    private var noteId: String? = null
    private val loadedNoteId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val title: StateFlow<String> = loadedNoteId
        .flatMapLatest { id -> if (id == null) flowOf(null) else noteRepository.observeNoteMetadata(id) }
        .map { it?.title.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    private val unsavedNodes = LinkedHashMap<String, CanvasNodeEntity>()
    private val unsavedEdges = LinkedHashMap<String, CanvasEdgeEntity>()
    private val unsavedStrokes = LinkedHashMap<String, CanvasStrokeEntity>()
    private val nodesBeingSaved = LinkedHashMap<String, CanvasNodeEntity>()
    private val edgesBeingSaved = LinkedHashMap<String, CanvasEdgeEntity>()
    private val strokesBeingSaved = LinkedHashMap<String, CanvasStrokeEntity>()
    private var saveJob: Job? = null
    private val history = CanvasHistory()
    private var textEditStepNodeId: String? = null
    private var freeTextCreatedInOpenStepId: String? = null
    private var lastIndexedSignature: Int? = null
    private var needsIndexBaseline = true

    init {
        ActiveEditorRegistry.registerCanvas(this)
        viewModelScope.launch {
            SyncEventBus.events.filterIsInstance<NoteSyncEvent.NoteChanged>().collect { event ->
                if (event.entityId == noteId) reloadFromDatabase()
            }
        }
        viewModelScope.launch {
            canvasRepository.locallySavedNoteIds.collect { savedNoteId ->
                if (savedNoteId == noteId) reloadFromDatabase()
            }
        }
    }

    suspend fun currentTitle(): String {
        val targetNoteId = noteId ?: return ""
        return noteRepository.getNoteById(targetNoteId)?.title.orEmpty()
    }

    fun renameCanvas(newTitle: String) {
        val targetNoteId = noteId ?: return
        appScope.launch(Dispatchers.IO) {
            SyncCoordinator.mutex.withLock {
                val metadata = noteRepository.getNoteById(targetNoteId) ?: return@withLock
                val content = noteRepository.getNoteContent(targetNoteId) ?: NoteContent(blocks = emptyList())
                noteRepository.saveNote(metadata.copy(title = newTitle), content)
            }
            noteRepository.indexCanvas(targetNoteId, _canvas.value)
            lastIndexedSignature = indexSignatureOf(_canvas.value)
        }
    }

    fun moveCanvasToTrash(onMoved: () -> Unit) {
        val targetNoteId = noteId ?: return
        saveJob?.cancel()
        saveUnsavedChanges()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                SyncCoordinator.mutex.withLock {
                    val metadata = noteRepository.getNoteById(targetNoteId) ?: return@withLock
                    val content = noteRepository.getNoteContent(targetNoteId) ?: NoteContent(blocks = emptyList())
                    val now = System.currentTimeMillis()
                    noteRepository.saveNote(metadata.copy(trashedAt = now, updatedAt = now), content)
                }
            }
            onMoved()
        }
    }

    fun loadCanvas(noteId: String) {
        if (this.noteId != noteId) {
            saveJob?.cancel()
            saveUnsavedChanges()
            indexInBackgroundIfChanged()
            lastIndexedSignature = null
            needsIndexBaseline = true
            this.noteId = noteId
            loadedNoteId.value = noteId
            history.clear()
            textEditStepNodeId = null
            _canvas.value = CanvasContent()
        }
        viewModelScope.launch { reloadFromDatabase() }
    }

    fun savedViewPosition(canvasNoteId: String): CanvasViewPosition? = viewPositionStore.load(canvasNoteId)

    fun saveViewPosition(canvasNoteId: String, position: CanvasViewPosition) {
        viewPositionStore.save(canvasNoteId, position)
    }

    fun savedStrokeStyle(tool: CanvasStrokeTool): CanvasStrokeStyle = toolSettingsStore.loadStrokeStyle(tool)

    fun saveStrokeStyle(tool: CanvasStrokeTool, style: CanvasStrokeStyle) {
        toolSettingsStore.saveStrokeStyle(tool, style)
    }

    fun savedEraserRadius(): Float = toolSettingsStore.loadEraserRadius()

    fun savedTextStyle(): CanvasTextStyle = toolSettingsStore.loadTextStyle()

    fun savedLineStyle(): CanvasLineStyle = toolSettingsStore.loadLineStyle()

    fun saveLineStyle(style: CanvasLineStyle) {
        toolSettingsStore.saveLineStyle(style)
    }

    fun saveTextStyle(style: CanvasTextStyle) {
        toolSettingsStore.saveTextStyle(style)
    }

    fun saveEraserRadius(radius: Float) {
        toolSettingsStore.saveEraserRadius(radius)
    }

    fun isDotGridVisible(canvasNoteId: String): Boolean = settingsManager.isCanvasDotGridVisible(canvasNoteId)

    fun saveDotGridVisible(canvasNoteId: String, isVisible: Boolean) {
        settingsManager.saveCanvasDotGridVisible(canvasNoteId, isVisible)
    }

    fun beginUndoStep() {
        history.beginStep(_canvas.value)
        textEditStepNodeId = null
        freeTextCreatedInOpenStepId = null
    }

    fun finishTextEditing(nodeId: String) {
        textEditStepNodeId = null
        freeTextCreatedInOpenStepId = null
        val node = _canvas.value.nodes.firstOrNull { it.nodeId == nodeId } ?: return
        if (node.isFreeText && node.text.isBlank()) deleteExactly(setOf(nodeId))
    }

    fun undo() {
        val step = history.takeStepToUndo(_canvas.value) ?: return
        textEditStepNodeId = null
        restoreStates(step.nodesBefore, step.edgesBefore, step.strokesBefore)
    }

    fun redo() {
        val step = history.takeStepToRedo(_canvas.value) ?: return
        textEditStepNodeId = null
        restoreStates(step.nodesAfter, step.edgesAfter, step.strokesAfter)
    }

    fun createNode(
        worldRect: Rect,
        groupToGrowId: String? = null,
        shape: CanvasNodeShape = CanvasNodeShape.RECTANGLE
    ): String {
        beginUndoStep()
        val now = System.currentTimeMillis()
        val node = CanvasNodeEntity(
            nodeId = UUID.randomUUID().toString(),
            noteId = noteId.orEmpty(),
            x = worldRect.left,
            y = worldRect.top,
            width = worldRect.width.coerceAtLeast(CANVAS_MIN_NODE_WIDTH),
            height = worldRect.height.coerceAtLeast(CANVAS_MIN_NODE_HEIGHT),
            text = "",
            createdAt = now,
            updatedAt = now,
            shape = shape
        )
        _canvas.value = _canvas.value.copy(nodes = _canvas.value.nodes + node)
        rememberUnsavedNode(node)
        val groupToGrow = _canvas.value.nodes.firstOrNull { it.nodeId == groupToGrowId && it.isGroup }
        if (groupToGrow != null && !node.isInside(groupToGrow)) {
            val grownBounds = groupToGrow.worldRect.expandedToInclude(node.worldRect.inflate(CANVAS_GROUP_PADDING))
            setNodeBounds(groupToGrow.nodeId, grownBounds)
        }
        return node.nodeId
    }

    fun addImageFromFile(uriOrPath: String, worldCenter: Offset) {
        if (uriOrPath.isBlank()) return
        addImage(worldCenter) { mediaStorageHelper.copyUriToInternalStorage(uriOrPath) }
    }

    fun pasteImageFromClipboard(worldCenter: Offset) = addImage(worldCenter) {
        when (val image = ImageClipboard.readImage()) {
            null -> null
            is ClipboardImage.FromFile -> mediaStorageHelper.copyUriToInternalStorage(image.uriOrPath)
            is ClipboardImage.FromPngBytes ->
                mediaStorageHelper.saveBytesToInternalStorage(image.bytes, extension = "png", mimeType = "image/png")
        }
    }

    private fun addImage(worldCenter: Offset, saveToMediaFolder: suspend () -> MediaInfo?) {
        val targetNoteId = noteId ?: return
        if (_isAddingImage.value) return
        _isAddingImage.value = true
        viewModelScope.launch {
            try {
                val (fileName, pixelSize) = withContext(Dispatchers.IO) {
                    val savedFileName = saveToMediaFolder()?.localFileName ?: return@withContext null
                    savedFileName to readImagePixelSize(mediaStorageHelper.getAbsoluteMediaPath(savedFileName))
                } ?: return@launch
                if (noteId != targetNoteId) return@launch
                placeImageNode(targetNoteId, fileName, pixelSize, worldCenter)
            } finally {
                _isAddingImage.value = false
            }
        }
    }

    private fun placeImageNode(targetNoteId: String, fileName: String, pixelSize: IntSize?, worldCenter: Offset) {
        val imageWidth = pixelSize?.width?.toFloat() ?: 4f
        val imageHeight = pixelSize?.height?.toFloat() ?: 3f
        val scale = max(NEW_IMAGE_LONGEST_SIDE / max(imageWidth, imageHeight), CANVAS_MIN_NODE_HEIGHT / min(imageWidth, imageHeight))
        val width = imageWidth * scale
        val height = imageHeight * scale
        beginUndoStep()
        val now = System.currentTimeMillis()
        val node = CanvasNodeEntity(
            nodeId = UUID.randomUUID().toString(),
            noteId = targetNoteId,
            x = worldCenter.x - width / 2f,
            y = worldCenter.y - height / 2f,
            width = width,
            height = height,
            text = "",
            createdAt = now,
            updatedAt = now,
            type = CanvasNodeType.IMAGE,
            imagePath = fileName
        )
        _canvas.value = _canvas.value.copy(nodes = _canvas.value.nodes + node)
        rememberUnsavedNode(node)
    }

    fun createFreeText(worldTopLeft: Offset, startingHeight: Float, style: CanvasTextStyle): String {
        beginUndoStep()
        val now = System.currentTimeMillis()
        val node = CanvasNodeEntity(
            nodeId = UUID.randomUUID().toString(),
            noteId = noteId.orEmpty(),
            x = worldTopLeft.x,
            y = worldTopLeft.y,
            width = CANVAS_FREE_TEXT_STARTING_WIDTH,
            height = startingHeight,
            text = "",
            createdAt = now,
            updatedAt = now,
            type = CanvasNodeType.FREE_TEXT
        ).withTextStyle(style)
        _canvas.value = _canvas.value.copy(nodes = _canvas.value.nodes + node)
        rememberUnsavedNode(node)
        freeTextCreatedInOpenStepId = node.nodeId
        return node.nodeId
    }

    fun setFreeTextStyle(nodeId: String, style: CanvasTextStyle) {
        if (freeTextCreatedInOpenStepId != nodeId) beginUndoStep()
        updateNode(nodeId) { it.withTextStyle(style) }
    }

    fun setFreeTextSize(nodeId: String, width: Float, height: Float) = updateNode(nodeId) {
        it.copy(width = width, height = height)
    }

    fun createGroup(memberNodeIds: Set<String>): String? {
        val members = _canvas.value.nodes.filter { it.nodeId in memberNodeIds }
        if (members.isEmpty()) return null
        beginUndoStep()
        val groupBounds = members.map { it.worldRect }
            .reduce { bounds, rect -> bounds.expandedToInclude(rect) }
            .inflate(CANVAS_GROUP_PADDING)
        val now = System.currentTimeMillis()
        val group = CanvasNodeEntity(
            nodeId = UUID.randomUUID().toString(),
            noteId = noteId.orEmpty(),
            x = groupBounds.left,
            y = groupBounds.top,
            width = groupBounds.width,
            height = groupBounds.height,
            text = DEFAULT_GROUP_TITLE,
            createdAt = now,
            updatedAt = now,
            type = CanvasNodeType.GROUP
        )
        _canvas.value = _canvas.value.copy(nodes = _canvas.value.nodes + group)
        rememberUnsavedNode(group)
        return group.nodeId
    }

    fun moveNodes(startPositions: Map<String, Offset>, travel: Offset) {
        val now = System.currentTimeMillis()
        val movedNodes = mutableListOf<CanvasNodeEntity>()
        _canvas.value = _canvas.value.copy(
            nodes = _canvas.value.nodes.map { node ->
                val startPosition = startPositions[node.nodeId] ?: return@map node
                node.copy(x = startPosition.x + travel.x, y = startPosition.y + travel.y, updatedAt = now)
                    .also { movedNodes.add(it) }
            }
        )
        movedNodes.forEach { rememberUnsavedNode(it) }
    }

    fun setNodeBounds(nodeId: String, bounds: Rect) = updateNode(nodeId) {
        it.copy(x = bounds.left, y = bounds.top, width = bounds.width, height = bounds.height)
    }

    fun updateNodeText(nodeId: String, text: String) {
        if (textEditStepNodeId != nodeId) {
            if (freeTextCreatedInOpenStepId != nodeId) beginUndoStep()
            textEditStepNodeId = nodeId
        }
        updateNode(nodeId) { it.copy(text = text) }
    }

    fun setNodeColor(nodeId: String, colorName: String?) {
        beginUndoStep()
        updateNode(nodeId) { it.copy(color = colorName) }
    }

    fun ungroup(groupId: String) {
        beginUndoStep()
        deleteExactly(setOf(groupId))
    }

    fun deleteNodes(nodeIds: Set<String>) {
        beginUndoStep()
        val current = _canvas.value
        val deletedGroups = current.nodes.filter { it.nodeId in nodeIds && it.isGroup }
        val groupContentIds = deletedGroups.flatMap { group -> current.membersOf(group) }.map { it.nodeId }
        deleteExactly(nodeIds + groupContentIds)
    }

    private fun deleteExactly(nodeIds: Set<String>) {
        val now = System.currentTimeMillis()
        val current = _canvas.value
        val (deletedNodes, remainingNodes) = current.nodes.partition { it.nodeId in nodeIds }
        if (deletedNodes.isEmpty()) return
        val (connectedEdges, remainingEdges) = current.edges.partition { it.fromNodeId in nodeIds || it.toNodeId in nodeIds }
        _canvas.value = current.copy(nodes = remainingNodes, edges = remainingEdges)
        deletedNodes.forEach { rememberUnsavedNode(it.copy(isDeleted = true, updatedAt = now)) }
        connectedEdges.forEach { rememberUnsavedEdge(it.copy(isDeleted = true, updatedAt = now)) }
    }

    fun createEdge(fromNodeId: String, fromSide: CanvasSide, toNodeId: String, toSide: CanvasSide) {
        if (fromNodeId == toNodeId) return
        beginUndoStep()
        val now = System.currentTimeMillis()
        val edge = CanvasEdgeEntity(
            edgeId = UUID.randomUUID().toString(),
            noteId = noteId.orEmpty(),
            fromNodeId = fromNodeId,
            fromSide = fromSide,
            toNodeId = toNodeId,
            toSide = toSide,
            createdAt = now,
            updatedAt = now
        )
        _canvas.value = _canvas.value.copy(edges = _canvas.value.edges + edge)
        rememberUnsavedEdge(edge)
    }

    fun reconnectEdge(edgeId: String, moveArrowHead: Boolean, newNodeId: String, newSide: CanvasSide) {
        val current = _canvas.value
        val edge = current.edges.firstOrNull { it.edgeId == edgeId } ?: return
        val reconnectedEdge = if (moveArrowHead) {
            edge.copy(toNodeId = newNodeId, toSide = newSide)
        } else {
            edge.copy(fromNodeId = newNodeId, fromSide = newSide)
        }
        if (reconnectedEdge.fromNodeId == reconnectedEdge.toNodeId) return
        beginUndoStep()
        val stampedEdge = reconnectedEdge.copy(updatedAt = System.currentTimeMillis())
        _canvas.value = current.copy(edges = current.edges.map { if (it.edgeId == edgeId) stampedEdge else it })
        rememberUnsavedEdge(stampedEdge)
    }

    fun deleteEdge(edgeId: String) {
        val current = _canvas.value
        val deletedEdge = current.edges.firstOrNull { it.edgeId == edgeId } ?: return
        beginUndoStep()
        _canvas.value = current.copy(edges = current.edges - deletedEdge)
        rememberUnsavedEdge(deletedEdge.copy(isDeleted = true, updatedAt = System.currentTimeMillis()))
    }

    fun addStroke(
        tool: CanvasStrokeTool,
        worldPoints: List<CanvasStrokePoint>,
        width: Float,
        color: String?,
        opacity: Float = 1f,
        usesPressure: Boolean = true
    ) {
        val origin = worldPoints.firstOrNull() ?: return
        beginUndoStep()
        val now = System.currentTimeMillis()
        val stroke = CanvasStrokeEntity(
            strokeId = UUID.randomUUID().toString(),
            noteId = noteId.orEmpty(),
            tool = tool,
            x = origin.x,
            y = origin.y,
            points = CanvasStrokePoints.encode(worldPoints.map { it.copy(x = it.x - origin.x, y = it.y - origin.y) }),
            width = width,
            createdAt = now,
            updatedAt = now,
            color = color,
            opacity = opacity,
            usesPressure = usesPressure
        )
        _canvas.value = _canvas.value.copy(strokes = _canvas.value.strokes + stroke)
        rememberUnsavedStroke(stroke)
    }

    fun addLine(worldStart: Offset, worldEnd: Offset, lineStyle: CanvasLineStyle, strokeStyle: CanvasStrokeStyle) {
        beginUndoStep()
        val now = System.currentTimeMillis()
        val relativeEnd = worldEnd - worldStart
        val line = CanvasStrokeEntity(
            strokeId = UUID.randomUUID().toString(),
            noteId = noteId.orEmpty(),
            tool = CanvasStrokeTool.LINE,
            x = worldStart.x,
            y = worldStart.y,
            points = CanvasStrokePoints.encode(listOf(CanvasStrokePoint(0f, 0f), CanvasStrokePoint(relativeEnd.x, relativeEnd.y))),
            width = strokeStyle.width,
            createdAt = now,
            updatedAt = now,
            color = strokeStyle.colorName,
            opacity = strokeStyle.opacity,
            usesPressure = false,
            linePattern = lineStyle.pattern,
            hasArrowHead = lineStyle.hasArrowHead
        )
        _canvas.value = _canvas.value.copy(strokes = _canvas.value.strokes + line)
        rememberUnsavedStroke(line)
    }

    fun eraseStrokes(strokeIds: Set<String>) {
        val current = _canvas.value
        val (erasedStrokes, remainingStrokes) = current.strokes.partition { it.strokeId in strokeIds }
        if (erasedStrokes.isEmpty()) return
        val now = System.currentTimeMillis()
        _canvas.value = current.copy(strokes = remainingStrokes)
        erasedStrokes.forEach { rememberUnsavedStroke(it.copy(isDeleted = true, updatedAt = now)) }
    }

    private fun updateNode(nodeId: String, change: (CanvasNodeEntity) -> CanvasNodeEntity) {
        var updatedNode: CanvasNodeEntity? = null
        _canvas.value = _canvas.value.copy(
            nodes = _canvas.value.nodes.map { node ->
                if (node.nodeId != nodeId) return@map node
                change(node).copy(updatedAt = System.currentTimeMillis()).also { updatedNode = it }
            }
        )
        updatedNode?.let { rememberUnsavedNode(it) }
    }

    private fun restoreStates(
        nodeStates: Map<String, CanvasNodeEntity?>,
        edgeStates: Map<String, CanvasEdgeEntity?>,
        strokeStates: Map<String, CanvasStrokeEntity?>
    ) {
        val now = System.currentTimeMillis()
        val current = _canvas.value
        val nodesById = current.nodes.associateByTo(LinkedHashMap<String, CanvasNodeEntity>()) { it.nodeId }
        val edgesById = current.edges.associateByTo(LinkedHashMap<String, CanvasEdgeEntity>()) { it.edgeId }
        val strokesById = current.strokes.associateByTo(LinkedHashMap<String, CanvasStrokeEntity>()) { it.strokeId }
        nodeStates.forEach { (nodeId, restoredState) ->
            if (restoredState == null) {
                val removedNode = nodesById.remove(nodeId) ?: return@forEach
                rememberUnsavedNode(removedNode.copy(isDeleted = true, updatedAt = now))
            } else {
                val restoredNode = restoredState.copy(isDeleted = false, updatedAt = now)
                nodesById[nodeId] = restoredNode
                rememberUnsavedNode(restoredNode)
            }
        }
        edgeStates.forEach { (edgeId, restoredState) ->
            if (restoredState == null) {
                val removedEdge = edgesById.remove(edgeId) ?: return@forEach
                rememberUnsavedEdge(removedEdge.copy(isDeleted = true, updatedAt = now))
            } else {
                val restoredEdge = restoredState.copy(isDeleted = false, updatedAt = now)
                edgesById[edgeId] = restoredEdge
                rememberUnsavedEdge(restoredEdge)
            }
        }
        strokeStates.forEach { (strokeId, restoredState) ->
            if (restoredState == null) {
                val removedStroke = strokesById.remove(strokeId) ?: return@forEach
                rememberUnsavedStroke(removedStroke.copy(isDeleted = true, updatedAt = now))
            } else {
                val restoredStroke = restoredState.copy(isDeleted = false, updatedAt = now)
                strokesById[strokeId] = restoredStroke
                rememberUnsavedStroke(restoredStroke)
            }
        }
        _canvas.value = CanvasContent(
            nodes = nodesById.values.sortedBy { it.createdAt },
            edges = edgesById.values.sortedBy { it.createdAt },
            strokes = strokesById.values.sortedBy { it.createdAt }
        ).liveOnly()
    }

    private fun rememberUnsavedNode(node: CanvasNodeEntity) {
        unsavedNodes[node.nodeId] = node
        history.recordTouchedNode(node.nodeId)
        scheduleSave()
    }

    private fun rememberUnsavedEdge(edge: CanvasEdgeEntity) {
        unsavedEdges[edge.edgeId] = edge
        history.recordTouchedEdge(edge.edgeId)
        scheduleSave()
    }

    private fun rememberUnsavedStroke(stroke: CanvasStrokeEntity) {
        unsavedStrokes[stroke.strokeId] = stroke
        history.recordTouchedStroke(stroke.strokeId)
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MILLIS)
            saveUnsavedChanges()
        }
    }

    private fun saveUnsavedChanges() {
        val targetNoteId = noteId ?: return
        val nodesToSave = unsavedNodes.values.toList()
        val edgesToSave = unsavedEdges.values.toList()
        val strokesToSave = unsavedStrokes.values.toList()
        if (nodesToSave.isEmpty() && edgesToSave.isEmpty() && strokesToSave.isEmpty()) return
        unsavedNodes.clear()
        unsavedEdges.clear()
        unsavedStrokes.clear()
        nodesToSave.forEach { nodesBeingSaved[it.nodeId] = it }
        edgesToSave.forEach { edgesBeingSaved[it.edgeId] = it }
        strokesToSave.forEach { strokesBeingSaved[it.strokeId] = it }
        appScope.launch {
            try {
                canvasRepository.saveChanges(
                    targetNoteId,
                    CanvasContent(nodes = nodesToSave, edges = edgesToSave, strokes = strokesToSave)
                )
            } finally {
                withContext(Dispatchers.Main) {
                    nodesToSave.forEach { if (nodesBeingSaved[it.nodeId] === it) nodesBeingSaved.remove(it.nodeId) }
                    edgesToSave.forEach { if (edgesBeingSaved[it.edgeId] === it) edgesBeingSaved.remove(it.edgeId) }
                    strokesToSave.forEach { if (strokesBeingSaved[it.strokeId] === it) strokesBeingSaved.remove(it.strokeId) }
                }
            }
        }
    }

    private suspend fun reloadFromDatabase() {
        val targetNoteId = noteId ?: return
        val stored = canvasRepository.loadCanvasIncludingDeleted(targetNoteId)
        if (targetNoteId != noteId) return
        val nodesById = stored.nodes.associateByTo(LinkedHashMap<String, CanvasNodeEntity>()) { it.nodeId }
        val edgesById = stored.edges.associateByTo(LinkedHashMap<String, CanvasEdgeEntity>()) { it.edgeId }
        val strokesById = stored.strokes.associateByTo(LinkedHashMap<String, CanvasStrokeEntity>()) { it.strokeId }
        nodesBeingSaved.values.filter { it.noteId == targetNoteId }.forEach { nodesById[it.nodeId] = it }
        edgesBeingSaved.values.filter { it.noteId == targetNoteId }.forEach { edgesById[it.edgeId] = it }
        strokesBeingSaved.values.filter { it.noteId == targetNoteId }.forEach { strokesById[it.strokeId] = it }
        unsavedNodes.values.forEach { nodesById[it.nodeId] = it }
        unsavedEdges.values.forEach { edgesById[it.edgeId] = it }
        unsavedStrokes.values.forEach { strokesById[it.strokeId] = it }
        _canvas.value = CanvasContent(
            nodes = nodesById.values.sortedBy { it.createdAt },
            edges = edgesById.values.sortedBy { it.createdAt },
            strokes = strokesById.values.sortedBy { it.createdAt }
        ).liveOnly()
        if (needsIndexBaseline) {
            lastIndexedSignature = indexSignatureOf(_canvas.value)
            needsIndexBaseline = false
        }
    }

    suspend fun indexForAiNowIfChanged() {
        val (targetNoteId, canvasToIndex) = claimIndexingIfChanged() ?: return
        noteRepository.indexCanvas(targetNoteId, canvasToIndex)
    }

    private fun indexInBackgroundIfChanged() {
        val (targetNoteId, canvasToIndex) = claimIndexingIfChanged() ?: return
        appScope.launch { noteRepository.indexCanvas(targetNoteId, canvasToIndex) }
    }

    private fun claimIndexingIfChanged(): Pair<String, CanvasContent>? {
        val targetNoteId = noteId ?: return null
        if (needsIndexBaseline) return null
        val canvasToIndex = _canvas.value
        val signature = indexSignatureOf(canvasToIndex)
        if (signature == lastIndexedSignature) return null
        lastIndexedSignature = signature
        return targetNoteId to canvasToIndex
    }

    private fun indexSignatureOf(canvas: CanvasContent): Int = listOf(
        canvas.nodes.map { Triple(it.nodeId, it.text, it.type) },
        canvas.edges.map { it.fromNodeId to it.toNodeId }
    ).hashCode()

    override fun onCleared() {
        super.onCleared()
        saveJob?.cancel()
        saveUnsavedChanges()
        indexInBackgroundIfChanged()
        ActiveEditorRegistry.unregisterCanvas(this)
    }
}
