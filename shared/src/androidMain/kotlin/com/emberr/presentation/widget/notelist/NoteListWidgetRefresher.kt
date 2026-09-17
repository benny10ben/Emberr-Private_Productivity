// Reads and writes each widget's saved note list, and pushes freshly drawn rows to the home screen.
package com.emberr.presentation.widget.notelist

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
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.presentation.widget.WidgetLog
import com.emberr.presentation.widget.readWidgetSpaceId
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext

private val cacheJson = Json { ignoreUnknownKeys = true }

private const val fallbackWidgetSideDp = 180

fun decodeCachedNoteList(rawCache: String?): NoteListWidgetContent? {
    if (rawCache.isNullOrBlank()) return null
    return try {
        cacheJson.decodeFromString<NoteListWidgetContent>(rawCache)
    } catch (cause: Exception) {
        WidgetLog.e("Dropped an unreadable note list cache", cause)
        null
    }
}

suspend fun readCachedNoteList(context: Context, glanceId: GlanceId): NoteListWidgetContent? =
    try {
        decodeCachedNoteList(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[cachedNoteListKey]
        )
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the note list cache", cause)
        null
    }

suspend fun writeCachedNoteList(context: Context, glanceId: GlanceId, content: NoteListWidgetContent) {
    try {
        val encoded = cacheJson.encodeToString(NoteListWidgetContent.serializer(), content)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { preferences ->
            preferences.toMutablePreferences().apply { this[cachedNoteListKey] = encoded }
        }
    } catch (cause: Exception) {
        WidgetLog.e("Could not write the note list cache", cause)
    }
}

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
suspend fun pushRenderedNoteList(context: Context, glanceId: GlanceId, content: NoteListWidgetContent) {
    try {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val widgetManager = AppWidgetManager.getInstance(context)
        val options = widgetManager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            .takeIf { it > 0 } ?: fallbackWidgetSideDp
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            .takeIf { it > 0 } ?: fallbackWidgetSideDp

        val rendered = GlanceRemoteViews().compose(
            context = context,
            size = DpSize(widthDp.dp, heightDp.dp)
        ) {
            NoteListWidgetBody(context = context, content = content)
        }

        widgetManager.updateAppWidget(appWidgetId, rendered.remoteViews)
    } catch (cause: Exception) {
        WidgetLog.e("Could not push the rendered note list", cause)
    }
}

suspend fun pushNoteListToWidgets(context: Context, notes: List<NoteMetadataEntity>) {
    try {
        val contentReader = GlobalContext.get().get<NoteListWidgetContentReader>()

        GlanceAppWidgetManager(context)
            .getGlanceIds(NoteListWidget::class.java)
            .forEach { glanceId ->
                val spaceId = readWidgetSpaceId(context, glanceId)
                val content = contentReader.buildContent(spaceId, notes)
                if (content != readCachedNoteList(context, glanceId)) {
                    writeCachedNoteList(context, glanceId, content)
                    pushRenderedNoteList(context, glanceId, content)
                }
            }
    } catch (cause: Exception) {
        WidgetLog.e("Could not push the note list to the widgets", cause)
    }
}

suspend fun refreshNoteListWidget(context: Context, glanceId: GlanceId) {
    try {
        val contentReader = GlobalContext.get().get<NoteListWidgetContentReader>()
        val spaceId = readWidgetSpaceId(context, glanceId)
        val freshContent = contentReader.readContentOnce(spaceId) ?: return

        if (freshContent == readCachedNoteList(context, glanceId)) return

        writeCachedNoteList(context, glanceId, freshContent)
        pushRenderedNoteList(context, glanceId, freshContent)
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh a note list widget", cause)
    }
}

suspend fun refreshNoteListWidgets(context: Context) {
    try {
        GlanceAppWidgetManager(context)
            .getGlanceIds(NoteListWidget::class.java)
            .forEach { glanceId -> refreshNoteListWidget(context, glanceId) }
    } catch (cause: Exception) {
        WidgetLog.e("Could not refresh the note list widgets", cause)
    }
}
