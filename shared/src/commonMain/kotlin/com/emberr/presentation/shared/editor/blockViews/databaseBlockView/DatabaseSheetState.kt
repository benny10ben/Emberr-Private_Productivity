package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.editor.EditorActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Stable
class DatabaseSheetState internal constructor(
    private val blockId: String,
    private val scope: CoroutineScope,
    private val latestActions: () -> EditorActions
) {
    private val sheetStack = mutableStateListOf<DatabaseSheet>()

    val openSheets: List<DatabaseSheet> get() = sheetStack
    val currentSheet: DatabaseSheet get() = sheetStack.lastOrNull() ?: DatabaseSheet.NONE

    var activeColId by mutableStateOf<String?>(null)
    var activeRowId by mutableStateOf<String?>(null)
    var renamingViewId by mutableStateOf<String?>(null)

    var textInput by mutableStateOf("")
    var textInputMax by mutableStateOf("")
    var filterOperator by mutableStateOf("contains")
    var filterPriority by mutableStateOf("")
    var aggregationExpandedSection by mutableStateOf<String?>(null)

    var isRecording by mutableStateOf(false)
    var recordingDuration by mutableIntStateOf(0)
    var playingFileUri by mutableStateOf<String?>(null)

    fun open(sheet: DatabaseSheet) {
        sheetStack.add(sheet)
    }

    fun close() {
        stopMediaSideEffects()
        sheetStack.clear()
        clearTransientSelection()
    }

    fun pop() {
        if (currentSheet == DatabaseSheet.FILE_OPTIONS) stopMediaSideEffects()
        if (sheetStack.isNotEmpty()) sheetStack.removeAt(sheetStack.lastIndex)
        if (sheetStack.isEmpty()) clearTransientSelection()
    }

    fun dismissCurrentSheet() {
        if (isDesktopPlatform) close() else pop()
    }

    fun applyAction(action: () -> Unit) {
        close()
        scope.launch {
            try {
                delay(250.milliseconds)
            } finally {
                action()
            }
        }
    }

    fun openForColumn(columnId: String, sheet: DatabaseSheet) {
        activeColId = columnId
        open(sheet)
    }

    private fun clearTransientSelection() {
        activeRowId = null
        renamingViewId = null
        aggregationExpandedSection = null
    }

    private fun stopMediaSideEffects() {
        val rowId = activeRowId
        val colId = activeColId
        if (isRecording && rowId != null && colId != null) {
            isRecording = false
            latestActions().onStopDbAudioRecording(blockId, rowId, colId, true)
        }
        if (playingFileUri != null) {
            playingFileUri = null
            latestActions().onStopAudio()
        }
    }
}

@Composable
fun rememberDatabaseSheetState(blockId: String, actions: EditorActions): DatabaseSheetState {
    val scope = rememberCoroutineScope()
    val currentActions by rememberUpdatedState(actions)
    val state = remember(blockId) { DatabaseSheetState(blockId, scope) { currentActions } }

    LaunchedEffect(state.isRecording) {
        if (state.isRecording) {
            state.recordingDuration = 0
            while (state.isRecording) {
                delay(1000.milliseconds)
                state.recordingDuration++
            }
        }
    }

    return state
}
