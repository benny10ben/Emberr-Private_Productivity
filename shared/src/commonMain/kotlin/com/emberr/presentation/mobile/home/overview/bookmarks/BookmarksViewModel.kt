package com.emberr.presentation.mobile.home.overview.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.markDeleted
import com.emberr.domain.repository.BookmarkCategoryOrderStore
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.util.network.HtmlMetadataFetcher
import com.emberr.domain.sync.AutoSyncTrigger
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.presentation.shared.editor.FocusRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import java.util.UUID

data class BookmarkGroup(
    val monthYear: String,
    val timestamp: Long,
    val blocks: List<BookmarkBlock>
)

class BookmarksViewModel constructor(
    private val repository: NoteRepository,
    private val bookmarkCategoryOrderStore: BookmarkCategoryOrderStore,
    private val activeSpaceStore: ActiveSpaceStore
) : ViewModel() {

    val categoryOrder: StateFlow<List<String>> = bookmarkCategoryOrderStore.orderFlow
        .map { it.categories }
        .stateIn(viewModelScope, SharingStarted.Eagerly, bookmarkCategoryOrderStore.getOrder().categories)

    fun saveCategoryOrder(categories: List<String>) {
        bookmarkCategoryOrderStore.saveOrder(categories)
        AutoSyncTrigger.requestSync()
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _groupedBlocks = MutableStateFlow<List<BookmarkGroup>>(emptyList())
    val groupedBlocks: StateFlow<List<BookmarkGroup>> = _groupedBlocks.asStateFlow()

    private val _selectedBlockIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedBlockIds: StateFlow<Set<String>> = _selectedBlockIds.asStateFlow()

    private val _focusRequest = MutableStateFlow<FocusRequest?>(null)
    val focusRequest: StateFlow<FocusRequest?> = _focusRequest.asStateFlow()

    private val blockSourceMap = mutableMapOf<String, String>()

    private var loadedSpaceId: String? = null

    fun loadAllBookmarks() {
        viewModelScope.launch {
            activeSpaceStore.activeSpaceId
                .onEach { spaceId -> startSessionForSpace(spaceId) }
                .flatMapLatest { repository.getAllBookmarksFlow() }
                .collectLatest { allBookmarks ->
                    blockSourceMap.clear()

                    val months = arrayOf("", "January", "February", "March", "April", "May",
                        "June", "July", "August", "September", "October", "November", "December")

                    val grouped = allBookmarks
                        .onEach { blockSourceMap[it.blockId] = it.noteId }
                        .groupBy {
                            val localDate = Instant.fromEpochMilliseconds(it.noteUpdatedAt)
                                .toLocalDateTime(TimeZone.currentSystemDefault()).date
                            "${months[localDate.month.number]} ${localDate.year}"
                        }
                        .map { (monthYear, entities) ->
                            BookmarkGroup(
                                monthYear = monthYear,
                                timestamp = entities.first().noteUpdatedAt,
                                blocks    = entities.map { e ->
                                    BookmarkBlock(
                                        id              = e.blockId,
                                        url             = e.url,
                                        title           = e.title,
                                        description     = e.description,
                                        previewImageUrl = e.previewImageUrl
                                    )
                                }
                            )
                        }

                    _groupedBlocks.value = grouped
                    _isLoading.value = false

                    allBookmarks.forEach { entity ->
                        if (entity.title.isNullOrBlank() ||
                            entity.title == "Loading preview..." ||
                            entity.title == "Loading...") {
                            fetchMissingMetadata(entity.blockId, entity.url, entity.noteId)
                        }
                    }
                }
        }
    }

    private fun startSessionForSpace(spaceId: String) {
        if (loadedSpaceId == spaceId) return
        val isSwitchingSpaces = loadedSpaceId != null
        loadedSpaceId = spaceId

        _isLoading.value = true
        if (isSwitchingSpaces) {
            blockSourceMap.clear()
            _groupedBlocks.value = emptyList()
            _selectedBlockIds.value = emptySet()
            _focusRequest.value = null
        }
    }

    private fun fetchMissingMetadata(blockId: String, url: String, noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val metadata = HtmlMetadataFetcher.fetchMetadata(url)
                    SyncCoordinator.mutex.withLock {
                        val meta = repository.getNoteById(noteId) ?: return@withLock
                        val content = repository.getNoteContent(noteId) ?: return@withLock

                        val updatedBlocks = updateBookmarkInList(content.blocks, blockId) {
                            it.copy(
                                title = metadata.title ?: "Unknown Link",
                                description = metadata.description,
                                previewImageUrl = metadata.imageUrl
                            )
                        }
                        repository.saveNote(meta.copy(updatedAt = System.currentTimeMillis()), NoteContent(blocks = updatedBlocks))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun handleUrlSubmit(blockId: String, url: String) {
        val noteId = blockSourceMap[blockId] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    val meta = repository.getNoteById(noteId) ?: return@withLock
                    val content = repository.getNoteContent(noteId) ?: return@withLock

                    val updatedBlocks = updateBookmarkInList(content.blocks, blockId) {
                        it.copy(url = url, title = "Loading...")
                    }
                    repository.saveNote(meta.copy(updatedAt = System.currentTimeMillis()), NoteContent(blocks = updatedBlocks))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(NonCancellable) {
                try {
                    val metadata = HtmlMetadataFetcher.fetchMetadata(url)
                    SyncCoordinator.mutex.withLock {
                        val meta = repository.getNoteById(noteId) ?: return@withLock
                        val content = repository.getNoteContent(noteId) ?: return@withLock

                        val updatedBlocks = updateBookmarkInList(content.blocks, blockId) {
                            it.copy(
                                title = metadata.title ?: "Unknown Link",
                                description = metadata.description,
                                previewImageUrl = metadata.imageUrl
                            )
                        }
                        repository.saveNote(meta.copy(updatedAt = System.currentTimeMillis()), NoteContent(blocks = updatedBlocks))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateBookmarkInList(
        blocks: List<NoteBlock>,
        targetId: String,
        updater: (BookmarkBlock) -> BookmarkBlock
    ): List<NoteBlock> {
        val now = System.currentTimeMillis()
        return blocks.map { block ->
            if (block.id == targetId && block is BookmarkBlock) {
                updater(block).copy(updatedAt = now)
            } else block
        }
    }

    fun toggleSelection(id: String) {
        _selectedBlockIds.update { if (it.contains(id)) it - id else it + id }
    }

    fun clearSelection() {
        _selectedBlockIds.value = emptySet()
    }

    fun selectAllBlocks() {
        _selectedBlockIds.value = _groupedBlocks.value.flatMap { it.blocks }.map { it.id }.toSet()
    }

    fun clearFocusRequest() {
        _focusRequest.value = null
    }

    fun getSelectedText(): String {
        return _groupedBlocks.value.flatMap { it.blocks }
            .filter { it.id in _selectedBlockIds.value }
            .joinToString("\n") { it.url }
    }

    fun cutSelectedBlocks(): String {
        val text = getSelectedText()
        deleteSelectedBlocks()
        return text
    }

    fun deleteSelectedBlocks() {
        val toDelete = _selectedBlockIds.value
        if (toDelete.isEmpty()) return

        val blocksByNote = toDelete.groupBy { blockSourceMap[it] }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    blocksByNote.forEach { (noteId, blockIdsToDelete) ->
                        if (noteId == null) return@forEach
                        val meta = repository.getNoteById(noteId) ?: return@forEach
                        val content = repository.getNoteContent(noteId) ?: return@forEach
                        val updatedBlocks = content.blocks.map { block ->
                            if (block.id in blockIdsToDelete) block.markDeleted() else block
                        }
                        repository.saveNote(meta.copy(updatedAt = System.currentTimeMillis()), NoteContent(blocks = updatedBlocks))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            clearSelection()
        }
    }

    fun insertBookmarkWithUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = UUID.randomUUID().toString()
            var inboxMeta: NoteMetadataEntity? = null

            try {
                SyncCoordinator.mutex.withLock {
                    var meta = repository.getAllNotes().first().find { it.title.equals("Inbox", ignoreCase = true) }
                    if (meta == null) {
                        meta = NoteMetadataEntity(
                            noteId = UUID.randomUUID().toString(),
                            title = "Inbox",
                            folderId = null,
                            isDaily = false,
                            dateString = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            filePath = "note_${UUID.randomUUID()}.json",
                            snippet = ""
                        )
                        repository.saveNote(meta, NoteContent(blocks = emptyList()))
                    }
                    inboxMeta = meta

                    val content = repository.getNoteContent(meta.noteId) ?: NoteContent(blocks = emptyList())
                    val newBlock = BookmarkBlock(
                        id = newId,
                        url = url,
                        title = "Loading preview...",
                        description = null,
                        previewImageUrl = null
                    )
                    val updatedBlocks = content.blocks + newBlock
                    repository.saveNote(meta.copy(updatedAt = System.currentTimeMillis()), NoteContent(blocks = updatedBlocks))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@launch
            }

            val noteId = inboxMeta?.noteId ?: return@launch
            withContext(NonCancellable) {
                try {
                    val metadata = HtmlMetadataFetcher.fetchMetadata(url)
                    SyncCoordinator.mutex.withLock {
                        val meta = repository.getNoteById(noteId) ?: return@withLock
                        val currentContent = repository.getNoteContent(noteId) ?: return@withLock

                        val updatedBlocks = currentContent.blocks.map {
                            if (it.id == newId && it is BookmarkBlock) {
                                it.copy(title = metadata.title, description = metadata.description, previewImageUrl = metadata.imageUrl)
                            } else it
                        }
                        repository.saveNote(meta.copy(updatedAt = System.currentTimeMillis()), NoteContent(blocks = updatedBlocks))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}