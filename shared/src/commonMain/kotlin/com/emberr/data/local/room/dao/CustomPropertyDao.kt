package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.CustomPropertyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPropertyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProperty(property: CustomPropertyEntity)

    @Query("SELECT * FROM custom_properties WHERE spaceId = :spaceId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllProperties(spaceId: String): Flow<List<CustomPropertyEntity>>

    @Query("SELECT * FROM custom_properties WHERE propertyId = :propertyId LIMIT 1")
    suspend fun getPropertyById(propertyId: String): CustomPropertyEntity?

    @Query("SELECT * FROM custom_properties WHERE updatedAt > :timestamp")
    suspend fun getPropertiesModifiedSince(timestamp: Long): List<CustomPropertyEntity>

    @Query("SELECT * FROM custom_properties")
    suspend fun getAllPropertiesForBackup(): List<CustomPropertyEntity>
}
