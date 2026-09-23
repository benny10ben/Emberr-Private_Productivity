package com.emberr.presentation.mobile.voice

import com.emberr.domain.model.ParsedTask
import com.emberr.domain.util.task.HeuristicTaskExtractor
import com.emberr.domain.util.task.TaskExtractor
import com.emberr.domain.util.voice.VoiceRecognizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceTaskSessionTest {

    private class FakeVoiceRecognizer : VoiceRecognizer {
        var startCount = 0
        var stopCount = 0
        private var onPartial: (String) -> Unit = {}
        private var onSegment: (String) -> Unit = {}
        private var onResult: (String) -> Unit = {}
        private var onError: (String) -> Unit = {}
        private var onPermissionNeeded: () -> Unit = {}

        override fun startListening(
            onPartial: (String) -> Unit,
            onSegment: (String) -> Unit,
            onResult: (String) -> Unit,
            onError: (String) -> Unit,
            onPermissionNeeded: () -> Unit
        ) {
            startCount++
            this.onPartial = onPartial
            this.onSegment = onSegment
            this.onResult = onResult
            this.onError = onError
            this.onPermissionNeeded = onPermissionNeeded
        }

        override fun stopListening() {
            stopCount++
        }

        override fun destroy() {}

        fun hearPartially(text: String) = onPartial(text)
        fun hearPieceThenKeepListening(text: String) = onSegment(text)
        fun hearSentence(transcript: String) = onResult(transcript)
        fun fail(message: String) = onError(message)
        fun askForPermission() = onPermissionNeeded()
    }

    private class OneTaskPerPieceExtractor : TaskExtractor {
        var lastTranscript = ""

        override fun extractTasks(transcript: String): List<ParsedTask> {
            lastTranscript = transcript
            return transcript.split(",", ";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { ParsedTask(taskText = it, timestamp = null) }
        }
    }

    private val extractor = OneTaskPerPieceExtractor()

    private fun TestScope.createSession(
        recognizer: FakeVoiceRecognizer,
        taskExtractor: TaskExtractor = extractor
    ) = VoiceTaskSession(recognizer, taskExtractor, backgroundScope)

    private fun TestScope.waitFor(milliseconds: Long) {
        advanceTimeBy(milliseconds.milliseconds)
        runCurrent()
    }

    @Test
    fun startListeningTurnsListeningOnAndStartsTheRecognizer() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)

        session.startListening(onPermissionNeeded = {})

        assertTrue(session.state.value.isListening)
        assertEquals(1, recognizer.startCount)
    }

    @Test
    fun aFinishedSentenceIsAddedAsATaskAndListeningContinues() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        recognizer.hearSentence("buy milk")

        assertEquals(listOf("buy milk"), session.state.value.tasks.map { it.taskText })
        assertTrue(session.state.value.isListening)
        assertEquals(2, recognizer.startCount)
    }

    @Test
    fun aPieceHeardMidSessionIsAddedWithoutRestartingTheRecognizer() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        recognizer.hearPieceThenKeepListening("buy milk")
        recognizer.hearPieceThenKeepListening("call mom")

        assertEquals(listOf("buy milk", "call mom"), session.state.value.tasks.map { it.taskText })
        assertTrue(session.state.value.isListening)
        assertEquals(1, recognizer.startCount)
    }

    @Test
    fun aHeardPieceResetsTheSilenceTimer() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        waitFor(4_000)
        recognizer.hearPieceThenKeepListening("buy milk")
        waitFor(4_000)

        assertTrue(session.state.value.isListening)
    }

    @Test
    fun piecesHeardInOneRunAreReadTogetherAsPauses() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        recognizer.hearSentence("buy milk")
        recognizer.hearPieceThenKeepListening("call mom")

        assertEquals("buy milk, call mom", extractor.lastTranscript)
    }

    @Test
    fun aNewRunAfterTappingTheOrbIsKeptSeparateFromTheLastOne() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("buy milk")
        waitFor(5_000)

        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("call mom")

        assertEquals("call mom", extractor.lastTranscript)
        assertEquals(listOf("buy milk", "call mom"), session.state.value.tasks.map { it.taskText })
    }

    @Test
    fun editingATaskChangesItsText() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("by milk")
        session.stopListening()

        session.editTask(0, "buy milk")

        assertEquals(listOf("buy milk"), session.state.value.tasks.map { it.taskText })
    }

    @Test
    fun editingWhileListeningStopsListeningAndKeepsEveryTask() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearPieceThenKeepListening("buy milk")
        recognizer.hearPieceThenKeepListening("call mum")

        session.editTask(1, "call mom")

        assertFalse(session.state.value.isListening)
        assertEquals(listOf("buy milk", "call mom"), session.state.value.tasks.map { it.taskText })
    }

    @Test
    fun speakingAgainAfterAnEditDoesNotUndoTheEdit() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("by milk")
        session.editTask(0, "buy milk")

        session.startListening(onPermissionNeeded = {})
        recognizer.hearPieceThenKeepListening("call mom")

        assertEquals(listOf("buy milk", "call mom"), session.state.value.tasks.map { it.taskText })
    }

    @Test
    fun aSentenceThatArrivesAfterAnEditIsAddedBelowIt() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearPieceThenKeepListening("by milk")
        session.editTask(0, "buy milk")

        recognizer.hearSentence("call mom")

        assertEquals(listOf("buy milk", "call mom"), session.state.value.tasks.map { it.taskText })
    }

    @Test
    fun endLeavesOutTasksThatWereClearedAndTrimsTheRest() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearPieceThenKeepListening("buy milk")
        recognizer.hearPieceThenKeepListening("call mom")
        session.editTask(0, "   ")
        session.editTask(1, " call mom  ")

        val recordedTasks = session.end()

        assertEquals(listOf("call mom"), recordedTasks.map { it.taskText })
    }

    @Test
    fun settingAReminderChangesOnlyThatTask() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearPieceThenKeepListening("buy milk")
        recognizer.hearPieceThenKeepListening("call mom")

        session.setTaskReminder(1, 1_000L)

        assertFalse(session.state.value.isListening)
        assertEquals(listOf(null, 1_000L), session.state.value.tasks.map { it.timestamp })
    }

    @Test
    fun aReminderCanBeRemovedAgain() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("buy milk")
        session.setTaskReminder(0, 1_000L)

        session.setTaskReminder(0, null)

        assertNull(session.state.value.tasks.single().timestamp)
    }

    @Test
    fun anEditToATaskThatDoesNotExistIsIgnored() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("buy milk")

        session.editTask(5, "call mom")

        assertEquals(listOf("buy milk"), session.state.value.tasks.map { it.taskText })
    }

    @Test
    fun aTimeSaidAfterAPauseJoinsTheTaskSaidBeforeIt() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer, HeuristicTaskExtractor())
        session.startListening(onPermissionNeeded = {})

        recognizer.hearPieceThenKeepListening("remind me to call mom")
        recognizer.hearPieceThenKeepListening("tomorrow at 5 pm")

        val task = session.state.value.tasks.single()
        assertEquals("Call mom", task.taskText)
        assertNotNull(task.timestamp)
    }

    @Test
    fun theEndOfARecognizerSessionWithNothingNewKeepsTheTasks() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearPieceThenKeepListening("buy milk")

        recognizer.hearSentence("")

        assertEquals(listOf("buy milk"), session.state.value.tasks.map { it.taskText })
        assertEquals(2, recognizer.startCount)
    }

    @Test
    fun partialTextIsShownUntilTheSentenceFinishes() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        recognizer.hearPartially("buy")
        assertEquals("buy", session.state.value.partialText)

        recognizer.hearSentence("buy milk")
        assertEquals("", session.state.value.partialText)
    }

    @Test
    fun listeningStopsAfterFiveSecondsOfSilence() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        waitFor(4_999)
        assertTrue(session.state.value.isListening)

        waitFor(1)
        assertFalse(session.state.value.isListening)
        assertEquals(1, recognizer.stopCount)
    }

    @Test
    fun speakingResetsTheSilenceTimer() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        waitFor(4_000)
        recognizer.hearPartially("call mom")
        waitFor(4_000)
        assertTrue(session.state.value.isListening)

        waitFor(1_000)
        assertFalse(session.state.value.isListening)
    }

    @Test
    fun listeningAgainKeepsTheTasksAlreadyRecorded() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("buy milk")
        waitFor(5_000)
        assertFalse(session.state.value.isListening)

        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("call mom")

        assertTrue(session.state.value.isListening)
        assertEquals(listOf("buy milk", "call mom"), session.state.value.tasks.map { it.taskText })
    }

    @Test
    fun aSentenceThatArrivesRightAfterTheSilenceStopIsStillKept() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        waitFor(5_000)

        recognizer.hearSentence("water plants")

        assertEquals(listOf("water plants"), session.state.value.tasks.map { it.taskText })
        assertFalse(session.state.value.isListening)
        assertEquals(1, recognizer.startCount)
    }

    @Test
    fun hearingNothingRestartsTheRecognizerWithoutStopping() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        recognizer.fail("No match")

        assertTrue(session.state.value.isListening)
        assertNull(session.state.value.errorMessage)
        assertEquals(2, recognizer.startCount)
    }

    @Test
    fun aRealErrorStopsListeningAndShowsTheMessage() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})

        recognizer.fail("Voice service is busy. Please wait.")

        assertFalse(session.state.value.isListening)
        assertEquals("Voice service is busy. Please wait.", session.state.value.errorMessage)
    }

    @Test
    fun listeningAgainClearsThePreviousError() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.fail("Voice service is busy. Please wait.")

        session.startListening(onPermissionNeeded = {})

        assertNull(session.state.value.errorMessage)
    }

    @Test
    fun missingPermissionStopsListeningAndAsksForIt() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        var wasPermissionRequested = false
        session.startListening(onPermissionNeeded = { wasPermissionRequested = true })

        recognizer.askForPermission()

        assertTrue(wasPermissionRequested)
        assertFalse(session.state.value.isListening)
    }

    @Test
    fun endReturnsTheTasksAndResetsTheSession() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        recognizer.hearSentence("buy milk")

        val recordedTasks = session.end()

        assertEquals(listOf("buy milk"), recordedTasks.map { it.taskText })
        assertEquals(VoiceTaskSessionState(), session.state.value)
    }

    @Test
    fun aSentenceThatArrivesAfterEndIsIgnored() = runTest {
        val recognizer = FakeVoiceRecognizer()
        val session = createSession(recognizer)
        session.startListening(onPermissionNeeded = {})
        session.end()

        recognizer.hearSentence("buy milk")

        assertEquals(VoiceTaskSessionState(), session.state.value)
    }
}
