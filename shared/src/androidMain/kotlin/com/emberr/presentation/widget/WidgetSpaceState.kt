// Which space a single widget instance shows, remembered per instance and falling back to the
// space the app itself is currently in.
package com.emberr.presentation.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.emberr.domain.space.ActiveSpaceStore
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

suspend fun readWidgetSpaceId(context: Context, glanceId: GlanceId): String =
    readPinnedSpaceId(context, glanceId) ?: activeSpaceId()

private fun activeSpaceId(): String =
    GlobalContext.get().get<ActiveSpaceStore>().currentActiveSpaceId()
