package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTag(tag: TagEntity)

    @Query("SELECT * FROM global_tags WHERE spaceId = :spaceId AND isDeleted = 0 ORDER BY name ASC")
    fun getAllTags(spaceId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM global_tags WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllTagsAcrossSpaces(): Flow<List<TagEntity>>

    @Query("UPDATE global_tags SET isDeleted = 1, updatedAt = :updatedAt WHERE tagId = :tagId")
    suspend fun markTagDeleted(tagId: String, updatedAt: Long)

    @Query("SELECT * FROM global_tags WHERE updatedAt > :timestamp")
    suspend fun getTagsModifiedSince(timestamp: Long): List<TagEntity>

    @Query("SELECT * FROM global_tags WHERE tagId = :tagId LIMIT 1")
    suspend fun getTagById(tagId: String): TagEntity?
}
