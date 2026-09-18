package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.SelfHostDeletedApiConfigEntity

@Dao
interface SelfHostDeletedApiConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: SelfHostDeletedApiConfigEntity)

    @Query("SELECT * FROM self_host_deleted_api_configs")
    suspend fun getAllTombstones(): List<SelfHostDeletedApiConfigEntity>

    @Query("SELECT * FROM self_host_deleted_api_configs WHERE deletedAt > :timestamp")
    suspend fun getTombstonesModifiedSince(timestamp: Long): List<SelfHostDeletedApiConfigEntity>

    @Query("SELECT * FROM self_host_deleted_api_configs WHERE provider = :provider LIMIT 1")
    suspend fun getTombstoneByProvider(provider: String): SelfHostDeletedApiConfigEntity?
}
