package com.emberr.domain.repository

import com.emberr.data.local.room.entity.BookmarkBlockEntity
import com.emberr.data.local.room.entity.CalendarEventExceptionEntity
import com.emberr.data.local.room.entity.CalendarTaskEntity
import com.emberr.data.local.room.entity.CategoryEntity
import com.emberr.data.local.room.entity.DatabaseTemplateEntity
import com.emberr.data.local.room.entity.DocumentBlockEntity
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.ImageBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.TagEntity
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.NoteSearchResult
import com.emberr.domain.model.RecurrenceEditScope
import kotlinx.coroutines.flow.Flow

/**
 * The single source of truth for accessing and modifying note data.
 * ViewModels should only talk to this interface, never directly to the database or file manager.
 */
interface NoteRepository {

    fun getCalendarTasksForMonth(yearMonth: String): Flow<List<CalendarTaskEntity>>
    fun getCalendarTasksForDate(dateString: String): Flow<List<CalendarTaskEntity>>
    fun getAllTasksFlow(): Flow<List<CalendarTaskEntity>>

    // Marks a single occurrence of a recurring checkbox as done/undone without touching the
    // base block or any other occurrence - see CalendarEventExceptionEntity.
    suspend fun upsertOccurrenceCompletion(blockId: String, occurrenceDate: String, isChecked: Boolean)

    // For a NON-recurring checkbox surfaced as a virtual occurrence on some other day/note's
    // screen (e.g. a NOTE-sourced reminder shown on the Daily screen) - there's only ever one
    // occurrence, so unlike upsertOccurrenceCompletion above, the base block itself is the sole
    // source of truth and is what gets toggled/edited directly, wherever it actually lives.
    suspend fun toggleTaskCompletion(blockId: String, isChecked: Boolean)
    suspend fun updateTaskText(blockId: String, text: String)

    // Applies an edit/delete to a recurring event under the given scope, splitting the series
    // into two (a truncated original + a fresh continuation) when the scope is FUTURE/PAST and
    // the acted-on occurrence isn't the series' first/last. See NoteRepositoryImpl for the full
    // per-scope mechanics.
    suspend fun applyRecurrenceScopedDelete(blockId: String, occurrenceDate: String, scope: RecurrenceEditScope)

    suspend fun applyRecurrenceScopedEdit(
        blockId: String,
        occurrenceDate: String,
        scope: RecurrenceEditScope,
        text: String,
        timestamp: Long,
        categoryId: String?,
        durationMinutes: Int,
        url: String?,
        description: String?
    )

    // Daily Tab operations
    suspend fun getDailyNoteMetadata(dateString: String): NoteMetadataEntity?
    suspend fun getDailyNoteMetadataInSpace(spaceId: String, dateString: String): NoteMetadataEntity?
    suspend fun getDailyNote(dateString: String): NoteContent?
    suspend fun getDailyNoteInSpace(spaceId: String, dateString: String): NoteContent?
    suspend fun getSavedDailyNoteDates(): List<String>
    suspend fun saveDailyNote(dateString: String, content: NoteContent, updatedAt: Long? = null, remoteMeta: NoteMetadataEntity? = null)
    fun refreshDailyNoteCache(spaceId: String, dateString: String, content: NoteContent)
    suspend fun dedupeDuplicateDailyNotes(): Int

    // Notes operations
    fun getAllNotes(): Flow<List<NoteMetadataEntity>>
    suspend fun getAllNotesAcrossSpaces(): List<NoteMetadataEntity>
    fun getNotesInFolder(folderId: String): Flow<List<NoteMetadataEntity>>
    suspend fun getNoteContent(noteId: String): NoteContent?
    suspend fun saveNote(metadata: NoteMetadataEntity, content: NoteContent, stampUpdatedAt: Boolean = true)

    suspend fun saveNoteInSpace(
        spaceId: String,
        metadata: NoteMetadataEntity,
        content: NoteContent,
        stampUpdatedAt: Boolean = true
    )
    fun refreshNoteContentCache(noteId: String, content: NoteContent)
    suspend fun refreshProjectionsForNote(metadata: NoteMetadataEntity, blocks: List<NoteBlock>)
    suspend fun deleteNote(noteId: String, filePath: String)
    suspend fun deleteAllContentInSpace(spaceId: String)

    // Hard-deletes the local Room row/blocks/index only, with no tombstone insert and no sync
    // trigger - used by SelfHostSyncEngine to apply a tombstone it received from another device,
    // as opposed to deleteNote which originates a new tombstone for this device's own deletion.
    suspend fun hardDeleteLocalNote(noteId: String)

    // Tombstones for notes permanently deleted from this device - shared by both sync engines.
    // entityId is matched against noteId first, then dateString (daily notes are addressed by
    // dateString in LAN sync envelopes, which don't carry a noteId).
    suspend fun getNoteTombstonesModifiedSince(timestamp: Long): List<com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity>
    suspend fun getNoteTombstone(entityId: String): com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity?
    suspend fun getNoteTombstoneInSpace(spaceId: String, entityId: String): com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity?

