package com.emberr.presentation.shared.editor

import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.room.entity.DatabaseTemplateEntity
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.*
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.eventbus.AiEventBus
import com.emberr.domain.util.voice.AudioRecorder
import com.emberr.domain.util.formula.FormulaEngine
import com.emberr.domain.util.network.HtmlMetadataFetcher
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.presentation.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@Stable
data class FocusRequest(
    val id: String,
    val placeCursorAtEnd: Boolean = false,
    val nonce: String = UUID.randomUUID().toString()
)

@Stable
data class SelectionRequest(
    val blockId: String,
    val selection: TextRange,
    val nonce: String = UUID.randomUUID().toString()
)

data class RecurringDeletionTarget(val blockId: String, val occurrenceDate: String)

// Surfaced whenever a delete action (backspace-on-empty or the multi-select trash button) would
// remove a checkbox that's part of a recurring series - the screen shows RecurrenceScopeChooser
// and calls confirmRecurringDeletion(scope) once the user picks one of the four scopes.
// plainBlockIds carries any non-recurring blocks caught in the same multi-select delete, so a
// mixed selection resolves as a single confirmation instead of two.
@Stable
data class PendingRecurringDeletion(
    val recurringTargets: List<RecurringDeletionTarget>,
    val plainBlockIds: Set<String> = emptySet()
)

