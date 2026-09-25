package com.emberr.presentation.shared.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeType
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.canvas.CanvasRepository
import com.emberr.domain.canvas.isGroup
import com.emberr.domain.canvas.isInside
import com.emberr.domain.canvas.membersOf
import com.emberr.domain.model.NoteContent
import com.emberr.domain.repository.NoteRepository
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

private const val SAVE_DELAY_MILLIS = 500L
private const val DEFAULT_GROUP_TITLE = "Group"

class CanvasViewModel(
    private val canvasRepository: CanvasRepository,
    private val noteRepository: NoteRepository,
    private val appScope: CoroutineScope
) : ViewModel() {

    private val _canvas = MutableStateFlow(CanvasContent())
    val canvas: StateFlow<CanvasContent> = _canvas.asStateFlow()

    private var noteId: String? = null
    private val loadedNoteId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val title: StateFlow<String> = loadedNoteId
        .flatMapLatest { id -> if (id == null) flowOf(null) else noteRepository.observeNoteMetadata(id) }
        .map { it?.title.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    private val unsavedNodes = LinkedHashMap<String, CanvasNodeEntity>()
    private val unsavedEdges = LinkedHashMap<String, CanvasEdgeEntity>()
    private val nodesBeingSaved = LinkedHashMap<String, CanvasNodeEntity>()
    private val edgesBeingSaved = LinkedHashMap<String, CanvasEdgeEntity>()
    private var saveJob: Job? = null
    private val history = CanvasHistory()
    private var textEditStepNodeId: String? = null

    init {
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
            this.noteId = noteId
            loadedNoteId.value = noteId
            history.clear()
            textEditStepNodeId = null
            _canvas.value = CanvasContent()
        }
        viewModelScope.launch { reloadFromDatabase() }
    }

    fun beginUndoStep() {
        history.beginStep(_canvas.value)
        textEditStepNodeId = null
    }

    fun finishTextEditStep() {
        textEditStepNodeId = null
    }

    fun undo() {
        val step = history.takeStepToUndo(_canvas.value) ?: return
        textEditStepNodeId = null
        restoreStates(step.nodesBefore, step.edgesBefore)
    }

    fun redo() {
        val step = history.takeStepToRedo(_canvas.value) ?: return
        textEditStepNodeId = null
        restoreStates(step.nodesAfter, step.edgesAfter)
    }

    fun createNode(worldRect: Rect, groupToGrowId: String? = null): String {
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
            updatedAt = now
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
            beginUndoStep()
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
        _canvas.value = CanvasContent(nodes = remainingNodes, edges = remainingEdges)
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

    private fun restoreStates(nodeStates: Map<String, CanvasNodeEntity?>, edgeStates: Map<String, CanvasEdgeEntity?>) {
        val now = System.currentTimeMillis()
        val current = _canvas.value
        val nodesById = current.nodes.associateByTo(LinkedHashMap<String, CanvasNodeEntity>()) { it.nodeId }
        val edgesById = current.edges.associateByTo(LinkedHashMap<String, CanvasEdgeEntity>()) { it.edgeId }
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
        _canvas.value = CanvasContent(
            nodes = nodesById.values.sortedBy { it.createdAt },
            edges = edgesById.values.sortedBy { it.createdAt }
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
        if (nodesToSave.isEmpty() && edgesToSave.isEmpty()) return
        unsavedNodes.clear()
        unsavedEdges.clear()
        nodesToSave.forEach { nodesBeingSaved[it.nodeId] = it }
        edgesToSave.forEach { edgesBeingSaved[it.edgeId] = it }
        appScope.launch {
            try {
                canvasRepository.saveChanges(targetNoteId, CanvasContent(nodes = nodesToSave, edges = edgesToSave))
            } finally {
                withContext(Dispatchers.Main) {
                    nodesToSave.forEach { if (nodesBeingSaved[it.nodeId] === it) nodesBeingSaved.remove(it.nodeId) }
                    edgesToSave.forEach { if (edgesBeingSaved[it.edgeId] === it) edgesBeingSaved.remove(it.edgeId) }
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
        nodesBeingSaved.values.filter { it.noteId == targetNoteId }.forEach { nodesById[it.nodeId] = it }
        edgesBeingSaved.values.filter { it.noteId == targetNoteId }.forEach { edgesById[it.edgeId] = it }
        unsavedNodes.values.forEach { nodesById[it.nodeId] = it }
        unsavedEdges.values.forEach { edgesById[it.edgeId] = it }
        _canvas.value = CanvasContent(
            nodes = nodesById.values.sortedBy { it.createdAt },
            edges = edgesById.values.sortedBy { it.createdAt }
        ).liveOnly()
    }

    override fun onCleared() {
        super.onCleared()
        saveJob?.cancel()
        saveUnsavedChanges()
    }
}
