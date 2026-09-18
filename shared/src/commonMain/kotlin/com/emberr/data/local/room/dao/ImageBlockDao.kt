package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.ImageBlockEntity
import kotlinx.coroutines.flow.Flow

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
