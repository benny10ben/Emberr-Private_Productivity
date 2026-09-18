package com.emberr.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.CategoryEntity
import com.emberr.data.local.room.entity.TaskSource
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.RecurrenceEditScope
import com.emberr.domain.model.RecurrenceRule
import com.emberr.domain.model.markDeleted
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.domain.util.sync.SyncEventBus
import com.emberr.presentation.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class CalendarViewModel(
    private val repository: NoteRepository,
    private val reminderScheduler: ReminderScheduler,
    private val settingsManager: SettingsManager,
    private val activeSpaceStore: ActiveSpaceStore
) : ViewModel() {

    val categories: StateFlow<List<CalendarCategory>> = repository.getAllCategories()
        .map { entities -> entities.map { it.toCalendarCategory() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultCalendarCategories())
    val viewMode: StateFlow<CalendarViewMode> = settingsManager.calendarViewModeFlow
        .map { stored -> runCatching { CalendarViewMode.valueOf(stored) }.getOrDefault(CalendarViewMode.DAY) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarViewMode.DAY)

    fun setViewMode(mode: CalendarViewMode) {
        settingsManager.saveCalendarViewMode(mode.name)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            activeSpaceStore.activeSpaceId.collect { spaceId ->
                if (repository.getAllCategories().first().isEmpty()) {
                    val default = defaultCalendarCategories().first()
                    repository.insertOrUpdateCategory(
                        categoryId = defaultCategoryIdInSpace(default.id, spaceId),
                        name = default.name,
                        colorHex = default.colorHex
                    )
                }
            }
        }
    }

    fun addCategory(name: String, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertOrUpdateCategory(UUID.randomUUID().toString(), name, colorHex)
        }
    }

    fun updateCategory(id: String, name: String, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertOrUpdateCategory(id, name, colorHex)
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(id)
        }
    }

    fun eventsForDate(dateString: String): Flow<List<CalendarEvent>> =
        repository.getCalendarTasksForDate(dateString).map { tasks -> tasks.mapNotNull { it.toCalendarEvent() } }
    fun eventsForMonth(yearMonth: String): Flow<List<CalendarEvent>> =
        repository.getCalendarTasksForMonth(yearMonth).map { tasks -> tasks.mapNotNull { it.toCalendarEvent() } }

    // Used by the checkbox row's three-dot menu (Daily/Note screens) to look up the CalendarEvent
    // for a tapped blockId. When occurrenceDate is known (the checkbox is a virtual recurring
    // occurrence materialized on some day other than the series' anchor), it's resolved through the
    // same expansion getCalendarTasksForDate uses so per-occurrence overrides/completion apply -
    // otherwise it falls back to the base (anchor) row.
    fun eventForBlock(blockId: String, occurrenceDate: String? = null): Flow<CalendarEvent?> {
        val source = occurrenceDate?.let { repository.getCalendarTasksForDate(it) } ?: repository.getAllTasksFlow()
        return source.map { tasks -> tasks.firstOrNull { it.blockId == blockId }?.toCalendarEvent() }
    }

    // Events ARE checkboxes: creating/rescheduling one means writing a CheckboxBlock into that
    // day's note content (which re-derives the CalendarTaskEntity row as a side effect, see
    // NoteRepositoryImpl.syncCalendarTasks) - there is no separate "event" storage to keep in sync.
    fun saveEvent(
        original: CalendarEvent?,
        dateString: String,
        timestamp: Long,
        name: String,
        categoryId: String?,
        durationMinutes: Int,
        url: String?,
        description: String?,
        recurrenceRule: RecurrenceRule? = null,
        editScope: RecurrenceEditScope = RecurrenceEditScope.ALL_EVENTS
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val normalizedUrl = url?.trim()?.takeIf { it.isNotEmpty() }?.let(::normalizeEventUrl)
                val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }

                // A scoped edit ("this event" / "all future events" / "all past events") on an
                // already-recurring event needs series-splitting logic that lives in the
                // repository - the date is fixed to the acted-on occurrence in every scope but
                // ALL_EVENTS, since single-occurrence edits can't move a date (see plan).
                if (original != null && original.recurrenceRule != null && editScope != RecurrenceEditScope.ALL_EVENTS) {
                    repository.applyRecurrenceScopedEdit(
                        blockId = original.blockId,
                        occurrenceDate = original.dateString,
                        scope = editScope,
                        text = name,
                        timestamp = timestamp,
                        categoryId = categoryId,
                        durationMinutes = durationMinutes,
                        url = normalizedUrl,
                        description = normalizedDescription
                    )
                    SyncEventBus.emitSyncCompleted(original.dateString)
                    return@launch
                }

                val blockId = original?.blockId ?: UUID.randomUUID().toString()
                val now = System.currentTimeMillis()

                if (original != null && original.sourceType == TaskSource.NOTE) {
                    var notificationTitle = "Task Reminder"
                    SyncCoordinator.mutex.withLock {
                        val meta = repository.getNoteById(original.noteId) ?: return@withLock
                        val content = repository.getNoteContent(original.noteId) ?: return@withLock
                        val baseBlock = content.blocks.firstOrNull { it.id == blockId } as? CheckboxBlock
                            ?: CheckboxBlock(id = blockId, updatedAt = now)
                        val updatedBlock = baseBlock.copy(
                            text = name,
                            reminderTimestamp = timestamp,
                            categoryId = categoryId,
                            durationMinutes = durationMinutes,
                            url = normalizedUrl,
                            description = normalizedDescription,
                            recurrenceRule = recurrenceRule,
                            updatedAt = now
                        )
                        val updatedBlocks = content.blocks.map { if (it.id == blockId) updatedBlock else it }
                        repository.saveNote(meta.copy(updatedAt = now), NoteContent(blocks = updatedBlocks))
                        notificationTitle = meta.title.ifBlank { "Task Reminder" }
                    }
                    SyncEventBus.emitSyncCompleted(original.noteId)
                    reminderScheduler.schedule(blockId, notificationTitle, name.ifBlank { "Unfinished task" }, timestamp)
                    return@launch
                }

                var baseBlock = CheckboxBlock(id = blockId, updatedAt = now)
                var oldBlocks: List<NoteBlock> = emptyList()
                if (original != null) {
                    oldBlocks = repository.getDailyNote(original.dateString)?.blocks ?: emptyList()
                    (oldBlocks.firstOrNull { it.id == blockId } as? CheckboxBlock)?.let { baseBlock = it }
                }

                val updatedBlock = baseBlock.copy(
                    text = name,
                    reminderTimestamp = timestamp,
                    categoryId = categoryId,
                    durationMinutes = durationMinutes,
                    url = normalizedUrl,
                    description = normalizedDescription,
                    recurrenceRule = recurrenceRule,
                    updatedAt = now
                )

                SyncCoordinator.mutex.withLock {
                    val targetBlocks = repository.getDailyNote(dateString)?.blocks ?: emptyList()
                    val newBlocks = if (targetBlocks.any { it.id == blockId }) {
                        targetBlocks.map { if (it.id == blockId) updatedBlock else it }
                    } else {
                        listOf(updatedBlock) + targetBlocks
                    }
                    repository.saveDailyNote(dateString, NoteContent(blocks = newBlocks))

                    if (original != null && original.dateString != dateString) {
                        repository.saveDailyNote(
                            original.dateString,
                            NoteContent(blocks = oldBlocks.map { if (it.id == blockId) it.markDeleted() else it })
                        )
                    }
                }

                SyncEventBus.emitBlockMoved(
                    blockId = blockId,
                    fromDateString = original?.dateString?.takeIf { it != dateString },
                    toDateString = dateString
                )

                reminderScheduler.schedule(
                    blockId = blockId,
                    noteTitle = "Daily: $dateString",
                    text = name.ifBlank { "Unfinished task" },
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteEvent(event: CalendarEvent, editScope: RecurrenceEditScope = RecurrenceEditScope.ALL_EVENTS) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (event.recurrenceRule != null) {
                    // Routed through the repository even for ALL_EVENTS so the base block's
                    // markDeleted() and its now-orphaned per-occurrence exceptions are cleaned up
                    // together - see NoteRepositoryImpl.deleteEntireSeries.
                    repository.applyRecurrenceScopedDelete(event.blockId, event.dateString, editScope)
                    SyncEventBus.emitSyncCompleted(event.dateString)
                    reminderScheduler.cancel(event.blockId)
                    return@launch
                }

                if (event.sourceType == TaskSource.NOTE) {
                    SyncCoordinator.mutex.withLock {
                        val meta = repository.getNoteById(event.noteId) ?: return@launch
                        val content = repository.getNoteContent(event.noteId) ?: return@launch
                        val updatedBlocks = content.blocks.map { if (it.id == event.blockId) it.markDeleted() else it }
                        repository.saveNote(meta.copy(updatedAt = System.currentTimeMillis()), NoteContent(blocks = updatedBlocks))
                    }
                    SyncEventBus.emitSyncCompleted(event.noteId)
                } else {
                    SyncCoordinator.mutex.withLock {
                        val blocks = repository.getDailyNote(event.dateString)?.blocks ?: return@launch
                        repository.saveDailyNote(
                            event.dateString,
                            NoteContent(blocks = blocks.map { if (it.id == event.blockId) it.markDeleted() else it })
                        )
                    }
                    SyncEventBus.emitBlockRemoved(event.blockId, event.dateString)
                }
                reminderScheduler.cancel(event.blockId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

private fun defaultCategoryIdInSpace(categoryId: String, spaceId: String) = "${categoryId}_$spaceId"

private fun CategoryEntity.toCalendarCategory() = CalendarCategory(
    id = categoryId,
    name = name,
    colorHex = colorHex
)

// UriHandler.openUri requires an absolute URI with a scheme - users typically type
// "example.com" rather than "https://example.com", so default a missing scheme to https
// instead of letting the link silently fail to open when tapped in the read view.
private fun normalizeEventUrl(raw: String): String =
    if (raw.contains("://")) raw else "https://$raw"