abstract class BaseEditorViewModel(
    protected val repository: NoteRepository,
    protected val mediaStorageHelper: MediaStorageHelper,
    protected val reminderScheduler: ReminderScheduler,
    protected val audioRecorder: AudioRecorder,
    private val appScope: CoroutineScope
) : ViewModel() {

    // AI event bus
    init {
        viewModelScope.launch {
            AiEventBus.indexRequest.collect {
                forceSyncAndIndexForAi()
            }
        }
        ActiveEditorRegistry.register(this)
    }

    fun forceSyncAndIndexForAi() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentHash = computeBlocksHash()
            if (currentHash != lastIndexedContentHash) {

                withContext(NonCancellable) {
                    performSave()
                    try {
                        performIndexing()
                        lastIndexedContentHash = currentHash
                        isAiIndexDirty = false
                        AiEventBus.notifyIndexComplete()
                    } catch (_: Exception) {
                        // Handle error
                    }
                }

            }
        }
    }
    // ----

    protected val _blocks = MutableStateFlow<List<NoteBlock>>(emptyList())
    var lastIndexedContentHash: Int = 0
    fun computeBlocksHash(): Int {
        return _blocks.value
            .filter { !it.isDeleted }
            .joinToString(separator = "|") { block ->
                when (block) {
                    is TextBlock -> "${block.id}:${block.text}"
                    is HeadingBlock -> "${block.id}:${block.text}"
                    is CheckboxBlock -> "${block.id}:${block.text}:${block.isChecked}"
                    is BulletedListBlock -> "${block.id}:${block.text}"
                    is NumberedListBlock -> "${block.id}:${block.text}"
                    is ToggleBlock -> "${block.id}:${block.text}"
                    is CodeBlock -> "${block.id}:${block.code}"
                    is QuoteBlock -> "${block.id}:${block.text}"
                    is DatabaseBlock -> "${block.id}:${block.rows.size}:${block.columns.size}"
                    else -> block.id
                }
            }
            .hashCode()
    }

    // Blocks materialized for display only, never persisted - overridden by DailyEditorViewModel to
    // surface other days'/notes' recurring checkboxes whose expansion lands on the currently loaded
    // day (see extraVisibleBlocks doc there). Kept entirely out of _blocks so every existing save
    // path (performSave, selectDate's reconcile-on-navigate-away, ...) stays untouched and can't
    // accidentally persist a virtual occurrence into the wrong note.
    protected open val extraVisibleBlocks: StateFlow<List<NoteBlock>> = MutableStateFlow(emptyList())

    protected fun visibleBlocksOf(allBlocks: List<NoteBlock>): List<NoteBlock> {
        val visible = mutableListOf<NoteBlock>()
        var skipUntilLevel: Int? = null
        for (block in allBlocks) {
            if (block.isDeleted) continue

            if (skipUntilLevel != null) {
                if (block.indentationLevel > skipUntilLevel) continue
                else skipUntilLevel = null
            }
            visible.add(block)
            if (block is ToggleBlock && !block.isExpanded) skipUntilLevel = block.indentationLevel
        }
        return visible
    }

    // Deferred via `by lazy` rather than an eager property initializer: this combines with
    // extraVisibleBlocks, an `open val` a subclass constructor hasn't finished assigning yet at the
    // point this class's own constructor runs - evaluating it eagerly here would observe the
    // subclass's backing field before it's set. Lazy defers first evaluation to first access, which
    // never happens until after the subclass is fully constructed.
    val visibleBlocks: StateFlow<List<NoteBlock>> by lazy {
        combine(_blocks, extraVisibleBlocks) { allBlocks, extra ->
            val visible = visibleBlocksOf(allBlocks)
            val realIds = visible.mapTo(HashSet()) { it.id }
            val (pinnedReal, unpinnedReal) = visible.partition { it.isPinned }

            val ordered = orderForDisplay(pinnedReal, extra.filter { it.id !in realIds }, unpinnedReal)

            val deduped = LinkedHashMap<String, NoteBlock>()
            ordered.forEach { deduped[it.id] = it }
            deduped.values.toList()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    protected val _focusRequest = MutableStateFlow<FocusRequest?>(null)
    val focusRequest: StateFlow<FocusRequest?> = _focusRequest.asStateFlow()

    protected val _selectionRequest = MutableStateFlow<SelectionRequest?>(null)
    val selectionRequest: StateFlow<SelectionRequest?> = _selectionRequest.asStateFlow()

    protected val _selectedBlockIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBlockIds: StateFlow<Set<String>> = _selectedBlockIds.asStateFlow()

    protected val _pendingRecurringDeletion = MutableStateFlow<PendingRecurringDeletion?>(null)
    val pendingRecurringDeletion: StateFlow<PendingRecurringDeletion?> = _pendingRecurringDeletion.asStateFlow()

    // A "virtual occurrence" is a CheckboxBlock materialized into _blocks for a recurring series
    // whose literal storage lives elsewhere (see DailyEditorViewModel) - only DailyEditorViewModel
    // overrides these; NoteEditorViewModel never has any, so the defaults are all no-ops/false.
    // Interleaves the three display groups. Base keeps them as three contiguous runs; only
    // DailyEditorViewModel overrides this, to merge recurring checkboxes into the virtual band and
    // sort the result by reminder time. Purely presentational - `extra` is display-only and the
    // real blocks keep their _blocks order, so nothing here reaches a save path. Implementations
    // must return every input block exactly once and must not fabricate new ones.
    protected open fun orderForDisplay(
        pinned: List<NoteBlock>,
        extra: List<NoteBlock>,
        rest: List<NoteBlock>
    ): List<NoteBlock> = pinned + extra + rest

    protected open fun isVirtualOccurrence(blockId: String): Boolean = false
    protected open fun onVirtualOccurrenceToggled(blockId: String, isChecked: Boolean) {}
    protected open fun onVirtualOccurrenceTextEdited(blockId: String, text: String) {}
    protected open fun virtualOccurrenceDate(blockId: String): String? = null

    private fun occurrenceDateFor(block: CheckboxBlock, blockId: String): String? {
        if (isVirtualOccurrence(blockId)) return virtualOccurrenceDate(blockId)
        return block.reminderTimestamp?.let {
            Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        }
    }

    protected fun requestRecurringDeletion(recurringTargets: List<RecurringDeletionTarget>, plainBlockIds: Set<String> = emptySet()) {
        _pendingRecurringDeletion.value = PendingRecurringDeletion(recurringTargets, plainBlockIds)
    }

    fun dismissRecurringDeletion() {
        _pendingRecurringDeletion.value = null
    }

    // Removes the affected blocks from view immediately (optimistic - the repository call below is
    // the actual source of truth) without going through modifyBlocks/scheduleAutosave, matching
    // DailyEditorViewModel.removeBlockLocally's precedent for edits that persist themselves rather
    // than through the normal whole-note save path.
    fun confirmRecurringDeletion(scope: RecurrenceEditScope) {
        val pending = _pendingRecurringDeletion.value ?: return
        _pendingRecurringDeletion.value = null
        autosaveJob?.cancel()

        val idsToStrip = pending.recurringTargets.mapTo(mutableSetOf()) { it.blockId } + pending.plainBlockIds
        _blocks.update { list -> list.filterNot { it.id in idsToStrip } }
        clearSelection()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                pending.recurringTargets.forEach { target ->
                    repository.applyRecurrenceScopedDelete(target.blockId, target.occurrenceDate, scope)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (pending.plainBlockIds.isNotEmpty()) {
            performPlainDeletion(pending.plainBlockIds)
        }
    }

    protected var currentlyFocusedBlockId: String? = null
    protected var autosaveJob: Job? = null
    protected var indexingJob: Job? = null

    // Returns whether the write actually persisted - implementations catch their own IO failures
    // rather than throwing, so callers that need to react to (e.g. revert an optimistic UI change on)
    // a failed save must check this rather than relying on an exception
    protected abstract suspend fun performSave(): Boolean
    protected abstract suspend fun performIndexing()
    protected abstract fun getNoteTitleForReminder(): String

    val allLinkableNotes: StateFlow<List<NoteMetadataEntity>> = repository.getAllLinkableNotes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val databaseTemplates: StateFlow<List<DatabaseTemplateEntity>> = repository.getAllDatabaseTemplates()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Dedicated Json instance for DatabaseBlock schema templates (columns/views only).
    private val templateJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    protected var lastLocalMutationTime: Long = 0L
    private val LOCAL_MUTATION_COOLDOWN_MS = 3000L

    protected fun isWithinLocalMutationCooldown(): Boolean =
        System.currentTimeMillis() - lastLocalMutationTime < LOCAL_MUTATION_COOLDOWN_MS

    protected var isAiIndexDirty = false

    open fun scheduleAutosave() {
        isAiIndexDirty = true

        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(1000L.milliseconds)
            performSave()
        }
    }

    suspend fun flushPendingSave() {
        if (autosaveJob?.isActive == true) {
            autosaveJob?.cancel()
            performSave()
        }
    }

    private var isDiscarded = false

    // Called when the note this editor is bound to is being deleted while the editor is still
    // open. Without this, the save in onCleared would write the note straight back to disk.
    fun discardPendingWrites() {
        isDiscarded = true
        autosaveJob?.cancel()
    }

    // A reactive collector's disk/sync snapshot can legitimately be older than what's already in
    // _blocks (e.g. it was queued before a just-completed local edit committed, or a self-host sync
    // pass reconciled a not-yet-pushed prior state) - the LOCAL_MUTATION_COOLDOWN_MS window is a
    // heuristic, not a guarantee the write has landed everywhere in time. Blindly replacing _blocks
    // with such a snapshot flips a field like isPinned back momentarily until the next correct
    // emission reasserts it, which is exactly what shows up as a block un-pinning and re-pinning
    // itself. Reconciling per-block by updatedAt (the same last-write-wins principle used throughout
    // the sync/merge code) keeps whichever version - incoming or already in memory - is actually newer.
    protected fun preserveNewerLocalBlocks(incoming: List<NoteBlock>): List<NoteBlock> {
        val current = _blocks.value
        if (current.isEmpty()) return incoming
        val currentById = current.associateBy { it.id }
        val reconciled = incoming.map { block ->
            val existing = currentById[block.id]
            if (existing != null && existing.updatedAt > block.updatedAt) existing else block
        }
        val reconciledIds = reconciled.mapTo(HashSet()) { it.id }
        val localOnly = current.filter { it.id !in reconciledIds }
        return if (localOnly.isEmpty()) reconciled else reconciled + localOnly
    }

    protected fun isNoteActuallyEmpty(blocks: List<NoteBlock>): Boolean {
        if (blocks.isEmpty()) return true
        if (blocks.size == 1) {
            val first = blocks.first()
            return first is TextBlock && first.text.isBlank()
        }
        return false
    }

    fun togglePinSelectedBlocks() {
        val toToggle = _selectedBlockIds.value
        if (toToggle.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            list.map { b -> if (b.id in toToggle) b.withPin(!b.isPinned, now) else b }
        }
        clearSelection()
        scheduleAutosave()
    }

    // Non-null only while every alignable block currently shares one alignment - drives which icon
    // in NoteOptions shows as active. A mixed note (or one with no alignable blocks yet) shows none.
    val blockAlignment: StateFlow<TextAlignment?> = _blocks.map { list ->
        list.mapNotNullTo(HashSet()) { it.textAlignmentOrNull() }.singleOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setAllBlocksAlignment(alignment: TextAlignment) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> list.map { it.withTextAlignment(alignment, now) } }
        scheduleAutosave()
    }

    fun setSelectedBlocksAlignment(alignment: TextAlignment) {
        val ids = _selectedBlockIds.value
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks { list -> list.map { b -> if (b.id in ids) b.withTextAlignment(alignment, now) else b } }
        scheduleAutosave()
    }

    protected fun modifyBlocks(action: (List<NoteBlock>) -> List<NoteBlock>) {
        lateinit var newList: List<NoteBlock>
        val currentList = _blocks.getAndUpdate { list ->
            val rawList = action(list)

            val segregatedList = if (rawList.any { it.isPinned }) {
                val pinned = rawList.filter { it.isPinned }
                val unpinned = rawList.filter { !it.isPinned }
                pinned + unpinned
            } else {
                rawList
            }

            val renumbered = if (segregatedList.any { it is NumberedListBlock }) {
                recalculateNumberedLists(segregatedList)
            } else {
                segregatedList
            }

            val withTrailingBlock = renumbered.toMutableList()
            val lastVisible = withTrailingBlock.lastOrNull { !it.isDeleted }
            val needsTrailing = lastVisible == null ||
                    lastVisible !is TextBlock ||
                    lastVisible.text.isNotEmpty() ||
                    lastVisible.isPinned
            if (needsTrailing) {
                withTrailingBlock.add(
                    TextBlock(
                        id = UUID.randomUUID().toString(),
                        text = "",
                        indentationLevel = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            newList = withTrailingBlock
            newList
        }

        if (currentList == newList) return

        lastLocalMutationTime = System.currentTimeMillis()
        if (!isApplyingHistory) recordHistory(currentList, newList)
    }

    private data class HistoryEntry(
        val before: List<NoteBlock>,
        val after: List<NoteBlock>,
        val coalescingKey: String?
    )

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()
    private var isApplyingHistory = false
    private var historyCoalescingSealed = true
    private val maxHistoryDepth = 100

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun historyTextOf(block: NoteBlock): String? = when (block) {
        is TextBlock -> block.text
        is HeadingBlock -> block.text
        is CheckboxBlock -> block.text
        is BulletedListBlock -> block.text
        is NumberedListBlock -> block.text
        is ToggleBlock -> block.text
        is QuoteBlock -> block.text
        is CodeBlock -> block.code
        else -> null
    }

    private fun changedBlockIds(before: List<NoteBlock>, after: List<NoteBlock>): Set<String> {
        val beforeById = before.associateBy { it.id }
        val afterById = after.associateBy { it.id }
        return (beforeById.keys + afterById.keys).filterTo(mutableSetOf()) { beforeById[it] != afterById[it] }
    }

    private fun coalescingKeyFor(before: List<NoteBlock>, after: List<NoteBlock>): String? {
        val changed = changedBlockIds(before, after)
        if (changed.size != 1) return null
        val id = changed.first()
        val previous = before.firstOrNull { it.id == id } ?: return null
        val current = after.firstOrNull { it.id == id } ?: return null
        if (previous::class != current::class || previous.isDeleted != current.isDeleted) return null
        val previousText = historyTextOf(previous) ?: return null
        val currentText = historyTextOf(current) ?: return null
        return if (previousText == currentText) null else id
    }

    private fun endsOnWordBoundary(after: List<NoteBlock>, id: String): Boolean {
        val text = after.firstOrNull { it.id == id }?.let { historyTextOf(it) } ?: return true
        return text.isEmpty() || text.last().isWhitespace()
    }

    private fun mergesIntoCreationStreak(before: List<NoteBlock>, after: List<NoteBlock>, top: HistoryEntry?): Boolean {
        if (top == null) return false
        val id = changedBlockIds(before, after).singleOrNull() ?: return false
        val newBlock = after.firstOrNull { it.id == id } ?: return false
        if (historyTextOf(newBlock) != null) return false
        return changedBlockIds(top.before, top.after) == setOf(id) && top.before.none { it.id == id }
    }

    private fun recordHistory(before: List<NoteBlock>, after: List<NoteBlock>) {
        redoStack.clear()
        val top = undoStack.lastOrNull()

        if (mergesIntoCreationStreak(before, after, top)) {
            undoStack[undoStack.lastIndex] = top!!.copy(after = after)
            historyCoalescingSealed = true
            updateHistoryFlags()
            return
        }

        val key = coalescingKeyFor(before, after)
        val coalesced = key != null && !historyCoalescingSealed && top != null && top.coalescingKey == key
        if (coalesced) {
            undoStack[undoStack.lastIndex] = top.copy(after = after)
        } else {
            undoStack.addLast(HistoryEntry(before, after, key))
            while (undoStack.size > maxHistoryDepth) undoStack.removeFirst()
        }
        historyCoalescingSealed = key == null || endsOnWordBoundary(after, key)
        updateHistoryFlags()
    }

    private fun sealHistoryCoalescing() { historyCoalescingSealed = true }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    protected fun clearUndoHistory() {
        undoStack.clear()
        redoStack.clear()
        historyCoalescingSealed = true
        updateHistoryFlags()
    }

    fun undo() {
        val entry = undoStack.removeLastOrNull() ?: return
        applyHistory(entry, restoreBefore = true)
        focusHistoryTarget(entry, restoreBefore = true)
        redoStack.addLast(entry)
        historyCoalescingSealed = true
        updateHistoryFlags()
        scheduleAutosave()
    }

    fun redo() {
        val entry = redoStack.removeLastOrNull() ?: return
        applyHistory(entry, restoreBefore = false)
        focusHistoryTarget(entry, restoreBefore = false)
        undoStack.addLast(entry)
        historyCoalescingSealed = true
        updateHistoryFlags()
        scheduleAutosave()
    }

    private fun focusHistoryTarget(entry: HistoryEntry, restoreBefore: Boolean) {
        val changed = changedBlockIds(entry.before, entry.after)
        val current = _blocks.value

        // Only blocks that host a text field can take focus. NoteBlockItem attaches its
        // FocusRequester exclusively inside the isTextBased branch, so targeting an
        // ImageBlock/VoiceBlock/DatabaseBlock leaves the requester unattached:
        // requestFocus() throws, nothing claims the input session, and the IME closes.
        val target = current.firstOrNull { it.id in changed && !it.isDeleted && historyTextOf(it) != null }
            ?: nearestTextNeighbour(current, changed)
            ?: return

        val toText = historyTextOf(target)
        val fromSnapshot = if (restoreBefore) entry.after else entry.before
        val fromText = fromSnapshot.firstOrNull { it.id == target.id }?.let { historyTextOf(it) }
        val offset = if (toText != null && fromText != null) cursorOffsetAfterChange(fromText, toText) else null

        if (target.id != currentlyFocusedBlockId) {
            currentlyFocusedBlockId = target.id
            _focusRequest.value = FocusRequest(id = target.id, placeCursorAtEnd = offset == null)
        }
        if (offset != null) {
            _selectionRequest.value = SelectionRequest(blockId = target.id, selection = TextRange(offset))
        }
    }

    // When the only thing an entry touched is a media block, park the caret on the closest
    // text block instead - preferring the one after it, which is normally the trailing empty block.
    private fun nearestTextNeighbour(current: List<NoteBlock>, changed: Set<String>): NoteBlock? {
        val anchor = current.indexOfFirst { it.id in changed && !it.isDeleted }
        if (anchor == -1) return null
        for (i in anchor + 1 until current.size) {
            val b = current[i]
            if (!b.isDeleted && historyTextOf(b) != null) return b
        }
        for (i in anchor - 1 downTo 0) {
            val b = current[i]
            if (!b.isDeleted && historyTextOf(b) != null) return b
        }
        return null
    }

    private fun cursorOffsetAfterChange(fromText: String, toText: String): Int {
        val prefix = fromText.commonPrefixWith(toText).length
        val suffix = fromText.commonSuffixWith(toText).length
            .coerceAtMost(minOf(fromText.length, toText.length) - prefix)
        return (toText.length - suffix).coerceIn(0, toText.length)
    }

    private fun historySameContent(a: NoteBlock, b: NoteBlock): Boolean =
        a.withUpdatedAt(0L) == b.withUpdatedAt(0L)

    private fun applyHistory(entry: HistoryEntry, restoreBefore: Boolean) {
        val target = if (restoreBefore) entry.before else entry.after
        val expected = if (restoreBefore) entry.after else entry.before
        val changed = changedBlockIds(entry.before, entry.after)
        val targetById = target.associateBy { it.id }
        val expectedById = expected.associateBy { it.id }
        val now = System.currentTimeMillis()

        isApplyingHistory = true
        modifyBlocks { live ->
            val liveById = live.associateBy { it.id }
            val placed = HashSet<String>()
            val result = mutableListOf<NoteBlock>()

            for (targetBlock in target) {
                if (!placed.add(targetBlock.id)) continue
                val liveBlock = liveById[targetBlock.id]
                if (targetBlock.id in changed) {
                    val expectedBlock = expectedById[targetBlock.id]
                    if (targetBlock is DatabaseBlock && liveBlock is DatabaseBlock && expectedBlock is DatabaseBlock &&
                        (targetBlock.rows != expectedBlock.rows || targetBlock.columns != expectedBlock.columns)) {
                        result.add(reconcileDatabaseHistory(targetBlock, expectedBlock, liveBlock, now))
                    } else if (liveBlock != null && expectedBlock != null && !historySameContent(liveBlock, expectedBlock)) {
                        result.add(liveBlock)
                    } else {
                        result.add(targetBlock.withUpdatedAt(now))
                    }
                } else {
                    result.add(liveBlock ?: targetBlock)
                }
            }

            for (liveBlock in live) {
                if (!placed.add(liveBlock.id)) continue
                val createdByEntry = liveBlock.id in changed &&
                        !targetById.containsKey(liveBlock.id) &&
                        expectedById.containsKey(liveBlock.id)
                if (createdByEntry) {
                    val expectedBlock = expectedById[liveBlock.id]
                    if (expectedBlock != null && !historySameContent(liveBlock, expectedBlock)) result.add(liveBlock)
                    else result.add(liveBlock.markDeleted())
                } else {
                    result.add(liveBlock)
                }
            }
            result
        }
        isApplyingHistory = false
    }

    private fun reconcileDatabaseHistory(
        target: DatabaseBlock,
        expected: DatabaseBlock,
        live: DatabaseBlock,
        now: Long
    ): DatabaseBlock = live.copy(
        rows = reconcileDbRows(target.rows, expected.rows, live.rows, now),
        columns = reconcileDbColumns(target.columns, expected.columns, live.columns, now),
        updatedAt = now
    )

    private fun reconcileDbRows(
        targetRows: List<DatabaseRow>,
        expectedRows: List<DatabaseRow>,
        liveRows: List<DatabaseRow>,
        now: Long
    ): List<DatabaseRow> {
        val targetById = targetRows.associateBy { it.id }
        val expectedById = expectedRows.associateBy { it.id }
        val touched = (targetById.keys + expectedById.keys).filterTo(mutableSetOf()) { targetById[it] != expectedById[it] }
        val result = mutableListOf<DatabaseRow>()
        val placed = HashSet<String>()
        for (liveRow in liveRows) {
            if (!placed.add(liveRow.id)) continue
            if (liveRow.id in touched) {
                val expectedRow = expectedById[liveRow.id]
                if (expectedRow != null && liveRow.copy(updatedAt = 0L) != expectedRow.copy(updatedAt = 0L)) {
                    result.add(liveRow)
                } else {
                    val targetRow = targetById[liveRow.id]
                    if (targetRow != null) result.add(targetRow.copy(updatedAt = now))
                    else result.add(liveRow.copy(isDeleted = true, updatedAt = now))
                }
            } else {
                result.add(liveRow)
            }
        }
        for (targetRow in targetRows) {
            if (placed.add(targetRow.id)) result.add(targetRow.copy(updatedAt = now))
        }
        return result
    }

    private fun reconcileDbColumns(
        targetColumns: List<DatabaseColumn>,
        expectedColumns: List<DatabaseColumn>,
        liveColumns: List<DatabaseColumn>,
        now: Long
    ): List<DatabaseColumn> {
        val targetById = targetColumns.associateBy { it.id }
        val expectedById = expectedColumns.associateBy { it.id }
        val touched = (targetById.keys + expectedById.keys).filterTo(mutableSetOf()) { targetById[it] != expectedById[it] }
        val result = mutableListOf<DatabaseColumn>()
        val placed = HashSet<String>()
        for (liveColumn in liveColumns) {
            if (!placed.add(liveColumn.id)) continue
            if (liveColumn.id in touched) {
                val expectedColumn = expectedById[liveColumn.id]
                if (expectedColumn != null && liveColumn.copy(updatedAt = 0L) != expectedColumn.copy(updatedAt = 0L)) {
                    result.add(liveColumn)
                } else {
                    val targetColumn = targetById[liveColumn.id]
                    if (targetColumn != null) result.add(targetColumn.copy(updatedAt = now))
                    else result.add(liveColumn.copy(isDeleted = true, updatedAt = now))
                }
            } else {
                result.add(liveColumn)
            }
        }
        for (targetColumn in targetColumns) {
            if (placed.add(targetColumn.id)) result.add(targetColumn.copy(updatedAt = now))
        }
        return result
    }

    fun startHardwareRecording() {
        audioRecorder.startRecording()
    }

    fun stopHardwareRecording(blockId: String, cancel: Boolean = false) {
        val result = audioRecorder.stopRecording(cancel)
        if (result != null && !cancel) {
            handleVoiceRecorded(blockId, result.first, result.second)
        }
    }

    fun handleDbFilePicked(blockId: String, rowId: String, colId: String, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaInfo = mediaStorageHelper.copyUriToInternalStorage(uriString)
            if (mediaInfo != null) {
                withContext(Dispatchers.Main) {
                    val cleanFileName = mediaInfo.localFileName.substringAfterLast("/")
                    val now = System.currentTimeMillis()
                    modifyBlocks { list ->
                        mapBlockById(list, blockId) { db ->
                            if (db is DatabaseBlock) {
                                val updatedRows = db.rows.map { row ->
                                    if (row.id == rowId) {
                                        val currentFiles = (row.cells[colId] as? CellData.MediaList)?.files ?: emptyList()
                                        val newFiles = currentFiles + MediaItem(cleanFileName, mediaInfo.originalName)

                                        val newMap = row.cells.toMutableMap()
                                        newMap[colId] = CellData.MediaList(newFiles)
                                        row.copy(cells = newMap, updatedAt = now)
                                    } else row
                                }
                                db.copy(rows = updatedRows, updatedAt = now)
                            } else db
                        }
                    }
                    scheduleAutosave()
                }
            }
        }
    }

    fun stopDbHardwareRecording(blockId: String, rowId: String, colId: String, cancel: Boolean = false) {
        val result = audioRecorder.stopRecording(cancel)
        if (result != null && !cancel) {
            val cleanFileName = result.first.substringAfterLast("/")
            val now = System.currentTimeMillis()
            modifyBlocks { list ->
                mapBlockById(list, blockId) { db ->
                    if (db is DatabaseBlock) {
                        val updatedRows = db.rows.map { row ->
                            if (row.id == rowId) {
                                val currentFiles = (row.cells[colId] as? CellData.MediaList)?.files ?: emptyList()
                                val newFiles = currentFiles + MediaItem(cleanFileName, "Audio Recording.m4a")

                                val newMap = row.cells.toMutableMap()
                                newMap[colId] = CellData.MediaList(newFiles)
                                row.copy(cells = newMap, updatedAt = now)
                            } else row
                        }
                        db.copy(rows = updatedRows, updatedAt = now)
                    } else db
                }
            }
            scheduleAutosave()
        }
    }

    fun playAudio(fileName: String, onComplete: () -> Unit) {
        audioRecorder.play(fileName, onComplete)
    }

    fun stopAudio() {
        audioRecorder.stopPlaying()
    }

    fun toggleCheckbox(blockId: String, isChecked: Boolean) {
        if (isVirtualOccurrence(blockId)) {
            onVirtualOccurrenceToggled(blockId, isChecked)
            return
        }
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) {
                if (it is CheckboxBlock) {
                    if (isChecked) reminderScheduler.cancel(blockId)
                    it.copy(isChecked = isChecked, updatedAt = now)
                } else it
            }
        }
        scheduleAutosave()
    }

    fun toggleToggleBlock(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) {
                if (it is ToggleBlock) it.copy(isExpanded = !it.isExpanded, updatedAt = now) else it
            }
        }
        scheduleAutosave()
    }

    // A real (non-collapsed) text selection means "format just this range" - otherwise format applies
    // to the whole block.
    fun toggleFormat(format: String) {
        val id = currentlyFocusedBlockId ?: return
        val selection = GlobalEditorState.currentSelection
        val focusedCellKey = GlobalEditorState.currentlyFocusedTableCellKey
        if (focusedCellKey != null) {
            when (_blocks.value.firstOrNull { it.id == id }) {
                is TableBlock -> {
                    toggleTableCellFormat(id, focusedCellKey, format, selection)
                    return
                }
                is DatabaseBlock -> {
                    toggleDatabaseCellFormat(id, focusedCellKey, format, selection)
                    return
                }
                else -> Unit
            }
        }
        if (!selection.collapsed) {
            toggleInlineFormat(id, format, selection)
            return
        }
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, id) { b ->
                when (format) {
                    "bold" -> updateFormat(b, !b.isBold, b.isItalic, b.isStrikeThrough, b.isUnderlined, now)
                    "italic" -> updateFormat(b, b.isBold, !b.isItalic, b.isStrikeThrough, b.isUnderlined, now)
                    "strike" -> updateFormat(b, b.isBold, b.isItalic, !b.isStrikeThrough, b.isUnderlined, now)
                    "underline" -> updateFormat(b, b.isBold, b.isItalic, b.isStrikeThrough, !b.isUnderlined, now)
                    else -> b
                }
            }
        }
        scheduleAutosave()
    }

    private fun updateFormat(b: NoteBlock, bld: Boolean, itl: Boolean, stk: Boolean, und: Boolean, now: Long) = when (b) {
        is TextBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is HeadingBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is CheckboxBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is BulletedListBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is NumberedListBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is ToggleBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        is CodeBlock -> b
        is QuoteBlock -> b.copy(isBold = bld, isItalic = itl, isStrikeThrough = stk, isUnderlined = und, updatedAt = now)
        else -> b
    }

    private fun isFormatApplied(b: NoteBlock, format: String): Boolean = when (format) {
        "bold" -> b.isBold
        "italic" -> b.isItalic
        "strike" -> b.isStrikeThrough
        "underline" -> b.isUnderlined
        else -> false
    }

    // Mixed selections turn the format ON everywhere (Docs/Word convention) rather than toggling
    // each block independently, which would otherwise leave the selection in an inconsistent state.
    fun toggleFormatForSelectedBlocks(format: String) {
        val ids = _selectedBlockIds.value
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val turnOn = _blocks.value.any { it.id in ids && !isFormatApplied(it, format) }
        modifyBlocks { list ->
            list.map { b ->
                if (b.id !in ids) return@map b
                when (format) {
                    "bold" -> updateFormat(b, turnOn, b.isItalic, b.isStrikeThrough, b.isUnderlined, now)
                    "italic" -> updateFormat(b, b.isBold, turnOn, b.isStrikeThrough, b.isUnderlined, now)
                    "strike" -> updateFormat(b, b.isBold, b.isItalic, turnOn, b.isUnderlined, now)
                    "underline" -> updateFormat(b, b.isBold, b.isItalic, b.isStrikeThrough, turnOn, now)
                    else -> b
                }
            }
        }
        scheduleAutosave()
    }

    // Applies [format] to exactly [start, end) of one block's text, leaving the rest of the block
    // untouched. Delegates to toggleInlineSpanFormat, which expands the existing spans + this range
    // into a flat per-character flag array, flips the target flag, and re-derives minimal spans from
    // that - simpler to get right than manually splitting/merging overlapping InlineSpan ranges.
    private fun toggleInlineFormat(blockId: String, format: String, selection: TextRange) {
        val now = System.currentTimeMillis()
        var newSelection: TextRange? = null
        modifyBlocks { list ->
            mapBlockById(list, blockId) { b ->
                val text = getBlockText(b)
                val start = selection.min.coerceIn(0, text.length)
                val end = selection.max.coerceIn(0, text.length)
                if (start >= end) return@mapBlockById b
                newSelection = TextRange(start, end)
                val newSpans = toggleInlineSpanFormat(b.inlineSpansOrEmpty(), text.length, start, end, format)
                b.withInlineSpans(newSpans, now)
            }
        }
        newSelection?.let { _selectionRequest.value = SelectionRequest(blockId, it) }
        scheduleAutosave()
    }

    private fun toggleTableCellFormat(blockId: String, cellKey: String, format: String, selection: TextRange) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { b ->
                if (b !is TableBlock) return@mapBlockById b
                val cellText = tableCellTextAt(b.rows, cellKey)
                val start = selection.min.coerceIn(0, cellText.length)
                val end = selection.max.coerceIn(0, cellText.length)

                if (start < end) {
                    val newSpans = toggleInlineSpanFormat(b.cellSpans[cellKey].orEmpty(), cellText.length, start, end, format)
                    b.copy(cellSpans = b.cellSpans + (cellKey to newSpans), updatedAt = now)
                } else {
                    val newStyle = withFormatToggled(b.cellStyles[cellKey] ?: TableCellStyle(), format)
                        ?: return@mapBlockById b
                    b.copy(cellStyles = b.cellStyles + (cellKey to newStyle), updatedAt = now)
                }
            }
        }
        scheduleAutosave()
    }

    private fun toggleDatabaseCellFormat(blockId: String, cellKey: String, format: String, selection: TextRange) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { b ->
                if (b !is DatabaseBlock) return@mapBlockById b
                val cellText = databaseCellTextAt(b, cellKey)
                val start = selection.min.coerceIn(0, cellText.length)
                val end = selection.max.coerceIn(0, cellText.length)

                if (start < end) {
                    val newSpans = toggleInlineSpanFormat(b.cellSpans[cellKey].orEmpty(), cellText.length, start, end, format)
                    b.copy(cellSpans = b.cellSpans + (cellKey to newSpans), updatedAt = now)
                } else {
                    val newStyle = withFormatToggled(b.cellStyles[cellKey] ?: TableCellStyle(), format)
                        ?: return@mapBlockById b
                    b.copy(cellStyles = b.cellStyles + (cellKey to newStyle), updatedAt = now)
                }
            }
        }
        scheduleAutosave()
    }

    private fun withFormatToggled(style: TableCellStyle, format: String): TableCellStyle? = when (format) {
        "bold" -> style.copy(isBold = !style.isBold)
        "italic" -> style.copy(isItalic = !style.isItalic)
        "strike" -> style.copy(isStrikeThrough = !style.isStrikeThrough)
        "underline" -> style.copy(isUnderlined = !style.isUnderlined)
        else -> null
    }

    private fun databaseCellTextAt(block: DatabaseBlock, cellKey: String): String {
        val rowId = cellKey.substringBefore(':')
        val colId = cellKey.substringAfter(':')
        val cell = block.rows.firstOrNull { it.id == rowId }?.cells?.get(colId)
        return (cell as? CellData.Text)?.value ?: ""
    }

    private fun tableCellTextAt(rows: List<List<String>>, cellKey: String): String {
        val rowIndex = cellKey.substringBefore(':').toIntOrNull() ?: return ""
        val columnIndex = cellKey.substringAfter(':').toIntOrNull() ?: return ""
        return rows.getOrNull(rowIndex)?.getOrNull(columnIndex) ?: ""
    }

    private fun shiftTableCellSpans(block: TableBlock, newRows: List<List<String>>): Map<String, List<InlineSpan>> {
        if (block.cellSpans.isEmpty()) return block.cellSpans
        val gridShapeIsUnchanged = block.rows.size == newRows.size &&
                block.rows.indices.all { block.rows[it].size == newRows[it].size }
        if (!gridShapeIsUnchanged) return block.cellSpans

        return block.cellSpans.mapValues { (cellKey, spans) ->
            shiftSpansForEdit(spans, tableCellTextAt(block.rows, cellKey), tableCellTextAt(newRows, cellKey))
        }
    }

    private data class CharFormatFlags(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var strike: Boolean = false,
        var underline: Boolean = false
    )

    private fun List<InlineSpan>.toFlagsArray(length: Int): Array<CharFormatFlags> {
        val flags = Array(length) { CharFormatFlags() }
        for (span in this) {
            val start = span.start.coerceIn(0, length)
            val end = span.end.coerceIn(0, length)
            for (i in start until end) {
                if (span.bold) flags[i].bold = true
                if (span.italic) flags[i].italic = true
                if (span.strikeThrough) flags[i].strike = true
                if (span.underline) flags[i].underline = true
            }
        }
        return flags
    }

    private fun Array<CharFormatFlags>.toSpans(): List<InlineSpan> {
        val result = mutableListOf<InlineSpan>()
        var i = 0
        while (i < size) {
            val f = this[i]
            if (!f.bold && !f.italic && !f.strike && !f.underline) {
                i++
                continue
            }
            var j = i + 1
            while (j < size && this[j] == f) j++
            result.add(InlineSpan(i, j, bold = f.bold, italic = f.italic, strikeThrough = f.strike, underline = f.underline))
            i = j
        }
        return result
    }

    private fun toggleInlineSpanFormat(spans: List<InlineSpan>, textLength: Int, start: Int, end: Int, format: String): List<InlineSpan> {
        if (start >= end || textLength <= 0) return spans
        val flags = spans.toFlagsArray(textLength)
        val isFullyOn = (start until end).all { i ->
            when (format) {
                "bold" -> flags[i].bold
                "italic" -> flags[i].italic
                "strike" -> flags[i].strike
                "underline" -> flags[i].underline
                else -> false
            }
        }
        val newValue = !isFullyOn
        for (i in start until end) {
            when (format) {
                "bold" -> flags[i].bold = newValue
                "italic" -> flags[i].italic = newValue
                "strike" -> flags[i].strike = newValue
                "underline" -> flags[i].underline = newValue
            }
        }
        return flags.toSpans()
    }

    // Keeps InlineSpan offsets valid as the underlying text is edited. Assumes a single contiguous
    // insert/delete (true for normal typing/backspacing/pasting with one cursor) - finds the common
    // prefix/suffix between old and new text and shifts anything outside that edited region by the
    // resulting length delta. A span whose start falls inside the edited region collapses its start
    // to the edit point rather than producing a nonsensical range.
    private fun shiftSpansForEdit(spans: List<InlineSpan>, oldText: String, newText: String): List<InlineSpan> {
        if (spans.isEmpty() || oldText == newText) return spans
        val prefixLen = oldText.commonPrefixWith(newText).length
        val maxSuffix = minOf(oldText.length, newText.length) - prefixLen
        var suffixLen = 0
        while (suffixLen < maxSuffix && oldText[oldText.length - 1 - suffixLen] == newText[newText.length - 1 - suffixLen]) {
            suffixLen++
        }
        val oldChangeStart = prefixLen
        val oldChangeEnd = oldText.length - suffixLen
        val delta = (newText.length - suffixLen) - oldChangeEnd

        fun mapOffset(old: Int): Int = when {
            old <= oldChangeStart -> old
            old >= oldChangeEnd -> old + delta
            else -> oldChangeStart
        }

        return spans.mapNotNull { span ->
            val newStart = mapOffset(span.start)
            val newEnd = mapOffset(span.end)
            if (newStart >= newEnd) null else span.copy(start = newStart, end = newEnd)
        }
    }

    fun setFocusedBlockAlignment(alignment: TextAlignment) {
        val id = currentlyFocusedBlockId ?: return
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, id) { it.withTextAlignment(alignment, now) } }
        scheduleAutosave()
    }

    private fun withIndentation(b: NoteBlock, newLevel: Int, now: Long): NoteBlock = when (b) {
        is TextBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
        is HeadingBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
        is CheckboxBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
        is BulletedListBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
        is NumberedListBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
        is ToggleBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
        is QuoteBlock -> b.copy(indentationLevel = newLevel, updatedAt = now)
        else -> b
    }

    fun adjustIndentation(increment: Boolean) {
        val id = currentlyFocusedBlockId ?: return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, id) { b ->
                val newLevel = if (increment) b.indentationLevel + 1 else maxOf(0, b.indentationLevel - 1)
                withIndentation(b, newLevel, now)
            }
        }
        scheduleAutosave()
    }

    fun adjustIndentationForSelectedBlocks(increment: Boolean) {
        val ids = _selectedBlockIds.value
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            list.map { b ->
                if (b.id !in ids) return@map b
                val newLevel = if (increment) b.indentationLevel + 1 else maxOf(0, b.indentationLevel - 1)
                withIndentation(b, newLevel, now)
            }
        }
        scheduleAutosave()
    }

    fun changeFocusedBlockType(type: String) {
        val id = currentlyFocusedBlockId ?: return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            spliceAtBlock(list, id) { mutable, idx ->
                val b = mutable[idx]
                val blockHoldsEditableText = b.textAlignmentOrNull() != null
                if (!blockHoldsEditableText) return@spliceAtBlock
                val rawText = getBlockText(b)

                val slashIndex = rawText.lastIndexOf('/')
                val isActivelySearching = slashIndex != -1 && !rawText.substring(slashIndex).contains(" ")
                val cleanedText = if (isActivelySearching) rawText.substring(0, slashIndex) else rawText

                val isNonTextBlock = type == "divider_solid" || type == "divider_dots" || type == "voice"

                if (isNonTextBlock && cleanedText.isNotEmpty()) {
                    val updatedText = when (b) {
                        is TextBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is HeadingBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is CheckboxBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is BulletedListBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is NumberedListBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is ToggleBlock -> b.copy(text = cleanedText, updatedAt = now)
                        is CodeBlock -> b.copy(code = cleanedText, updatedAt = now)
                        is QuoteBlock -> b.copy(text = cleanedText, updatedAt = now)
                        else -> b
                    }
                    mutable[idx] = updatedText

                    val newId = UUID.randomUUID().toString()
                    val newBlock = when (type) {
                        "divider_solid" -> SolidDividerBlock(id = newId, indentationLevel = b.indentationLevel, updatedAt = now)
                        "divider_dots" -> ThreeDotDividerBlock(id = newId, indentationLevel = b.indentationLevel, updatedAt = now)
                        "voice" -> VoiceBlock(id = newId, indentationLevel = b.indentationLevel, updatedAt = now)
                        else -> b
                    }
                    mutable.add(idx + 1, newBlock.withPin(b.isPinned, now))
                } else {
                    val inheritedAlignment = b.textAlignmentOrNull() ?: TextAlignment.LEFT
                    val inheritedSpans = shiftSpansForEdit(b.inlineSpansOrEmpty(), rawText, cleanedText)
                    val newBlock = when (type) {
                        "text" -> TextBlock(id, cleanedText, b.indentationLevel, inheritedAlignment, inheritedSpans, updatedAt = now)
                        "h1" -> HeadingBlock(id, cleanedText, 1, b.indentationLevel, inheritedAlignment, inheritedSpans, updatedAt = now)
                        "h2" -> HeadingBlock(id, cleanedText, 2, b.indentationLevel, inheritedAlignment, inheritedSpans, updatedAt = now)
                        "checkbox" -> CheckboxBlock(id, cleanedText, false, b.indentationLevel, inheritedAlignment, inheritedSpans, updatedAt = now)
                        "quote" -> QuoteBlock(id, cleanedText, b.indentationLevel, inheritedAlignment, inheritedSpans, updatedAt = now)
                        "bullet" -> BulletedListBlock(id, cleanedText, b.indentationLevel, inheritedAlignment, inheritedSpans, updatedAt = now)
                        "number" -> NumberedListBlock(id, cleanedText, 1, b.indentationLevel, inheritedAlignment, inheritedSpans, updatedAt = now)
                        "toggle" -> ToggleBlock(id, cleanedText, true, b.indentationLevel, inheritedAlignment, inheritedSpans, updatedAt = now)
                        "code" -> CodeBlock(id, cleanedText, textAlignment = inheritedAlignment, updatedAt = now)
                        "voice" -> VoiceBlock(id, indentationLevel = b.indentationLevel, updatedAt = now)
                        "divider_solid" -> SolidDividerBlock(id = id, indentationLevel = b.indentationLevel, updatedAt = now)
                        "divider_dots" -> ThreeDotDividerBlock(id = id, indentationLevel = b.indentationLevel, updatedAt = now)
                        else -> b
                    }

                    mutable[idx] = newBlock.withPin(b.isPinned, now)

                    if (type == "toggle") {
                        val nextBlock = mutable.getOrNull(idx + 1)
                        if (nextBlock == null || nextBlock.indentationLevel <= b.indentationLevel) {
                            mutable.add(idx + 1, TextBlock(UUID.randomUUID().toString(), "", b.indentationLevel + 1, inheritedAlignment, updatedAt = now))
                        }
                    }
                }
            }
        }
        scheduleAutosave()
    }

    // Bulk conversion for a multi-block selection.
    fun changeBlockTypeForSelectedBlocks(type: String) {
        val ids = _selectedBlockIds.value
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            list.map { b ->
                val alignment = b.textAlignmentOrNull()
                if (b.id !in ids || alignment == null) return@map b
                val text = getBlockText(b)
                val spans = b.inlineSpansOrEmpty()
                val newBlock = when (type) {
                    "text" -> TextBlock(b.id, text, b.indentationLevel, alignment, spans, updatedAt = now)
                    "h1" -> HeadingBlock(b.id, text, 1, b.indentationLevel, alignment, spans, updatedAt = now)
                    "h2" -> HeadingBlock(b.id, text, 2, b.indentationLevel, alignment, spans, updatedAt = now)
                    "checkbox" -> CheckboxBlock(b.id, text, false, b.indentationLevel, alignment, spans, updatedAt = now)
                    "quote" -> QuoteBlock(b.id, text, b.indentationLevel, alignment, spans, updatedAt = now)
                    "bullet" -> BulletedListBlock(b.id, text, b.indentationLevel, alignment, spans, updatedAt = now)
                    "number" -> NumberedListBlock(b.id, text, 1, b.indentationLevel, alignment, spans, updatedAt = now)
                    "toggle" -> ToggleBlock(b.id, text, true, b.indentationLevel, alignment, spans, updatedAt = now)
                    "code" -> CodeBlock(b.id, text, textAlignment = alignment, updatedAt = now)
                    else -> b
                }
                newBlock.withPin(b.isPinned, now)
            }
        }
        scheduleAutosave()
    }

    // The single authoritative slash strip. Runs synchronously against _blocks.value rather than a
    // composition snapshot, and uses the text itself rather than slashQuery - which desyncs the moment
    // isSlashKilled forces it to "" while the block text keeps its "/whatever".
    fun clearActiveSlashQuery() {
        val id = currentlyFocusedBlockId ?: return
        val block = _blocks.value.firstOrNull { it.id == id } ?: return
        val raw = getBlockText(block)
        val slashIndex = raw.lastIndexOf('/')
        if (slashIndex == -1) return
        if (raw.substring(slashIndex).contains(" ")) return
        updateBlockText(id, raw.substring(0, slashIndex))
    }

    private fun getBlockText(b: NoteBlock) = when (b) {
        is TextBlock -> b.text
        is HeadingBlock -> b.text
        is CheckboxBlock -> b.text
        is BulletedListBlock -> b.text
        is NumberedListBlock -> b.text
        is ToggleBlock -> b.text
        is CodeBlock -> b.code
        is QuoteBlock -> b.text
        else -> ""
    }

    // Splits a block's existing InlineSpans at [splitPoint] (the cursor position where Enter was
    // pressed) so the "before" half keeps spans in their original coordinates and the "after" half
    // gets spans clipped to the tail and rebased to start at 0 in the new block's own text.
    private fun splitSpansAt(spans: List<InlineSpan>, splitPoint: Int): Pair<List<InlineSpan>, List<InlineSpan>> {
        val before = mutableListOf<InlineSpan>()
        val after = mutableListOf<InlineSpan>()
        for (span in spans) {
            when {
                span.end <= splitPoint -> before.add(span)
                span.start >= splitPoint -> after.add(span.copy(start = span.start - splitPoint, end = span.end - splitPoint))
                else -> {
                    before.add(span.copy(end = splitPoint))
                    after.add(span.copy(start = 0, end = span.end - splitPoint))
                }
            }
        }
        return before to after
    }

    fun handleEnter(id: String, textBefore: String, textAfter: String) {
        if (isVirtualOccurrence(id)) {
            val firstUnpinned = _blocks.value.firstOrNull { !it.isDeleted && !it.isPinned }
            if (firstUnpinned is TextBlock && firstUnpinned.text.isEmpty()) {
                _focusRequest.value = FocusRequest(id = firstUnpinned.id, placeCursorAtEnd = true)
                return
            }
            val newId = UUID.randomUUID().toString()
            modifyBlocks { list ->
                list.toMutableList().apply {
                    add(0, TextBlock(id = newId, text = "", indentationLevel = 0, updatedAt = System.currentTimeMillis()))
                }
            }
            _focusRequest.value = FocusRequest(id = newId, placeCursorAtEnd = true)
            scheduleAutosave()
            return
        }
        var blockToFocusId = ""
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            spliceAtBlock(list, id) { mutable, idx ->
                val cur = mutable[idx]
                val newId = UUID.randomUUID().toString()
                blockToFocusId = newId
                var insertIdx = idx + 1

                val (spansBefore, spansAfter) = splitSpansAt(cur.inlineSpansOrEmpty(), textBefore.length)
                val updatedCurrent = withText(cur, textBefore, now, spansBefore)

                val inheritedAlignment = cur.textAlignmentOrNull() ?: TextAlignment.LEFT
                val newBlock = when (cur) {
                    is CheckboxBlock -> CheckboxBlock(newId, textAfter, false, cur.indentationLevel, inheritedAlignment, spansAfter, isPinned = cur.isPinned, updatedAt = now)
                    is BulletedListBlock -> BulletedListBlock(newId, textAfter, cur.indentationLevel, inheritedAlignment, spansAfter, isPinned = cur.isPinned, updatedAt = now)
                    is NumberedListBlock -> NumberedListBlock(newId, textAfter, cur.number + 1, cur.indentationLevel, inheritedAlignment, spansAfter, isPinned = cur.isPinned, updatedAt = now)
                    is HeadingBlock -> TextBlock(newId, textAfter, 0, inheritedAlignment, spansAfter, isPinned = cur.isPinned, updatedAt = now)
                    is QuoteBlock -> QuoteBlock(newId, textAfter, cur.indentationLevel, inheritedAlignment, spansAfter, isPinned = cur.isPinned, updatedAt = now)
                    is ToggleBlock -> {
                        if (cur.isExpanded) {
                            TextBlock(newId, textAfter, cur.indentationLevel + 1, inheritedAlignment, spansAfter, isPinned = cur.isPinned, updatedAt = now)
                        } else {
                            var i = idx + 1
                            while (i < mutable.size && mutable[i].indentationLevel > cur.indentationLevel) i++
                            insertIdx = i
                            ToggleBlock(newId, textAfter, false, cur.indentationLevel, inheritedAlignment, spansAfter, isPinned = cur.isPinned, updatedAt = now)
                        }
                    }
                    else -> TextBlock(newId, textAfter, cur.indentationLevel, inheritedAlignment, spansAfter, isPinned = cur.isPinned, updatedAt = now)
                }

                mutable[idx] = updatedCurrent
                mutable.add(insertIdx, newBlock)
            }
        }

        if (blockToFocusId.isNotEmpty()) {
            _focusRequest.value = FocusRequest(id = blockToFocusId)
            scheduleAutosave()
        }
    }

    fun handleBackspaceOnEmpty(id: String) {
        val currentBlocks = _blocks.value
        val idx = currentBlocks.indexOfFirst { it.id == id }
        if (idx == -1) return

        val cur = currentBlocks[idx]
        val now = System.currentTimeMillis()

        // Backspacing an empty recurring checkbox away from a CheckboxBlock is effectively
        // deleting that occurrence/series - it needs the same four-way scope decision as an
        // explicit delete instead of silently downgrading it to a plain TextBlock below.
        if (cur is CheckboxBlock && (isVirtualOccurrence(id) || cur.recurrenceRule != null)) {
            val occurrenceDate = occurrenceDateFor(cur, id)
            if (occurrenceDate != null) {
                requestRecurringDeletion(listOf(RecurringDeletionTarget(id, occurrenceDate)))
                return
            }
        }

        if (cur !is TextBlock) {
            val inheritedAlignment = cur.textAlignmentOrNull() ?: TextAlignment.LEFT
            modifyBlocks { list ->
                spliceAtBlock(list, id) { mutable, i ->
                    mutable[i] = TextBlock(cur.id, "", cur.indentationLevel, inheritedAlignment, isPinned = cur.isPinned, updatedAt = now)
                }
            }
            scheduleAutosave()
            return
        }

        if (currentBlocks.size <= 1) return

        val prevBlock = currentBlocks.subList(0, idx).lastOrNull { !it.isDeleted }

        if (prevBlock is ToggleBlock && prevBlock.indentationLevel == cur.indentationLevel - 1) {
            val nextBlock = currentBlocks.subList(idx + 1, currentBlocks.size).firstOrNull { !it.isDeleted }
            val isOnlyChild = nextBlock == null || nextBlock.indentationLevel < cur.indentationLevel
            if (isOnlyChild) return
        }

        if (prevBlock != null) {
            val isMediaOrDivider = prevBlock is ImageBlock || prevBlock is DocumentBlock ||
                    prevBlock is DatabaseBlock || prevBlock is SolidDividerBlock ||
                    prevBlock is ThreeDotDividerBlock || prevBlock is BookmarkBlock ||
                    prevBlock is VoiceBlock

            if (isMediaOrDivider) {
                modifyBlocks { list ->
                    deleteBlockEverywhereById(list, prevBlock.id)
                }
                scheduleAutosave()
            } else {
                _focusRequest.value = FocusRequest(id = prevBlock.id, placeCursorAtEnd = true)
                viewModelScope.launch {
                    delay(50.milliseconds)
                    modifyBlocks { list ->
                        deleteBlockEverywhereById(list, id)
                    }
                    scheduleAutosave()
                }
            }
        } else {
            val nextBlock = currentBlocks.subList(idx + 1, currentBlocks.size).firstOrNull { !it.isDeleted }
            if (nextBlock != null) {
                _focusRequest.value = FocusRequest(id = nextBlock.id, placeCursorAtEnd = false)
                viewModelScope.launch {
                    delay(50.milliseconds)
                    modifyBlocks { list ->
                        deleteBlockEverywhereById(list, id)
                    }
                    scheduleAutosave()
                }
            }
        }
    }

    private fun deleteBlockEverywhereById(list: List<NoteBlock>, id: String): List<NoteBlock> {
        val spliced = spliceAtBlock(list, id) { mutable, i -> mutable[i] = mutable[i].markDeleted() }
        return mapBlockById(spliced, id) { it.markDeleted() }
    }

    fun addBlankBlockBelowFocused() {
        val targetId = currentlyFocusedBlockId ?: _blocks.value.lastOrNull()?.id ?: return
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            val idx = list.indexOfFirst { it.id == targetId }
            val indent = if (idx != -1) list[idx].indentationLevel else 0
            val isPinnedContext = if (idx != -1) list[idx].isPinned else false
            val alignmentContext = if (idx != -1) list[idx].textAlignmentOrNull() ?: TextAlignment.LEFT else TextAlignment.LEFT
            val new = TextBlock(id = newId, text = "", indentationLevel = indent, textAlignment = alignmentContext, isPinned = isPinnedContext, updatedAt = now)
            list.toMutableList().apply {
                if (idx != -1) add(idx + 1, new) else add(new)
            }
        }
        _focusRequest.value = FocusRequest(id = newId)
        scheduleAutosave()
    }

    fun setFocusedBlock(id: String) {
        if (id != currentlyFocusedBlockId) sealHistoryCoalescing()
        currentlyFocusedBlockId = id
    }

    // Tapping an unfocused block's text goes through a separate tap-detection overlay first (to
    // distinguish tapping a note-link from tapping plain text - see NoteBlockItem), which already
    // grants focus directly via its own FocusRequester for instant response. That path alone never
    // touches the field's TextFieldValue.selection though, so the cursor stayed wherever it last
    // was (usually the end) instead of where the tap landed. This rides the same SelectionRequest
    // channel toggleInlineFormat uses to fix that, in parallel with (not instead of) the direct focus.
    fun requestCursorPosition(blockId: String, offset: Int) {
        currentlyFocusedBlockId = blockId
        _selectionRequest.value = SelectionRequest(blockId, TextRange(offset))
    }
    fun clearFocusRequest() { _focusRequest.value = null }
    fun toggleSelection(id: String) { _selectedBlockIds.update { if (it.contains(id)) it - id else it + id } }
    fun clearSelection() { _selectedBlockIds.value = emptySet() }
    fun selectAllBlocks() { _selectedBlockIds.value = _blocks.value.map { it.id }.toSet() }

    fun getSelectedText(): String {
        val ids = _selectedBlockIds.value
        return _blocks.value.filter { it.id in ids }.joinToString("\n") { getBlockText(it) }
    }

    fun deleteSelectedBlocks() {
        val toDelete = _selectedBlockIds.value
        val currentBlocks = _blocks.value

        val recurringTargets = mutableListOf<RecurringDeletionTarget>()
        val plainIds = mutableSetOf<String>()
        for (id in toDelete) {
            val block = currentBlocks.firstOrNull { it.id == id }
            val occurrenceDate = (block as? CheckboxBlock)
                ?.takeIf { isVirtualOccurrence(id) || it.recurrenceRule != null }
                ?.let { occurrenceDateFor(it, id) }
            if (occurrenceDate != null) {
                recurringTargets.add(RecurringDeletionTarget(id, occurrenceDate))
            } else {
                plainIds.add(id)
            }
        }

        if (recurringTargets.isNotEmpty()) {
            requestRecurringDeletion(recurringTargets, plainIds)
            return
        }

        performPlainDeletion(plainIds)
        clearSelection()
    }

    private fun performPlainDeletion(toDelete: Set<String>) {
        if (toDelete.isEmpty()) return
        val now = System.currentTimeMillis()

        modifyBlocks { list ->
            val afterDelete = list.map { b -> if (b.id in toDelete) b.markDeleted() else b }
            val hasVisible = afterDelete.any { !it.isDeleted }
            if (!hasVisible) {
                afterDelete + listOf(TextBlock(id = UUID.randomUUID().toString(), text = "", updatedAt = now))
            } else {
                afterDelete
            }
        }

        scheduleAutosave()
    }

    fun cutSelectedBlocks(): String {
        val text = getSelectedText()
        deleteSelectedBlocks()
        return text
    }

    fun addBlockAboveSelection() {
        val selected = _selectedBlockIds.value
        if (selected.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            spliceAtBlock(list, selected.first()) { mutable, idx ->
                val targetLevel = mutable[idx].indentationLevel
                val targetAlignment = mutable[idx].textAlignmentOrNull() ?: TextAlignment.LEFT
                mutable.add(idx, TextBlock(id = UUID.randomUUID().toString(), text = "", indentationLevel = targetLevel, textAlignment = targetAlignment, updatedAt = now))
            }
        }
        clearSelection()
        scheduleAutosave()
    }

    fun addBlockBelowSelection() {
        val selected = _selectedBlockIds.value
        if (selected.isEmpty()) return
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            spliceAtBlock(list, selected.last()) { mutable, idx ->
                val targetLevel = mutable[idx].indentationLevel
                val targetAlignment = mutable[idx].textAlignmentOrNull() ?: TextAlignment.LEFT
                mutable.add(idx + 1, TextBlock(id = UUID.randomUUID().toString(), text = "", indentationLevel = targetLevel, textAlignment = targetAlignment, updatedAt = now))
            }
        }
        clearSelection()
        scheduleAutosave()
    }

    private fun withText(b: NoteBlock, newText: String, now: Long, spans: List<InlineSpan> = b.inlineSpansOrEmpty()): NoteBlock = when (b) {
        is TextBlock -> b.copy(text = newText, inlineSpans = spans, updatedAt = now)
        is HeadingBlock -> b.copy(text = newText, inlineSpans = spans, updatedAt = now)
        is CheckboxBlock -> b.copy(text = newText, inlineSpans = spans, updatedAt = now)
        is BulletedListBlock -> b.copy(text = newText, inlineSpans = spans, updatedAt = now)
        is NumberedListBlock -> b.copy(text = newText, inlineSpans = spans, updatedAt = now)
        is ToggleBlock -> b.copy(text = newText, inlineSpans = spans, updatedAt = now)
        is CodeBlock -> b.copy(code = newText, updatedAt = now)
        is QuoteBlock -> b.copy(text = newText, inlineSpans = spans, updatedAt = now)
        else -> b
    }

    fun updateBlockText(blockId: String, newText: String) {
        if (isVirtualOccurrence(blockId)) {
            onVirtualOccurrenceTextEdited(blockId, newText)
            return
        }
        val existing = _blocks.value.firstOrNull { it.id == blockId }
        if (existing != null && !existing.isDeleted && getBlockText(existing) == newText) return

        val now = System.currentTimeMillis()

        modifyBlocks { list ->
            mapBlockById(list, blockId) { b ->
                val shiftedSpans = shiftSpansForEdit(b.inlineSpansOrEmpty(), getBlockText(b), newText)
                withText(b, newText, now, shiftedSpans)
            }
        }
        scheduleAutosave()
    }

    protected fun findBlockById(blocks: List<NoteBlock>, id: String): NoteBlock? =
        blocks.find { it.id == id }

    protected fun mapBlockById(
        blocks: List<NoteBlock>,
        id: String,
        transform: (NoteBlock) -> NoteBlock
    ): List<NoteBlock> = blocks.map { b -> if (b.id == id) transform(b) else b }

    protected fun spliceAtBlock(
        blocks: List<NoteBlock>,
        id: String,
        onFound: (MutableList<NoteBlock>, Int) -> Unit
    ): List<NoteBlock> {
        val idx = blocks.indexOfFirst { it.id == id }
        if (idx == -1) return blocks
        val mutable = blocks.toMutableList()
        onFound(mutable, idx)
        return mutable
    }

    protected fun recalculateNumberedLists(blocks: List<NoteBlock>): List<NoteBlock> {
        val uniqueBlocks = blocks.distinctBy { it.id }
        val counters = mutableMapOf<Int, Int>()
        val now = System.currentTimeMillis()
        return uniqueBlocks.map { block ->
            if (block is NumberedListBlock) {
                val currentNum = counters.getOrDefault(block.indentationLevel, 1)
                counters[block.indentationLevel] = currentNum + 1
                if (block.number != currentNum) {
                    block.copy(number = currentNum, updatedAt = now)
                } else block
            } else {
                val keysToReset = counters.keys.filter { it >= block.indentationLevel }
                keysToReset.forEach { counters.remove(it) }
                block
            }
        }
    }

    open fun updateReminder(blockId: String, timestamp: Long?) {
        val now = System.currentTimeMillis()
        val blockText = (findBlockById(_blocks.value, blockId) as? CheckboxBlock)?.text ?: ""
        modifyBlocks { list ->
            mapBlockById(list, blockId) {
                if (it is CheckboxBlock) it.copy(reminderTimestamp = timestamp, updatedAt = now) else it
            }
        }
        scheduleAutosave()

        if (timestamp != null) {
            reminderScheduler.schedule(
                blockId = blockId,
                noteTitle = getNoteTitleForReminder(),
                text = blockText.ifBlank { "Unfinished task" },
                timestamp = timestamp
            )
        } else {
            reminderScheduler.cancel(blockId)
        }
    }

    private fun buildDatabaseBlock(
        id: String,
        indent: Int,
        isPinned: Boolean,
        now: Long,
        template: DatabaseTemplateEntity?
    ): DatabaseBlock {
        if (template == null) {
            val defaultViewId = UUID.randomUUID().toString()
            return DatabaseBlock(
                id = id,
                columns = listOf(DatabaseColumn(id = UUID.randomUUID().toString(), databaseId = id, name = "Name", type = ColumnType.TEXT, updatedAt = now)),
                rows = emptyList(),
                views = listOf(DatabaseView(id = defaultViewId, name = "Table", type = ViewType.TABLE)),
                activeViewId = defaultViewId,
                indentationLevel = indent,
                isPinned = isPinned,
                updatedAt = now
            )
        }

        val templateColumns = try {
            templateJson.decodeFromString<List<DatabaseColumn>>(template.serializedColumns)
        } catch (_: Exception) {
            emptyList()
        }
        val templateViews = try {
            templateJson.decodeFromString<List<DatabaseView>>(template.serializedViews)
        } catch (_: Exception) {
            emptyList()
        }

        val oldToNewColumnId = templateColumns.associate { it.id to UUID.randomUUID().toString() }

        val newColumns = templateColumns.map { col ->
            col.copy(id = oldToNewColumnId.getValue(col.id), databaseId = id, updatedAt = now)
        }
        val newViews = templateViews.map { view ->
            view.copy(
                id = UUID.randomUUID().toString(),
                groupByColumnId = view.groupByColumnId?.let { oldToNewColumnId[it] }
            )
        }

        return DatabaseBlock(
            id = id,
            columns = newColumns,
            rows = emptyList(),
            views = newViews,
            activeViewId = newViews.firstOrNull()?.id,
            indentationLevel = indent,
            isPinned = isPinned,
            updatedAt = now
        )
    }

    /**
     * Saves a DatabaseBlock's schema (columns + views) as a reusable template. Rows are
     * intentionally never captured - a template is a blank-slate shape, not a data snapshot.
     */
    fun saveDatabaseAsTemplate(blockId: String, templateName: String) {
        val block = findBlockById(_blocks.value, blockId) as? DatabaseBlock ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertDatabaseTemplate(
                DatabaseTemplateEntity(
                    templateId = UUID.randomUUID().toString(),
                    name = templateName,
                    serializedColumns = templateJson.encodeToString(block.columns),
                    serializedViews = templateJson.encodeToString(block.views)
                )
            )
        }
    }

    fun insertNewMediaBlock(type: String, databaseTemplate: DatabaseTemplateEntity? = null, linkedNoteId: String? = null) {
        val activeBlockId = currentlyFocusedBlockId ?: _focusRequest.value?.id ?: _selectedBlockIds.value.firstOrNull()
        var newIdToFocus: String? = null
        val now = System.currentTimeMillis()

        modifyBlocks { list ->
            val mutableList = list.toMutableList()
            val newId = UUID.randomUUID().toString()
            newIdToFocus = newId

            val activeIndex = if (activeBlockId != null) mutableList.indexOfFirst { it.id == activeBlockId } else mutableList.size - 1
            val indent = if (activeIndex != -1) mutableList[activeIndex].indentationLevel else 0
            val isPinnedContext = if (activeIndex != -1) mutableList[activeIndex].isPinned else false

            val newBlock = when (type) {
                "image" -> ImageBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                "document" -> DocumentBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                "bookmark" -> BookmarkBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                "linked_note" -> {
                    if (linkedNoteId == null) return@modifyBlocks list
                    LinkedNoteBlock(id = newId, linkedNoteId = linkedNoteId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                }
                "voice" -> VoiceBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                "database" -> buildDatabaseBlock(newId, indent, isPinnedContext, now, databaseTemplate)
                "table" -> TableBlock(id = newId, indentationLevel = indent, isPinned = isPinnedContext, updatedAt = now)
                else -> return@modifyBlocks list
            }

            if (activeIndex != -1) {
                val activeBlock = mutableList[activeIndex]
                if (activeBlock is TextBlock) {
                    val text = activeBlock.text
                    val slashIndex = text.lastIndexOf('/')
                    val isActivelySearching = slashIndex != -1 && !text.substring(slashIndex).contains(" ")

                    val cleanedText = if (isActivelySearching) text.substring(0, slashIndex) else text

                    if (cleanedText.isEmpty()) {
                        mutableList[activeIndex] = newBlock
                    } else {
                        mutableList[activeIndex] = activeBlock.copy(text = cleanedText, updatedAt = now)
                        mutableList.add(activeIndex + 1, newBlock)
                    }
                } else {
                    mutableList.add(activeIndex + 1, newBlock)
                }
            } else mutableList.add(newBlock)

            mutableList
        }
        newIdToFocus?.let { _focusRequest.value = FocusRequest(id = it) }
        scheduleAutosave()
    }

    fun updateTable(blockId: String, rows: List<List<String>>) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) {
                if (it is TableBlock) {
                    it.copy(rows = rows, cellSpans = shiftTableCellSpans(it, rows), updatedAt = now)
                } else it
            }
        }
        scheduleAutosave()
    }

    fun updateTableColumnWidth(blockId: String, columnIndex: Int, width: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) {
                if (it is TableBlock) it.copy(
                    columnWidths = it.columnWidths + (columnIndex.toString() to width.coerceIn(40, 600)),
                    updatedAt = now
                ) else it
            }
        }
        scheduleAutosave()
    }

    fun updateTableStyle(
        blockId: String,
        cellStyles: Map<String, TableCellStyle>,
        rowStyles: Map<String, TableCellStyle>,
        columnStyles: Map<String, TableCellStyle>
    ) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) {
                if (it is TableBlock) it.copy(
                    cellStyles = cellStyles,
                    rowStyles = rowStyles,
                    columnStyles = columnStyles,
                    updatedAt = now
                ) else it
            }
        }
        scheduleAutosave()
    }

    fun handleUrlSubmit(blockId: String, url: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { if (it is BookmarkBlock) it.copy(url = url, title = "Loading...", updatedAt = now) else it } }
        viewModelScope.launch(Dispatchers.IO) {
            val metadata = HtmlMetadataFetcher.fetchMetadata(url)
            val fetchedAt = System.currentTimeMillis()
            modifyBlocks { list ->
                mapBlockById(list, blockId) {
                    if (it is BookmarkBlock)
                        it.copy(title = metadata.title, description = metadata.description, previewImageUrl = metadata.imageUrl, updatedAt = fetchedAt)
                    else it
                }
            }
            scheduleAutosave()
        }
    }

    fun handleImagePicked(blockId: String, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaInfo = mediaStorageHelper.copyUriToInternalStorage(uriString)
            if (mediaInfo != null) {
                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    modifyBlocks { list -> mapBlockById(list, blockId) { if (it is ImageBlock) it.copy(localFilePath = mediaInfo.localFileName, updatedAt = now) else it } }
                    scheduleAutosave()
                }
            }
        }
    }

    fun handleDocumentPicked(blockId: String, uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaInfo = mediaStorageHelper.copyUriToInternalStorage(uriString)
            if (mediaInfo != null) {
                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    modifyBlocks { list ->
                        mapBlockById(list, blockId) {
                            if (it is DocumentBlock) {
                                it.copy(localFilePath = mediaInfo.localFileName, fileName = mediaInfo.originalName, mimeType = mediaInfo.mimeType, fileSizeString = mediaInfo.formattedSize, updatedAt = now)
                            } else it
                        }
                    }
                    scheduleAutosave()
                }
            }
        }
    }

    fun deleteImageBlock(blockId: String) {
        modifyBlocks { list -> mapBlockById(list, blockId) { it.markDeleted() } }
        scheduleAutosave()
    }

    fun handleVoiceRecorded(blockId: String, filePath: String, duration: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { if (it is VoiceBlock) it.copy(localFilePath = filePath, durationSeconds = duration, updatedAt = now) else it } }
        scheduleAutosave()
    }

    fun handleRemoveVoice(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { if (it is VoiceBlock) it.copy(localFilePath = null, durationSeconds = 0, updatedAt = now) else it } }
        scheduleAutosave()
    }

    /**
     * Sorts/filters now live per-[DatabaseView] instead of on the block root, so every mutation
     * needs to find the currently active view before touching them. Falls back to the first view
     * when [DatabaseBlock.activeViewId] hasn't been set yet, and is a no-op if there are no views
     * at all (shouldn't happen for blocks created after this refactor).
     */
    private fun DatabaseBlock.withActiveViewUpdated(now: Long, transform: (DatabaseView) -> DatabaseView): DatabaseBlock {
        val targetViewId = activeViewId ?: views.firstOrNull()?.id ?: return this
        return copy(views = views.map { if (it.id == targetViewId) transform(it) else it }, updatedAt = now)
    }

    fun updateDbTitle(blockId: String, newTitle: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { if (it is DatabaseBlock) it.copy(title = newTitle, updatedAt = now) else it } }
        scheduleAutosave()
    }

    fun addDbRow(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { if (it is DatabaseBlock) it.copy(rows = it.rows + DatabaseRow(id = UUID.randomUUID().toString(), databaseId = blockId, cells = emptyMap(), updatedAt = now), updatedAt = now) else it } }
        scheduleAutosave()
    }

    fun addDbColumn(blockId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { if (it is DatabaseBlock) it.copy(columns = it.columns + DatabaseColumn(id = UUID.randomUUID().toString(), databaseId = blockId, name = "New Column", type = ColumnType.TEXT, updatedAt = now), updatedAt = now) else it } }
        scheduleAutosave()
    }

    suspend fun getNoteTitle(noteId: String): String {
        return repository.getNoteById(noteId)?.title ?: ""
    }

    suspend fun getNoteMetadata(noteId: String): NoteMetadataEntity? {
        return repository.getNoteById(noteId)
    }

    fun updateLinkedNoteOptions(blockId: String, showIcon: Boolean, showCoverImage: Boolean) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) {
                if (it is LinkedNoteBlock) it.copy(showIcon = showIcon, showCoverImage = showCoverImage, updatedAt = now) else it
            }
        }
        scheduleAutosave()
    }

    fun updateDbCell(blockId: String, rowId: String, colId: String, newValue: CellData) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { block ->
                if (block is DatabaseBlock) {
                    val updatedRows = block.rows.map { row ->
                        if (row.id == rowId) {
                            val newMap = row.cells.toMutableMap()
                            newMap[colId] = newValue
                            block.columns.filter { it.type == ColumnType.FORMULA }.forEach { formulaCol ->
                                formulaCol.formulaExpression?.let { expr ->
                                    newMap[formulaCol.id] = FormulaEngine.evaluate(expr, newMap, block.columns)
                                }
                            }
                            row.copy(cells = newMap, updatedAt = now)
                        } else row
                    }
                    val cellKey = "$rowId:$colId"
                    val existingSpans = block.cellSpans[cellKey]
                    val shiftedSpans = if (existingSpans.isNullOrEmpty()) block.cellSpans else {
                        block.cellSpans + (cellKey to shiftSpansForEdit(
                            existingSpans,
                            databaseCellTextAt(block, cellKey),
                            (newValue as? CellData.Text)?.value ?: databaseCellTextAt(block, cellKey)
                        ))
                    }
                    block.copy(rows = updatedRows, cellSpans = shiftedSpans, updatedAt = now)
                } else block
            }
        }
        scheduleAutosave()
    }

    fun updateDbFormula(blockId: String, colId: String, expression: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val updatedCols = db.columns.map { col -> if (col.id == colId) col.copy(formulaExpression = expression, updatedAt = now) else col }
                    val updatedRows = db.rows.map { row ->
                        val newMap = row.cells.toMutableMap()
                        newMap[colId] = FormulaEngine.evaluate(expression, newMap, updatedCols)
                        row.copy(cells = newMap, updatedAt = now)
                    }
                    db.copy(columns = updatedCols, rows = updatedRows, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbColumn(blockId: String, colId: String, newName: String, newType: ColumnType, isManualNameChange: Boolean = true) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { db -> if (db is DatabaseBlock) db.copy(columns = db.columns.map { col -> if (col.id == colId) col.copy(name = newName, type = newType, isNameManuallySet = col.isNameManuallySet || isManualNameChange, updatedAt = now) else col }, updatedAt = now) else db } }
        scheduleAutosave()
    }

    fun updateDbColumnWidth(blockId: String, colId: String, newWidth: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { db -> if (db is DatabaseBlock) db.copy(columns = db.columns.map { col -> if (col.id == colId) col.copy(width = newWidth.coerceIn(40, 600), updatedAt = now) else col }, updatedAt = now) else db } }
        scheduleAutosave()
    }

    fun updateDbSort(blockId: String, colId: String, isAscending: Boolean?) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view ->
                        val modifiedSortList = view.activeSorts.toMutableList()
                        modifiedSortList.removeAll { it.columnId == colId }
                        if (isAscending != null) {
                            modifiedSortList.add(SortConfig(colId, isAscending))
                        }
                        view.copy(activeSorts = modifiedSortList)
                    }
                } else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbGroupBy(blockId: String, colId: String?) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view -> view.copy(groupByColumnId = colId) }
                } else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbGalleryCardSize(blockId: String, size: GalleryCardSize) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view -> view.copy(galleryCardSize = size) }
                } else db
            }
        }
        scheduleAutosave()
    }

    // Kanban bucket visibility is per-view.
    fun toggleKanbanGroupVisibility(blockId: String, viewId: String, groupName: String, isHidden: Boolean) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    db.copy(
                        views = db.views.map { view ->
                            if (view.id != viewId) view else view.copy(
                                hiddenGroups = if (isHidden) (view.hiddenGroups + groupName).distinct()
                                else view.hiddenGroups - groupName
                            )
                        },
                        updatedAt = now
                    )
                } else db
            }
        }
        scheduleAutosave()
    }

    // Persists the drag-reordered board sequence chosen in the Group By sheet.
    fun reorderKanbanGroups(blockId: String, viewId: String, orderedGroupKeys: List<String>) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    db.copy(
                        views = db.views.map { view ->
                            if (view.id != viewId) view else view.copy(groupOrder = orderedGroupKeys)
                        },
                        updatedAt = now
                    )
                } else db
            }
        }
        scheduleAutosave()
    }

    fun addDbFilter(blockId: String, colId: String, operator: String, value: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view -> view.copy(activeFilters = view.activeFilters + FilterConfig(colId, operator, value)) }
                } else db
            }
        }
        scheduleAutosave()
    }

    fun removeDbFilter(blockId: String, filter: FilterConfig) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    db.withActiveViewUpdated(now) { view -> view.copy(activeFilters = view.activeFilters - filter) }
                } else db
            }
        }
        scheduleAutosave()
    }

    // Database views (Table/Kanban/Gallery)
    fun addDatabaseView(blockId: String, type: ViewType) {
        val now = System.currentTimeMillis()
        val newViewId = UUID.randomUUID().toString()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val baseName = when (type) {
                        ViewType.TABLE -> "Table"
                        ViewType.KANBAN -> "Board"
                        ViewType.GALLERY -> "Gallery"
                    }
                    val sameTypeCount = db.views.count { it.type == type }
                    val name = if (sameTypeCount == 0) baseName else "$baseName ${sameTypeCount + 1}"
                    val newView = DatabaseView(id = newViewId, name = name, type = type)
                    db.copy(views = db.views + newView, activeViewId = newViewId, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun deleteDatabaseView(blockId: String, viewId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock && db.views.size > 1) {
                    val remainingViews = db.views.filter { it.id != viewId }
                    val newActiveViewId = if (db.activeViewId == viewId) remainingViews.firstOrNull()?.id else db.activeViewId
                    db.copy(views = remainingViews, activeViewId = newActiveViewId, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun setActiveDatabaseView(blockId: String, viewId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list -> mapBlockById(list, blockId) { db -> if (db is DatabaseBlock) db.copy(activeViewId = viewId, updatedAt = now) else db } }
        scheduleAutosave()
    }

    fun renameDatabaseView(blockId: String, viewId: String, newName: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    db.copy(views = db.views.map { if (it.id == viewId) it.copy(name = newName) else it }, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun reorderDbColumns(blockId: String, fromIndex: Int, toIndex: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val cols = db.columns.toMutableList()
                    val moved = cols.removeAt(fromIndex)
                    cols.add(toIndex, moved)
                    db.copy(columns = cols, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun reorderDbRows(blockId: String, fromIndex: Int, toIndex: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val rows = db.rows.toMutableList()
                    val moved = rows.removeAt(fromIndex)
                    rows.add(toIndex, moved)
                    db.copy(rows = rows, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun reorderDatabaseViews(blockId: String, fromIndex: Int, toIndex: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val views = db.views.toMutableList()
                    val moved = views.removeAt(fromIndex)
                    views.add(toIndex, moved)
                    db.copy(views = views, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun deleteDbColumn(blockId: String, colId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val updatedCols = db.columns.map { col ->
                        if (col.id == colId) col.copy(isDeleted = true, updatedAt = now) else col
                    }
                    db.copy(columns = updatedCols, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun deleteDbRow(blockId: String, rowId: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val updatedRows = db.rows.map { row ->
                        if (row.id == rowId) row.copy(isDeleted = true, updatedAt = now) else row
                    }
                    db.copy(rows = updatedRows, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun addDbRowAt(blockId: String, index: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val rows = db.rows.toMutableList()
                    rows.add(index.coerceIn(0, rows.size), DatabaseRow(id = UUID.randomUUID().toString(), databaseId = blockId, cells = emptyMap(), updatedAt = now))
                    db.copy(rows = rows, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    fun addDbColumnAt(blockId: String, index: Int) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock) {
                    val cols = db.columns.toMutableList()
                    cols.add(index.coerceIn(0, cols.size), DatabaseColumn(id = UUID.randomUUID().toString(), databaseId = blockId, name = "New Column", type = ColumnType.TEXT, updatedAt = now))
                    db.copy(columns = cols, updatedAt = now)
                } else db
            }
        }
        scheduleAutosave()
    }

    override fun onCleared() {
        super.onCleared()
        ActiveEditorRegistry.unregister(this)
        autosaveJob?.cancel()
        if (isDiscarded) return
        val needsIndexing = computeBlocksHash() != lastIndexedContentHash
        appScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                performSave()
                if (needsIndexing) {
                    try {
                        performIndexing()
                    } catch (_: Exception) {
                        // Handle error
                    }
                }
            }
        }
    }

    // Database tags
    val globalTags: StateFlow<List<TagEntity>> = repository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createGlobalTag(name: String, colorHex: String): String {
        val newId = UUID.randomUUID().toString()
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertOrUpdateTag(newId, name, colorHex)
        }
        return newId
    }

    fun addBlockAbove(id: String) {
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        modifyBlocks { list ->
            spliceAtBlock(list, id) { mutable, idx ->
                val indent = mutable[idx].indentationLevel
                val alignment = mutable[idx].textAlignmentOrNull() ?: TextAlignment.LEFT
                mutable.add(idx, TextBlock(id = newId, text = "", indentationLevel = indent, textAlignment = alignment, updatedAt = now))
            }
        }
        _focusRequest.value = FocusRequest(id = newId)
        scheduleAutosave()
    }

    fun addBlockBelow(id: String) {
        val now = System.currentTimeMillis()
        val newId = UUID.randomUUID().toString()
        modifyBlocks { list ->
            spliceAtBlock(list, id) { mutable, idx ->
                val indent = mutable[idx].indentationLevel
                val alignment = mutable[idx].textAlignmentOrNull() ?: TextAlignment.LEFT
                mutable.add(idx + 1, TextBlock(id = newId, text = "", indentationLevel = indent, textAlignment = alignment, updatedAt = now))
            }
        }
        _focusRequest.value = FocusRequest(id = newId)
        scheduleAutosave()
    }

    // Aggregators
    fun updateDbAggregation(blockId: String, colId: String, aggregationType: String?) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock)
                    db.copy(columns = db.columns.map { c ->
                        if (c.id == colId) c.copy(aggregationType = aggregationType, updatedAt = now) else c
                    }, updatedAt = now)
                else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbCurrency(blockId: String, colId: String, symbol: String) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock)
                    db.copy(columns = db.columns.map { c ->
                        if (c.id == colId) c.copy(currencySymbol = symbol, updatedAt = now) else c
                    }, updatedAt = now)
                else db
            }
        }
        scheduleAutosave()
    }

    fun updateDbFormulaCurrency(blockId: String, colId: String, enabled: Boolean) {
        val now = System.currentTimeMillis()
        modifyBlocks { list ->
            mapBlockById(list, blockId) { db ->
                if (db is DatabaseBlock)
                    db.copy(columns = db.columns.map { c ->
                        if (c.id == colId) c.copy(isFormulaCurrency = enabled, updatedAt = now) else c
                    }, updatedAt = now)
                else db
            }
        }
        scheduleAutosave()
    }

    // Database table notes
    fun openDatabaseNote(
        blockId: String,
        rowId: String,
        colId: String,
        existingNoteId: String?,
        onNavigate: (String) -> Unit
    ) {
        if (!existingNoteId.isNullOrBlank()) {
            viewModelScope.launch {
                performSave()
                withContext(Dispatchers.Main) {
                    onNavigate(existingNoteId)
                }
            }
            return
        }

        val newNoteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val subNoteMeta = NoteMetadataEntity(
                    noteId = newNoteId,
                    title = "",
                    folderId = null,
                    isDaily = false,
                    dateString = null,
                    createdAt = now,
                    updatedAt = now,
                    filePath = "note_$newNoteId.json",
                    isSubNote = true
                )

                // Create the sub-note first - if this fails, the parent's cell is never left pointing
                // at a note that doesn't exist. performSave() below takes SyncCoordinator.mutex itself
                // (it's not reentrant), so this write gets its own lock rather than one shared with it.
                SyncCoordinator.mutex.withLock {
                    repository.saveNote(subNoteMeta, NoteContent(blocks = emptyList()))
                }

                withContext(Dispatchers.Main) {
                    updateDbCell(blockId, rowId, colId, CellData.NoteRelation(listOf(newNoteId)))
                }

                performSave()

                withContext(Dispatchers.Main) {
                    onNavigate(newNoteId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun createLinkedNote(title: String): String {
        val newNoteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        viewModelScope.launch(Dispatchers.IO) {
            val othersFolderId = getOrCreateOthersFolder()
            val newMeta = NoteMetadataEntity(
                noteId = newNoteId,
                title = title,
                folderId = othersFolderId,
                isDaily = false,
                dateString = null,
                createdAt = now,
                updatedAt = now,
                filePath = "note_$newNoteId.json",
                isSubNote = false
            )
            repository.saveNote(newMeta, NoteContent(blocks = emptyList()))
        }
        return newNoteId
    }

    private suspend fun getOrCreateOthersFolder(): String = othersFolderMutex.withLock {
        val existing = repository.getAllFolders().first()
            .find { it.parentFolderId == null && it.name.equals(OTHERS_FOLDER_NAME, ignoreCase = true) }
        if (existing != null) return@withLock existing.folderId

        val folderId = UUID.randomUUID().toString()
        repository.insertFolder(
            FolderEntity(
                folderId = folderId,
                name = OTHERS_FOLDER_NAME,
                parentFolderId = null,
                createdAt = System.currentTimeMillis()
            )
        )
        folderId
    }

    companion object {
        private const val OTHERS_FOLDER_NAME = "Others"
        private val othersFolderMutex = Mutex()
    }
}