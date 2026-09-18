// Ticking a task off, shared by every widget that shows tasks, plus the refresh that follows it.
package com.emberr.presentation.widget

import android.content.Context
import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.domain.repository.NoteRepository
import com.emberr.presentation.widget.tasks.refreshTaskWidgets
import com.emberr.presentation.widget.todaytasks.refreshTodayTasksWidgets
import org.koin.core.context.GlobalContext

suspend fun applyTaskCompletion(blockId: String, isChecked: Boolean) {
    val koin = GlobalContext.get()
    val noteRepository = koin.get<NoteRepository>()
    val calendarTaskDao = koin.get<CalendarTaskDao>()

    val task = calendarTaskDao.getTaskById(blockId)
    val occurrenceDate = task?.targetDate

    if (task?.recurrenceFrequency != null && occurrenceDate != null) {
        noteRepository.upsertOccurrenceCompletion(blockId, occurrenceDate, isChecked)
    } else {
        noteRepository.toggleTaskCompletion(blockId, isChecked)
    }
}

suspend fun refreshEveryTaskWidget(context: Context) {
    refreshTaskWidgets(context)
    refreshTodayTasksWidgets(context)
}
