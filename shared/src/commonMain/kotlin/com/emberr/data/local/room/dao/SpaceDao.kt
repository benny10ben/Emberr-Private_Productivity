package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.SpaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSpace(space: SpaceEntity)

    @Query("SELECT * FROM spaces WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllSpaces(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllSpacesOnce(): List<SpaceEntity>

    @Query("SELECT * FROM spaces WHERE spaceId = :spaceId LIMIT 1")
    suspend fun getSpaceById(spaceId: String): SpaceEntity?

    @Query("SELECT COUNT(*) FROM spaces WHERE isDeleted = 0")
    suspend fun countSpaces(): Int

    @Query("SELECT * FROM spaces WHERE updatedAt > :timestamp")
    suspend fun getSpacesModifiedSince(timestamp: Long): List<SpaceEntity>

    @Query("SELECT * FROM spaces")
    suspend fun getAllSpacesForBackup(): List<SpaceEntity>

    @Query("UPDATE spaces SET displayName = :displayName, updatedAt = :updatedAt WHERE spaceId = :spaceId")
    suspend fun renameSpace(spaceId: String, displayName: String, updatedAt: Long)

    @Query("UPDATE spaces SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE spaceId = :spaceId")
    suspend fun updateSpaceSortOrder(spaceId: String, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE spaces SET isDeleted = 1, updatedAt = :updatedAt WHERE spaceId = :spaceId")
    suspend fun markSpaceDeleted(spaceId: String, updatedAt: Long)
}
