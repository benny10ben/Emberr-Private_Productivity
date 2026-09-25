package com.emberr.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteKind
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.EmbeddedCanvasCleaner
import com.emberr.domain.media.LocalMediaGarbageCollector
import com.emberr.domain.model.*
import com.emberr.domain.repository.FavoriteNoteOrderStore
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.sample.SampleNotesSeeder
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.template.DefaultTemplateSeeder
import com.emberr.domain.util.sync.SyncCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

enum class SortType { LAST_EDITED, DATE_CREATED, NAME, MANUAL, TYPE }
enum class SortOrder { ASCENDING, DESCENDING }

private val expandedFolderJson = Json { ignoreUnknownKeys = true }

internal val FolderEntity.lastEditedAt: Long
    get() = if (updatedAt > 0L) updatedAt else createdAt

class HomeViewModel(
    private val repository: NoteRepository,
    private val settingsManager: SettingsManager,
    private val templateSeeder: DefaultTemplateSeeder,
    private val sampleNotesSeeder: SampleNotesSeeder,
    private val localMediaGarbageCollector: LocalMediaGarbageCollector,
    private val embeddedCanvasCleaner: EmbeddedCanvasCleaner,
    private val favoriteNoteOrderStore: FavoriteNoteOrderStore,
    private val activeSpaceStore: ActiveSpaceStore
) : ViewModel() {

    val sortType: StateFlow<SortType> = settingsManager.sortTypeFlow
        .map { stored -> SortType.entries.firstOrNull { it.name == stored } ?: SortType.LAST_EDITED }
        .stateIn(viewModelScope, SharingStarted.Lazily, SortType.LAST_EDITED)

    val sortOrder: StateFlow<SortOrder> = settingsManager.sortOrderFlow
        .map { stored -> SortOrder.entries.firstOrNull { it.name == stored } ?: SortOrder.DESCENDING }
        .stateIn(viewModelScope, SharingStarted.Lazily, SortOrder.DESCENDING)

    fun updateSort(type: SortType, order: SortOrder) {
        settingsManager.saveSortSettings(type.name, order.name)
    }

    private fun homeSectionExpandedState(sectionKey: String): StateFlow<Boolean> =
        settingsManager.homeSectionExpandedFlow(sectionKey)
            .stateIn(viewModelScope, SharingStarted.Eagerly, settingsManager.isHomeSectionExpanded(sectionKey))

    val isFavoritesSectionExpanded: StateFlow<Boolean> =
        homeSectionExpandedState(SyncConstants.HOME_SECTION_FAVORITES)

    val isNotesSectionExpanded: StateFlow<Boolean> =
        homeSectionExpandedState(SyncConstants.HOME_SECTION_NOTES)

    val isRecentsSectionExpanded: StateFlow<Boolean> =
        homeSectionExpandedState(SyncConstants.HOME_SECTION_RECENTS)

    fun toggleHomeSection(sectionKey: String) {
        settingsManager.saveHomeSectionExpanded(sectionKey, !settingsManager.isHomeSectionExpanded(sectionKey))
    }

    private fun applyNoteSort(
        list: List<NoteMetadataEntity>,
        type: SortType,
        order: SortOrder
    ): List<NoteMetadataEntity> {
        val descending = order == SortOrder.DESCENDING
        return when (type) {
            SortType.MANUAL -> list.sortedBy { it.sortOrder }
            SortType.LAST_EDITED -> if (descending) list.sortedByDescending { it.updatedAt } else list.sortedBy { it.updatedAt }
            SortType.DATE_CREATED -> if (descending) list.sortedByDescending { it.createdAt } else list.sortedBy { it.createdAt }
            SortType.NAME, SortType.TYPE -> if (descending) list.sortedByDescending { it.title.lowercase() } else list.sortedBy { it.title.lowercase() }
        }
    }

    private fun applyFolderSort(
        list: List<FolderEntity>,
        type: SortType,
        order: SortOrder
    ): List<FolderEntity> {
        val descending = order == SortOrder.DESCENDING
        return when (type) {
            SortType.MANUAL -> list.sortedBy { it.sortOrder }
            SortType.LAST_EDITED -> if (descending) list.sortedByDescending { it.lastEditedAt } else list.sortedBy { it.lastEditedAt }
            SortType.DATE_CREATED -> if (descending) list.sortedByDescending { it.createdAt } else list.sortedBy { it.createdAt }
            SortType.NAME, SortType.TYPE -> if (descending) list.sortedByDescending { it.name.lowercase() } else list.sortedBy { it.name.lowercase() }
        }
    }

    fun toggleFolderExpansion(folderId: String) {
        updateExpandedFolderIds { if (it.contains(folderId)) it - folderId else it + folderId }
    }

    private fun readStoredExpandedFolderIds(): Set<String> {
        val storedIds = settingsManager.getExpandedFolderIdsJson()
        if (storedIds.isBlank()) return emptySet()
        return try {
            expandedFolderJson.decodeFromString<Set<String>>(storedIds)
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun updateExpandedFolderIds(change: (Set<String>) -> Set<String>) {
        _expandedFolderIds.update(change)
        settingsManager.saveExpandedFolderIdsJson(expandedFolderJson.encodeToString(_expandedFolderIds.value))
    }

    private data class RowParent(val parentFolderId: String?)

    private suspend fun findRowParent(rowKey: String): RowParent? = when {
        HomeItemKey.isFolder(rowKey) ->
            _allFolders.value
                .find { it.folderId == HomeItemKey.folderIdOf(rowKey) }
                ?.let { RowParent(it.parentFolderId) }

        HomeItemKey.isNote(rowKey) ->
            repository.getNoteById(HomeItemKey.noteIdOf(rowKey))
                ?.let { RowParent(it.folderId) }

        else -> null
    }

    private fun isFolderDescendantOf(candidateId: String?, ancestorId: String): Boolean {
        var curr = candidateId
        while (curr != null) {
            if (curr == ancestorId) return true
            curr = _allFolders.value.find { it.folderId == curr }?.parentFolderId
        }
        return false
    }

    private suspend fun reparentRow(rowKey: String, newParentFolderId: String?): Boolean {
        try {
            SyncCoordinator.mutex.withLock {
                when {
                    HomeItemKey.isFolder(rowKey) -> {
                        val folderId = HomeItemKey.folderIdOf(rowKey)
                        if (isFolderDescendantOf(newParentFolderId, folderId)) return false
                        val folder = _allFolders.value.find { it.folderId == folderId } ?: return false
                        repository.insertFolder(folder.copy(parentFolderId = newParentFolderId))
                    }

                    HomeItemKey.isNote(rowKey) -> {
                        val noteId = HomeItemKey.noteIdOf(rowKey)
                        val meta = repository.getNoteById(noteId) ?: return false
                        val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                        repository.saveNote(
                            meta.copy(folderId = newParentFolderId, updatedAt = System.currentTimeMillis()),
                            content
                        )
                    }

                    else -> return false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    // orderedKeys: the flat visual order of the rows as the user saw them, passed in from the
    // screen so the VM doesn't re-derive a potentially different order.
    fun reorderItems(draggedKey: String, targetKey: String, insertBefore: Boolean, orderedKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {

            val scopeKeys = orderedKeys.filter {
                HomeItemKey.isNote(it) || HomeItemKey.isFolder(it)
            }.toMutableList()

            if (draggedKey !in scopeKeys) return@launch
            if (targetKey !in scopeKeys) return@launch

            val draggedParent = findRowParent(draggedKey) ?: return@launch
            val targetParent = findRowParent(targetKey) ?: return@launch
            if (draggedParent.parentFolderId != targetParent.parentFolderId &&
                !reparentRow(draggedKey, targetParent.parentFolderId)
            ) return@launch

            // Move dragged item to new position.
            scopeKeys.remove(draggedKey)
            val targetIndex = scopeKeys.indexOf(targetKey)
            if (targetIndex == -1) return@launch
            val insertAt = if (insertBefore) targetIndex else targetIndex + 1
            scopeKeys.add(insertAt.coerceIn(0, scopeKeys.size), draggedKey)

            persistManualOrder(scopeKeys)
        }
    }

    private suspend fun persistManualOrder(orderedKeys: List<String>) {
        orderedKeys.forEachIndexed { index, key ->
            val order = index + 1
            when {
                HomeItemKey.isFolder(key) ->
                    repository.updateFolderSortOrder(HomeItemKey.folderIdOf(key), order)
                HomeItemKey.isNote(key) ->
                    repository.updateNoteSortOrder(HomeItemKey.noteIdOf(key), order)
            }
        }

        withContext(Dispatchers.Main) {
            settingsManager.saveSortSettings(SortType.MANUAL.name, SortOrder.ASCENDING.name)
        }
    }

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<String?>(null)

    private val _searchQuery = MutableStateFlow("")

    private val _selectedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNoteIds: StateFlow<Set<String>> = _selectedNoteIds.asStateFlow()

    private val _selectedFolderIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolderIds: StateFlow<Set<String>> = _selectedFolderIds.asStateFlow()

    private val _expandedFolderIds = MutableStateFlow(readStoredExpandedFolderIds())
    val expandedFolderIds: StateFlow<Set<String>> = _expandedFolderIds.asStateFlow()

    private val _remindersCount = MutableStateFlow(0)
    val remindersCount: StateFlow<Int> = _remindersCount.asStateFlow()

    private val _bookmarksCount = MutableStateFlow(0)
    val bookmarksCount: StateFlow<Int> = _bookmarksCount.asStateFlow()

    private val _imagesCount = MutableStateFlow(0)
    val imagesCount: StateFlow<Int> = _imagesCount.asStateFlow()

    private val _documentsCount = MutableStateFlow(0)
    val documentsCount: StateFlow<Int> = _documentsCount.asStateFlow()

    private val _allFolders = repository.getAllFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val noteCountsByFolder: StateFlow<Map<String, Int>> = repository.getNoteCountsByFolder()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    val recentNotes = repository.getAllLinkableNotes()
        .map { notes ->
            notes.filter { !it.title.equals("Inbox", ignoreCase = true) }
                .sortedByDescending { it.updatedAt }.take(MAXIMUM_RECENT_NOTES_SHOWN)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteNotes = combine(
        repository.getFavoriteNotes(),
        favoriteNoteOrderStore.orderedNoteIdsFlow
    ) { notes, manuallyOrderedNoteIds ->
        val positionOfNote = manuallyOrderedNoteIds.withIndex()
            .associate { (position, noteId) -> noteId to position }
        notes.sortedBy { positionOfNote[it.noteId] ?: Int.MAX_VALUE }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun reorderFavoriteNotes(
        draggedNoteId: String,
        targetNoteId: String,
        insertBefore: Boolean,
        orderedNoteIds: List<String>
    ) {
        if (draggedNoteId == targetNoteId) return

        val reordered = orderedNoteIds.toMutableList()
        if (!reordered.remove(draggedNoteId)) return

        val targetIndex = reordered.indexOf(targetNoteId)
        if (targetIndex == -1) return

        val insertAt = if (insertBefore) targetIndex else targetIndex + 1
        reordered.add(insertAt.coerceIn(0, reordered.size), draggedNoteId)

        favoriteNoteOrderStore.saveOrder(reordered)
    }

    val foldersByParent: StateFlow<Map<String?, List<FolderEntity>>> =
        combine(
            _allFolders,
            sortType,
            sortOrder
        ) { all: List<FolderEntity>, type: SortType, order: SortOrder ->
            applyFolderSort(all.filter { !it.isDeleted }, type, order)
                .groupBy { it.parentFolderId }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteMetadataEntity>> = combine(
        _selectedFolderId.flatMapLatest { folderId ->
            if (folderId == null) repository.getAllNotes()
            else repository.getNotesInFolder(folderId)
        },
        _searchQuery,
        sortType,
        sortOrder
    ) { noteList, query, activeSortType, activeSortOrder ->
        val visibleNotes = noteList.filter { !it.title.equals("Inbox", ignoreCase = true) }
        val folderFiltered = if (query.isNotBlank()) visibleNotes else visibleNotes.filter { it.folderId == _selectedFolderId.value }

        val finalFilteredList = if (query.isBlank()) {
            folderFiltered
        } else {
            val q = query.lowercase()
            val filteredList = mutableListOf<NoteMetadataEntity>()

            for (note in folderFiltered) {
                if (note.title.lowercase().contains(q) || note.snippet.lowercase().contains(q)) {
                    filteredList.add(note)
                    continue
                }

                val content = repository.getNoteContent(note.noteId)
                if (content != null) {
                    val matches = content.blocks.any { block ->
                        when (block) {
                            is TextBlock -> block.text.lowercase().contains(q)
                            is HeadingBlock -> block.text.lowercase().contains(q)
                            is CheckboxBlock -> block.text.lowercase().contains(q)
                            is BulletedListBlock -> block.text.lowercase().contains(q)
                            is NumberedListBlock -> block.text.lowercase().contains(q)
                            is ToggleBlock -> block.text.lowercase().contains(q)
                            is CodeBlock -> block.code.lowercase().contains(q)
                            is BookmarkBlock -> {
                                block.url.lowercase().contains(q) ||
                                        block.title?.lowercase()?.contains(q) == true ||
                                        block.description?.lowercase()?.contains(q) == true
                            }
                            is DocumentBlock -> block.fileName.lowercase().contains(q)
                            is ImageBlock -> block.localFilePath?.lowercase()?.contains(q) == true
                            else -> false
                        }
                    }
                    if (matches) {
                        filteredList.add(note)
                    }
                }
            }
            filteredList
        }

        applyNoteSort(finalFilteredList, activeSortType, activeSortOrder)
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // All non-trashed notes grouped by folderId, sorted per the active sort setting.
    // Root notes live under the null key. Drives the desktop sidebar tree.
    val notesByFolder: StateFlow<Map<String?, List<NoteMetadataEntity>>> =
        combine(repository.getAllNotes(), sortType, sortOrder) { allNotes, type, order ->
            applyNoteSort(
                allNotes.filter { !it.isFavorite && !it.title.equals("Inbox", ignoreCase = true) },
                type,
                order
            ).groupBy { it.folderId }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    // Every saved template (predefined + user-created), alphabetical per NoteDao.getAllTemplates.
    val templates: StateFlow<List<NoteMetadataEntity>> = repository.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _templateSearchQuery = MutableStateFlow("")
    val templateSearchQuery: StateFlow<String> = _templateSearchQuery.asStateFlow()

    // Case-insensitive name filter over `templates`, recombined whenever either the query or the
    // underlying template list changes - mirrors the `notes` search pattern above.
    val filteredTemplates: StateFlow<List<NoteMetadataEntity>> = combine(
        templates,
        _templateSearchQuery
    ) { allTemplates, query ->
        if (query.isBlank()) allTemplates
        else allTemplates.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Updates the templates-sheet search box; filteredTemplates recomputes automatically.
    fun updateTemplateSearchQuery(query: String) {
        _templateSearchQuery.value = query
    }

    init {
        _isLoading.value = false
        viewModelScope.launch(Dispatchers.IO) {
            repository.cleanupOldTrashedNotes()
            templateSeeder.seedIfMissing()
            sampleNotesSeeder.seedIfNeededAndReturnFolderToOpen()?.let { folderId ->
                updateExpandedFolderIds { it + folderId }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            delay(2_000.milliseconds)
            localMediaGarbageCollector.collectAndDeleteOrphanedMedia()
            embeddedCanvasCleaner.deleteCanvasesOfRemovedBlocks()
            embeddedCanvasCleaner.forgetViewPositionsOfDeletedCanvases()
            com.emberr.domain.ai.models.cleanupPendingModelDeletions()
        }
        viewModelScope.launch {
            activeSpaceStore.activeSpaceId.drop(1).collect {
                _selectedFolderId.value = null
                _selectedNoteIds.value = emptySet()
                _selectedFolderIds.value = emptySet()
                _searchQuery.value = ""
                withContext(Dispatchers.IO) { templateSeeder.seedIfMissing() }
            }
        }
        viewModelScope.launch {
            repository.getIncompleteTasksCount().collect { count ->
                _remindersCount.value = count
            }
        }
        viewModelScope.launch {
            repository.getImagesCount().collect { _imagesCount.value = it }
        }
        viewModelScope.launch {
            repository.getDocumentsCount().collect { _documentsCount.value = it }
        }
        viewModelScope.launch {
            repository.getBookmarksCount().collect { _bookmarksCount.value = it }
        }
    }

    fun createNewFolder(name: String) {
        createFolderInParent(parentFolderId = _selectedFolderId.value, name = name, autoExpand = false)
    }

    // Used by the sidebar tree's per-folder "+" action. autoExpand opens the parent so the new child is visible.
    fun createFolderInParent(parentFolderId: String?, name: String, autoExpand: Boolean = true) {
        if (autoExpand) parentFolderId?.let { fid -> updateExpandedFolderIds { it + fid } }
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertFolder(
                FolderEntity(
                    folderId = UUID.randomUUID().toString(),
                    name = name,
                    parentFolderId = parentFolderId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun renameNote(noteId: String, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    val meta = repository.getNoteById(noteId) ?: return@withLock
                    val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                    repository.saveNote(meta.copy(title = newTitle, updatedAt = System.currentTimeMillis()), content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = _allFolders.value.find { it.folderId == folderId } ?: return@launch
            repository.insertFolder(folder.copy(name = newName))
        }
    }

    fun trashNote(noteId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    val meta = repository.getNoteById(noteId) ?: return@withLock
                    val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                    repository.saveNote(meta.copy(trashedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()), content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun trashFolder(folderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    trashFolderContentsRecursively(folderId, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleNoteSelection(noteId: String) {
        _selectedNoteIds.update { if (it.contains(noteId)) it - noteId else it + noteId }
    }

    fun toggleFolderSelection(folderId: String) {
        _selectedFolderIds.update { if (it.contains(folderId)) it - folderId else it + folderId }
    }

    fun addToSelection(noteIds: Collection<String>, folderIds: Collection<String>) {
        _selectedNoteIds.update { it + noteIds }
        _selectedFolderIds.update { it + folderIds }
    }

    fun setNotesFavorite(noteIds: Collection<String>, isFavorite: Boolean) {
        if (noteIds.isEmpty()) return
        clearSelection()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                noteIds.forEach { noteId ->
                    if (isFavorite) repository.addNoteToFavorites(noteId)
                    else repository.removeNoteFromFavoritesAndMoveToRoot(noteId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
        _selectedFolderIds.value = emptySet()
    }

    private suspend fun deleteFolderRecursively(folderId: String) {
        val notesInFolder = repository.getNotesInFolder(folderId).first()
        notesInFolder.forEach { note ->
            repository.deleteNote(note.noteId, note.filePath)
        }

        val subFolders = _allFolders.value.filter { it.parentFolderId == folderId }
        subFolders.forEach { subFolder ->
            deleteFolderRecursively(subFolder.folderId)
        }

        repository.deleteFolder(folderId)
        updateExpandedFolderIds { it - folderId }
    }

    fun deleteSelectedItems() {
        val toDeleteNotes = _selectedNoteIds.value
        val toDeleteFolders = _selectedFolderIds.value
        val now = System.currentTimeMillis()
        clearSelection()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    toDeleteNotes.forEach { noteId ->
                        val meta = repository.getNoteById(noteId)
                        if (meta != null) {
                            val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                            repository.saveNote(meta.copy(trashedAt = now, updatedAt = now), content)
                        }
                    }

                    toDeleteFolders.forEach { folderId ->
                        trashFolderContentsRecursively(folderId, now)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun trashFolderContentsRecursively(folderId: String, trashTime: Long) {
        val notesInFolder = repository.getNotesInFolder(folderId).first()
        notesInFolder.forEach { note ->
            val content = repository.getNoteContent(note.noteId) ?: NoteContent(blocks = emptyList())
            repository.saveNote(note.copy(trashedAt = trashTime, updatedAt = trashTime), content)
        }

        val subFolders = _allFolders.value.filter { it.parentFolderId == folderId }
        subFolders.forEach { subFolder ->
            trashFolderContentsRecursively(subFolder.folderId, trashTime)
        }

        val folder = _allFolders.value.find { it.folderId == folderId }
        if (folder != null) {
            repository.insertFolder(folder.copy(isDeleted = true, createdAt = trashTime))
        }
    }

    fun createNewNote(
        title: String = "",
        forceHomeFolder: Boolean = false,
        kind: NoteKind = NoteKind.NOTE,
        onNoteCreated: (String) -> Unit
    ) {
        val target = if (forceHomeFolder) null else _selectedFolderId.value
        createNoteInParent(parentFolderId = target, title = title, autoExpand = false, kind = kind, onNoteCreated = onNoteCreated)
    }

    // Used by the sidebar tree's per-folder "+" action.
    fun createNoteInParent(
        parentFolderId: String?,
        title: String = "",
        autoExpand: Boolean = true,
        kind: NoteKind = NoteKind.NOTE,
        onNoteCreated: (String) -> Unit
    ) {
        if (autoExpand) parentFolderId?.let { fid -> updateExpandedFolderIds { it + fid } }
        viewModelScope.launch(Dispatchers.IO) {
            val newNoteId = UUID.randomUUID().toString()
            val fileName = "note_$newNoteId.json"

            val metadata = NoteMetadataEntity(
                noteId = newNoteId,
                title = title,
                icon = null,
                folderId = parentFolderId,
                isDaily = false,
                dateString = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                filePath = fileName,
                snippet = "",
                kind = kind
            )

            repository.saveNote(metadata, NoteContent(blocks = emptyList()))

            withContext(Dispatchers.Main) {
                onNoteCreated(newNoteId)
            }
        }
    }

    // Re-seeds any missing predefined template. Idempotent and cheap (a single Flow read plus,
    // at most, two inserts), so it's safe to call every time the templates sheet is opened -
    // this is what brings back a predefined template the user deleted, per spec.
    fun onTemplatesMenuOpened() {
        viewModelScope.launch(Dispatchers.IO) {
            templateSeeder.seedIfMissing()
        }
    }

    // Clones a template's content with fresh block/schema ids (see NoteBlock.deepCopyWithNewIds)
    // and saves it as a brand-new, regular note in the currently open folder.
    fun createNoteFromTemplate(
        templateId: String,
        parentFolderId: String? = _selectedFolderId.value,
        autoExpand: Boolean = false,
        onNoteCreated: (String) -> Unit
    ) {
        if (autoExpand) parentFolderId?.let { fid -> updateExpandedFolderIds { it + fid } }
        viewModelScope.launch(Dispatchers.IO) {
            val templateMeta = repository.getNoteById(templateId) ?: return@launch
            val templateContent = repository.getNoteContent(templateId) ?: NoteContent(blocks = emptyList())
            val newNoteId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            val metadata = NoteMetadataEntity(
                noteId = newNoteId,
                title = templateMeta.title,
                icon = templateMeta.icon,
                coverImagePath = templateMeta.coverImagePath,
                showWordCount = templateMeta.showWordCount,
                folderId = parentFolderId,
                isDaily = false,
                dateString = null,
                createdAt = now,
                updatedAt = now,
                filePath = "",
                isTemplate = false
            )

            repository.saveNote(metadata, repository.copyEmbeddedCanvasesIn(templateContent.deepCopyWithNewIds()))

            withContext(Dispatchers.Main) {
                onNoteCreated(newNoteId)
            }
        }
    }

    // Persists the given content as a brand-new, reusable template (e.g. "Save as template"
    // from an open note, or the Templates sheet's "Create New Template" button, which passes
    // empty content - same blank-title-allowed convention as createNewNote). Templates never
    // carry a folderId - the templates sheet is flat.
    fun saveAsTemplate(title: String, content: NoteContent, onTemplateCreated: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val newTemplateId = UUID.randomUUID().toString()
            val metadata = NoteMetadataEntity(
                noteId = newTemplateId,
                title = title,
                icon = null,
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = now,
                updatedAt = now,
                filePath = "",
                isTemplate = true
            )
            repository.saveNote(metadata, content)

            withContext(Dispatchers.Main) {
                onTemplateCreated(newTemplateId)
            }
        }
    }

    // Permanently removes a template (predefined or user-created). Templates are hard-deleted
    // rather than trashed - they don't participate in the Trash flow.
    fun deleteTemplate(templateId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTemplate(templateId)
        }
    }

    fun moveNote(noteId: String, targetFolderId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SyncCoordinator.mutex.withLock {
                    val meta = repository.getNoteById(noteId) ?: return@withLock
                    val content = repository.getNoteContent(noteId) ?: NoteContent(blocks = emptyList())
                    repository.saveNote(meta.copy(folderId = targetFolderId, updatedAt = System.currentTimeMillis()), content)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun moveFolder(folderId: String, targetParentId: String?) {
        // Prevent dropping a folder into itself or its own descendants.
        if (folderId == targetParentId) return
        if (isFolderDescendantOf(targetParentId, folderId)) return
        viewModelScope.launch(Dispatchers.IO) {
            val folder = _allFolders.value.find { it.folderId == folderId } ?: return@launch
            repository.insertFolder(folder.copy(parentFolderId = targetParentId))
        }
    }

    companion object {
        private const val MAXIMUM_RECENT_NOTES_SHOWN = 10
    }
}