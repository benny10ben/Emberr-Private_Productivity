// Watches the stored note list and pushes its changes to the home screen while the app runs.
package com.emberr.presentation.widget.notelist

import android.content.Context
import com.emberr.presentation.widget.WidgetLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val noteListSettleDelayMillis = 150L

class NoteListWidgetCoordinator(
    private val context: Context,
    private val contentReader: NoteListWidgetContentReader
) {
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        coordinatorScope.launch {
            try {
                contentReader.observeNotes()
                    .distinctUntilChanged()
                    .debounce(noteListSettleDelayMillis)
                    .collect { notes -> pushNoteListToWidgets(context, notes) }
            } catch (cause: Exception) {
                WidgetLog.e("Stopped watching the note list", cause)
            }
        }
    }
}
