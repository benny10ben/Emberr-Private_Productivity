package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.NoteBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId AND isDeleted = 0 ORDER BY displayOrder ASC")
    suspend fun getBlocksForNote(noteId: String): List<NoteBlockEntity>

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId AND isDeleted = 0 ORDER BY displayOrder ASC")
    fun observeBlocksForNote(noteId: String): Flow<List<NoteBlockEntity>>

    @Query("SELECT * FROM note_blocks WHERE noteId = :noteId ORDER BY displayOrder ASC")
    suspend fun getAllBlocksForNoteIncludingDeleted(noteId: String): List<NoteBlockEntity>

    @Query(
        "SELECT DISTINCT noteId FROM note_blocks WHERE isDeleted = 0 " +
            "AND blockDataJson LIKE '%' || :query || '%' " +
            "AND noteId IN (SELECT noteId FROM notes_metadata WHERE spaceId = :spaceId)"
    )
    suspend fun findNoteIdsMatchingContent(spaceId: String, query: String): List<String>

    // Lets SQLite do the "which blocks could possibly match" filtering so cross-note search
    // only ever JSON-decodes blocks that already contain the query text.
    @Query(
        "SELECT * FROM note_blocks WHERE noteId = :noteId AND isDeleted = 0 " +
            "AND blockDataJson LIKE '%' || :query || '%' ORDER BY displayOrder ASC"
    )
    suspend fun findMatchingBlocksForNote(noteId: String, query: String): List<NoteBlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBlock(block: NoteBlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBlocks(blocks: List<NoteBlockEntity>)

    @Query("DELETE FROM note_blocks WHERE noteId = :noteId")
    suspend fun deleteAllBlocksForNote(noteId: String)
}