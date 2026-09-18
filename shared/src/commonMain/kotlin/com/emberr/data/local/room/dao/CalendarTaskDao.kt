package com.emberr.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.emberr.data.local.room.entity.CalendarTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<CalendarTaskEntity>)

    // Fast query for the Calendar Pager to render the dot indicators.
    // Pass yearMonth as "2026-07" to get everything in July.
    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate LIKE :yearMonth || '%'")
    fun getTasksForMonth(spaceId: String, yearMonth: String): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate LIKE :yearMonth || '%'")
    suspend fun getTasksInMonth(spaceId: String, yearMonth: String): List<CalendarTaskEntity>

    // Precise query for the Bottom Sheet when a user clicks a specific day.
    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate = :dateString")
    fun getTasksForDate(spaceId: String, dateString: String): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate = :dateString")
    suspend fun getTasksOnDate(spaceId: String, dateString: String): List<CalendarTaskEntity>

    @Query("SELECT * FROM calendar_tasks WHERE blockId = :blockId LIMIT 1")
    suspend fun getTaskById(blockId: String): CalendarTaskEntity?

    // Allows the Bottom Sheet to instantly toggle a checkbox without loading the full note.
    @Query("UPDATE calendar_tasks SET isChecked = :isChecked WHERE blockId = :blockId")
    suspend fun updateTaskStatus(blockId: String, isChecked: Boolean)

    // Used by the NoteEditorViewModel/DailyEditorViewModel to clear old tasks before saving new ones.
    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND noteId = :noteId")
    suspend fun getTasksForNote(spaceId: String, noteId: String): List<CalendarTaskEntity>

    @Query("DELETE FROM calendar_tasks WHERE spaceId = :spaceId AND noteId = :noteId")
    suspend fun deleteTasksByNoteId(spaceId: String, noteId: String)

    // For when a user backspaces/deletes a single task in the editor.
    @Query("DELETE FROM calendar_tasks WHERE blockId = :blockId")
    suspend fun deleteTaskById(blockId: String)

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId AND targetDate >= :fromDate AND isChecked = 0 ORDER BY targetDate ASC")
    suspend fun getUpcomingTasks(spaceId: String, fromDate: String): List<CalendarTaskEntity>

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId")
    fun getAllTasksFlow(spaceId: String): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks WHERE spaceId = :spaceId")
    suspend fun getAllTasks(spaceId: String): List<CalendarTaskEntity>

    @Query("SELECT * FROM calendar_tasks")
    fun getAllTasksAcrossSpacesFlow(): Flow<List<CalendarTaskEntity>>

    @Query("SELECT * FROM calendar_tasks")
    suspend fun getAllTasksAcrossSpaces(): List<CalendarTaskEntity>
}
