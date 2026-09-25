package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity

@Dao
interface CanvasDao {
    @Query("SELECT * FROM canvas_nodes WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getAllNodesForNoteIncludingDeleted(noteId: String): List<CanvasNodeEntity>

    @Query("SELECT * FROM canvas_edges WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun getAllEdgesForNoteIncludingDeleted(noteId: String): List<CanvasEdgeEntity>

    @Query("SELECT * FROM canvas_nodes")
    suspend fun getAllNodesForBackup(): List<CanvasNodeEntity>

    @Query("SELECT * FROM canvas_edges")
    suspend fun getAllEdgesForBackup(): List<CanvasEdgeEntity>

    @Upsert
    suspend fun upsertNodes(nodes: List<CanvasNodeEntity>)

    @Upsert
    suspend fun upsertEdges(edges: List<CanvasEdgeEntity>)

    @Query("DELETE FROM canvas_nodes WHERE noteId = :noteId")
    suspend fun deleteAllNodesForNote(noteId: String)

    @Query("DELETE FROM canvas_edges WHERE noteId = :noteId")
    suspend fun deleteAllEdgesForNote(noteId: String)
}
