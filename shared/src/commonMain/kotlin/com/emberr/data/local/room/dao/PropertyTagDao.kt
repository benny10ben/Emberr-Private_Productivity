package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.PropertyTagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTag(tag: PropertyTagEntity)

    @Query("SELECT * FROM property_tags WHERE spaceId = :spaceId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllTags(spaceId: String): Flow<List<PropertyTagEntity>>

    @Query("SELECT * FROM property_tags WHERE spaceId = :spaceId AND isDeleted = 0")
    suspend fun getAllTagsOnce(spaceId: String): List<PropertyTagEntity>

    @Query("SELECT * FROM property_tags WHERE tagId = :tagId LIMIT 1")
    suspend fun getTagById(tagId: String): PropertyTagEntity?

    @Query("SELECT * FROM property_tags WHERE updatedAt > :timestamp")
    suspend fun getTagsModifiedSince(timestamp: Long): List<PropertyTagEntity>

    @Query("SELECT * FROM property_tags")
    suspend fun getAllTagsForBackup(): List<PropertyTagEntity>
}
