package com.emberr.presentation.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.space.SpaceRepository
import com.emberr.presentation.widget.calendar.CalendarWidget
import com.emberr.presentation.widget.calendaragenda.CalendarAgendaWidget
import com.emberr.presentation.widget.notelist.NoteListWidget
import com.emberr.presentation.widget.tasks.TasksWidget
import com.emberr.presentation.widget.todaytasks.TodayTasksWidget
import com.emberr.presentation.widget.upcomingevents.UpcomingEventsWidget
import org.koin.core.context.GlobalContext

val selectedSpaceIdKey = stringPreferencesKey("selected_space_id")

suspend fun readPinnedSpaceId(context: Context, glanceId: GlanceId): String? =
    try {
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[selectedSpaceIdKey]
            ?.takeIf { it.isNotBlank() }
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the pinned space", cause)
        null
    }

suspend fun writePinnedSpaceId(context: Context, glanceId: GlanceId, spaceId: String) {
    try {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { this[selectedSpaceIdKey] = spaceId }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not save the pinned space", cause)
    }
}

suspend fun clearPinnedSpaceId(context: Context, glanceId: GlanceId) {
    try {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { remove(selectedSpaceIdKey) }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not clear the pinned space", cause)
    }
}

suspend fun readWidgetSpaceId(context: Context, glanceId: GlanceId): String {
    val pinnedSpaceId = readPinnedSpaceId(context, glanceId) ?: return activeSpaceId()
    return if (spaceIsStillLive(pinnedSpaceId)) pinnedSpaceId else activeSpaceId()
}

suspend fun clearWidgetPinsForSpace(context: Context, spaceId: String) {
    val widgetClasses = listOf(
        CalendarWidget::class.java,
        CalendarAgendaWidget::class.java,
        NoteListWidget::class.java,
        TasksWidget::class.java,
        TodayTasksWidget::class.java,
        UpcomingEventsWidget::class.java
    )

    widgetClasses.forEach { widgetClass ->
        try {
            GlanceAppWidgetManager(context).getGlanceIds(widgetClass).forEach { glanceId ->
                if (readPinnedSpaceId(context, glanceId) == spaceId) {
                    clearPinnedSpaceId(context, glanceId)
                }
            }
        } catch (cause: Exception) {
            WidgetLog.e("Could not clear pinned spaces for ${widgetClass.simpleName}", cause)
        }
    }
}

private suspend fun spaceIsStillLive(spaceId: String): Boolean =
    try {
        GlobalContext.get().get<SpaceRepository>().getSpace(spaceId)?.isDeleted == false
    } catch (cause: Exception) {
        WidgetLog.e("Could not verify the pinned space", cause)
        false
    }

private fun activeSpaceId(): String =
    GlobalContext.get().get<ActiveSpaceStore>().currentActiveSpaceId()
