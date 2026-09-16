package com.emberr.domain.repository

import com.emberr.data.local.room.BlockDao
import com.emberr.data.local.room.BookmarkBlockDao
import com.emberr.data.local.room.BookmarkBlockEntity
import com.emberr.data.local.room.CalendarEventExceptionDao
import com.emberr.data.local.room.CalendarEventExceptionEntity
import com.emberr.data.local.room.CalendarTaskEntity
import com.emberr.data.local.room.toEntityColumns
import com.emberr.data.local.room.toRecurrenceRule
import com.emberr.data.local.room.DatabaseTemplateDao
import com.emberr.data.local.room.DatabaseTemplateEntity
import com.emberr.data.local.room.FolderDao
import com.emberr.data.local.room.FolderEntity
import com.emberr.data.local.room.NoteBlockEntity
import com.emberr.data.local.room.NoteDao
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.data.local.room.TagDao
import com.emberr.data.local.room.TagEntity
import com.emberr.data.local.room.CategoryDao
import com.emberr.data.local.room.CategoryEntity
import com.emberr.data.local.room.CalendarTaskDao
import com.emberr.data.local.room.DocumentBlockDao
import com.emberr.data.local.room.DocumentBlockEntity
import com.emberr.data.local.room.ImageBlockDao
import com.emberr.data.local.room.ImageBlockEntity
import com.emberr.data.local.room.SelfHostDeletedNoteDao
import com.emberr.data.local.room.SelfHostDeletedNoteEntity
import com.emberr.data.local.room.TaskSource
import com.emberr.domain.ai.NoteIndexer
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.NoteSearchResult
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.RecurrenceEditScope
import com.emberr.domain.model.RecurrenceEngine
import com.emberr.domain.model.RecurrenceRule
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.markDeleted
import com.emberr.domain.util.sync.SyncCoordinator
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import com.emberr.domain.sync.AutoSyncTrigger
import com.emberr.domain.vault.VaultMirrorTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

// NoteRepositoryImpl is the single point through which all note data flows.
// Every ViewModel reads from and writes to this class — nothing talks to Room directly.
//
// ARCHITECTURE: Why a cache exists here
//
// The editor holds its own in-memory MutableStateFlow<List<NoteBlock>> for performance.
// Writing to Room on every keystroke would trigger Room Flow emissions that fight the
// editor mid-typing, causing cursor jumps and UI flicker. So the editor writes to its
// own in-memory state instantly, then flushes to Room on a 1-second debounce.
//
// This creates a problem: other screens (e.g. TasksScreen) write directly to Room
// via saveNote/saveDailyNote, but the editor's in-memory state doesn't know about it.
// On navigation back, the editor was showing stale data.
//
// The fix: this repository maintains two MutableStateFlow caches — one for regular notes,
// one for daily notes. Every write updates the cache synchronously before touching Room.
// Every read checks the cache first. ViewModels that need to stay in sync (DailyEditorViewModel,
// NoteEditorViewModel) observe these caches via observeDailyNote / observeNoteContent.
// When any writer calls saveNote or saveDailyNote, the relevant observer fires automatically —
// on mobile, on desktop, regardless of how screens were navigated or dismissed.
//
// The editor guards against its own writes bouncing back by checking autosaveJob?.isActive.
// If the editor itself triggered the write, the cache emission is ignored. If another
// ViewModel (TasksViewModel, SyncRepositoryImpl, etc.) triggered it, the emission
// goes through and updates the editor's blocks.
//
// CRASH SAFETY
//
// The cache lives in memory and is gone on crash. That's fine — it's purely a mirror of
// Room. On next launch, getDailyNote and getNoteContent read from Room and repopulate
// the cache on first access. Data loss on crash is at most 1 second of typing (the
// autosave debounce window). BaseEditorViewModel.onCleared() fires a final save on
// normal process death, so real-world data loss is essentially zero.

