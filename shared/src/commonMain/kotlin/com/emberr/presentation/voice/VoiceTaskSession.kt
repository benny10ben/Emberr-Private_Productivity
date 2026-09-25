package com.emberr.presentation.voice

import com.emberr.domain.model.ParsedTask
import com.emberr.domain.util.task.TaskExtractor
import com.emberr.domain.util.voice.VoiceRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val NO_SPEECH_HEARD_MESSAGE = "No match"
private const val PAUSE_SEPARATOR = ", "

data class VoiceTaskSessionState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val tasks: List<ParsedTask> = emptyList(),
    val errorMessage: String? = null
)

class VoiceTaskSession(
    private val voiceRecognizer: VoiceRecognizer,
    private val taskExtractor: TaskExtractor,
    private val scope: CoroutineScope,
    private val silenceTimeout: Duration = 5.seconds
) {
    private val _state = MutableStateFlow(VoiceTaskSessionState())
    val state: StateFlow<VoiceTaskSessionState> = _state.asStateFlow()

    private var silenceTimeoutJob: Job? = null
    private var sessionNumber = 0
    private var finishedTasks = emptyList<ParsedTask>()
    private var currentRunText = ""

    fun startListening(onPermissionNeeded: () -> Unit) {
        if (_state.value.isListening) return
        _state.update { it.copy(isListening = true, partialText = "", errorMessage = null) }
        restartSilenceTimeout()
        listen(sessionNumber, onPermissionNeeded)
    }

    fun stopListening() {
        silenceTimeoutJob?.cancel()
        silenceTimeoutJob = null
        voiceRecognizer.stopListening()
        finishedTasks = finishedTasks + taskExtractor.extractTasks(currentRunText)
        currentRunText = ""
        _state.update { it.copy(isListening = false, partialText = "", tasks = finishedTasks) }
    }

    fun editTask(index: Int, newText: String) {
        updateFinishedTask(index) { it.copy(taskText = newText) }
    }

    fun setTaskReminder(index: Int, reminder: Long?) {
        updateFinishedTask(index) { it.copy(timestamp = reminder) }
    }

    private fun updateFinishedTask(index: Int, change: (ParsedTask) -> ParsedTask) {
        if (_state.value.isListening) stopListening()
        if (index !in finishedTasks.indices) return
        finishedTasks = finishedTasks.mapIndexed { taskIndex, task ->
            if (taskIndex == index) change(task) else task
        }
        _state.update { it.copy(tasks = finishedTasks) }
    }

    fun end(): List<ParsedTask> {
        sessionNumber++
        stopListening()
        val recordedTasks = finishedTasks
            .map { it.copy(taskText = it.taskText.trim()) }
            .filter { it.taskText.isNotEmpty() }
        finishedTasks = emptyList()
        _state.value = VoiceTaskSessionState()
        return recordedTasks
    }

    private fun listen(session: Int, onPermissionNeeded: () -> Unit) {
        voiceRecognizer.startListening(
            onPartial = { text -> handlePartial(session, text) },
            onSegment = { text -> handleSegment(session, text) },
            onResult = { transcript -> handleResult(session, transcript, onPermissionNeeded) },
            onError = { message -> handleError(session, message, onPermissionNeeded) },
            onPermissionNeeded = { handlePermissionNeeded(session, onPermissionNeeded) }
        )
    }

    private fun handlePartial(session: Int, text: String) {
        if (session != sessionNumber || !_state.value.isListening || text.isBlank()) return
        _state.update { it.copy(partialText = text) }
        restartSilenceTimeout()
    }

    private fun handleSegment(session: Int, text: String) {
        if (session != sessionNumber) return
        addHeardText(text)
        if (_state.value.isListening && text.isNotBlank()) restartSilenceTimeout()
    }

    private fun handleResult(session: Int, transcript: String, onPermissionNeeded: () -> Unit) {
        if (session != sessionNumber) return
        addHeardText(transcript)
        if (_state.value.isListening) {
            if (transcript.isNotBlank()) restartSilenceTimeout()
            listen(session, onPermissionNeeded)
        }
    }

    private fun addHeardText(text: String) {
        if (text.isNotBlank()) {
            if (_state.value.isListening) {
                val separator = if (currentRunText.isEmpty()) "" else PAUSE_SEPARATOR
                currentRunText += separator + text.trim()
            } else {
                finishedTasks = finishedTasks + taskExtractor.extractTasks(text)
            }
        }
        val currentRunTasks = taskExtractor.extractTasks(currentRunText)
        _state.update { it.copy(tasks = finishedTasks + currentRunTasks, partialText = "") }
    }

    private fun handleError(session: Int, message: String, onPermissionNeeded: () -> Unit) {
        if (session != sessionNumber || !_state.value.isListening) return
        if (message == NO_SPEECH_HEARD_MESSAGE) {
            listen(session, onPermissionNeeded)
        } else {
            stopListening()
            _state.update { it.copy(errorMessage = message) }
        }
    }

    private fun handlePermissionNeeded(session: Int, onPermissionNeeded: () -> Unit) {
        if (session != sessionNumber) return
        stopListening()
        onPermissionNeeded()
    }

    private fun restartSilenceTimeout() {
        silenceTimeoutJob?.cancel()
        silenceTimeoutJob = scope.launch {
            delay(silenceTimeout)
            stopListening()
        }
    }
}
