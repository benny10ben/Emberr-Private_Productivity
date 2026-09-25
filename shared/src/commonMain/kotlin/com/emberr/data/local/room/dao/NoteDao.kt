package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.emberr.data.local.room.entity.FolderNoteCount
import com.emberr.data.local.room.entity.NoteMetadataEntity
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
    suspend fun searchNotesByTitleOrSnippet(spaceId: String, query: String): List<NoteMetadataEntity>

    // isTemplate = 0 here too: callers like the tasks widget and the reminder rescheduler look up
    // titles by id and must never resolve a template row.
    @Query("SELECT * FROM notes_metadata WHERE noteId IN (:ids) AND isTemplate = 0")
    suspend fun getNotesByIds(ids: List<String>): List<NoteMetadataEntity>

    // Resolves cross-note *content* search hits (NoteRepositoryImpl.searchNotes). The block-JSON
    // scan that produces those ids has no idea what a trashed note, sub-note or template is, so
    // this repeats searchNotesByTitleOrSnippet's filters to keep both halves of one search in sync.
    @Query(
        """
        SELECT * FROM notes_metadata
        WHERE noteId IN (:ids) AND trashedAt IS NULL AND isSubNote = 0 AND isTemplate = 0
        """
    )
    suspend fun getSearchableNotesByIds(ids: List<String>): List<NoteMetadataEntity>

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

    @Query("UPDATE notes_metadata SET updatedAt = :updatedAt WHERE noteId = :noteId")
    suspend fun updateNoteUpdatedAt(noteId: String, updatedAt: Long)

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