    // Applies a tombstone received from a peer: hard-deletes the local copy unless it was genuinely
    // edited after the deletion (last-write-wins), and records the tombstone locally regardless so
    // this device won't itself resurrect the note and can propagate the deletion onward.
    suspend fun applyRemoteNoteTombstone(
        spaceId: String,
        noteId: String,
        isDaily: Boolean,
        dateString: String?,
        deletedAt: Long
    )

    // Favorites and Trash management
    fun getFavoriteNotes(): Flow<List<NoteMetadataEntity>>
    fun getTrashedNotes(): Flow<List<NoteMetadataEntity>>
    suspend fun restoreNote(noteId: String)
    suspend fun cleanupOldTrashedNotes()

    // Folder management
    fun getAllFolders(): Flow<List<FolderEntity>>
    suspend fun insertFolder(folder: FolderEntity)
    suspend fun insertFolderInSpace(spaceId: String, folder: FolderEntity)
    suspend fun deleteFolder(folderId: String)
    suspend fun getNoteById(noteId: String): NoteMetadataEntity?
    suspend fun getFoldersModifiedSince(timestamp: Long): List<FolderEntity>

    // Applies a folder received from a peer as-is (preserving its own updatedAt/isDeleted) if it's
    // newer than the local copy - unlike insertFolder, which is for this device's own edits and
    // always restamps updatedAt to now.
    suspend fun applyRemoteFolder(folder: FolderEntity)

    // Database
    fun getAllTags(): Flow<List<TagEntity>>
    suspend fun insertOrUpdateTag(tagId: String, name: String, colorHex: String)
    suspend fun deleteTag(tagId: String)
    suspend fun getTagsModifiedSince(timestamp: Long): List<TagEntity>

    // Same reasoning as applyRemoteFolder - preserves the peer's updatedAt/isDeleted instead of
    // restamping it as a fresh local edit.
    suspend fun applyRemoteTag(tag: TagEntity)

    // Calendar categories
    fun getAllCategories(): Flow<List<CategoryEntity>>
    suspend fun insertOrUpdateCategory(categoryId: String, name: String, colorHex: String)
    suspend fun deleteCategory(categoryId: String)
    suspend fun getCategoriesModifiedSince(timestamp: Long): List<CategoryEntity>
    suspend fun applyRemoteCategory(category: CategoryEntity)

    suspend fun applyRemoteEventException(exception: CalendarEventExceptionEntity)

    // Database templates (saved schemas: columns + views, never rows)
    fun getAllDatabaseTemplates(): Flow<List<DatabaseTemplateEntity>>
    suspend fun insertDatabaseTemplate(template: DatabaseTemplateEntity)
    suspend fun deleteDatabaseTemplate(templateId: String)

    // Note templates (full NoteMetadataEntity + NoteContent, reusable as a starting point for new notes)
    fun getAllTemplates(): Flow<List<NoteMetadataEntity>>
    suspend fun deleteTemplate(templateId: String)

    // sync
    suspend fun getNotesModifiedSince(timestamp: Long): List<NoteMetadataEntity>
    fun searchDailyNotes(query: String): Flow<List<NoteMetadataEntity>>

    // Cross-note search. searchNoteTitlesAndSnippets is the cheap metadata-only pass the UI
    // shows first; searchNotes adds the slower block-content matches on top of it.
    suspend fun searchNoteTitlesAndSnippets(query: String): List<NoteSearchResult>
    suspend fun searchNotes(query: String): List<NoteSearchResult>

    suspend fun indexNote(metadata: NoteMetadataEntity, content: NoteContent)
    suspend fun indexDailyNote(dateString: String, content: NoteContent, metadata: NoteMetadataEntity)

    fun getIncompleteTasksCount(): Flow<Int>

    fun getNoteCountsByFolder(): Flow<Map<String, Int>>

    fun getAllImagesFlow(): Flow<List<ImageBlockEntity>>
    fun getAllDocumentsFlow(): Flow<List<DocumentBlockEntity>>
    fun getAllBookmarksFlow(): Flow<List<BookmarkBlockEntity>>
    fun getImagesCount(): Flow<Int>
    fun getDocumentsCount(): Flow<Int>
    fun getBookmarksCount(): Flow<Int>

    fun getAllLinkableNotes(): Flow<List<NoteMetadataEntity>>

    fun observeNoteContent(noteId: String): Flow<NoteContent?>
    fun observeDailyNote(dateString: String): Flow<NoteContent?>

    suspend fun updateNoteSortOrder(noteId: String, order: Int)
    suspend fun addNoteToFavorites(noteId: String)
    suspend fun removeNoteFromFavoritesAndMoveToRoot(noteId: String)
    suspend fun updateFolderSortOrder(folderId: String, order: Int)

    // clear cache after import
    fun clearCaches()
}