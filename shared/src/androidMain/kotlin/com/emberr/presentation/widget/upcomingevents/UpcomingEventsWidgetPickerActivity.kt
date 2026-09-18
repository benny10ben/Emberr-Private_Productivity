package com.emberr.presentation.widget.upcomingevents

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.space.SpaceRepository
import com.emberr.presentation.widget.WidgetLog
import com.emberr.presentation.widget.WidgetSetupNotice
import com.emberr.presentation.widget.WidgetSpaceChooser
import com.emberr.presentation.widget.clearPinnedSpaceId
import com.emberr.presentation.widget.writePinnedSpaceId
import com.emberr.ui.theme.EmberrTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get

class UpcomingEventsWidgetPickerActivity : ComponentActivity() {

    private val spaceRepository: SpaceRepository? by lazy {
        runCatching { get<SpaceRepository>() }.getOrNull()
    }

    private val activeSpaceStore: ActiveSpaceStore? by lazy {
        runCatching { get<ActiveSpaceStore>() }.getOrNull()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED, resultIntentFor(appWidgetId))

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            EmberrTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val repository = spaceRepository
                    val spaceStore = activeSpaceStore

                    if (repository == null || spaceStore == null) {
                        WidgetSetupNotice("Open the app once before adding a widget")
                    } else {
                        val spacesFlow = remember { repository.observeSpaces() }
                        val spaces by spacesFlow.collectAsState(initial = emptyList())

                        WidgetSpaceChooser(
                            spaces = spaces,
                            activeSpaceId = spaceStore.currentActiveSpaceId(),
                            onSpaceChosen = { spaceId -> applySelection(appWidgetId, spaceId) }
                        )
                    }
                }
            }
        }
    }

    private fun applySelection(appWidgetId: Int, spaceId: String?) {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val glanceId = GlanceAppWidgetManager(this@UpcomingEventsWidgetPickerActivity)
                    .getGlanceIdBy(appWidgetId)

                if (spaceId == null) {
                    clearPinnedSpaceId(this@UpcomingEventsWidgetPickerActivity, glanceId)
                } else {
                    writePinnedSpaceId(this@UpcomingEventsWidgetPickerActivity, glanceId, spaceId)
                }

                refreshUpcomingEventsWidget(this@UpcomingEventsWidgetPickerActivity, glanceId)
                UpcomingEventsWidget().update(this@UpcomingEventsWidgetPickerActivity, glanceId)
                withContext(Dispatchers.Main) { setResult(RESULT_OK, resultIntentFor(appWidgetId)) }
            } catch (cause: Exception) {
                WidgetLog.e("Could not attach a space to the upcoming events widget", cause)
                withContext(Dispatchers.Main) {
                    setResult(RESULT_CANCELED, resultIntentFor(appWidgetId))
                }
            }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    private fun resultIntentFor(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}
