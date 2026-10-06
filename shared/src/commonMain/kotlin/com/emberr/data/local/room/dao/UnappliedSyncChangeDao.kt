package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.UnappliedSyncChangeEntity

@Dao
interface UnappliedSyncChangeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveChange(change: UnappliedSyncChangeEntity)

    @Query("SELECT * FROM unapplied_sync_changes WHERE failedOnAppVersion != :appVersion")
    suspend fun getChangesThatFailedOnAnotherAppVersion(appVersion: String): List<UnappliedSyncChangeEntity>

    @Query("DELETE FROM unapplied_sync_changes WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteChange(entityType: String, entityId: String)
}
