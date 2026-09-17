// Watches the stored task list and pushes today's changes to the home screen while the app runs.
package com.emberr.presentation.widget.todaytasks

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

private const val todayTasksSettleDelayMillis = 150L

class TodayTasksWidgetCoordinator(
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
                    .debounce(todayTasksSettleDelayMillis)
                    .collect { refreshTodayTasksWidgets(context) }
            } catch (cause: Exception) {
                WidgetLog.e("Stopped watching today's tasks", cause)
            }
        }
    }
}
