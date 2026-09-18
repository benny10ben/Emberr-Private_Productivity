package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.MediaReferenceEntity

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
