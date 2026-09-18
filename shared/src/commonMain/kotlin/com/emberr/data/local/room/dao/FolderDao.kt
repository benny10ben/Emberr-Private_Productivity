package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

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
