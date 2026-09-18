package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.DocumentBlockEntity
import kotlinx.coroutines.flow.Flow

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
