package com.emberr.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Handles all local database operations for notes.
 * This mostly deals with filtering metadata, like checking if a note is trashed,
 * favorited, or belongs to a specific folder before trying to load its actual content.
 */
@Dao
interface NoteDao {
    // Must be a real UPDATE, not INSERT-OR-REPLACE: note_blocks has an ON DELETE CASCADE foreign
    // key on noteId, and REPLACE resolves a primary-key conflict via an actual DELETE-then-INSERT of
    // the metadata row - which cascades into wiping every block for this note on every single save,
    // moments before upsertChangedBlocks reads them to decide what needs tombstoning. @Upsert compiles
    // to a real "ON CONFLICT DO UPDATE", never a delete, so it can't trigger that cascade.
    @Upsert
    suspend fun insertOrUpdateMetadata(metadata: NoteMetadataEntity)

    @Query("SELECT coverImagePath FROM notes_metadata WHERE coverImagePath IS NOT NULL")
    suspend fun getAllCoverImagePaths(): List<String?>

    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isDaily = 0 AND trashedAt IS NULL AND isSubNote = 0 AND isTemplate = 0 ORDER BY updatedAt DESC")
    fun getAllNotes(spaceId: String): Flow<List<NoteMetadataEntity>>

    @Query("SELECT * FROM notes_metadata WHERE folderId = :folderId AND trashedAt IS NULL AND isSubNote = 0 AND isTemplate = 0 ORDER BY updatedAt DESC")
    fun getNotesInFolder(folderId: String): Flow<List<NoteMetadataEntity>>
    @Query(
        """
        SELECT folderId AS folderId, COUNT(*) AS noteCount FROM notes_metadata
        WHERE spaceId = :spaceId AND folderId IS NOT NULL AND trashedAt IS NULL AND isSubNote = 0
              AND isTemplate = 0 AND isFavorite = 0
        GROUP BY folderId
        """
    )
    fun getNoteCountsByFolder(spaceId: String): Flow<List<FolderNoteCount>>

    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isDaily = 1 AND dateString = :date AND isTemplate = 0 LIMIT 1")
    suspend fun getDailyNoteMetadata(spaceId: String, date: String): NoteMetadataEntity?

    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isDaily = 1 AND isTemplate = 0")
    suspend fun getAllDailyNoteMetadata(spaceId: String): List<NoteMetadataEntity>

    @Query("SELECT * FROM notes_metadata WHERE isDaily = 1 AND isTemplate = 0")
    suspend fun getAllDailyNoteMetadataAcrossSpaces(): List<NoteMetadataEntity>

    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isDaily = 1 AND isTemplate = 0 AND snippet LIKE '%' || :query || '%' ORDER BY dateString DESC")
    fun searchDailyNotes(spaceId: String, query: String): Flow<List<NoteMetadataEntity>>

    // Cross-note search
    @Query(
        """
        SELECT * FROM notes_metadata
        WHERE spaceId = :spaceId AND trashedAt IS NULL AND isSubNote = 0 AND isTemplate = 0
        AND (title LIKE '%' || :query || '%' OR snippet LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
        """
    )
    fun searchNotesByTitleOrSnippet(spaceId: String, query: String): Flow<List<NoteMetadataEntity>>

    // isTemplate = 0 here too: this is also used to resolve cross-note *content* search hits
    // (NoteRepositoryImpl.searchNotes), and that content match comes from a raw block-JSON scan
    // that has no idea what a template is, so the filter has to be enforced on this side instead.
    @Query("SELECT * FROM notes_metadata WHERE noteId IN (:ids) AND isTemplate = 0")
    suspend fun getNotesByIds(ids: List<String>): List<NoteMetadataEntity>

    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isFavorite = 1 AND trashedAt IS NULL AND isTemplate = 0 ORDER BY updatedAt DESC")
    fun getFavoriteNotes(spaceId: String): Flow<List<NoteMetadataEntity>>

    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND trashedAt IS NOT NULL AND isTemplate = 0 ORDER BY trashedAt DESC")
    fun getTrashedNotes(spaceId: String): Flow<List<NoteMetadataEntity>>

    @Query("DELETE FROM notes_metadata WHERE noteId = :noteId")
    suspend fun deleteNoteMetadata(noteId: String)

    @Query("SELECT * FROM notes_metadata WHERE noteId = :id LIMIT 1")
    suspend fun getNoteById(id: String): NoteMetadataEntity?

