// Reads and writes each widget's shown month and content, and pushes a freshly drawn month to the home screen.
package com.emberr.presentation.widget.calendaragenda

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.emberr.presentation.widget.WidgetLog
import com.emberr.presentation.widget.readWidgetSpaceId
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext

private val cacheJson = Json { ignoreUnknownKeys = true }

private const val fallbackWidgetWidthDp = 250
private const val fallbackWidgetHeightDp = 110

fun decodeCachedAgenda(rawCache: String?): CalendarAgendaWidgetContent? {
    if (rawCache.isNullOrBlank()) return null
    return try {
        cacheJson.decodeFromString<CalendarAgendaWidgetContent>(rawCache)
    } catch (cause: Exception) {
        WidgetLog.e("Dropped an unreadable agenda cache", cause)
        null
    }
}

suspend fun readAgendaShownMonth(context: Context, glanceId: GlanceId): String? =
    try {
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[agendaShownMonthKey]
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the shown month", cause)
        null
    }

suspend fun readCachedAgenda(context: Context, glanceId: GlanceId): CalendarAgendaWidgetContent? =
    try {
        decodeCachedAgenda(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[cachedAgendaKey]
        )
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the agenda cache", cause)
        null
    }

suspend fun writeAgendaShownMonth(context: Context, glanceId: GlanceId, shownMonth: String) {
    try {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { this[agendaShownMonthKey] = shownMonth }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not save the shown month", cause)
    }
}

suspend fun writeCachedAgenda(
    context: Context,
    glanceId: GlanceId,
    content: CalendarAgendaWidgetContent
) {
    try {
        val encoded = cacheJson.encodeToString(CalendarAgendaWidgetContent.serializer(), content)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { this[cachedAgendaKey] = encoded }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not write the agenda cache", cause)
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
suspend fun pushRenderedAgenda(
    context: Context,
    glanceId: GlanceId,
    content: CalendarAgendaWidgetContent
) {
    try {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val widgetManager = AppWidgetManager.getInstance(context)
        val options = widgetManager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .takeIf { it > 0 } ?: fallbackWidgetWidthDp
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            .takeIf { it > 0 } ?: fallbackWidgetHeightDp

        val rendered = GlanceRemoteViews().compose(
            context = context,
            size = DpSize(widthDp.dp, heightDp.dp)
        ) {
            CalendarAgendaWidgetBody(
                context = context,
                appWidgetId = appWidgetId,
                content = content
            )
        }

        widgetManager.updateAppWidget(appWidgetId, rendered.remoteViews)
    } catch (cause: Exception) {
        WidgetLog.e("Could not push the rendered agenda", cause)
    }
}

suspend fun refreshCalendarAgendaWidget(context: Context, glanceId: GlanceId) {
    try {
        val contentReader = GlobalContext.get().get<CalendarAgendaWidgetContentReader>()
        val shownMonth = readAgendaShownMonth(context, glanceId)
        val spaceId = readWidgetSpaceId(context, glanceId)
        val freshContent = contentReader.readContentOnce(spaceId, shownMonth) ?: return

        if (freshContent == readCachedAgenda(context, glanceId)) return

        writeCachedAgenda(context, glanceId, freshContent)
        pushRenderedAgenda(context, glanceId, freshContent)
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh an agenda widget", cause)
    }
}

suspend fun refreshCalendarAgendaWidgets(context: Context) {
    try {
        GlanceAppWidgetManager(context)
            .getGlanceIds(CalendarAgendaWidget::class.java)
            .forEach { glanceId -> refreshCalendarAgendaWidget(context, glanceId) }
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh the agenda widgets", cause)
    }
}
