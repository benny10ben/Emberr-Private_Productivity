// Reads and writes each widget's saved task list, and pushes freshly drawn rows to the home screen.
package com.emberr.presentation.widget.tasks

import android.appwidget.AppWidgetManager
import android.content.Context
import com.emberr.data.local.room.CalendarTaskEntity
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

private const val fallbackWidgetSideDp = 180

fun decodeCachedTasks(rawCache: String?): TasksWidgetContent? {
    if (rawCache.isNullOrBlank()) return null
    return try {
        cacheJson.decodeFromString<TasksWidgetContent>(rawCache)
    } catch (cause: Exception) {
        WidgetLog.e("Dropped an unreadable task cache", cause)
        null
    }
}

suspend fun readShowingCompleted(context: Context, glanceId: GlanceId): Boolean =
    try {
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[showingCompletedKey] ?: false
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the completed-view flag", cause)
        false
    }

suspend fun readCachedTasks(context: Context, glanceId: GlanceId): TasksWidgetContent? =
    try {
        decodeCachedTasks(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[cachedTasksKey]
        )
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the task cache", cause)
        null
    }

suspend fun writeShowingCompleted(context: Context, glanceId: GlanceId, isShowingCompleted: Boolean) {
    try {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { this[showingCompletedKey] = isShowingCompleted }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not save the completed-view flag", cause)
    }
}

suspend fun writeCachedTasks(context: Context, glanceId: GlanceId, content: TasksWidgetContent) {
    try {
        val encoded = cacheJson.encodeToString(TasksWidgetContent.serializer(), content)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { this[cachedTasksKey] = encoded }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not write the task cache", cause)
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
suspend fun pushRenderedTasks(context: Context, glanceId: GlanceId, content: TasksWidgetContent) {
    try {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val widgetManager = AppWidgetManager.getInstance(context)
        val options = widgetManager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .takeIf { it > 0 } ?: fallbackWidgetSideDp
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            .takeIf { it > 0 } ?: fallbackWidgetSideDp

        val pinnedSpaceId = readWidgetSpaceId(context, glanceId)

        val rendered = GlanceRemoteViews().compose(
            context = context,
            size = DpSize(widthDp.dp, heightDp.dp)
        ) {
            TasksWidgetBody(
                context = context,
                appWidgetId = appWidgetId,
                spaceId = pinnedSpaceId,
                content = content
            )
        }

        widgetManager.updateAppWidget(appWidgetId, rendered.remoteViews)
    } catch (cause: Exception) {
        WidgetLog.e("Could not push the rendered task list", cause)
    }
}

suspend fun refreshTaskWidget(context: Context, glanceId: GlanceId) {
    try {
        val contentReader = GlobalContext.get().get<TasksWidgetContentReader>()
        val isShowingCompleted = readShowingCompleted(context, glanceId)
        val spaceId = readWidgetSpaceId(context, glanceId)
        val freshContent = contentReader.readContentOnce(spaceId, isShowingCompleted) ?: return

        if (freshContent == readCachedTasks(context, glanceId)) return

        writeCachedTasks(context, glanceId, freshContent)
        pushRenderedTasks(context, glanceId, freshContent)
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh a task widget", cause)
    }
}

suspend fun refreshTaskWidgets(context: Context) {
    try {
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(TasksWidget::class.java)
        glanceIds.forEach { glanceId -> refreshTaskWidget(context, glanceId) }
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh the task widgets", cause)
    }
}

suspend fun pushTasksToWidgets(context: Context, tasks: List<CalendarTaskEntity>) {
    try {
        val contentReader = GlobalContext.get().get<TasksWidgetContentReader>()
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(TasksWidget::class.java)

        glanceIds.forEach { glanceId ->
            val isShowingCompleted = readShowingCompleted(context, glanceId)
            val spaceId = readWidgetSpaceId(context, glanceId)
            val freshContent = contentReader.buildContent(spaceId, tasks, isShowingCompleted)
            if (freshContent != readCachedTasks(context, glanceId)) {
                writeCachedTasks(context, glanceId, freshContent)
                pushRenderedTasks(context, glanceId, freshContent)
            }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not push tasks to the widgets", cause)
    }
}
