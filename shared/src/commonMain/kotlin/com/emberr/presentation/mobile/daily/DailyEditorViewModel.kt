package com.emberr.presentation.mobile.daily

import androidx.lifecycle.viewModelScope
import com.emberr.data.local.room.CalendarTaskEntity
import com.emberr.data.local.room.TaskSource
import com.emberr.data.local.room.toRecurrenceRule
import com.emberr.domain.model.*
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.sample.SampleDailyNoteSeeder
import com.emberr.domain.util.eventbus.AiEventBus
import com.emberr.domain.util.voice.AudioRecorder
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.sync.NoteSyncEvent
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.domain.util.sync.SyncEventBus
import com.emberr.domain.util.eventbus.VoiceTaskEventBus
import com.emberr.presentation.reminders.ReminderScheduler
import com.emberr.presentation.shared.FirstContentRenderSignal
import com.emberr.presentation.shared.editor.BaseEditorViewModel
import com.emberr.presentation.shared.editor.FocusRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import kotlinx.coroutines.flow.flatMapLatest
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class DailyEditorViewModel(
    repository: NoteRepository,
    mediaStorageHelper: MediaStorageHelper,
    reminderScheduler: ReminderScheduler,
    audioRecorder: AudioRecorder,
    appScope: CoroutineScope,
    private val sampleDailyNoteSeeder: SampleDailyNoteSeeder
) : BaseEditorViewModel(repository, mediaStorageHelper, reminderScheduler, audioRecorder, appScope) {

    // Date state
    private val _currentDateString = MutableStateFlow<String?>(null)
    private var currentDateString: String?
        get() = _currentDateString.value
        set(value) { _currentDateString.value = value }
    private val _selectedDate =
        MutableStateFlow(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _loadedDateString = MutableStateFlow<String?>(null)
    val loadedDateString: StateFlow<String?> = _loadedDateString.asStateFlow()

    // Preview cache
    private val _previewCache = MutableStateFlow<Map<String, List<NoteBlock>>>(emptyMap())
    val previewCache: StateFlow<Map<String, List<NoteBlock>>> = _previewCache.asStateFlow()

    private fun cachePreviewBlocks(dateString: String, blocks: List<NoteBlock>) {
        _previewCache.update { it + (dateString to visibleBlocksOf(blocks)) }
    }

    private fun ensureTrailingEmptyBlock(blocks: List<NoteBlock>, dateString: String): List<NoteBlock> {
        if (blocks.isEmpty() || blocks.lastOrNull() !is TextBlock || (blocks.lastOrNull() as? TextBlock)?.text?.isNotEmpty() == true) {
            val cached = _previewCache.value[dateString]
            val cachedTail = cached?.lastOrNull() as? TextBlock
            val tailId = if (cachedTail != null && cachedTail.text.isEmpty()) {
                cachedTail.id // Reuse the ID
            } else {
                java.util.UUID.randomUUID().toString()
            }
            return blocks + listOf(TextBlock(id = tailId, text = ""))
        }
        return blocks
    }

    fun prefetchDateIfNeeded(dateString: String) {
        if (_previewCache.value.containsKey(dateString)) return
        viewModelScope.launch(Dispatchers.IO) { refreshPreviewCacheEntry(dateString) }
    }

    // Reloads a single date's preview from the repository, overwriting any stale cached entry for it
    private suspend fun refreshPreviewCacheEntry(dateString: String) {
        val pinnedContent = repository.getDailyNote("global_pinned")
        val pinnedBlocks = pinnedContent?.blocks?.filter { !it.isDeleted } ?: emptyList()

        val content = repository.getDailyNote(dateString)
        val blocks = content?.blocks?.filter { !it.isDeleted } ?: emptyList()

        var merged = pinnedBlocks + (if (isNoteActuallyEmpty(blocks)) emptyList() else blocks)
        merged = ensureTrailingEmptyBlock(merged, dateString)

        val resolved = recalculateNumberedLists(merged)
        cachePreviewBlocks(dateString, resolved)
    }

    // Strips a single known block out of in-memory state directly rather than reloading the whole note,
    // so it can't be undone by a pending autosave still holding the note's content from before the edit.
    private fun removeBlockLocally(blockId: String, dateString: String) {
        if (dateString == currentDateString) {
            _blocks.update { blocks -> blocks.filterNot { it.id == blockId } }
        }
        if (dateString in _previewCache.value.keys) {
            _previewCache.update { cache ->
                val existing = cache[dateString] ?: return@update cache
                cache + (dateString to existing.filterNot { it.id == blockId })
            }
        }
    }

    // Merges a single moved/edited block into in-memory state by id, fetching just that block from disk
    // rather than reloading the whole note - if this date is the open page and its in-memory snapshot is
    // never told about the block, a later selectDate/autosave would overwrite the file with the stale
    // snapshot and silently erase the block that was just written from elsewhere (e.g. Calendar)
    private suspend fun upsertBlockLocally(blockId: String, dateString: String) {
        val diskBlock = repository.getDailyNote(dateString)?.blocks?.firstOrNull { it.id == blockId } ?: return

        if (dateString == currentDateString) {
            _blocks.update { blocks ->
                if (blocks.any { it.id == blockId }) {
                    blocks.map { if (it.id == blockId) diskBlock else it }
                } else {
                    blocks + diskBlock
                }
            }
        }
        if (dateString in _previewCache.value.keys) {
            _previewCache.update { cache ->
                val existing = cache[dateString] ?: return@update cache
                val updated = if (existing.any { it.id == blockId }) {
                    existing.map { if (it.id == blockId) diskBlock else it }
                } else {
                    existing + diskBlock
                }
                cache + (dateString to updated)
            }
        }
    }

    // A checkbox's reminder date is what the Calendar screen treats as its day, so setting a reminder
    // for a different day than the note it was typed into must relocate the block there too - otherwise
    // it stays filed under today while Calendar and any future edit through it disagree on where it lives
    override fun updateReminder(blockId: String, timestamp: Long?) {
        val homeDateString = currentDateString
        val block = findBlockById(_blocks.value, blockId) as? CheckboxBlock
        val targetDateString = timestamp?.let {
            Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        }

        if (homeDateString == null || block == null || targetDateString == null || targetDateString == homeDateString) {
            super.updateReminder(blockId, timestamp)
            return
        }

        autosaveJob?.cancel()
        val now = System.currentTimeMillis()
        val updatedBlock = block.copy(reminderTimestamp = timestamp, updatedAt = now)

        // Cancelling the pending autosave means any of the user's other unsaved edits in this note
        // would be lost if we re-fetched its content from disk, so persist the current in-memory
        // snapshot instead of what's still on disk.
        val homeSnapshot = _blocks.value.map { if (it.id == blockId) it.markDeleted() else it }
        removeBlockLocally(blockId, homeDateString)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    val targetBlocks = repository.getDailyNote(targetDateString)?.blocks ?: emptyList()
                    val newTargetBlocks = if (targetBlocks.any { it.id == blockId }) {
                        targetBlocks.map { if (it.id == blockId) updatedBlock else it }
                    } else {
                        listOf(updatedBlock) + targetBlocks
                    }
                    repository.saveDailyNote(targetDateString, NoteContent(blocks = newTargetBlocks))

                    repository.saveDailyNote("global_pinned", NoteContent(blocks = homeSnapshot.filter { it.isPinned }))
                    repository.saveDailyNote(homeDateString, NoteContent(blocks = homeSnapshot.filter { !it.isPinned }))
                }

                SyncEventBus.emitBlockMoved(blockId, fromDateString = homeDateString, toDateString = targetDateString)

                reminderScheduler.schedule(
                    blockId = blockId,
                    noteTitle = "Daily: $targetDateString",
                    text = block.text.ifBlank { "Unfinished task" },
                    timestamp = timestamp
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                if (homeDateString == currentDateString && _blocks.value.none { it.id == blockId }) {
                    _blocks.update { it + block }
                }
                _previewCache.update { cache ->
                    val existing = cache[homeDateString] ?: return@update cache
                    if (existing.any { it.id == blockId }) cache else cache + (homeDateString to existing + block)
                }
            }
        }
    }

    fun evictPreviewCache(keepDates: Set<String>) {
        _previewCache.update { current -> current.filterKeys { it in keepDates } }
    }

    // Timeline (read-only recap of every day that actually has content)
    private val _timelineDays = MutableStateFlow<List<DailyTimelineDay>>(emptyList())
    val timelineDays: StateFlow<List<DailyTimelineDay>> = _timelineDays.asStateFlow()

    private val _isTimelineLoading = MutableStateFlow(false)
    val isTimelineLoading: StateFlow<Boolean> = _isTimelineLoading.asStateFlow()

    private var timelineLoadJob: Job? = null
    private var timelineFocusJob: Job? = null

    fun loadTimeline() {
        timelineLoadJob?.cancel()
        timelineLoadJob = viewModelScope.launch {
            _isTimelineLoading.value = true
            try {
                val anchorDate = _selectedDate.value
                val liveDateString = _loadedDateString.value
                val liveBlocks = _blocks.value.toList()

                val days = withContext(Dispatchers.IO) {
                    val dates = repository.getSavedDailyNoteDates()
                        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                        .toMutableSet()
                    dates.add(anchorDate)

                    dates.sorted().mapNotNull { date ->
                        val dateString = date.toString()
                        val sourceBlocks = if (dateString == liveDateString) {
                            liveBlocks
                        } else {
                            repository.getDailyNote(dateString)?.blocks ?: emptyList()
                        }
                        val readableBlocks = sourceBlocks.filter {
                            !it.isDeleted && !it.isPinned && !isBlockEmptyForTimeline(it)
                        }
                        when {
                            readableBlocks.isNotEmpty() ->
                                DailyTimelineDay(date, recalculateNumberedLists(readableBlocks))
                            date == anchorDate -> DailyTimelineDay(date, emptyList())
                            else -> null
                        }
                    }
                }
                _timelineDays.value = days
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _timelineDays.value = emptyList()
            } finally {
                _isTimelineLoading.value = false
            }
        }
    }

    fun clearTimeline() {
        timelineLoadJob?.cancel()
        timelineLoadJob = null
        _timelineDays.value = emptyList()
        _isTimelineLoading.value = false
    }

    // Jumps the daily editor to the tapped timeline block: switching days reloads asynchronously,
    // so the focus request has to wait until that day's blocks are actually in memory.
    fun openTimelineBlock(date: LocalDate, blockId: String) {
        val dateString = date.toString()
        selectDate(date)
        timelineFocusJob?.cancel()
        timelineFocusJob = viewModelScope.launch {
            val isDayReady = withTimeoutOrNull(5000L.milliseconds) {
                _loadedDateString.first { it == dateString }
                true
            } ?: false
            if (!isDayReady) return@launch
            if (_blocks.value.none { it.id == blockId }) return@launch
            _focusRequest.value = FocusRequest(id = blockId)
        }
    }

    override fun scheduleAutosave() {
        isAiIndexDirty = true
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            currentDateString?.let { date ->
                cachePreviewBlocks(date, _blocks.value)
            }
            delay(1000L.milliseconds)
            performSave()
        }
    }

    // Init
    init {
        val todayDateString = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        if (sampleDailyNoteSeeder.isSampleDayPending()) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    sampleDailyNoteSeeder.seedSampleDayIfNeeded(todayDateString)
                }
                loadDailyNote(_selectedDate.value.toString())
            }
        } else {
            loadDailyNote(todayDateString)
        }
        viewModelScope.launch {
            _loadedDateString.filterNotNull().first()
            FirstContentRenderSignal.reportContentRendered()
        }
        viewModelScope.launch {
            VoiceTaskEventBus.taskAddedEvent.collect { event ->
                if (event.dateString == currentDateString) {
                    val currentBlocks = _blocks.value.toMutableList()

                    if (currentBlocks.size == 1
                        && currentBlocks.first() is TextBlock
                        && (currentBlocks.first() as TextBlock).text.isBlank()
                    ) {
                        currentBlocks.clear()
                    }

                    currentBlocks.add(event.block)
                    _blocks.value = recalculateNumberedLists(currentBlocks)
                    scheduleAutosave()
                }
            }
        }
        viewModelScope.launch {
            SyncEventBus.events.collect { event ->
                when (event) {
                    // Block-level moves/removals are safe to apply even mid-autosave - they mutate only
                    // the one block id they name, so they can't clobber unrelated unsaved local edits
                    is NoteSyncEvent.BlockMoved -> {
                        event.fromDateString?.let { removeBlockLocally(event.blockId, it) }
                        if (event.toDateString == currentDateString) {
                            withContext(Dispatchers.IO) { upsertBlockLocally(event.blockId, event.toDateString) }
                        } else if (event.toDateString in _previewCache.value.keys) {
                            withContext(Dispatchers.IO) { refreshPreviewCacheEntry(event.toDateString) }
                        }
                    }
                    is NoteSyncEvent.BlockRemoved -> removeBlockLocally(event.blockId, event.dateString)
                    is NoteSyncEvent.NoteChanged -> {
                        val syncedEntityId = event.entityId

                        // If the remote edit affects a cached date other than the currently active one,
                        // refresh its entry in the preview cache directly.
                        if (syncedEntityId != currentDateString && syncedEntityId in _previewCache.value.keys) {
                            withContext(Dispatchers.IO) { refreshPreviewCacheEntry(syncedEntityId) }
                            return@collect
                        }

                        if (syncedEntityId == currentDateString || syncedEntityId == "global_pinned" || syncedEntityId == "import_complete") {
                            if (syncedEntityId == "import_complete") {
                                autosaveJob?.cancel()
                            } else if (autosaveJob?.isActive == true || isWithinLocalMutationCooldown()) {
                                return@collect
                            }

                            currentDateString?.let { dateString ->
                                val pinnedContentRaw =
                                    withContext(Dispatchers.IO) { repository.getDailyNote("global_pinned") }?.blocks.orEmpty()
                                val pinnedBlocks = pinnedContentRaw.filter { !it.isDeleted }
                                val content = withContext(Dispatchers.IO) { repository.getDailyNote(dateString) }
                                val newBlocksRaw = content?.blocks.orEmpty()
                                val newBlocks = newBlocksRaw.filter { !it.isDeleted }

                                // Include deleted blocks (tombstones) so preserveNewerLocalBlocks can distinguish
                                // remotely deleted blocks from unsaved local blocks and prevent accidental resurrection.
                                val tombstones = (newBlocksRaw.filter { it.isDeleted } + pinnedContentRaw.filter { it.isDeleted })

                                // Filter out tombstones immediately after merging so they aren't displayed in UI state.
                                var merged = preserveNewerLocalBlocks(
                                    pinnedBlocks + (if (isNoteActuallyEmpty(newBlocks)) emptyList() else newBlocks) + tombstones
                                ).filter { !it.isDeleted }
                                merged = ensureTrailingEmptyBlock(merged, dateString)
                                val finalBlocks = recalculateNumberedLists(merged)
                                if (finalBlocks != _blocks.value) {
                                    _blocks.value = finalBlocks
                                    cachePreviewBlocks(dateString, finalBlocks)
                                }
                            }
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            _currentDateString
                .filterNotNull()
                .flatMapLatest { date -> repository.observeDailyNote(date) }
                .filterNotNull()
                .collect { freshContent ->
                    val date = currentDateString ?: return@collect
                    if (_loadedDateString.value != date) return@collect
                    if (autosaveJob?.isActive == true) return@collect
                    if (isWithinLocalMutationCooldown()) return@collect

                    val pinnedContentRaw = repository.getDailyNote("global_pinned")?.blocks.orEmpty()
                    val pinnedBlocks = pinnedContentRaw.filter { !it.isDeleted }

                    val freshBlocksRaw = freshContent.blocks
                    val freshBlocks = freshBlocksRaw.filter { !it.isDeleted }
                    // See the identical comment in the NoteChanged handler above - tombstones must
                    // stay in preserveNewerLocalBlocks's input or a remote deletion looks exactly like
                    // an unsaved local block and gets resurrected.
                    val tombstones = (freshBlocksRaw.filter { it.isDeleted } + pinnedContentRaw.filter { it.isDeleted })
                    // Dropped again immediately after resolving - see the identical comment in the
                    // NoteChanged handler above.
                    var merged = preserveNewerLocalBlocks(
                        pinnedBlocks + (if (isNoteActuallyEmpty(freshBlocks)) emptyList() else freshBlocks) + tombstones
                    ).filter { !it.isDeleted }
                    merged = ensureTrailingEmptyBlock(merged, date)
                    val final = recalculateNumberedLists(merged)

                    if (final != _blocks.value) {
                        _blocks.value = final
                        cachePreviewBlocks(date, final)
                    }
                }
        }
    }

    private suspend fun reconcileWithDisk(dateString: String, snapshot: List<NoteBlock>): List<NoteBlock> {
        val diskBlocks = repository.getDailyNote(dateString)?.blocks ?: emptyList()
        val diskById = diskBlocks.associateBy { it.id }
        val snapshotIds = snapshot.mapTo(HashSet()) { it.id }

        val reconciledSnapshot = snapshot.map { block ->
            val diskBlock = diskById[block.id]
            // A pinned block is deliberately tombstoned in this date's own storage (it now lives in
            // "global_pinned" instead) - that self-inflicted tombstone must never be adopted here, or
            // every pin gets undone the moment the user navigates away and this date's disk state is
            // reconciled against, since the tombstone always looks like an external deletion otherwise.
            // The updatedAt check covers the mirror case: right after unpinning, disk may still hold the
            // OLDER tombstone from when the block was originally pinned (the autosave that would replace
            // it with a fresh, live row hasn't landed yet) - that stale tombstone must lose to the fresher
            // in-memory unpin, or unpinning silently undoes itself the moment the user navigates away too.
            if (diskBlock != null && diskBlock.isDeleted && !block.isDeleted && !block.isPinned && diskBlock.updatedAt > block.updatedAt) diskBlock else block
        }

        if (isWithinLocalMutationCooldown()) return reconciledSnapshot

        val externallyAdded = diskBlocks.filter { it.id !in snapshotIds }
        return if (externallyAdded.isEmpty()) reconciledSnapshot else reconciledSnapshot + externallyAdded
    }

    private suspend fun theDayAlreadyHoldsThis(dateString: String, blocksToSave: List<NoteBlock>): Boolean {
        val storedBlocks = repository.getDailyNote(dateString)?.blocks.orEmpty()
        return blocksToSave.filter { !it.isDeleted } == storedBlocks.filter { !it.isDeleted }
    }

    private suspend fun saveDayUnlessNothingChanged(dateString: String, reconciled: List<NoteBlock>) {
        val pinnedBlocks = reconciled.filter { it.isPinned }
        val dayBlocks = reconciled.filter { !it.isPinned }

        if (theDayAlreadyHoldsThis("global_pinned", pinnedBlocks) && theDayAlreadyHoldsThis(dateString, dayBlocks)) return

        repository.saveDailyNote("global_pinned", NoteContent(blocks = pinnedBlocks))
        repository.saveDailyNote(dateString, NoteContent(blocks = dayBlocks))
    }

    override suspend fun performSave(): Boolean {
        if (_loadedDateString.value == null || _loadedDateString.value != currentDateString) return false

        val dateToSave = currentDateString ?: return false

        return try {
            withContext(Dispatchers.IO) {
                SyncCoordinator.mutex.withLock {
                    // Re-validate now that the lock is actually held - acquiring it can be delayed for
                    // as long as a self-host sync pass holds the same mutex, and _blocks always tracks
                    // whichever date is CURRENTLY loaded, not dateToSave. Without this check, a save
                    // queued behind a long lock wait would write whatever date the user has since
                    // switched to into dateToSave's note instead of its own - cross-date contamination.
                    if (currentDateString != dateToSave) return@withLock false

                    val reconciled = reconcileWithDisk(dateToSave, _blocks.value)
                    if (reconciled !== _blocks.value) _blocks.value = reconciled

                    saveDayUnlessNothingChanged(dateToSave, reconciled)
                    true
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun getNoteTitleForReminder(): String =
        "Daily: ${currentDateString ?: "Note"}"

    // The reactive observeDailyNote collector drops every cache emission while _loadedDateString
    // doesn't yet match this date - if a background sync refreshed this exact date (or its pinned
    // blocks) during that load window, that update is gone for good (StateFlow doesn't redeliver a
    // value a collector already saw). Called once right after _loadedDateString flips to "ready" on
    // every exit path of loadDailyNote, so a sync landing mid-load isn't silently lost.
    private suspend fun refreshBlocksIfStale(dateString: String) {
        if (currentDateString != dateString) return
        val pinnedBlocks = repository.getDailyNote("global_pinned")?.blocks?.filter { !it.isDeleted } ?: emptyList()
        val existingBlocks = repository.getDailyNote(dateString)?.blocks?.filter { !it.isDeleted } ?: emptyList()
        val cleanExistingBlocks = if (isNoteActuallyEmpty(existingBlocks)) emptyList() else existingBlocks
        var mergedBlocks = pinnedBlocks + cleanExistingBlocks
        mergedBlocks = ensureTrailingEmptyBlock(mergedBlocks, dateString)
        val finalBlocks = recalculateNumberedLists(mergedBlocks)
        if (currentDateString != dateString) return
        if (finalBlocks != _blocks.value) {
            _blocks.value = finalBlocks
            cachePreviewBlocks(dateString, finalBlocks)
        }
    }

    // Loading
    fun loadDailyNote(dateString: String) {
        if (currentDateString == dateString) return
        currentDateString = dateString
        _loadedDateString.value = null

        AiEventBus.activeNoteId = dateString

        viewModelScope.launch(Dispatchers.IO) {
            // These two reads must never propagate uncaught - the try/catch below is what guarantees
            // _loadedDateString eventually gets set (in both its success and fallback paths), and the
            // reactive sync collector drops every emission for this date while that stays null.
            val pinnedContent = try { repository.getDailyNote("global_pinned") } catch (e: Exception) { e.printStackTrace(); null }
            val pinnedBlocks = pinnedContent?.blocks?.filter { !it.isDeleted } ?: emptyList()

            val content = try { repository.getDailyNote(dateString) } catch (e: Exception) { e.printStackTrace(); null }
            val existingBlocks = content?.blocks?.filter { !it.isDeleted } ?: emptyList()

            try {
                val targetDate = LocalDate.parse(dateString)
                if (targetDate == Clock.System.todayIn(TimeZone.currentSystemDefault())) {
                    val yesterdayString = targetDate.minus(1, DateTimeUnit.DAY).toString()
                    val yesterdayContent = repository.getDailyNote(yesterdayString)
                    val allYesterdayBlocks = yesterdayContent?.blocks ?: emptyList()
                    val unfinishedTasks = allYesterdayBlocks
                        .filterIsInstance<CheckboxBlock>()
                        .filter { !it.isChecked && !it.isDeleted }

                    if (unfinishedTasks.isNotEmpty()) {
                        val cleanExistingBlocks =
                            if (isNoteActuallyEmpty(existingBlocks)) emptyList() else existingBlocks
                        val existingIds = cleanExistingBlocks.mapTo(HashSet()) { it.id }
                        val rolledOverTasks = unfinishedTasks
                            .map { it.copy(id = "rollover_${it.id}_$dateString") }
                            .filter { it.id !in existingIds }

                        var mergedBlocks = pinnedBlocks + rolledOverTasks + cleanExistingBlocks
                        mergedBlocks = ensureTrailingEmptyBlock(mergedBlocks, dateString)

                        val finalBlocks = recalculateNumberedLists(mergedBlocks)
                        if (currentDateString != dateString) return@launch
                        _blocks.value = finalBlocks
                        cachePreviewBlocks(dateString, finalBlocks)
                        _loadedDateString.value = dateString
                        lastIndexedContentHash = 0

                        val rolledIds = unfinishedTasks.mapTo(HashSet()) { it.id }
                        val updatedYesterdayBlocks = allYesterdayBlocks
                            .map { if (it.id in rolledIds) it.markDeleted() else it }

                        try {
                            SyncCoordinator.mutex.withLock {
                                repository.saveDailyNote(
                                    yesterdayString,
                                    NoteContent(blocks = updatedYesterdayBlocks)
                                )
                            }

                            val yesterdayMeta = repository.getDailyNoteMetadata(yesterdayString)
                            if (yesterdayMeta != null) {
                                repository.indexDailyNote(yesterdayString, NoteContent(blocks = updatedYesterdayBlocks), yesterdayMeta)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        performSave()
                        refreshBlocksIfStale(dateString)
                        return@launch
                    }
                }

                val cleanExistingBlocks = if (isNoteActuallyEmpty(existingBlocks)) emptyList() else existingBlocks
                var mergedBlocks = pinnedBlocks + cleanExistingBlocks

                mergedBlocks = ensureTrailingEmptyBlock(mergedBlocks, dateString)

                val finalBlocks = recalculateNumberedLists(mergedBlocks)
                if (currentDateString != dateString) return@launch
                _blocks.value = finalBlocks
                cachePreviewBlocks(dateString, finalBlocks)
                _loadedDateString.value = dateString
                lastIndexedContentHash = 0
                refreshBlocksIfStale(dateString)

            } catch (_: Exception) {
                val cleanExistingBlocks = if (isNoteActuallyEmpty(existingBlocks)) emptyList() else existingBlocks
                var mergedBlocks = pinnedBlocks + cleanExistingBlocks

                mergedBlocks = ensureTrailingEmptyBlock(mergedBlocks, dateString)

                val finalBlocks = recalculateNumberedLists(mergedBlocks)
                if (currentDateString != dateString) return@launch
                _blocks.value = finalBlocks
                cachePreviewBlocks(dateString, finalBlocks)
                _loadedDateString.value = dateString
                lastIndexedContentHash = 0
                refreshBlocksIfStale(dateString)
            }
        }
    }

    // Date selection
    fun selectDate(date: LocalDate) {
        if (_selectedDate.value == date) return
        clearUndoHistory()
        clearFocusRequest()
        autosaveJob?.cancel()
        indexingJob?.cancel()

        val dateToSave = currentDateString
        val blocksToSave = _blocks.value.toList()
        val wasLoaded = _loadedDateString.value == dateToSave

        _selectedDate.value = date
        currentDateString = null
        _loadedDateString.value = null
        _blocks.value = emptyList()
        clearSelection()

        viewModelScope.launch {
            if (wasLoaded && dateToSave != null) {
                try {
                    withContext(Dispatchers.IO + NonCancellable) {
                        SyncCoordinator.mutex.withLock {
                            val reconciled = reconcileWithDisk(dateToSave, blocksToSave)
                            saveDayUnlessNothingChanged(dateToSave, reconciled)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            loadDailyNote(date.toString())
        }
    }

    override suspend fun performIndexing() {
        if (_loadedDateString.value == null || _loadedDateString.value != currentDateString) return
        val dateToSave = currentDateString ?: return
        val snapshot = _blocks.value.toList()

        val dailyBlocks = snapshot.filter { !it.isPinned }

        withContext(Dispatchers.IO) {
            val meta = repository.getDailyNoteMetadata(dateToSave)
            if (meta != null) {
                repository.indexDailyNote(dateToSave, NoteContent(blocks = dailyBlocks), meta)
            }
        }
    }

    private val _virtualOccurrenceIds = MutableStateFlow<Set<String>>(emptySet())
    override fun orderForDisplay(
        pinned: List<NoteBlock>,
        extra: List<NoteBlock>,
        rest: List<NoteBlock>
    ): List<NoteBlock> {
        if (extra.isEmpty() && rest.none { it is CheckboxBlock && it.recurrenceRule != null }) {
            return pinned + rest
        }

        val recurring = mutableListOf<NoteBlock>()
        val everythingElse = mutableListOf<NoteBlock>()
        for (block in rest) {
            if (block is CheckboxBlock && block.recurrenceRule != null) recurring += block
            else everythingElse += block
        }

        recurring += extra
        // Stable sort: equal timestamps keep their relative order, and anything somehow missing a
        // timestamp sinks to the end of the band rather than jumping to the front.
        recurring.sortBy { (it as? CheckboxBlock)?.reminderTimestamp ?: Long.MAX_VALUE }
        return pinned + recurring + everythingElse
    }

    override fun isVirtualOccurrence(blockId: String): Boolean = blockId in _virtualOccurrenceIds.value
    override fun virtualOccurrenceDate(blockId: String): String? = currentDateString

    private fun CalendarTaskEntity.toVirtualCheckboxBlock(): CheckboxBlock = CheckboxBlock(
        id = blockId,
        text = text,
        isChecked = isChecked,
        reminderTimestamp = reminderTimestamp,
        categoryId = categoryId,
        durationMinutes = durationMinutes,
        url = url,
        description = description,
        recurrenceRule = toRecurrenceRule(),
        updatedAt = System.currentTimeMillis()
    )

    // Surfaces recurring checkboxes whose literal storage is a different day (or a NOTE) as real,
    // independently-completable checkboxes on every day their series expands into - reactive off
    // the same repository.getCalendarTasksForDate Calendar uses, so a completion/edit made from
    // Calendar (or another virtual occurrence) shows up here without any extra plumbing.
    override val extraVisibleBlocks: StateFlow<List<NoteBlock>> = _currentDateString
        .filterNotNull()
        .flatMapLatest { date ->
            repository.getCalendarTasksForDate(date).map { tasks ->
                tasks.filter { it.noteId != date }.map { it.toVirtualCheckboxBlock() }
            }
        }
        .onEach { blocks -> _virtualOccurrenceIds.value = blocks.mapTo(mutableSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // A non-recurring checkbox only ever has one occurrence, so its base block IS the sole
    // source of truth - going through the exceptions table (upsertOccurrenceCompletion /
    // applyRecurrenceScopedEdit) for it would write state that expandOccurrences never reads back
    // for a non-recurring row, silently discarding the toggle/edit. Only truly recurring series use
    // the per-occurrence exception mechanism.
    override fun onVirtualOccurrenceToggled(blockId: String, isChecked: Boolean) {
        val date = currentDateString ?: return
        val current = extraVisibleBlocks.value.firstOrNull { it.id == blockId } as? CheckboxBlock ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (current.recurrenceRule != null) {
                    repository.upsertOccurrenceCompletion(blockId, date, isChecked)
                } else {
                    repository.toggleTaskCompletion(blockId, isChecked)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onVirtualOccurrenceTextEdited(blockId: String, text: String) {
        val date = currentDateString ?: return
        val current = extraVisibleBlocks.value.firstOrNull { it.id == blockId } as? CheckboxBlock ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (current.recurrenceRule != null) {
                    repository.applyRecurrenceScopedEdit(
                        blockId = blockId,
                        occurrenceDate = date,
                        scope = RecurrenceEditScope.THIS_EVENT,
                        text = text,
                        timestamp = current.reminderTimestamp ?: System.currentTimeMillis(),
                        categoryId = current.categoryId,
                        durationMinutes = current.durationMinutes,
                        url = current.url,
                        description = current.description
                    )
                } else {
                    repository.updateTaskText(blockId, text)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val _visibleCalendarMonth = MutableStateFlow(Clock.System.todayIn(TimeZone.currentSystemDefault()))

    @OptIn(ExperimentalCoroutinesApi::class)
    val calendarTaskMap: StateFlow<Map<LocalDate, List<CalendarTaskEntity>>> = _visibleCalendarMonth
        .flatMapLatest { date ->
            val monthStr = date.month.number.toString().padStart(2, '0')
            val yearMonth = "${date.year}-$monthStr"
            repository.getCalendarTasksForMonth(yearMonth)
        }
        .map { tasks ->
            tasks.groupBy { LocalDate.parse(it.targetDate!!) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun toggleCalendarTask(task: CalendarTaskEntity, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (task.sourceType == TaskSource.DAILY && task.noteId == currentDateString) {
                _blocks.update { currentBlocks ->
                    currentBlocks.map { block ->
                        if (block.id == task.blockId && block is CheckboxBlock) {
                            block.copy(isChecked = isChecked)
                        } else block
                    }
                }
                val saved = performSave()
                if (!saved) {
                    _blocks.update { currentBlocks ->
                        currentBlocks.map { block ->
                            if (block.id == task.blockId && block is CheckboxBlock) {
                                block.copy(isChecked = !isChecked)
                            } else block
                        }
                    }
                }
                return@launch
            }

            try {
                SyncCoordinator.mutex.withLock {
                    if (task.sourceType == TaskSource.DAILY) {
                        val content = repository.getDailyNote(task.noteId) ?: return@withLock
                        val updatedBlocks = content.blocks.map { block ->
                            if (block.id == task.blockId && block is CheckboxBlock) {
                                block.copy(isChecked = isChecked)
                            } else block
                        }
                        val meta = repository.getDailyNoteMetadata(task.noteId)
                        repository.saveDailyNote(task.noteId, NoteContent(blocks = updatedBlocks), remoteMeta = meta)
                    } else if (task.sourceType == TaskSource.NOTE) {
                        val meta = repository.getNoteById(task.noteId) ?: return@withLock
                        val content = repository.getNoteContent(task.noteId) ?: return@withLock
                        val updatedBlocks = content.blocks.map { block ->
                            if (block.id == task.blockId && block is CheckboxBlock) {
                                block.copy(isChecked = isChecked)
                            } else block
                        }
                        repository.saveNote(meta, NoteContent(blocks = updatedBlocks))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
// One day of the read-only timeline: the date plus every block on it that renders something.
data class DailyTimelineDay(
    val date: LocalDate,
    val blocks: List<NoteBlock>
)

// The daily editor always keeps a trailing empty text block for typing, and media blocks can exist
// without a file while their picker is still open - none of those should show up in the timeline.
private fun isBlockEmptyForTimeline(block: NoteBlock): Boolean = when (block) {
    is TextBlock -> block.text.isBlank()
    is HeadingBlock -> block.text.isBlank()
    is QuoteBlock -> block.text.isBlank()
    is CheckboxBlock -> block.text.isBlank()
    is BulletedListBlock -> block.text.isBlank()
    is NumberedListBlock -> block.text.isBlank()
    is ToggleBlock -> block.text.isBlank()
    is CodeBlock -> block.code.isBlank()
    is BookmarkBlock -> block.url.isBlank()
    is ImageBlock -> block.localFilePath.isNullOrBlank()
    is DocumentBlock -> block.localFilePath.isNullOrBlank()
    is VoiceBlock -> block.localFilePath.isNullOrBlank()
    is TableBlock -> block.rows.all { row -> row.all { cell -> cell.isBlank() } }
    is SolidDividerBlock -> true
    is ThreeDotDividerBlock -> true
    else -> false
}