    @Query("SELECT * FROM notes_metadata WHERE noteId = :id LIMIT 1")
    fun observeNoteById(id: String): Flow<NoteMetadataEntity?>

    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isDaily = 0 AND trashedAt IS NULL AND isSubNote = 0 AND isTemplate = 0 ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentNotes(spaceId: String, limit: Int): List<NoteMetadataEntity>

    @Query("UPDATE notes_metadata SET trashedAt = NULL, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun restoreNote(noteId: String, updatedAt: Long)

    // Deliberately NOT filtered by isTemplate - this is what actually purges a soft-deleted
    // template's row (see NoteRepositoryImpl.deleteTemplate) once every paired device has had
    // 30 days to receive its tombstone via sync, exactly like a regular trashed note.
    @Query("SELECT * FROM notes_metadata WHERE trashedAt IS NOT NULL AND trashedAt < :cutoffTime")
    suspend fun getOldTrashedNotes(cutoffTime: Long): List<NoteMetadataEntity>

    // Deliberately NOT filtered by isTemplate - sync needs to propagate template
    // creation/edits/deletion across paired devices exactly like any other note.
    @Query("SELECT * FROM notes_metadata WHERE updatedAt > :timestamp")
    suspend fun getNotesModifiedSince(timestamp: Long): List<NoteMetadataEntity>

    // Self-host's per-note candidate discovery - comparing each row's own two columns needs no
    // externally-tracked watermark parameter at all, unlike getNotesModifiedSince above. Deliberately
    // NOT filtered by isTemplate, same reasoning as getNotesModifiedSince.
    @Query("SELECT * FROM notes_metadata WHERE updatedAt > selfHostSyncedAt")
    suspend fun getNotesNeedingSelfHostSync(): List<NoteMetadataEntity>

    // Self-host manifest reconciliation needs to check a remote entry's updatedAt against the
    // matching local row's own selfHostSyncedAt - getNotesByIds above can't be reused here since it
    // filters out templates, which self-host sync must still track.
    @Query("SELECT * FROM notes_metadata WHERE noteId IN (:ids)")
    suspend fun getNotesByIdsIncludingTemplates(ids: List<String>): List<NoteMetadataEntity>

    @Query("UPDATE notes_metadata SET selfHostSyncedAt = :syncedAt WHERE noteId = :noteId")
    suspend fun updateSelfHostSyncedAt(noteId: String, syncedAt: Long)

    @Query("SELECT COUNT(*) FROM calendar_tasks WHERE spaceId = :spaceId AND isChecked = 0")
    fun getIncompleteTasksCount(spaceId: String): Flow<Int>

    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isDaily = 0 AND trashedAt IS NULL AND isTemplate = 0")
    fun getAllLinkableNotes(spaceId: String): Flow<List<NoteMetadataEntity>>

    @Query("SELECT * FROM notes_metadata WHERE isDaily = 0 AND trashedAt IS NULL AND isSubNote = 0 AND isTemplate = 0 ORDER BY updatedAt DESC")
    suspend fun getAllNotesAcrossSpaces(): List<NoteMetadataEntity>

    @Query("SELECT * FROM notes_metadata WHERE isDaily = 0 AND trashedAt IS NULL AND isSubNote = 0 AND isTemplate = 0 ORDER BY updatedAt DESC")
    fun getAllNotesAcrossSpacesFlow(): Flow<List<NoteMetadataEntity>>

    @Query("SELECT * FROM notes_metadata")
    suspend fun getAllNotesForBackup(): List<NoteMetadataEntity>

    @Query("UPDATE notes_metadata SET sortOrder = :order, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateNoteSortOrder(noteId: String, order: Int, updatedAt: Long)

    @Query("UPDATE notes_metadata SET isFavorite = 1, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun addNoteToFavorites(noteId: String, updatedAt: Long)

    @Query("UPDATE notes_metadata SET isFavorite = 0, folderId = NULL, updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun removeNoteFromFavoritesAndMoveToRoot(noteId: String, updatedAt: Long)

    // Templates menu: every reusable template (predefined + user-saved), alphabetical so the
    // search/filter UI has a stable starting order.
    @Query("SELECT * FROM notes_metadata WHERE spaceId = :spaceId AND isTemplate = 1 AND trashedAt IS NULL ORDER BY title ASC")
    fun getAllTemplates(spaceId: String): Flow<List<NoteMetadataEntity>>
}

/**
 * Simple DAO to manage the creation and deletion of folders.
 */
@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE spaceId = :spaceId AND isDeleted = 0 ORDER BY CASE WHEN sortOrder = 0 THEN 1 ELSE 0 END, sortOrder ASC, createdAt ASC")
    fun getAllFolders(spaceId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY CASE WHEN sortOrder = 0 THEN 1 ELSE 0 END, sortOrder ASC, createdAt ASC")
    fun getAllFoldersAcrossSpaces(): Flow<List<FolderEntity>>

    @Query("UPDATE folders SET isDeleted = 1, updatedAt = :updatedAt WHERE folderId = :folderId")
    suspend fun markFolderDeleted(folderId: String, updatedAt: Long)

    @Query("SELECT * FROM folders WHERE updatedAt > :timestamp")
    suspend fun getFoldersModifiedSince(timestamp: Long): List<FolderEntity>

    @Query("UPDATE folders SET sortOrder = :order WHERE folderId = :folderId")
    suspend fun updateFolderSortOrder(folderId: String, order: Int)

    @Query("SELECT * FROM folders WHERE folderId = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: String): FolderEntity?
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTag(tag: TagEntity)

    @Query("SELECT * FROM global_tags WHERE spaceId = :spaceId AND isDeleted = 0 ORDER BY name ASC")
    fun getAllTags(spaceId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM global_tags WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllTagsAcrossSpaces(): Flow<List<TagEntity>>

    @Query("UPDATE global_tags SET isDeleted = 1, updatedAt = :updatedAt WHERE tagId = :tagId")
    suspend fun markTagDeleted(tagId: String, updatedAt: Long)

    @Query("SELECT * FROM global_tags WHERE updatedAt > :timestamp")
    suspend fun getTagsModifiedSince(timestamp: Long): List<TagEntity>

    @Query("SELECT * FROM global_tags WHERE tagId = :tagId LIMIT 1")
    suspend fun getTagById(tagId: String): TagEntity?
}

@Dao
interface SpaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSpace(space: SpaceEntity)

    @Query("SELECT * FROM spaces WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllSpaces(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllSpacesOnce(): List<SpaceEntity>

    @Query("SELECT * FROM spaces WHERE spaceId = :spaceId LIMIT 1")
    suspend fun getSpaceById(spaceId: String): SpaceEntity?

    @Query("SELECT COUNT(*) FROM spaces WHERE isDeleted = 0")
    suspend fun countSpaces(): Int

    @Query("SELECT * FROM spaces WHERE updatedAt > :timestamp")
    suspend fun getSpacesModifiedSince(timestamp: Long): List<SpaceEntity>

    @Query("SELECT * FROM spaces")
    suspend fun getAllSpacesForBackup(): List<SpaceEntity>

    @Query("UPDATE spaces SET displayName = :displayName, updatedAt = :updatedAt WHERE spaceId = :spaceId")
    suspend fun renameSpace(spaceId: String, displayName: String, updatedAt: Long)

    @Query("UPDATE spaces SET isDeleted = 1, updatedAt = :updatedAt WHERE spaceId = :spaceId")
    suspend fun markSpaceDeleted(spaceId: String, updatedAt: Long)
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCategory(category: CategoryEntity)

    @Query("SELECT * FROM calendar_categories WHERE spaceId = :spaceId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllCategories(spaceId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM calendar_categories WHERE spaceId = :spaceId AND isDeleted = 0")
    suspend fun getAllCategoriesOnce(spaceId: String): List<CategoryEntity>

    @Query("SELECT * FROM calendar_categories WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllCategoriesAcrossSpaces(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM calendar_categories WHERE isDeleted = 0")
    suspend fun getAllCategoriesOnceAcrossSpaces(): List<CategoryEntity>

    @Query("SELECT * FROM calendar_categories WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): CategoryEntity?

    @Query("SELECT * FROM calendar_categories WHERE updatedAt > :timestamp")
    suspend fun getCategoriesModifiedSince(timestamp: Long): List<CategoryEntity>

    @Query("UPDATE calendar_categories SET isDeleted = 1, updatedAt = :updatedAt WHERE categoryId = :categoryId")
    suspend fun markCategoryDeleted(categoryId: String, updatedAt: Long)
}

@Dao
interface CalendarTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<CalendarTaskEntity>)

    // Fast query for the Calendar Pager to render the dot indicators.
    // Pass yearMonth as "2026-07" to get everything in July.
    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate LIKE :yearMonth || '%'")
    fun getTasksForMonth(spaceId: String, yearMonth: String): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate LIKE :yearMonth || '%'")
    suspend fun getTasksInMonth(spaceId: String, yearMonth: String): List<CalendarTaskEntity>

    // Precise query for the Bottom Sheet when a user clicks a specific day.
    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate = :dateString")
    fun getTasksForDate(spaceId: String, dateString: String): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate = :dateString")
    suspend fun getTasksOnDate(spaceId: String, dateString: String): List<CalendarTaskEntity>

    @Query("SELECT * FROM calendar_tasks WHERE blockId = :blockId LIMIT 1")
    suspend fun getTaskById(blockId: String): CalendarTaskEntity?

    // Allows the Bottom Sheet to instantly toggle a checkbox without loading the full note.
    @Query("UPDATE calendar_tasks SET isChecked = :isChecked WHERE blockId = :blockId")
    suspend fun updateTaskStatus(blockId: String, isChecked: Boolean)

    // Used by the NoteEditorViewModel/DailyEditorViewModel to clear old tasks before saving new ones.
    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND noteId = :noteId")
    suspend fun getTasksForNote(spaceId: String, noteId: String): List<CalendarTaskEntity>

    @Query("DELETE FROM calendar_tasks WHERE spaceId = :spaceId AND noteId = :noteId")
    suspend fun deleteTasksByNoteId(spaceId: String, noteId: String)

    // For when a user backspaces/deletes a single task in the editor.
    @Query("DELETE FROM calendar_tasks WHERE blockId = :blockId")
    suspend fun deleteTaskById(blockId: String)

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate >= :fromDate AND isChecked = 0 ORDER BY targetDate ASC")
    suspend fun getUpcomingTasks(spaceId: String, fromDate: String): List<CalendarTaskEntity>

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId")
    fun getAllTasksFlow(spaceId: String): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId")
    suspend fun getAllTasks(spaceId: String): List<CalendarTaskEntity>

    @Query("SELECT * FROM calendar_tasks")
    fun getAllTasksAcrossSpacesFlow(): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks")
    suspend fun getAllTasksAcrossSpaces(): List<CalendarTaskEntity>
}

@Dao
interface CalendarEventExceptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exception: CalendarEventExceptionEntity)

    @Query("SELECT * FROM calendar_event_exceptions")
    fun getAllExceptionsFlow(): Flow<List<CalendarEventExceptionEntity>>

    @Query("SELECT * FROM calendar_event_exceptions WHERE blockId = :blockId AND occurrenceDate = :occurrenceDate LIMIT 1")
    suspend fun getException(blockId: String, occurrenceDate: String): CalendarEventExceptionEntity?

    @Query("SELECT * FROM calendar_event_exceptions WHERE updatedAt > :timestamp")
    suspend fun getExceptionsModifiedSince(timestamp: Long): List<CalendarEventExceptionEntity>

    @Query("DELETE FROM calendar_event_exceptions WHERE blockId = :blockId")
    suspend fun deleteExceptionsForBlock(blockId: String)

    // Used when a series is split by an "all future events" edit/delete - the truncated
    // original block keeps only exceptions dated before the split point.
    @Query("DELETE FROM calendar_event_exceptions WHERE blockId = :blockId AND occurrenceDate >= :fromDateInclusive")
    suspend fun deleteExceptionsFrom(blockId: String, fromDateInclusive: String)

    // Symmetric case for "all past events" - the original block's anchor moves forward, so
    // exceptions dated at/before the split point no longer belong to it.
    @Query("DELETE FROM calendar_event_exceptions WHERE blockId = :blockId AND occurrenceDate <= :toDateInclusive")
    suspend fun deleteExceptionsUpTo(blockId: String, toDateInclusive: String)

    @Query("UPDATE calendar_event_exceptions SET blockId = :newBlockId, updatedAt = :updatedAt WHERE blockId = :oldBlockId AND occurrenceDate >= :fromDateInclusive")
    suspend fun rekeyExceptionsFrom(oldBlockId: String, newBlockId: String, fromDateInclusive: String, updatedAt: Long)

    @Query("UPDATE calendar_event_exceptions SET blockId = :newBlockId, updatedAt = :updatedAt WHERE blockId = :oldBlockId AND occurrenceDate <= :toDateInclusive")
    suspend fun rekeyExceptionsUpTo(oldBlockId: String, newBlockId: String, toDateInclusive: String, updatedAt: Long)
}

@Dao
interface ImageBlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImages(images: List<ImageBlockEntity>)

    @Query("DELETE FROM image_blocks WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("SELECT * FROM image_blocks WHERE noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId) ORDER BY noteCreatedAt DESC")
    fun getAllImagesFlow(spaceId: String): Flow<List<ImageBlockEntity>>

    @Query("SELECT COUNT(*) FROM image_blocks WHERE noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId)")
    fun getImagesCount(spaceId: String): Flow<Int>

    @Query("SELECT * FROM image_blocks ORDER BY noteCreatedAt DESC")
    fun getAllImagesAcrossSpacesFlow(): Flow<List<ImageBlockEntity>>
}

@Dao
interface DocumentBlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<DocumentBlockEntity>)

