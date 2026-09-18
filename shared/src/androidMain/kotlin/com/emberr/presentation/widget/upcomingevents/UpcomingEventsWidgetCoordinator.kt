// Watches the stored task list so new or changed events reach the widget while the app runs.
package com.emberr.presentation.widget.upcomingevents

import android.content.Context
import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.presentation.widget.WidgetLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val upcomingEventsSettleDelayMillis = 150L

class UpcomingEventsWidgetCoordinator(
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
                    .debounce(upcomingEventsSettleDelayMillis)
                    .collect { refreshUpcomingEventsWidgets(context) }
            } catch (cause: Exception) {
                WidgetLog.e("Stopped watching events for the upcoming events widgets", cause)
            }
        }
    }
}
