package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCategory(category: CategoryEntity)

    @Query("SELECT * FROM calendar_categories WHERE spaceId = :spaceId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllCategories(spaceId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM calendar_categories WHERE spaceId = :spaceId AND isDeleted = 0")
    suspend fun getAllCategoriesOnce(spaceId: String): List<CategoryEntity>

    @Query("SELECT * FROM calendar_categories WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllCategoriesAcrossSpaces(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM calendar_categories WHERE isDeleted = 0")
    suspend fun getAllCategoriesOnceAcrossSpaces(): List<CategoryEntity>

    @Query("SELECT * FROM calendar_categories WHERE categoryId = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): CategoryEntity?

    @Query("SELECT * FROM calendar_categories WHERE updatedAt > :timestamp")
    suspend fun getCategoriesModifiedSince(timestamp: Long): List<CategoryEntity>

    @Query("UPDATE calendar_categories SET isDeleted = 1, updatedAt = :updatedAt WHERE categoryId = :categoryId")
    suspend fun markCategoryDeleted(categoryId: String, updatedAt: Long)
}
