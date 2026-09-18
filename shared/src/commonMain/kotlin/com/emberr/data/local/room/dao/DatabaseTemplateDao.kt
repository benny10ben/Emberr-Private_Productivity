package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.DatabaseTemplateEntity
import kotlinx.coroutines.flow.Flow

/**
 * Manages saved DatabaseBlock schemas (columns/views) that users can reuse when
 * creating a new database block.
 */
@Dao
interface DatabaseTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: DatabaseTemplateEntity)

    @Query("SELECT * FROM database_templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<DatabaseTemplateEntity>>

    @Query("DELETE FROM database_templates WHERE templateId = :templateId")
    suspend fun deleteTemplate(templateId: String)
}
