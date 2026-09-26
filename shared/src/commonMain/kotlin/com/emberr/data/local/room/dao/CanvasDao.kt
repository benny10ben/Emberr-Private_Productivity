package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasStrokeEntity

@Dao
interface CanvasDao {
    @Query("SELECT * FROM canvas_nodes WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getAllNodesForNoteIncludingDeleted(noteId: String): List<CanvasNodeEntity>

    @Query("SELECT * FROM canvas_edges WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getAllEdgesForNoteIncludingDeleted(noteId: String): List<CanvasEdgeEntity>

    @Query("SELECT * FROM canvas_strokes WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getAllStrokesForNoteIncludingDeleted(noteId: String): List<CanvasStrokeEntity>

    @Query("SELECT * FROM canvas_nodes")
    suspend fun getAllNodesForBackup(): List<CanvasNodeEntity>

    @Query("SELECT * FROM canvas_edges")
    suspend fun getAllEdgesForBackup(): List<CanvasEdgeEntity>

    @Query("SELECT * FROM canvas_strokes")
    suspend fun getAllStrokesForBackup(): List<CanvasStrokeEntity>

    @Query(
        "SELECT DISTINCT canvas_nodes.noteId FROM canvas_nodes " +
            "JOIN notes_metadata ON notes_metadata.noteId = canvas_nodes.noteId " +
            "WHERE canvas_nodes.isDeleted = 0 AND notes_metadata.trashedAt IS NULL AND notes_metadata.spaceId = :spaceId " +
            "AND canvas_nodes.text LIKE '%' || :query || '%'"
    )
    suspend fun findCanvasNoteIdsWithTextMatching(spaceId: String, query: String): List<String>

    @Query(
        "SELECT text FROM canvas_nodes WHERE noteId = :noteId AND isDeleted = 0 " +
            "AND text LIKE '%' || :query || '%' ORDER BY createdAt ASC LIMIT 1"
    )
    suspend fun findFirstNodeTextMatching(noteId: String, query: String): String?

    @Upsert
    suspend fun upsertNodes(nodes: List<CanvasNodeEntity>)

    @Upsert
    suspend fun upsertEdges(edges: List<CanvasEdgeEntity>)

    @Upsert
    suspend fun upsertStrokes(strokes: List<CanvasStrokeEntity>)

    @Query("DELETE FROM canvas_nodes WHERE noteId = :noteId")
    suspend fun deleteAllNodesForNote(noteId: String)

    @Query("DELETE FROM canvas_edges WHERE noteId = :noteId")
    suspend fun deleteAllEdgesForNote(noteId: String)

    @Query("DELETE FROM canvas_strokes WHERE noteId = :noteId")
    suspend fun deleteAllStrokesForNote(noteId: String)
}
