package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.UnusedMediaFileEntity

@Dao
interface UnusedMediaFileDao {

    @Query("SELECT * FROM unused_media_files")
    suspend fun getAll(): List<UnusedMediaFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markUnused(files: List<UnusedMediaFileEntity>)

    @Query("DELETE FROM unused_media_files WHERE fileName IN (:fileNames)")
    suspend fun forget(fileNames: List<String>)

    @Query("DELETE FROM unused_media_files")
    suspend fun forgetAll()
}