    @Query("DELETE FROM document_blocks WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("SELECT * FROM document_blocks WHERE noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId) ORDER BY noteCreatedAt DESC")
    fun getAllDocumentsFlow(spaceId: String): Flow<List<DocumentBlockEntity>>

    @Query("SELECT COUNT(*) FROM document_blocks WHERE noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId)")
    fun getDocumentsCount(spaceId: String): Flow<Int>

    @Query("SELECT * FROM document_blocks ORDER BY noteCreatedAt DESC")
    fun getAllDocumentsAcrossSpacesFlow(): Flow<List<DocumentBlockEntity>>
}

@Dao
interface BookmarkBlockDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmarks(bookmarks: List<BookmarkBlockEntity>)

    @Query("DELETE FROM bookmark_blocks WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("SELECT * FROM bookmark_blocks WHERE noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId) ORDER BY noteUpdatedAt DESC")
    fun getAllBookmarksFlow(spaceId: String): Flow<List<BookmarkBlockEntity>>

    @Query("SELECT COUNT(*) FROM bookmark_blocks WHERE noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId)")
    fun getBookmarksCount(spaceId: String): Flow<Int>

    @Query("SELECT * FROM bookmark_blocks ORDER BY noteUpdatedAt DESC")
    fun getAllBookmarksAcrossSpacesFlow(): Flow<List<BookmarkBlockEntity>>
}

