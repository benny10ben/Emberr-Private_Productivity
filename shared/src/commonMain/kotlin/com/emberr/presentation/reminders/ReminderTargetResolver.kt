package com.emberr.presentation.reminders

import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.dao.NoteDao

data class ReminderTarget(
    val spaceId: String,
    val noteId: String,
    val isDaily: Boolean,
    val dateString: String?
)

class ReminderTargetResolver(
    private val calendarTaskDao: CalendarTaskDao,
    private val noteDao: NoteDao
) {

    suspend fun resolve(blockId: String): ReminderTarget? {
        val task = calendarTaskDao.getTaskById(blockId) ?: return null
        val note = noteDao.getNoteById(task.noteId) ?: return null
        if (note.trashedAt != null) return null

        return ReminderTarget(
            spaceId = note.spaceId,
            noteId = note.noteId,
            isDaily = note.isDaily,
            dateString = note.dateString
        )
    }
}
