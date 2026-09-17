// Reads and writes each widget's cached content, and pushes freshly drawn events to the home screen.
package com.emberr.presentation.widget.upcomingevents

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

private const val fallbackWidgetWidthDp = 180
private const val fallbackWidgetHeightDp = 180

fun decodeCachedUpcomingEvents(rawCache: String?): UpcomingEventsWidgetContent? {
    if (rawCache.isNullOrBlank()) return null
    return try {
        cacheJson.decodeFromString<UpcomingEventsWidgetContent>(rawCache)
    } catch (cause: Exception) {
        WidgetLog.e("Dropped an unreadable upcoming events cache", cause)
        null
    }
}

suspend fun readCachedUpcomingEvents(context: Context, glanceId: GlanceId): UpcomingEventsWidgetContent? =
    try {
        decodeCachedUpcomingEvents(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[cachedUpcomingEventsKey]
        )
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the upcoming events cache", cause)
        null
    }

suspend fun writeCachedUpcomingEvents(
    context: Context,
    glanceId: GlanceId,
    content: UpcomingEventsWidgetContent
) {
    try {
        val encoded = cacheJson.encodeToString(UpcomingEventsWidgetContent.serializer(), content)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { this[cachedUpcomingEventsKey] = encoded }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not write the upcoming events cache", cause)
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
suspend fun pushRenderedUpcomingEvents(
    context: Context,
    glanceId: GlanceId,
    content: UpcomingEventsWidgetContent
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
            UpcomingEventsWidgetBody(context = context, content = content)
        }

        widgetManager.updateAppWidget(appWidgetId, rendered.remoteViews)
    } catch (cause: Exception) {
        WidgetLog.e("Could not push the rendered upcoming events", cause)
    }
}

suspend fun refreshUpcomingEventsWidget(context: Context, glanceId: GlanceId) {
    try {
        val contentReader = GlobalContext.get().get<UpcomingEventsWidgetContentReader>()
        val spaceId = readWidgetSpaceId(context, glanceId)
        val freshContent = contentReader.readContentOnce(spaceId) ?: return

        if (freshContent == readCachedUpcomingEvents(context, glanceId)) return

        writeCachedUpcomingEvents(context, glanceId, freshContent)
        pushRenderedUpcomingEvents(context, glanceId, freshContent)
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh an upcoming events widget", cause)
    }
}

suspend fun refreshUpcomingEventsWidgets(context: Context) {
    try {
        GlanceAppWidgetManager(context)
            .getGlanceIds(UpcomingEventsWidget::class.java)
            .forEach { glanceId -> refreshUpcomingEventsWidget(context, glanceId) }
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh the upcoming events widgets", cause)
    }
}