/**
 * Manages saved DatabaseBlock schemas (columns/views) that users can reuse when
 * creating a new database block.
 */
@Dao
interface DatabaseTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: DatabaseTemplateEntity)

    @Query("SELECT * FROM database_templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<DatabaseTemplateEntity>>

    @Query("DELETE FROM database_templates WHERE templateId = :templateId")
    suspend fun deleteTemplate(templateId: String)
}

@Dao
interface SelfHostDeletedNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: SelfHostDeletedNoteEntity)

    @Query("SELECT * FROM self_host_deleted_notes")
    suspend fun getAllTombstones(): List<SelfHostDeletedNoteEntity>

    @Query("UPDATE self_host_deleted_notes SET remoteFileDeleted = 1 WHERE noteId = :noteId")
    suspend fun markRemoteFileDeleted(noteId: String)

    // Shared by both sync engines - self-host uses getAllTombstones/markRemoteFileDeleted for
    // its own manifest-tombstone bookkeeping; LAN sync uses the queries below to propagate a hard
    // delete over the peer-to-peer protocol and to guard against resurrecting an already-deleted note.
    @Query("SELECT * FROM self_host_deleted_notes WHERE deletedAt > :timestamp")
    suspend fun getTombstonesModifiedSince(timestamp: Long): List<SelfHostDeletedNoteEntity>

    @Query("SELECT * FROM self_host_deleted_notes WHERE noteId = :noteId LIMIT 1")
    suspend fun getTombstoneByNoteId(noteId: String): SelfHostDeletedNoteEntity?

    @Query("SELECT * FROM self_host_deleted_notes WHERE spaceId = :spaceId AND dateString = :dateString LIMIT 1")
    suspend fun getTombstoneByDateString(spaceId: String, dateString: String): SelfHostDeletedNoteEntity?
}

@Dao
interface MediaReferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReferences(references: List<MediaReferenceEntity>)

    @Query("DELETE FROM media_references WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("DELETE FROM media_references")
    suspend fun deleteAllReferences()

    @Query("SELECT DISTINCT fileName FROM media_references")
    suspend fun getAllReferencedFileNames(): List<String>
}
