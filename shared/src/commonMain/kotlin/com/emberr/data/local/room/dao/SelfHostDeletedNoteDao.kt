package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity

@Dao
interface SelfHostDeletedNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: SelfHostDeletedNoteEntity)

    @Query("SELECT * FROM self_host_deleted_notes")
    suspend fun getAllTombstones(): List<SelfHostDeletedNoteEntity>

    @Query("UPDATE self_host_deleted_notes SET remoteFileDeleted = 1 WHERE noteId = :noteId")
    suspend fun markRemoteFileDeleted(noteId: String)

    // Shared by both sync engines - self-host uses getAllTombstones/markRemoteFileDeleted for
    // its own manifest-tombstone bookkeeping; LAN sync uses the queries below to propagate a hard
    // delete over the peer-to-peer protocol and to guard against resurrecting an already-deleted note.
    @Query("SELECT * FROM self_host_deleted_notes WHERE deletedAt > :timestamp")
    suspend fun getTombstonesModifiedSince(timestamp: Long): List<SelfHostDeletedNoteEntity>

    @Query("SELECT * FROM self_host_deleted_notes WHERE noteId = :noteId LIMIT 1")
    suspend fun getTombstoneByNoteId(noteId: String): SelfHostDeletedNoteEntity?

    @Query("SELECT * FROM self_host_deleted_notes WHERE spaceId = :spaceId AND dateString = :dateString LIMIT 1")
    suspend fun getTombstoneByDateString(spaceId: String, dateString: String): SelfHostDeletedNoteEntity?
}
