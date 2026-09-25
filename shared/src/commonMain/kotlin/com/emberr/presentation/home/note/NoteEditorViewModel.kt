package com.emberr.presentation.home.note

import androidx.lifecycle.viewModelScope
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.*
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.voice.AudioRecorder
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.domain.reminders.ReminderScheduler
import com.emberr.presentation.shared.editor.BaseEditorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModel(
    repository: NoteRepository,
    mediaStorageHelper: MediaStorageHelper,
    reminderScheduler: ReminderScheduler,
    audioRecorder: AudioRecorder,
    appScope: CoroutineScope
) : BaseEditorViewModel(repository, mediaStorageHelper, reminderScheduler, audioRecorder, appScope) {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val wordCount: StateFlow<Int> = _blocks.map { blocks ->
        blocks.sumOf { block ->
            val text = extractTextFromBlock(block) ?: ""
            if (text.isBlank()) 0 else text.trim().split("\\s+".toRegex()).size
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _noteTitle = MutableStateFlow("")
    val noteTitle: StateFlow<String> = _noteTitle.asStateFlow()

    private val _noteIcon = MutableStateFlow<String?>(null)
    val noteIcon: StateFlow<String?> = _noteIcon.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _coverImagePath = MutableStateFlow<String?>(null)
    val coverImagePath: StateFlow<String?> = _coverImagePath.asStateFlow()

    // True when the currently loaded note is a template - drives the "Editing Template" pill
    // in NoteScreen's top bar so it's obvious this note won't show up in the regular notes list.
    private val _isTemplate = MutableStateFlow(false)
    val isTemplate: StateFlow<Boolean> = _isTemplate.asStateFlow()

    private val _noteUpdatedAt = MutableStateFlow(0L)

    private var currentMetadata: NoteMetadataEntity? = null
    private val _currentlyLoadedNoteId = MutableStateFlow<String?>(null)
    private var currentlyLoadedNoteId: String?
        get() = _currentlyLoadedNoteId.value
        set(value) { _currentlyLoadedNoteId.value = value }

    private val _showWordCount = MutableStateFlow(false)
    val showWordCount: StateFlow<Boolean> = _showWordCount.asStateFlow()

    init {
        viewModelScope.launch {
            com.emberr.domain.util.sync.SyncEventBus.events.collect { event ->
                if (event !is com.emberr.domain.util.sync.NoteSyncEvent.NoteChanged) return@collect
                val syncedEntityId = event.entityId
                val currentId = currentMetadata?.noteId
                if (currentId != null && syncedEntityId == currentId) {
                    if (autosaveJob?.isActive == true) return@collect
                    if (isWithinLocalMutationCooldown()) return@collect

                    val updatedMeta = withContext(Dispatchers.IO) { repository.getNoteById(currentId) }
                    if (updatedMeta != null) {
                        currentMetadata = updatedMeta
                        _noteTitle.value = updatedMeta.title
                        _noteIcon.value = updatedMeta.icon
                        _isFavorite.value = updatedMeta.isFavorite
                        _coverImagePath.value = updatedMeta.coverImagePath
                        _showWordCount.value = updatedMeta.showWordCount
                        _noteUpdatedAt.value = updatedMeta.updatedAt
                        _isTemplate.value = updatedMeta.isTemplate
                    }

                    val content = withContext(Dispatchers.IO) { repository.getNoteContent(currentId) }
                    val newBlocks = preserveNewerLocalBlocks(content?.blocks ?: emptyList())
                    val resolved = recalculateNumberedLists(
                        newBlocks.ifEmpty { listOf(TextBlock(id = java.util.UUID.randomUUID().toString(), text = "")) }
                    )
                    if (resolved != _blocks.value) {
                        _blocks.value = resolved
                    }
                }
            }
        }
        viewModelScope.launch {
            _currentlyLoadedNoteId
                .filterNotNull()
                .flatMapLatest { noteId -> repository.observeNoteContent(noteId) }
                .filterNotNull()
                .collect { freshContent ->
                    if (_isLoading.value) return@collect
                    if (autosaveJob?.isActive == true) return@collect
                    if (isWithinLocalMutationCooldown()) return@collect

                    val final = recalculateNumberedLists(
                        preserveNewerLocalBlocks(freshContent.blocks).ifEmpty { listOf(TextBlock(id = java.util.UUID.randomUUID().toString(), text = "")) }
                    )
                    if (final != _blocks.value) {
                        _blocks.value = final
                    }
                }
        }
    }

    private suspend fun reconcileWithDisk(noteId: String, snapshot: List<NoteBlock>): List<NoteBlock> {
        val diskBlocks = repository.getNoteContent(noteId)?.blocks ?: emptyList()
        val diskById = diskBlocks.associateBy { it.id }
        val snapshotIds = snapshot.mapTo(HashSet()) { it.id }

        val reconciledSnapshot = snapshot.map { block ->
            val diskBlock = diskById[block.id]
            if (diskBlock != null && diskBlock.isDeleted && !block.isDeleted && diskBlock.updatedAt > block.updatedAt) diskBlock else block
        }

        if (isWithinLocalMutationCooldown()) return reconciledSnapshot

        val externallyAdded = diskBlocks.filter { it.id !in snapshotIds }
        return if (externallyAdded.isEmpty()) reconciledSnapshot else reconciledSnapshot + externallyAdded
    }

    private suspend fun theNoteAlreadyHoldsThis(
        noteId: String,
        metadataToSave: NoteMetadataEntity,
        blocksToSave: List<NoteBlock>
    ): Boolean {
        val storedMetadata = repository.getNoteById(noteId) ?: return false
        if (blocksToSave != repository.getNoteContent(noteId)?.blocks.orEmpty()) return false

        return metadataToSave.copy(
            updatedAt = storedMetadata.updatedAt,
            selfHostSyncedAt = storedMetadata.selfHostSyncedAt
        ) == storedMetadata
    }

    override suspend fun performSave(): Boolean {
        if (_isLoading.value) return false
        val meta = currentMetadata ?: return false

        return try {
            withContext(Dispatchers.IO) {
                SyncCoordinator.mutex.withLock {
                    // Re-validate now that the lock is actually held - acquiring it can be delayed for
                    // as long as a self-host sync pass holds the same mutex, and _blocks always tracks
                    // whichever note is CURRENTLY loaded, not meta.noteId. Without this check, a save
                    // queued behind a long lock wait would write whatever note the user has since
                    // switched to into meta's note instead of its own - cross-note contamination.
                    if (currentMetadata?.noteId != meta.noteId) return@withLock false

                    val reconciled = reconcileWithDisk(meta.noteId, _blocks.value)
                    if (reconciled !== _blocks.value) _blocks.value = reconciled

                    val updatedMeta = meta.copy(
                        title = _noteTitle.value,
                        icon = _noteIcon.value,
                        isFavorite = _isFavorite.value,
                        coverImagePath = _coverImagePath.value,
                        snippet = generateSnippet(reconciled),
                        updatedAt = System.currentTimeMillis(),
                        showWordCount = _showWordCount.value
                    )

                    if (theNoteAlreadyHoldsThis(meta.noteId, updatedMeta, reconciled)) return@withLock true

                    currentMetadata = updatedMeta
                    _noteUpdatedAt.value = updatedMeta.updatedAt

                    repository.saveNote(updatedMeta, NoteContent(blocks = reconciled))
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

    override suspend fun performIndexing() {
        if (_isLoading.value) return
        val meta = currentMetadata ?: return
        val snapshot = _blocks.value.toList()

        withContext(Dispatchers.IO) {
            repository.indexNote(
                meta,
                NoteContent(blocks = snapshot)
            )
        }
    }

    override fun getNoteTitleForReminder(): String {
        return _noteTitle.value.ifBlank { "Note" }
    }

    fun toggleWordCount() {
        val previous = _showWordCount.value
        _showWordCount.value = !previous
        viewModelScope.launch { if (!performSave()) _showWordCount.value = previous }
    }

    fun loadNote(noteId: String) {
        if (currentlyLoadedNoteId == noteId) return
        currentlyLoadedNoteId = noteId
        clearUndoHistory()

        com.emberr.domain.util.eventbus.AiEventBus.activeNoteId = noteId

        autosaveJob?.cancel()
        indexingJob?.cancel()

        val previousMeta = currentMetadata
        val snapshot = _blocks.value.toList()

        val flushedMeta = previousMeta?.copy(
            title = _noteTitle.value,
            icon = _noteIcon.value,
            isFavorite = _isFavorite.value,
            coverImagePath = _coverImagePath.value,
            snippet = generateSnippet(snapshot),
            showWordCount = _showWordCount.value,
            updatedAt = System.currentTimeMillis()
        )

        clearSelection()

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            // Any exception below (a repository/network call throwing) must never leave _isLoading
            // stuck true - the reactive observeNoteContent collector above drops every emission while
            // it's true, so a stuck flag silently stops this note from ever picking up a background
            // sync update again until the ViewModel itself is recreated (a full app restart).
            try {
                if (flushedMeta != null) {
                    SyncCoordinator.mutex.withLock {
                        val reconciled = reconcileWithDisk(previousMeta.noteId, snapshot)
                        if (!theNoteAlreadyHoldsThis(previousMeta.noteId, flushedMeta, reconciled)) {
                            val contentToSave = NoteContent(blocks = reconciled)
                            repository.saveNote(flushedMeta, contentToSave)
                            repository.indexNote(flushedMeta, contentToSave)
                        }
                    }
                }

                currentMetadata = repository.getNoteById(noteId)

                if (currentMetadata != null) {
                    _noteTitle.value = currentMetadata?.title ?: ""
                    _noteIcon.value = currentMetadata?.icon
                    _isFavorite.value = currentMetadata?.isFavorite ?: false
                    _coverImagePath.value = currentMetadata?.coverImagePath
                    _showWordCount.value = currentMetadata?.showWordCount ?: false
                    _noteUpdatedAt.value = currentMetadata?.updatedAt ?: 0L
                    _isTemplate.value = currentMetadata?.isTemplate ?: false

                    val content = repository.getNoteContent(noteId)
                    val existingBlocks = content?.blocks ?: emptyList()

                    _blocks.value = recalculateNumberedLists(
                        existingBlocks.ifEmpty { listOf(TextBlock(id = java.util.UUID.randomUUID().toString(), text = "")) }
                    )
                }
                isAiIndexDirty = false
                lastIndexedContentHash = 0
            } finally {
                _isLoading.value = false
            }

            // The reactive observeNoteContent collector drops every cache emission while _isLoading
            // was true above - if a background sync refreshed this exact note during that window, that
            // update is gone for good (StateFlow doesn't redeliver a value a collector already saw).
            // Re-read once now that the guard is open, so a sync landing mid-load isn't silently lost.
            val latestContent = repository.getNoteContent(noteId)
            if (latestContent != null) {
                val resolved = recalculateNumberedLists(
                    latestContent.blocks.ifEmpty { listOf(TextBlock(id = java.util.UUID.randomUUID().toString(), text = "")) }
                )
                if (resolved != _blocks.value) {
                    _blocks.value = resolved
                }
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _noteTitle.value = newTitle
        scheduleAutosave()
    }

    fun updateIcon(newIcon: String?) {
        val previous = _noteIcon.value
        _noteIcon.value = newIcon
        viewModelScope.launch { if (!performSave()) _noteIcon.value = previous }
    }

    fun toggleFavorite() {
        val previous = _isFavorite.value
        _isFavorite.value = !previous
        viewModelScope.launch { if (!performSave()) _isFavorite.value = previous }
    }

    fun handleCoverImagePicked(uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val mediaInfo = mediaStorageHelper.copyUriToInternalStorage(uriString)
            if (mediaInfo != null) {
                val previous = _coverImagePath.value
                _coverImagePath.value = mediaInfo.localFileName
                if (!performSave()) _coverImagePath.value = previous
            }
        }
    }

    fun removeCoverImage() {
        val previous = _coverImagePath.value
        _coverImagePath.value = null
        viewModelScope.launch { if (!performSave()) _coverImagePath.value = previous }
    }

    fun moveToTrash(onMoved: () -> Unit) {
        val meta = currentMetadata ?: return
        val snapshot = _blocks.value.toList()

        viewModelScope.launch(Dispatchers.IO) {
            SyncCoordinator.mutex.withLock {
                val reconciled = reconcileWithDisk(meta.noteId, snapshot)
                val trashedMeta = meta.copy(
                    title = _noteTitle.value,
                    icon = _noteIcon.value,
                    isFavorite = _isFavorite.value,
                    coverImagePath = _coverImagePath.value,
                    snippet = generateSnippet(reconciled),
                    showWordCount = _showWordCount.value,
                    trashedAt = System.currentTimeMillis()
                )

                val content = NoteContent(blocks = reconciled)

                repository.saveNote(trashedMeta, content)
                repository.indexNote(trashedMeta, content)

                currentMetadata = trashedMeta
            }

            withContext(Dispatchers.Main) {
                onMoved()
            }
        }
    }

    private fun generateSnippet(blocks: List<NoteBlock>): String {
        return blocks.asSequence()
            .mapNotNull { extractTextFromBlock(it) }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .take(120)
    }

    private fun extractTextFromBlock(block: NoteBlock): String? {
        if (block.isDeleted) return null
        return when (block) {
            is TextBlock -> block.text
            is HeadingBlock -> block.text
            is QuoteBlock -> block.text
            is CheckboxBlock -> block.text
            is BulletedListBlock -> block.text
            is NumberedListBlock -> block.text
            is ToggleBlock -> block.text
            else -> null
        }
    }

    fun generatePlainTextExport(): String {
        val title = _noteTitle.value.ifBlank { "Untitled Note" }
        val body = com.emberr.domain.util.export.ExportEngine.generatePlainText(_blocks.value)
        return "$title\n\n$body"
    }

    suspend fun generateMarkdownExport(): String {
        val title = _noteTitle.value.ifBlank { "Untitled Note" }
        val categoryNamesById = repository.getAllCategories().first()
            .filter { !it.isDeleted }
            .associate { it.categoryId to it.name }
        val body = com.emberr.domain.util.export.ExportEngine.generateMarkdown(
            blocks = _blocks.value,
            categoryNamesById = categoryNamesById
        )
        return "# $title\n\n$body"
    }
}

