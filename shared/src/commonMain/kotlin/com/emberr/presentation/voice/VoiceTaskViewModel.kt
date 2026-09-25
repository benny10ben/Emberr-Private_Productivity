package com.emberr.presentation.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.ParsedTask
import com.emberr.domain.model.TextBlock
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.eventbus.VoiceTaskEventBus
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.domain.util.task.TaskExtractor
import com.emberr.domain.util.voice.VoiceRecognizer
import com.emberr.presentation.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.collections.iterator
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class VoiceTaskViewModel(
    private val repository: NoteRepository,
    private val reminderScheduler: ReminderScheduler,
    private val voiceRecognizer: VoiceRecognizer,
    taskExtractor: TaskExtractor
) : ViewModel() {

    private val voiceTaskSession = VoiceTaskSession(voiceRecognizer, taskExtractor, viewModelScope)
    val sessionState: StateFlow<VoiceTaskSessionState> = voiceTaskSession.state

    fun startListening(onPermissionNeeded: () -> Unit = {}) {
        voiceTaskSession.startListening(onPermissionNeeded)
    }

    fun stopListening() {
        voiceTaskSession.stopListening()
    }

    fun editTask(index: Int, newText: String) {
        voiceTaskSession.editTask(index, newText)
    }

    fun setTaskReminder(index: Int, reminder: Long?) {
        voiceTaskSession.setTaskReminder(index, reminder)
    }

    fun addTasks() {
        saveTasks(voiceTaskSession.end())
    }

    fun discardTasks() {
        voiceTaskSession.end()
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognizer.destroy()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun saveTasks(parsedTasks: List<ParsedTask>) {
        if (parsedTasks.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val systemTZ = TimeZone.currentSystemDefault()

            val tasksByDate = parsedTasks.groupBy { task ->
                if (task.timestamp != null) {
                    Instant.fromEpochMilliseconds(task.timestamp)
                        .toLocalDateTime(systemTZ)
                        .date
                        .toString()
                } else {
                    Clock.System.todayIn(systemTZ).toString()
                }
            }

            try {
                SyncCoordinator.mutex.withLock {
                    for ((targetDateString, tasks) in tasksByDate) {
                        val content = repository.getDailyNote(targetDateString)
                        val currentBlocks = mutableListOf<NoteBlock>()

                        if (content != null && content.blocks.isNotEmpty()) {
                            currentBlocks.addAll(content.blocks)
                        } else {
                            currentBlocks.add(
                                TextBlock(
                                    id = "root_$targetDateString",
                                    text = "",
                                    updatedAt = Clock.System.now().toEpochMilliseconds()
                                )
                            )
                        }

                        for (task in tasks) {
                            val newVoiceTaskBlock = CheckboxBlock(
                                id = Uuid.random().toString(),
                                text = task.taskText,
                                isChecked = false,
                                reminderTimestamp = task.timestamp,
                                indentationLevel = 0,
                                updatedAt = Clock.System.now().toEpochMilliseconds()
                            )

                            currentBlocks.add(newVoiceTaskBlock)

                            VoiceTaskEventBus.emitTaskAdded(targetDateString, newVoiceTaskBlock)

                            task.timestamp?.let { timeInMillis ->
                                reminderScheduler.schedule(
                                    blockId = newVoiceTaskBlock.id,
                                    noteTitle = "Daily: $targetDateString",
                                    text = task.taskText,
                                    timestamp = timeInMillis
                                )
                            }
                        }

                        repository.saveDailyNote(targetDateString, NoteContent(blocks = currentBlocks))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
