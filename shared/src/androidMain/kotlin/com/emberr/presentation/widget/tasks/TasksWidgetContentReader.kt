// Loads the task list from storage and groups it into rows the widget can draw.
package com.emberr.presentation.widget.tasks

import com.emberr.data.local.room.CalendarTaskDao
import com.emberr.data.local.room.CalendarTaskEntity
import com.emberr.data.local.room.NoteDao
import com.emberr.data.local.room.TaskSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import com.emberr.presentation.widget.WidgetLog

private const val maximumTasksShown = 40
private const val maximumCharactersPerTask = 160
private const val inboxNoteTitle = "Inbox"

class TasksWidgetContentReader(
    private val calendarTaskDao: CalendarTaskDao,
    private val noteDao: NoteDao
) {
    fun observeTasks(): Flow<List<CalendarTaskEntity>> =
        calendarTaskDao.getAllTasksAcrossSpacesFlow()
            .flowOn(Dispatchers.IO)
            .catch { cause -> WidgetLog.e("Stopped observing the task list", cause) }

    suspend fun readContentOnce(spaceId: String, isShowingCompleted: Boolean): TasksWidgetContent? =
        withContext(Dispatchers.IO) {
            val tasks = try {
                calendarTaskDao.getAllTasks(spaceId)
            } catch (cause: Exception) {
                WidgetLog.e("Could not read the task list", cause)
                return@withContext null
            }
            buildContent(spaceId, tasks, isShowingCompleted)
        }

    suspend fun buildContent(
        spaceId: String,
        tasks: List<CalendarTaskEntity>,
        isShowingCompleted: Boolean
    ): TasksWidgetContent = withContext(Dispatchers.Default) {
        val matchingTasks = tasks.filter { task ->
            task.spaceId == spaceId && task.isChecked == isShowingCompleted
        }
        TasksWidgetContent(
            isShowingCompleted = isShowingCompleted,
            rows = buildRows(matchingTasks)
        )
    }

    private suspend fun buildRows(tasks: List<CalendarTaskEntity>): List<TasksWidgetRow> {
        if (tasks.isEmpty()) return emptyList()

        val noteTitlesById = resolveNoteTitles(tasks)
        val inboxNoteId = findInboxNoteId(noteTitlesById)

        val (unlabelled, labelled) = tasks.partition { task ->
            headingFor(task, noteTitlesById, inboxNoteId) == null
        }

        val dailyTasks = labelled
            .filter { task -> task.sourceType == TaskSource.DAILY }
            .sortedBy { task -> task.noteId }
        val noteTasks = labelled
            .filter { task -> task.sourceType != TaskSource.DAILY }
            .sortedBy { task -> noteTitlesById[task.noteId].orEmpty() }

        val orderedTasks = (sortedWithinGroup(unlabelled) + dailyTasks + noteTasks)
            .take(maximumTasksShown)

        val rows = mutableListOf<TasksWidgetRow>()
        var lastHeading: String? = null

        orderedTasks.forEach { task ->
            val heading = headingFor(task, noteTitlesById, inboxNoteId)
            if (heading != null && heading != lastHeading) {
                rows += TasksWidgetRow.SectionHeading(key = "heading:${task.blockId}", label = heading)
            }
            lastHeading = heading

            rows += TasksWidgetRow.Task(
                key = "task:${task.blockId}",
                blockId = task.blockId,
                text = task.text.trim().take(maximumCharactersPerTask).ifBlank { "Untitled task" },
                isChecked = task.isChecked,
                timeLabel = task.reminderTimestamp?.let { timestamp -> formatClockTime(timestamp) }
            )
        }

        return rows
    }

    private fun sortedWithinGroup(tasks: List<CalendarTaskEntity>): List<CalendarTaskEntity> =
        tasks.sortedWith(
            compareBy(
                { task -> task.reminderTimestamp ?: Long.MAX_VALUE },
                { task -> task.text.lowercase() }
            )
        )

    private suspend fun resolveNoteTitles(tasks: List<CalendarTaskEntity>): Map<String, String> {
        val noteIds = tasks
            .filter { task -> task.sourceType != TaskSource.DAILY }
            .map { task -> task.noteId }
            .distinct()
        if (noteIds.isEmpty()) return emptyMap()

        return try {
            noteDao.getNotesByIds(noteIds).associate { note -> note.noteId to note.title.trim() }
        } catch (cause: Exception) {
            WidgetLog.e("Could not resolve note titles for the task list", cause)
            emptyMap()
        }
    }

    private fun findInboxNoteId(noteTitlesById: Map<String, String>): String? =
        noteTitlesById.entries
            .firstOrNull { entry -> entry.value.equals(inboxNoteTitle, ignoreCase = true) }
            ?.key

    private fun headingFor(
        task: CalendarTaskEntity,
        noteTitlesById: Map<String, String>,
        inboxNoteId: String?
    ): String? = when {
        task.sourceType == TaskSource.DAILY -> dailyDateLabel(task.noteId)
        task.noteId == inboxNoteId -> null
        else -> noteTitlesById[task.noteId]?.takeIf { it.isNotBlank() } ?: "Note"
    }
}

private fun dailyDateLabel(dateString: String): String {
    val date = try {
        LocalDate.parse(dateString)
    } catch (cause: IllegalArgumentException) {
        return dateString
    }

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return when (date) {
        today -> "Today"
        today.plus(DatePeriod(days = 1)) -> "Tomorrow"
        today.minus(DatePeriod(days = 1)) -> "Yesterday"
        else -> {
            val shortDay = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            val shortMonth = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
            "$shortDay, $shortMonth ${date.day}"
        }
    }
}

private fun formatClockTime(timestamp: Long): String {
    val localTime = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localTime.hour.toString().padStart(2, '0')}:${localTime.minute.toString().padStart(2, '0')}"
}
