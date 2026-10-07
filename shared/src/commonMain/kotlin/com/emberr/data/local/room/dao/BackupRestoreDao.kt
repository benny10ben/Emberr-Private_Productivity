package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.CalendarEventExceptionEntity
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasStrokeEntity
import com.emberr.data.local.room.entity.CategoryEntity
import com.emberr.data.local.room.entity.ChatSessionEntity
import com.emberr.data.local.room.entity.CustomPropertyEntity
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.PropertyTagEntity
import com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity
import com.emberr.data.local.room.entity.SpaceEntity

@Dao
interface BackupRestoreDao {

    @Query("DELETE FROM note_blocks")
    suspend fun deleteAllBlocks()

    @Query("DELETE FROM canvas_strokes")
    suspend fun deleteAllCanvasStrokes()

    @Query("DELETE FROM canvas_edges")
    suspend fun deleteAllCanvasEdges()

    @Query("DELETE FROM canvas_nodes")
    suspend fun deleteAllCanvasNodes()

    @Query("DELETE FROM notes_metadata")
    suspend fun deleteAllNotes()

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()

    @Query("DELETE FROM spaces")
    suspend fun deleteAllSpaces()

    @Query("DELETE FROM calendar_categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM property_tags")
    suspend fun deleteAllPropertyTags()

    @Query("DELETE FROM custom_properties")
    suspend fun deleteAllCustomProperties()

    @Query("DELETE FROM chat_sessions")
    suspend fun deleteAllChatSessions()

    @Query("DELETE FROM calendar_event_exceptions")
    suspend fun deleteAllCalendarEventExceptions()

    @Query("DELETE FROM self_host_deleted_notes")
    suspend fun deleteAllNoteTombstones()

    @Query("DELETE FROM calendar_tasks")
    suspend fun deleteAllCalendarTasks()

    @Query("DELETE FROM image_blocks")
    suspend fun deleteAllImageBlocks()

    @Query("DELETE FROM document_blocks")
    suspend fun deleteAllDocumentBlocks()

    @Query("DELETE FROM bookmark_blocks")
    suspend fun deleteAllBookmarkBlocks()

    @Query("DELETE FROM media_references")
    suspend fun deleteAllMediaReferences()

    @Query("DELETE FROM unapplied_sync_changes")
    suspend fun deleteAllUnappliedSyncChanges()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpaces(spaces: List<SpaceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteMetadataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<NoteBlockEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPropertyTags(tags: List<PropertyTagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomProperties(properties: List<CustomPropertyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatSessions(sessions: List<ChatSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEventExceptions(exceptions: List<CalendarEventExceptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteTombstones(tombstones: List<SelfHostDeletedNoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanvasNodes(nodes: List<CanvasNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanvasEdges(edges: List<CanvasEdgeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanvasStrokes(strokes: List<CanvasStrokeEntity>)
}
