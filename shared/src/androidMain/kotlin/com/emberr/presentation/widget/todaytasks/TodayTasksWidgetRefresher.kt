// Reads and writes each widget's saved day, and pushes freshly drawn tasks to the home screen.
package com.emberr.presentation.widget.todaytasks

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
private const val fallbackWidgetHeightDp = 180

fun decodeCachedTodayTasks(rawCache: String?): TodayTasksWidgetContent? {
    if (rawCache.isNullOrBlank()) return null
    return try {
        cacheJson.decodeFromString<TodayTasksWidgetContent>(rawCache)
    } catch (cause: Exception) {
        WidgetLog.e("Dropped an unreadable today tasks cache", cause)
        null
    }
}

suspend fun readCachedTodayTasks(context: Context, glanceId: GlanceId): TodayTasksWidgetContent? =
    try {
        decodeCachedTodayTasks(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[cachedTodayTasksKey]
        )
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the today tasks cache", cause)
        null
    }

suspend fun writeCachedTodayTasks(
    context: Context,
    glanceId: GlanceId,
    content: TodayTasksWidgetContent
) {
    try {
        val encoded = cacheJson.encodeToString(TodayTasksWidgetContent.serializer(), content)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { this[cachedTodayTasksKey] = encoded }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not write the today tasks cache", cause)
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
suspend fun pushRenderedTodayTasks(
    context: Context,
    glanceId: GlanceId,
    content: TodayTasksWidgetContent
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
            TodayTasksWidgetBody(
                context = context,
                appWidgetId = appWidgetId,
                content = content
            )
        }

        widgetManager.updateAppWidget(appWidgetId, rendered.remoteViews)
    } catch (cause: Exception) {
        WidgetLog.e("Could not push the rendered today tasks", cause)
    }
}

suspend fun refreshTodayTasksWidget(context: Context, glanceId: GlanceId) {
    try {
        val contentReader = GlobalContext.get().get<TodayTasksWidgetContentReader>()
        val spaceId = readWidgetSpaceId(context, glanceId)
        val freshContent = contentReader.readContentOnce(spaceId) ?: return

        if (freshContent == readCachedTodayTasks(context, glanceId)) return

        writeCachedTodayTasks(context, glanceId, freshContent)
        pushRenderedTodayTasks(context, glanceId, freshContent)
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh a today tasks widget", cause)
    }
}

suspend fun refreshTodayTasksWidgets(context: Context) {
    try {
        GlanceAppWidgetManager(context)
            .getGlanceIds(TodayTasksWidget::class.java)
            .forEach { glanceId -> refreshTodayTasksWidget(context, glanceId) }
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh the today tasks widgets", cause)
    }
}