class NoteRepositoryImpl(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val blockDao: BlockDao,
    private val noteIndexer: NoteIndexer,
    private val calendarTaskDao: CalendarTaskDao,
    private val calendarEventExceptionDao: CalendarEventExceptionDao,
    private val imageBlockDao: ImageBlockDao,
    private val documentBlockDao: DocumentBlockDao,
    private val bookmarkBlockDao: BookmarkBlockDao,
    private val databaseTemplateDao: DatabaseTemplateDao,
    private val categoryDao: CategoryDao,
    private val selfHostDeletedNoteDao: SelfHostDeletedNoteDao
) : NoteRepository {

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // In-memory cache for regular notes keyed by noteId.
    // Updated on every saveNote call, checked before every getNoteContent DB read.
    private val noteContentCache = MutableStateFlow<Map<String, NoteContent>>(emptyMap())

    // In-memory cache for daily notes keyed by dateString (e.g. "2025-06-01").
    private val dailyNoteCache = MutableStateFlow<Map<String, NoteContent>>(emptyMap())

    // Exposes a Flow that emits whenever the cache entry for this noteId changes.
    // NoteEditorViewModel subscribes to this in its init block to stay in sync
    // with external writes (e.g. TasksViewModel toggling a checkbox).
    override fun observeNoteContent(noteId: String): Flow<NoteContent?> =
        noteContentCache.map { it[noteId] }

    // Exposes a Flow that emits whenever the cache entry for this dateString changes.
    // DailyEditorViewModel subscribes to this in its init block for the same reason.
    override fun observeDailyNote(dateString: String): Flow<NoteContent?> =
        dailyNoteCache.map { it[dateString] }

    override suspend fun getDailyNote(dateString: String): NoteContent? =
        withContext(Dispatchers.IO) {
            // Return from cache if available — avoids a DB round-trip on repeat reads
            // and ensures callers always see the most recently written content.
            dailyNoteCache.value[dateString]?.let { return@withContext it }

            val metadata = noteDao.getDailyNoteMetadata(dateString) ?: return@withContext null
            val entities = blockDao.getAllBlocksForNoteIncludingDeleted(metadata.noteId)
            if (entities.isEmpty()) return@withContext null

            val blocks = entities.mapNotNull { entity -> decodeBlockOrNull(entity.blockDataJson) }
            val content = NoteContent(blocks = blocks)

            // Populate the cache so subsequent reads and observers get this value.
            dailyNoteCache.update { it + (dateString to content) }
            content
        }

    override fun refreshDailyNoteCache(dateString: String, content: NoteContent) {
        dailyNoteCache.update { it + (dateString to content) }
    }

    private fun savedContentOmitsTombstones(dateString: String) = dateString == "global_pinned"

    private fun cacheSavedContent(noteId: String, dailyDateString: String?, content: NoteContent) {
        if (dailyDateString != null && savedContentOmitsTombstones(dailyDateString)) {
            noteContentCache.update { it - noteId }
            dailyNoteCache.update { it - dailyDateString }
            return
        }

        noteContentCache.update { it + (noteId to content) }
        if (dailyDateString != null) dailyNoteCache.update { it + (dailyDateString to content) }
    }

    override suspend fun getSavedDailyNoteDates(): List<String> =
        withContext(Dispatchers.IO) {
            noteDao.getAllDailyNoteMetadata()
                .filter { it.trashedAt == null }
                .mapNotNull { it.dateString }
                .filter { it != "global_pinned" && it.isNotBlank() }
                .distinct()
        }

    override suspend fun dedupeDuplicateDailyNotes(): Int =
        withContext(Dispatchers.IO) {
            val duplicateGroups = noteDao.getAllDailyNoteMetadata()
                .filter { it.dateString != null }
                .groupBy { it.dateString }
                .filterValues { it.size > 1 }

            var removedCount = 0
            for ((dateString, duplicates) in duplicateGroups) {
                if (dateString == null) continue
                removedCount += mergeDuplicateDailyNoteGroup(dateString, duplicates)
            }
            removedCount
        }

    private suspend fun mergeDuplicateDailyNoteGroup(dateString: String, duplicates: List<NoteMetadataEntity>): Int {
        val winner = duplicates.sortedWith(
            compareByDescending<NoteMetadataEntity> { it.updatedAt }.thenByDescending { it.noteId }
        ).first()
        val losers = duplicates.filter { it.noteId != winner.noteId }

        val mergedBlocksByBlockId = LinkedHashMap<String, NoteBlockEntity>()
        for (row in duplicates) {
            blockDao.getAllBlocksForNoteIncludingDeleted(row.noteId).forEach { block ->
                val current = mergedBlocksByBlockId[block.blockId]
                if (current == null || block.updatedAt >= current.updatedAt) {
                    mergedBlocksByBlockId[block.blockId] = block
                }
            }
        }
        val mergedBlocks = mergedBlocksByBlockId.values.map { it.copy(noteId = winner.noteId) }
        val decodedBlocks = mergedBlocks.mapNotNull { entity -> decodeBlockOrNull(entity.blockDataJson) }

        noteDao.insertOrUpdateMetadata(winner.copy(filePath = ""))
        blockDao.insertOrUpdateBlocks(mergedBlocks)
        syncImageBlocks(winner.noteId, decodedBlocks, TaskSource.DAILY, winner.createdAt)
        syncDocumentBlocks(winner.noteId, decodedBlocks, TaskSource.DAILY, winner.createdAt)
        syncBookmarkBlocks(winner.noteId, decodedBlocks, TaskSource.DAILY, winner.updatedAt)

        losers.forEach { loser ->
            imageBlockDao.deleteByNoteId(loser.noteId)
            documentBlockDao.deleteByNoteId(loser.noteId)
            bookmarkBlockDao.deleteByNoteId(loser.noteId)
            noteDao.deleteNoteMetadata(loser.noteId)
        }

        dailyNoteCache.update { it + (dateString to NoteContent(blocks = decodedBlocks)) }

        return losers.size
    }

    override suspend fun getDailyNoteMetadata(dateString: String): NoteMetadataEntity? =
        withContext(Dispatchers.IO) {
            noteDao.getDailyNoteMetadata(dateString)
        }

    // Upserts only the blocks that actually changed since the last save. note_blocks is keyed by
    // (noteId, blockId), so a block that moved here from another note simply gets its own row -
    // it can't collide with or reclaim the row that block still has under its previous note.
    private suspend fun upsertChangedBlocks(noteId: String, content: NoteContent): List<NoteBlock> {
        val currentEntities = blockDao.getAllBlocksForNoteIncludingDeleted(noteId).associateBy { it.blockId }
        val presentIds = content.blocks.mapTo(HashSet()) { it.id }
        val now = System.currentTimeMillis()
        val entitiesToUpsert = mutableListOf<NoteBlockEntity>()

        content.blocks.forEachIndexed { index, block ->
            val existingBlock = currentEntities[block.id]
            if (existingBlock == null || existingBlock.updatedAt != block.updatedAt || existingBlock.displayOrder != index || existingBlock.isDeleted != block.isDeleted) {
                entitiesToUpsert.add(
                    NoteBlockEntity(
                        blockId = block.id,
                        noteId = noteId,
                        displayOrder = index,
                        blockDataJson = jsonFormat.encodeToString(NoteBlock.serializer(), block),
                        updatedAt = block.updatedAt,
                        isDeleted = block.isDeleted
                    )
                )
            }
        }

        // A block removed from this note (deleted, or moved to another note) still has its old row
        // here. Tombstone it instead of hard-deleting so the change still converges through sync,
        // and re-encode the JSON because the load path reads isDeleted from there, not the row flag.
        for (entity in currentEntities.values) {
            if (entity.isDeleted || entity.blockId in presentIds) continue
            val tombstonedJson = markJsonDeleted(entity.blockDataJson, now) ?: continue
            entitiesToUpsert.add(
                entity.copy(blockDataJson = tombstonedJson, isDeleted = true, updatedAt = now)
            )
        }

        if (entitiesToUpsert.isNotEmpty()) {
            blockDao.insertOrUpdateBlocks(entitiesToUpsert)
        }

        return SavedNoteContent.blocksTheNoteHoldsAfterSaving(
            blocksBeingSaved = content.blocks,
            blocksAlreadyStored = currentEntities.values.mapNotNull { decodeBlockOrNull(it.blockDataJson) },
            removedAt = now
        )
    }

    private fun decodeBlockOrNull(blockDataJson: String): NoteBlock? =
        try {
            jsonFormat.decodeFromString<NoteBlock>(blockDataJson)
        } catch (_: Exception) {
            null
        }

    // Flips isDeleted on a serialised block without a typed copy for every NoteBlock subtype.
// The "type" discriminator and all other fields are preserved, so decode still resolves the
// right subtype. Returns null on unparseable JSON - the load-side decode already drops those.
    private fun markJsonDeleted(blockJson: String, now: Long): String? =
        try {
            val obj = jsonFormat.parseToJsonElement(blockJson).jsonObject
            buildJsonObject {
                obj.forEach { (k, v) -> if (k != "isDeleted" && k != "updatedAt") put(k, v) }
                put("isDeleted", true)
                put("updatedAt", now)
            }.toString()
        } catch (_: Exception) { null }

    override suspend fun saveDailyNote(dateString: String, content: NoteContent, updatedAt: Long?, remoteMeta: NoteMetadataEntity?) =
        withContext(Dispatchers.IO) {

            val existing = noteDao.getDailyNoteMetadata(dateString)
            val noteId = existing?.noteId ?: remoteMeta?.noteId ?: UUID.randomUUID().toString()

            val previewText = content.blocks.joinToString(" ") { block ->
                when (block) {
                    is TextBlock -> block.text
                    is HeadingBlock -> block.text
                    is CheckboxBlock -> block.text
                    is BulletedListBlock -> block.text
                    is NumberedListBlock -> block.text
                    is ToggleBlock -> block.text
                    is CodeBlock -> block.code
                    else -> ""
                }
            }.trim().take(120)

            val baseMeta = remoteMeta ?: existing

            val metadata = NoteMetadataEntity(
                noteId = noteId,
                title = "Daily: $dateString",
                folderId = baseMeta?.folderId,
                isDaily = true,
                dateString = dateString,
                createdAt = baseMeta?.createdAt ?: System.currentTimeMillis(),
                updatedAt = updatedAt ?: System.currentTimeMillis(),
                filePath = "",
                snippet = previewText,
                isFavorite = baseMeta?.isFavorite ?: false,
                coverImagePath = baseMeta?.coverImagePath,
                trashedAt = baseMeta?.trashedAt
            )
            noteDao.insertOrUpdateMetadata(metadata)

            cacheSavedContent(noteId, dateString, NoteContent(blocks = upsertChangedBlocks(noteId, content)))

            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestNoteRefresh(noteId)

            // Sync projection tables — these are flat Room tables that allow
            // TasksScreen, ImagesScreen, DocumentsScreen, and BookmarksScreen
            // to query their content without scanning every note's block list.
            if (dateString != "global_pinned") {
                syncCalendarTasks(
                    noteId = dateString,
                    blocks = content.blocks,
                    sourceType = TaskSource.DAILY,
                    dailyDateString = dateString
                )
                syncImageBlocks(
                    noteId        = noteId,
                    blocks        = content.blocks,
                    sourceType    = TaskSource.DAILY,
                    noteCreatedAt = metadata.createdAt
                )
                syncDocumentBlocks(
                    noteId        = noteId,
                    blocks        = content.blocks,
                    sourceType    = TaskSource.DAILY,
                    noteCreatedAt = metadata.createdAt
                )
                syncBookmarkBlocks(
                    noteId        = noteId,
                    blocks        = content.blocks,
                    sourceType    = TaskSource.DAILY,
                    noteUpdatedAt = updatedAt ?: System.currentTimeMillis()
                )
            }
        }

    override fun searchDailyNotes(query: String): Flow<List<NoteMetadataEntity>> = noteDao.searchDailyNotes(query)

    // Cross-note search. Runs the two DAO queries added for this feature:
    // 1) a title/snippet LIKE match (cheap, covers most everyday searches), and
    // 2) a content LIKE match over the raw block JSON, which only tells us *which* notes
    //    matched - so for those we still have to decode blocks to find the actual matching
    //    text to show/highlight. That decode only happens for notes not already found by (1),
    //    keeping the expensive part proportional to result size, not corpus size.
    override suspend fun searchNotes(query: String): List<NoteSearchResult> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()

            val titleOrSnippetMatches = noteDao.searchNotesByTitleOrSnippet(query).first()
            val matchedIds = titleOrSnippetMatches.mapTo(mutableSetOf()) { it.noteId }

            val contentMatchIds = blockDao.findNoteIdsMatchingContent(query)
                .filterNot { it in matchedIds }

            val contentMatches = if (contentMatchIds.isEmpty()) {
                emptyList()
            } else {
                noteDao.getNotesByIds(contentMatchIds).mapNotNull { metadata ->
                    val matchedText = findMatchingBlockText(metadata.noteId, query) ?: return@mapNotNull null
                    NoteSearchResult(note = metadata, matchedText = matchedText)
                }
            }

            val metadataResults = titleOrSnippetMatches.map { metadata ->
                NoteSearchResult(
                    note = metadata,
                    matchedText = metadata.snippet.ifBlank { metadata.title }
                )
            }

            (metadataResults + contentMatches).sortedByDescending { it.note.updatedAt }
        }

    // Decodes a single note's blocks and returns the flattened text of the first
    // non-deleted block whose text contains the query (case-insensitive).
    private suspend fun findMatchingBlockText(noteId: String, query: String): String? {
        val entities = blockDao.getAllBlocksForNoteIncludingDeleted(noteId)
        val lowerQuery = query.lowercase()
        for (entity in entities) {
            val block = decodeBlockOrNull(entity.blockDataJson) ?: continue
            if (block.isDeleted) continue
            val text = flattenBlockText(block) ?: continue
            if (text.lowercase().contains(lowerQuery)) return text
        }
        return null
    }

    // Reduces any block type down to its searchable plain text, mirroring the
    // per-block-type switch already used for the (unused) filter in HomeViewModel.notes.
    private fun flattenBlockText(block: NoteBlock): String? = when (block) {
        is TextBlock -> block.text
        is HeadingBlock -> block.text
        is CheckboxBlock -> block.text
        is BulletedListBlock -> block.text
        is NumberedListBlock -> block.text
        is ToggleBlock -> block.text
        is CodeBlock -> block.code
        is BookmarkBlock -> block.title?.takeIf { it.isNotBlank() } ?: block.url
        is DocumentBlock -> block.fileName
        else -> null
    }.let { text -> text?.takeIf { it.isNotBlank() } }

    override fun getAllNotes(): Flow<List<NoteMetadataEntity>> = noteDao.getAllNotes()

    override fun getNotesInFolder(folderId: String): Flow<List<NoteMetadataEntity>> = noteDao.getNotesInFolder(folderId)

    override fun getNoteCountsByFolder(): Flow<Map<String, Int>> =
        noteDao.getNoteCountsByFolder().map { rows -> rows.associate { it.folderId to it.noteCount } }

    override fun getFavoriteNotes(): Flow<List<NoteMetadataEntity>> = noteDao.getFavoriteNotes()

    override fun getTrashedNotes(): Flow<List<NoteMetadataEntity>> = noteDao.getTrashedNotes()

    override suspend fun getNoteContent(noteId: String): NoteContent? =
        withContext(Dispatchers.IO) {
            // Return from cache if available — same reasoning as getDailyNote.
            noteContentCache.value[noteId]?.let { return@withContext it }

            val entities = blockDao.getAllBlocksForNoteIncludingDeleted(noteId)
            if (entities.isEmpty()) return@withContext null

            val blocks = entities.mapNotNull { entity -> decodeBlockOrNull(entity.blockDataJson) }
            val content = NoteContent(blocks = blocks)

            // Populate cache on first DB read so future reads and observers are live.
            noteContentCache.update { it + (noteId to content) }
            content
        }

    override fun refreshNoteContentCache(noteId: String, content: NoteContent) {
        noteContentCache.update { it + (noteId to content) }
    }

    override suspend fun refreshProjectionsForNote(metadata: NoteMetadataEntity, blocks: List<NoteBlock>) =
        withContext(Dispatchers.IO) {
            if (metadata.isDaily) {
                val dateString = metadata.dateString
                if (dateString != null && dateString != "global_pinned") {
                    syncCalendarTasks(
                        noteId = dateString,
                        blocks = blocks,
                        sourceType = TaskSource.DAILY,
                        dailyDateString = dateString
                    )
                    syncImageBlocks(noteId = metadata.noteId, blocks = blocks, sourceType = TaskSource.DAILY, noteCreatedAt = metadata.createdAt)
                    syncDocumentBlocks(noteId = metadata.noteId, blocks = blocks, sourceType = TaskSource.DAILY, noteCreatedAt = metadata.createdAt)
                    syncBookmarkBlocks(noteId = metadata.noteId, blocks = blocks, sourceType = TaskSource.DAILY, noteUpdatedAt = metadata.updatedAt)
                }
            } else {
                syncCalendarTasks(noteId = metadata.noteId, blocks = blocks, sourceType = TaskSource.NOTE, dailyDateString = null)
                syncImageBlocks(noteId = metadata.noteId, blocks = blocks, sourceType = TaskSource.NOTE, noteCreatedAt = metadata.createdAt)
                syncDocumentBlocks(noteId = metadata.noteId, blocks = blocks, sourceType = TaskSource.NOTE, noteCreatedAt = metadata.createdAt)
                syncBookmarkBlocks(noteId = metadata.noteId, blocks = blocks, sourceType = TaskSource.NOTE, noteUpdatedAt = metadata.updatedAt)
            }
        }

    override suspend fun saveNote(metadata: NoteMetadataEntity, content: NoteContent, stampUpdatedAt: Boolean) =
        withContext(Dispatchers.IO) {

            val stampedMetadata = if (stampUpdatedAt) metadata.copy(updatedAt = System.currentTimeMillis()) else metadata
            noteDao.insertOrUpdateMetadata(stampedMetadata.copy(filePath = ""))

            val persistedBlocks = upsertChangedBlocks(metadata.noteId, content)

            cacheSavedContent(
                metadata.noteId,
                metadata.dateString?.takeIf { metadata.isDaily },
                NoteContent(blocks = persistedBlocks)
            )

            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestNoteRefresh(metadata.noteId)
            refreshProjectionsForNote(stampedMetadata, content.blocks)
        }

    override suspend fun deleteNote(noteId: String, filePath: String) {
        withContext(Dispatchers.IO) {
            // Captured before the row is gone - without this, another device that hasn't seen the
            // deletion yet has no way to tell "permanently deleted" apart from "never existed here",
            // so its own next manifest upload would silently resurrect the note everywhere.
            val metadata = noteDao.getNoteById(noteId)
            hardDeleteLocalNote(noteId)
            selfHostDeletedNoteDao.upsertTombstone(
                SelfHostDeletedNoteEntity(
                    noteId = noteId,
                    isDaily = metadata?.isDaily ?: false,
                    dateString = metadata?.dateString,
                    deletedAt = System.currentTimeMillis()
                )
            )
            AutoSyncTrigger.requestSync()
        }
    }

    override suspend fun hardDeleteLocalNote(noteId: String) {
        withContext(Dispatchers.IO) {
            // Evict from cache so no observer gets a stale emission after deletion,
            // and so a future note created with the same ID starts with a clean slate.
            noteContentCache.update { it - noteId }
            noteDao.deleteNoteMetadata(noteId)
            blockDao.deleteAllBlocksForNote(noteId)
            noteIndexer.deleteNoteFromIndex(noteId)
            VaultMirrorTrigger.requestNoteRefresh(noteId)
        }
    }

    override suspend fun getNoteTombstonesModifiedSince(timestamp: Long): List<SelfHostDeletedNoteEntity> =
        selfHostDeletedNoteDao.getTombstonesModifiedSince(timestamp)

    override suspend fun getNoteTombstone(entityId: String): SelfHostDeletedNoteEntity? =
        selfHostDeletedNoteDao.getTombstoneByNoteId(entityId) ?: selfHostDeletedNoteDao.getTombstoneByDateString(entityId)

    override suspend fun applyRemoteNoteTombstone(noteId: String, isDaily: Boolean, dateString: String?, deletedAt: Long) =
        withContext(Dispatchers.IO) {
            val local = if (isDaily && dateString != null) {
                noteDao.getDailyNoteMetadata(dateString)
            } else {
                noteDao.getNoteById(noteId)
            }
            // A local edit strictly newer than the tombstone means someone is genuinely still using
            // this note elsewhere - don't destroy a live edit, let LWW push it back out instead.
            if (local != null && local.updatedAt <= deletedAt) {
                hardDeleteLocalNote(local.noteId)
            }
            selfHostDeletedNoteDao.upsertTombstone(
                SelfHostDeletedNoteEntity(
                    noteId = noteId,
                    isDaily = isDaily,
                    dateString = dateString,
                    deletedAt = deletedAt
                )
            )
        }

    override suspend fun getNoteById(noteId: String): NoteMetadataEntity? = noteDao.getNoteById(noteId)

    override fun getAllFolders(): Flow<List<FolderEntity>> = folderDao.getAllFolders()

    override suspend fun getFoldersModifiedSince(timestamp: Long): List<FolderEntity> =
        folderDao.getFoldersModifiedSince(timestamp)

    override suspend fun insertFolder(folder: FolderEntity) =
        withContext(Dispatchers.IO) {
            folderDao.insertFolder(folder.copy(updatedAt = System.currentTimeMillis()))
            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestFullRefresh()
        }

    // Strictly greater, not >= - see applyRemoteCategory's identical reasoning.
    override suspend fun applyRemoteFolder(folder: FolderEntity) =
        withContext(Dispatchers.IO) {
            val local = folderDao.getFolderById(folder.folderId)
            if (local == null || folder.updatedAt > local.updatedAt) {
                folderDao.insertFolder(folder)
                VaultMirrorTrigger.requestFullRefresh()
            }
        }

    override suspend fun deleteFolder(folderId: String) =
        withContext(Dispatchers.IO) {
            folderDao.markFolderDeleted(folderId, System.currentTimeMillis())
            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestFullRefresh()
        }

    override suspend fun restoreNote(noteId: String) =
        withContext(Dispatchers.IO) {
            noteDao.restoreNote(noteId, System.currentTimeMillis())
            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestNoteRefresh(noteId)
        }

    override suspend fun cleanupOldTrashedNotes() = withContext(Dispatchers.IO) {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val cutoffTime = System.currentTimeMillis() - thirtyDaysInMillis
        val oldNotes = noteDao.getOldTrashedNotes(cutoffTime)
        var deletedAny = false
        for (note in oldNotes) {
            deleteNote(note.noteId, note.filePath)
            deletedAny = true
        }

        if (deletedAny) {
            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestFullRefresh()
        }
    }

    override fun getAllTags(): Flow<List<TagEntity>> = tagDao.getAllTags()

    override suspend fun getTagsModifiedSince(timestamp: Long): List<TagEntity> =
        tagDao.getTagsModifiedSince(timestamp)

    override suspend fun insertOrUpdateTag(tagId: String, name: String, colorHex: String) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val existing = tagDao.getTagsModifiedSince(0L).firstOrNull { it.tagId == tagId }
            tagDao.insertOrUpdateTag(
                TagEntity(
                    tagId = tagId,
                    name = name,
                    colorHex = colorHex,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    isDeleted = false
                )
            )
            AutoSyncTrigger.requestSync()
        }

    // Strictly greater, not >= - see applyRemoteCategory's identical reasoning.
    override suspend fun applyRemoteTag(tag: TagEntity) =
        withContext(Dispatchers.IO) {
            val local = tagDao.getTagById(tag.tagId)
            if (local == null || tag.updatedAt > local.updatedAt) {
                tagDao.insertOrUpdateTag(tag)
            }
        }

    override suspend fun deleteTag(tagId: String) =
        withContext(Dispatchers.IO) {
            tagDao.markTagDeleted(tagId, System.currentTimeMillis())
            AutoSyncTrigger.requestSync()
        }

    override fun getAllCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    override suspend fun insertOrUpdateCategory(categoryId: String, name: String, colorHex: String) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            // Preserve the original createdAt across edits so renames don't look like new
            // categories - only updatedAt should move, since that's what sync filters on.
            val existing = categoryDao.getCategoryById(categoryId)
            categoryDao.insertOrUpdateCategory(
                CategoryEntity(
                    categoryId = categoryId,
                    name = name,
                    colorHex = colorHex,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    isDeleted = false
                )
            )
            AutoSyncTrigger.requestSync()
        }

    override suspend fun deleteCategory(categoryId: String) =
        withContext(Dispatchers.IO) {
            categoryDao.markCategoryDeleted(categoryId, System.currentTimeMillis())
            AutoSyncTrigger.requestSync()
        }

    override suspend fun getCategoriesModifiedSince(timestamp: Long): List<CategoryEntity> =
        categoryDao.getCategoriesModifiedSince(timestamp)

    // Last-write-wins against whatever's already local, mirroring how note/folder/tag sync
    // resolves conflicts elsewhere in this file.
    override suspend fun applyRemoteCategory(category: CategoryEntity) =
        withContext(Dispatchers.IO) {
            val local = categoryDao.getCategoryById(category.categoryId)
            // Strictly greater, not >= - an exact-millisecond tie must not let an incoming remote
            // write silently overwrite a local edit that landed at the same instant.
            if (local == null || category.updatedAt > local.updatedAt) {
                categoryDao.insertOrUpdateCategory(category)
            }
        }

    override fun getAllDatabaseTemplates(): Flow<List<DatabaseTemplateEntity>> = databaseTemplateDao.getAllTemplates()

    override suspend fun insertDatabaseTemplate(template: DatabaseTemplateEntity) =
        withContext(Dispatchers.IO) {
            databaseTemplateDao.insertTemplate(template)
        }

    override suspend fun deleteDatabaseTemplate(templateId: String) =
        withContext(Dispatchers.IO) {
            databaseTemplateDao.deleteTemplate(templateId)
        }

    override fun getAllTemplates(): Flow<List<NoteMetadataEntity>> = noteDao.getAllTemplates()

    override suspend fun deleteTemplate(templateId: String) =
        withContext(Dispatchers.IO) {
            val existing = noteDao.getNoteById(templateId) ?: return@withContext
            val now = System.currentTimeMillis()
            noteDao.insertOrUpdateMetadata(existing.copy(trashedAt = now, updatedAt = now))
            AutoSyncTrigger.requestSync()
        }

    override suspend fun getNotesModifiedSince(timestamp: Long): List<NoteMetadataEntity> {
        return noteDao.getNotesModifiedSince(timestamp)
    }

    override suspend fun indexNote(metadata: NoteMetadataEntity, content: NoteContent) =
        withContext(Dispatchers.IO) {
            try {
                noteIndexer.indexNote(metadata, content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    override suspend fun indexDailyNote(dateString: String, content: NoteContent, metadata: NoteMetadataEntity) =
        withContext(Dispatchers.IO) {
            try {
                noteIndexer.indexNote(metadata, content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    private fun extractActiveCheckboxes(blocks: List<NoteBlock>): List<CheckboxBlock> =
        blocks.filterIsInstance<CheckboxBlock>().filter { !it.isDeleted }

    // Rebuilds the CalendarTaskEntity projection table for a given note on every save.
    // TasksScreen and the calendar strip both read from this table, so they always
    // reflect the latest checkbox state without scanning raw block JSON.
    private suspend fun syncCalendarTasks(
        noteId: String,
        blocks: List<NoteBlock>,
        sourceType: TaskSource,
        dailyDateString: String? = null
    ) {
        calendarTaskDao.deleteTasksByNoteId(noteId)
        val allCheckboxes = extractActiveCheckboxes(blocks)
        val tasksToInsert = allCheckboxes.map { block ->
            val targetDate = when (sourceType) {
                TaskSource.DAILY -> dailyDateString ?: ""
                TaskSource.NOTE -> {
                    if (block.reminderTimestamp != null) {
                        val instant = Instant.fromEpochMilliseconds(block.reminderTimestamp)
                        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                        val monthStr = dt.month.number.toString().padStart(2, '0')
                        val dayStr = dt.day.toString().padStart(2, '0')
                        "${dt.year}-${monthStr}-${dayStr}"
                    } else ""
                }
            }

            val (recurrenceFrequency, recurrenceInterval, recurrenceDaysOfWeek) =
                block.recurrenceRule?.toEntityColumns() ?: Triple(null, 1, null)

            CalendarTaskEntity(
                blockId = block.id,
                noteId = noteId,
                text = block.text,
                isChecked = block.isChecked,
                targetDate = targetDate,
                reminderTimestamp = block.reminderTimestamp,
                sourceType = sourceType,
                categoryId = block.categoryId,
                durationMinutes = block.durationMinutes,
                url = block.url,
                description = block.description,
                recurrenceFrequency = recurrenceFrequency,
                recurrenceInterval = recurrenceInterval,
                recurrenceDaysOfWeek = recurrenceDaysOfWeek,
                recurrenceUntil = block.recurrenceRule?.untilDateString
            )
        }

        if (tasksToInsert.isNotEmpty()) {
            calendarTaskDao.upsertTasks(tasksToInsert)
        }
    }

    // Recurring rows in calendar_tasks store only their anchor occurrence - this expands them
    // into one synthetic row per occurrence date that falls in [rangeStart, rangeEnd], applying
    // any per-occurrence exception (cancellation, completion, or field override) on top. Both
    // Calendar and the Daily screen's virtual-occurrence materialization read through this.
    private fun expandOccurrences(
        tasks: List<CalendarTaskEntity>,
        exceptions: List<CalendarEventExceptionEntity>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate
    ): List<CalendarTaskEntity> {
        val exceptionsByKey = exceptions.associateBy { it.blockId to it.occurrenceDate }
        val result = mutableListOf<CalendarTaskEntity>()

        for (task in tasks) {
            val rule = task.toRecurrenceRule()
            if (rule == null) {
                val targetDate = task.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (targetDate != null && targetDate in rangeStart..rangeEnd) result.add(task)
                continue
            }

            val anchor = task.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            val occurrenceDates = RecurrenceEngine.occurrenceDatesInRange(rule, anchor, rangeStart, rangeEnd)

            for (occurrenceDate in occurrenceDates) {
                val occurrenceDateString = occurrenceDate.toString()
                val exception = exceptionsByKey[task.blockId to occurrenceDateString]
                if (exception?.isCancelled == true) continue

                val occurrenceTimestamp = exception?.overrideTimestamp
                    ?: task.reminderTimestamp?.let { retargetTimestampToDate(it, occurrenceDate) }

                result.add(
                    task.copy(
                        targetDate = occurrenceDateString,
                        reminderTimestamp = occurrenceTimestamp,
                        isChecked = exception?.isChecked ?: false,
                        text = exception?.overrideText ?: task.text,
                        categoryId = exception?.overrideCategoryId ?: task.categoryId,
                        durationMinutes = exception?.overrideDurationMinutes ?: task.durationMinutes,
                        url = exception?.overrideUrl ?: task.url,
                        description = exception?.overrideDescription ?: task.description
                    )
                )
            }
        }
        return result
    }

    private fun retargetTimestampToDate(originalTimestamp: Long, newDate: LocalDate): Long {
        val originalDateTime = Instant.fromEpochMilliseconds(originalTimestamp).toLocalDateTime(TimeZone.currentSystemDefault())
        val retargeted = LocalDateTime(
            newDate.year, newDate.month.number, newDate.day,
            originalDateTime.hour, originalDateTime.minute, originalDateTime.second
        )
        return retargeted.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }

    override fun getCalendarTasksForMonth(yearMonth: String): Flow<List<CalendarTaskEntity>> {
        val (year, month) = yearMonth.split("-").map { it.toInt() }
        val monthStart = LocalDate(year, month, 1)
        val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return combine(calendarTaskDao.getAllTasksFlow(), calendarEventExceptionDao.getAllExceptionsFlow()) { tasks, exceptions ->
            expandOccurrences(tasks, exceptions, monthStart, monthEnd)
        }
    }

    override fun getCalendarTasksForDate(dateString: String): Flow<List<CalendarTaskEntity>> {
        val date = LocalDate.parse(dateString)
        return combine(calendarTaskDao.getAllTasksFlow(), calendarEventExceptionDao.getAllExceptionsFlow()) { tasks, exceptions ->
            expandOccurrences(tasks, exceptions, date, date)
        }
    }

    override fun getAllTasksFlow(): Flow<List<CalendarTaskEntity>> {
        return calendarTaskDao.getAllTasksFlow()
    }

    override suspend fun upsertOccurrenceCompletion(blockId: String, occurrenceDate: String, isChecked: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = calendarEventExceptionDao.getAllExceptionsFlow().first()
                .firstOrNull { it.blockId == blockId && it.occurrenceDate == occurrenceDate }
            calendarEventExceptionDao.upsert(
                (existing ?: CalendarEventExceptionEntity(blockId = blockId, occurrenceDate = occurrenceDate)).copy(
                    isChecked = isChecked,
                    completedAt = if (isChecked) System.currentTimeMillis() else null
                )
            )
        }

    override suspend fun toggleTaskCompletion(blockId: String, isChecked: Boolean) =
        withContext(Dispatchers.IO) {
            SyncCoordinator.mutex.withLock {
                val task = calendarTaskDao.getTaskById(blockId) ?: return@withLock
                val blocks = loadOwnerBlocks(task)
                val now = System.currentTimeMillis()
                val updated = blocks.map { block ->
                    if (block.id == blockId && block is CheckboxBlock) {
                        block.copy(isChecked = isChecked, completedAt = if (isChecked) now else null, updatedAt = now)
                    } else block
                }
                saveOwnerBlocks(task, updated)
            }
        }

    override suspend fun updateTaskText(blockId: String, text: String) =
        withContext(Dispatchers.IO) {
            SyncCoordinator.mutex.withLock {
                val task = calendarTaskDao.getTaskById(blockId) ?: return@withLock
                val blocks = loadOwnerBlocks(task)
                val now = System.currentTimeMillis()
                val updated = blocks.map { block ->
                    if (block.id == blockId && block is CheckboxBlock) block.copy(text = text, updatedAt = now) else block
                }
                saveOwnerBlocks(task, updated)
            }
        }

    private suspend fun loadOwnerBlocks(task: CalendarTaskEntity): List<NoteBlock> = when (task.sourceType) {
        TaskSource.DAILY -> getDailyNote(task.noteId)?.blocks ?: emptyList()
        TaskSource.NOTE -> getNoteContent(task.noteId)?.blocks ?: emptyList()
    }

    private suspend fun saveOwnerBlocks(task: CalendarTaskEntity, blocks: List<NoteBlock>) {
        when (task.sourceType) {
            TaskSource.DAILY -> saveDailyNote(task.noteId, NoteContent(blocks = blocks))
            TaskSource.NOTE -> {
                val meta = getNoteById(task.noteId) ?: return
                saveNote(meta, NoteContent(blocks = blocks))
            }
        }
    }

    override suspend fun applyRecurrenceScopedDelete(blockId: String, occurrenceDate: String, scope: RecurrenceEditScope) =
        withContext(Dispatchers.IO) {
            SyncCoordinator.mutex.withLock {
                val task = calendarTaskDao.getTaskById(blockId) ?: return@withLock
                val rule = task.toRecurrenceRule()
                if (rule == null || scope == RecurrenceEditScope.ALL_EVENTS) {
                    deleteEntireSeries(task)
                    return@withLock
                }

                val anchor = task.targetDate?.let { LocalDate.parse(it) } ?: return@withLock
                val d = LocalDate.parse(occurrenceDate)

                when (scope) {
                    RecurrenceEditScope.THIS_EVENT -> {
                        upsertException(blockId, occurrenceDate) { it.copy(isCancelled = true) }
                    }
                    RecurrenceEditScope.ALL_FUTURE_EVENTS -> {
                        if (d == anchor) {
                            deleteEntireSeries(task)
                        } else {
                            val previous = RecurrenceEngine.previousOccurrence(rule, anchor, before = d)
                            truncateSeriesUntil(task, previous?.toString())
                            calendarEventExceptionDao.deleteExceptionsFrom(blockId, occurrenceDate)
                        }
                    }
                    RecurrenceEditScope.ALL_PAST_EVENTS -> {
                        val next = RecurrenceEngine.nextOccurrence(rule, anchor, after = d)
                        if (next == null) {
                            deleteEntireSeries(task)
                        } else {
                            moveSeriesAnchor(task, next)
                            calendarEventExceptionDao.deleteExceptionsUpTo(blockId, occurrenceDate)
                        }
                    }
                    RecurrenceEditScope.ALL_EVENTS -> Unit
                }
            }
        }

    override suspend fun applyRecurrenceScopedEdit(
        blockId: String,
        occurrenceDate: String,
        scope: RecurrenceEditScope,
        text: String,
        timestamp: Long,
        categoryId: String?,
        durationMinutes: Int,
        url: String?,
        description: String?
    ) = withContext(Dispatchers.IO) {
        SyncCoordinator.mutex.withLock {
            val task = calendarTaskDao.getTaskById(blockId) ?: return@withLock
            val rule = task.toRecurrenceRule()
            if (rule == null || scope == RecurrenceEditScope.ALL_EVENTS) {
                editEntireSeries(task, text, timestamp, categoryId, durationMinutes, url, description)
                return@withLock
            }

            val anchor = task.targetDate?.let { LocalDate.parse(it) } ?: return@withLock
            val d = LocalDate.parse(occurrenceDate)

            when (scope) {
                RecurrenceEditScope.THIS_EVENT -> {
                    upsertException(blockId, occurrenceDate) {
                        it.copy(
                            overrideText = text,
                            overrideTimestamp = timestamp,
                            overrideCategoryId = categoryId,
                            overrideDurationMinutes = durationMinutes,
                            overrideUrl = url,
                            overrideDescription = description
                        )
                    }
                }
                RecurrenceEditScope.ALL_FUTURE_EVENTS -> {
                    if (d == anchor) {
                        editEntireSeries(task, text, timestamp, categoryId, durationMinutes, url, description)
                    } else {
                        val previous = RecurrenceEngine.previousOccurrence(rule, anchor, before = d)
                        truncateSeriesUntil(task, previous?.toString())
                        val newBlockId = createSplitSeries(
                            task, rule, anchor = d, until = rule.untilDateString,
                            text = text, timestamp = timestamp, categoryId = categoryId,
                            durationMinutes = durationMinutes, url = url, description = description
                        )
                        calendarEventExceptionDao.rekeyExceptionsFrom(blockId, newBlockId, occurrenceDate)
                    }
                }
                RecurrenceEditScope.ALL_PAST_EVENTS -> {
                    val next = RecurrenceEngine.nextOccurrence(rule, anchor, after = d)
                    val newBlockId = createSplitSeries(
                        task, rule, anchor = anchor, until = occurrenceDate,
                        text = text, timestamp = timestamp, categoryId = categoryId,
                        durationMinutes = durationMinutes, url = url, description = description
                    )
                    calendarEventExceptionDao.rekeyExceptionsUpTo(blockId, newBlockId, occurrenceDate)
                    if (next == null) {
                        deleteEntireSeries(task)
                    } else {
                        moveSeriesAnchor(task, next)
                    }
                }
                RecurrenceEditScope.ALL_EVENTS -> Unit
            }
        }
    }

    private suspend fun upsertException(
        blockId: String,
        occurrenceDate: String,
        transform: (CalendarEventExceptionEntity) -> CalendarEventExceptionEntity
    ) {
        val existing = calendarEventExceptionDao.getAllExceptionsFlow().first()
            .firstOrNull { it.blockId == blockId && it.occurrenceDate == occurrenceDate }
        val base = existing ?: CalendarEventExceptionEntity(blockId = blockId, occurrenceDate = occurrenceDate)
        calendarEventExceptionDao.upsert(transform(base))
    }

    private suspend fun deleteEntireSeries(task: CalendarTaskEntity) {
        val blocks = loadOwnerBlocks(task)
        saveOwnerBlocks(task, blocks.map { if (it.id == task.blockId) it.markDeleted() else it })
        calendarEventExceptionDao.deleteExceptionsForBlock(task.blockId)
    }

    private suspend fun editEntireSeries(
        task: CalendarTaskEntity, text: String, timestamp: Long, categoryId: String?,
        durationMinutes: Int, url: String?, description: String?
    ) {
        val blocks = loadOwnerBlocks(task)
        val now = System.currentTimeMillis()
        val updated = blocks.map { block ->
            if (block.id == task.blockId && block is CheckboxBlock) {
                block.copy(
                    text = text, reminderTimestamp = timestamp, categoryId = categoryId,
                    durationMinutes = durationMinutes, url = url, description = description, updatedAt = now
                )
            } else block
        }
        saveOwnerBlocks(task, updated)
    }

    private suspend fun truncateSeriesUntil(task: CalendarTaskEntity, untilDateString: String?) {
        val blocks = loadOwnerBlocks(task)
        val now = System.currentTimeMillis()
        val updated = blocks.map { block ->
            if (block.id == task.blockId && block is CheckboxBlock && block.recurrenceRule != null) {
                block.copy(recurrenceRule = block.recurrenceRule.copy(untilDateString = untilDateString), updatedAt = now)
            } else block
        }
        saveOwnerBlocks(task, updated)
    }

    private suspend fun moveSeriesAnchor(task: CalendarTaskEntity, newAnchor: LocalDate) {
        val blocks = loadOwnerBlocks(task)
        val now = System.currentTimeMillis()
        val newTimestamp = task.reminderTimestamp?.let { retargetTimestampToDate(it, newAnchor) }
        val updated = blocks.map { block ->
            if (block.id == task.blockId && block is CheckboxBlock) {
                block.copy(reminderTimestamp = newTimestamp ?: block.reminderTimestamp, updatedAt = now)
            } else block
        }
        saveOwnerBlocks(task, updated)
    }

    // Creates the "split-off" continuation of a series when a future/past-scoped edit needs to
    // keep a differently-configured portion alive alongside the (truncated or anchor-shifted)
    // original - e.g. editing "this and following events" leaves earlier occurrences on the
    // original block and puts the edited occurrence onward on a brand new one.
    private suspend fun createSplitSeries(
        task: CalendarTaskEntity, rule: RecurrenceRule, anchor: LocalDate, until: String?,
        text: String, timestamp: Long, categoryId: String?, durationMinutes: Int, url: String?, description: String?
    ): String {
        val newBlockId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val newBlock = CheckboxBlock(
            id = newBlockId,
            text = text,
            reminderTimestamp = timestamp,
            categoryId = categoryId,
            durationMinutes = durationMinutes,
            url = url,
            description = description,
            recurrenceRule = rule.copy(untilDateString = until),
            updatedAt = now
        )

        when (task.sourceType) {
            TaskSource.DAILY -> {
                val anchorDateString = anchor.toString()
                val targetBlocks = getDailyNote(anchorDateString)?.blocks ?: emptyList()
                saveDailyNote(anchorDateString, NoteContent(blocks = listOf(newBlock) + targetBlocks))
            }
            TaskSource.NOTE -> {
                val blocks = loadOwnerBlocks(task)
                saveOwnerBlocks(task, blocks + newBlock)
            }
        }
        return newBlockId
    }

    override fun getIncompleteTasksCount(): Flow<Int> = noteDao.getIncompleteTasksCount()

    // Rebuilds the ImageBlockEntity projection table for a given note on every save.
    // ImagesScreen reads from this table via getAllImagesFlow() — a Room Flow that
    // emits automatically whenever this table changes.
    private suspend fun syncImageBlocks(
        noteId: String,
        blocks: List<NoteBlock>,
        sourceType: TaskSource,
        noteCreatedAt: Long
    ) {
        imageBlockDao.deleteByNoteId(noteId)

        val images = blocks
            .filterIsInstance<ImageBlock>()
            .filter { !it.isDeleted && it.localFilePath != null }
            .map { block ->
                ImageBlockEntity(
                    blockId = block.id,
                    noteId = noteId,
                    localFilePath = block.localFilePath!!,
                    noteCreatedAt = noteCreatedAt,
                    sourceType = sourceType
                )
            }

        if (images.isNotEmpty()) {
            imageBlockDao.upsertImages(images)
        }
    }

    override fun getAllImagesFlow(): Flow<List<ImageBlockEntity>> =
        imageBlockDao.getAllImagesFlow()

    // Rebuilds the DocumentBlockEntity projection table for a given note on every save.
    // DocumentsScreen reads from this via getAllDocumentsFlow().
    private suspend fun syncDocumentBlocks(
        noteId: String,
        blocks: List<NoteBlock>,
        sourceType: TaskSource,
        noteCreatedAt: Long
    ) {
        documentBlockDao.deleteByNoteId(noteId)

        val documents = blocks
            .filterIsInstance<DocumentBlock>()
            .filter { !it.isDeleted && it.localFilePath != null }
            .map { block ->
                DocumentBlockEntity(
                    blockId = block.id,
                    noteId = noteId,
                    localFilePath = block.localFilePath!!,
                    fileName = block.fileName,
                    mimeType = block.mimeType,
                    fileSizeString = block.fileSizeString,
                    noteCreatedAt = noteCreatedAt,
                    sourceType = sourceType
                )
            }

        if (documents.isNotEmpty()) {
            documentBlockDao.upsertDocuments(documents)
        }
    }

    override fun getAllDocumentsFlow(): Flow<List<DocumentBlockEntity>> =
        documentBlockDao.getAllDocumentsFlow()

    // Rebuilds the BookmarkBlockEntity projection table for a given note on every save.
    // BookmarksScreen reads from this via getAllBookmarksFlow().
    private suspend fun syncBookmarkBlocks(
        noteId: String,
        blocks: List<NoteBlock>,
        sourceType: TaskSource,
        noteUpdatedAt: Long
    ) {
        bookmarkBlockDao.deleteByNoteId(noteId)

        val bookmarks = blocks
            .filterIsInstance<BookmarkBlock>()
            .filter { !it.isDeleted && it.url.isNotBlank() }
            .map { block ->
                BookmarkBlockEntity(
                    blockId = block.id,
                    noteId = noteId,
                    url = block.url,
                    title = block.title,
                    description = block.description,
                    previewImageUrl = block.previewImageUrl,
                    noteUpdatedAt = noteUpdatedAt,
                    sourceType = sourceType
                )
            }

        if (bookmarks.isNotEmpty()) {
            bookmarkBlockDao.upsertBookmarks(bookmarks)
        }
    }

    override fun getAllBookmarksFlow(): Flow<List<BookmarkBlockEntity>> =
        bookmarkBlockDao.getAllBookmarksFlow()

    override fun getImagesCount(): Flow<Int> = imageBlockDao.getImagesCount()
    override fun getDocumentsCount(): Flow<Int> = documentBlockDao.getDocumentsCount()
    override fun getBookmarksCount(): Flow<Int> = bookmarkBlockDao.getBookmarksCount()

    override fun getAllLinkableNotes(): Flow<List<NoteMetadataEntity>> {
        return noteDao.getAllLinkableNotes()
    }

    override suspend fun updateNoteSortOrder(noteId: String, order: Int) =
        withContext(Dispatchers.IO) {
            noteDao.updateNoteSortOrder(noteId, order, System.currentTimeMillis())
            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestNoteRefresh(noteId)
        }

    override suspend fun addNoteToFavorites(noteId: String) =
        withContext(Dispatchers.IO) {
            noteDao.addNoteToFavorites(noteId, System.currentTimeMillis())
            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestNoteRefresh(noteId)
        }

    override suspend fun removeNoteFromFavoritesAndMoveToRoot(noteId: String) =
        withContext(Dispatchers.IO) {
            noteDao.removeNoteFromFavoritesAndMoveToRoot(noteId, System.currentTimeMillis())
            AutoSyncTrigger.requestSync()
            VaultMirrorTrigger.requestNoteRefresh(noteId)
        }

    override suspend fun updateFolderSortOrder(folderId: String, order: Int) =
        withContext(Dispatchers.IO) {
            folderDao.updateFolderSortOrder(folderId, order)
        }

    // clear cache after import
    override fun clearCaches() {
        noteContentCache.value = emptyMap()
        dailyNoteCache.value = emptyMap()
    }
}