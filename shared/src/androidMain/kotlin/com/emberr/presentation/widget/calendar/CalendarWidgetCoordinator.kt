// Watches the stored task list so new or removed tasks update their day's dots while the app runs.
package com.emberr.presentation.widget.calendar

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

private const val calendarSettleDelayMillis = 150L

class CalendarWidgetCoordinator(
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
                    .debounce(calendarSettleDelayMillis)
                    .collect { refreshCalendarWidgets(context) }
            } catch (cause: Exception) {
                WidgetLog.e("Stopped watching tasks for the calendar widgets", cause)
            }
        }
    }
}
