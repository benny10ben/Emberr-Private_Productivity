package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.CalendarEventExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventExceptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exception: CalendarEventExceptionEntity)

    @Query("SELECT * FROM calendar_event_exceptions")
    fun getAllExceptionsFlow(): Flow<List<CalendarEventExceptionEntity>>

    @Query("SELECT * FROM calendar_event_exceptions WHERE blockId = :blockId AND occurrenceDate = :occurrenceDate LIMIT 1")
    suspend fun getException(blockId: String, occurrenceDate: String): CalendarEventExceptionEntity?

    @Query("SELECT * FROM calendar_event_exceptions WHERE updatedAt > :timestamp")
    suspend fun getExceptionsModifiedSince(timestamp: Long): List<CalendarEventExceptionEntity>

    @Query("DELETE FROM calendar_event_exceptions WHERE blockId = :blockId")
    suspend fun deleteExceptionsForBlock(blockId: String)

    // Used when a series is split by an "all future events" edit/delete - the truncated
    // original block keeps only exceptions dated before the split point.
    @Query("DELETE FROM calendar_event_exceptions WHERE blockId = :blockId AND occurrenceDate >= :fromDateInclusive")
    suspend fun deleteExceptionsFrom(blockId: String, fromDateInclusive: String)

    // Symmetric case for "all past events" - the original block's anchor moves forward, so
    // exceptions dated at/before the split point no longer belong to it.
    @Query("DELETE FROM calendar_event_exceptions WHERE blockId = :blockId AND occurrenceDate <= :toDateInclusive")
    suspend fun deleteExceptionsUpTo(blockId: String, toDateInclusive: String)

    @Query("UPDATE calendar_event_exceptions SET blockId = :newBlockId, updatedAt = :updatedAt WHERE blockId = :oldBlockId AND occurrenceDate >= :fromDateInclusive")
    suspend fun rekeyExceptionsFrom(oldBlockId: String, newBlockId: String, fromDateInclusive: String, updatedAt: Long)

    @Query("UPDATE calendar_event_exceptions SET blockId = :newBlockId, updatedAt = :updatedAt WHERE blockId = :oldBlockId AND occurrenceDate <= :toDateInclusive")
    suspend fun rekeyExceptionsUpTo(oldBlockId: String, newBlockId: String, toDateInclusive: String, updatedAt: Long)
}
