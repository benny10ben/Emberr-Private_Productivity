// Watches the stored task list so new or changed events reach the widget while the app runs.
package com.emberr.presentation.widget.calendaragenda

import android.content.Context
import com.emberr.data.local.room.CalendarTaskDao
import com.emberr.presentation.widget.WidgetLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val agendaSettleDelayMillis = 150L

class CalendarAgendaWidgetCoordinator(
    private val context: Context,
    private val calendarTaskDao: CalendarTaskDao
) {
    private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun start() {
        coordinatorScope.launch {
            try {
                calendarTaskDao.getAllTasksAcrossSpacesFlow()
                    .distinctUntilChanged()
                    .debounce(agendaSettleDelayMillis)
                    .collect { refreshCalendarAgendaWidgets(context) }
            } catch (cause: Exception) {
                WidgetLog.e("Stopped watching events for the agenda widgets", cause)
            }
        }
    }
}
