// The home screen widget itself: draws today's date, a heading and today's tasks as a nested card.
package com.emberr.presentation.widget.todaytasks

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.emberr.MainActivity
import com.emberr.R
import com.emberr.presentation.widget.WidgetLog
import com.emberr.presentation.widget.elevatedSurfaceColor
import com.emberr.presentation.widget.primaryTextColor
import com.emberr.presentation.widget.secondaryTextColor
import com.emberr.presentation.widget.separatorColor
import com.emberr.presentation.widget.surfaceColor
import com.emberr.presentation.widget.taskBlockIdExtra
import com.emberr.presentation.widget.taskIsCheckedExtra
import com.emberr.presentation.widget.widgetDailyDateExtra
import com.emberr.presentation.widget.widgetDailyScreenExtra
import com.emberr.presentation.widget.widgetSpaceIdExtra
import com.emberr.presentation.widget.readWidgetSpaceId
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val dateBandHeight = 42.dp
private val checkboxWidth = 34.dp
private val clockIconSize = 12.dp

class TodayTasksWidget : GlanceAppWidget(), KoinComponent {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadAndCacheTodayTasks(context, id) ?: readCachedTodayTasks(context, id)
        val appWidgetId = resolveAppWidgetId(context, id)
        val spaceId = readWidgetSpaceId(context, id)

        provideContent {
            TodayTasksWidgetBody(
                context = context,
                appWidgetId = appWidgetId,
                spaceId = spaceId,
                content = content
            )
        }
    }

    private suspend fun resolveAppWidgetId(context: Context, id: GlanceId): Int =
        try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (cause: Exception) {
            WidgetLog.e("Could not resolve the widget id", cause)
            INVALID_APPWIDGET_ID
        }

    private suspend fun loadAndCacheTodayTasks(
        context: Context,
        id: GlanceId
    ): TodayTasksWidgetContent? {
        val contentReader = runCatching { get<TodayTasksWidgetContentReader>() }.getOrNull() ?: return null
        val spaceId = readWidgetSpaceId(context, id)
        val freshContent = contentReader.readContentOnce(spaceId) ?: return null

        if (freshContent != readCachedTodayTasks(context, id)) {
            writeCachedTodayTasks(context, id, freshContent)
        }
        return freshContent
    }
}

@Composable
internal fun TodayTasksWidgetBody(
    context: Context,
    appWidgetId: Int,
    spaceId: String,
    content: TodayTasksWidgetContent?
) {
    val openDailyScreen = actionStartActivity(openDailyScreenIntent(context, spaceId))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surfaceColor)
            .cornerRadius(26.dp)
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 12.dp)
    ) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(dateBandHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = content?.dateLabel.orEmpty(),
                maxLines = 1,
                style = TextStyle(
                    color = secondaryTextColor,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            )
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(elevatedSurfaceColor)
                .cornerRadius(22.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Today's Tasks",
                maxLines = 1,
                style = TextStyle(
                    color = primaryTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.fillMaxWidth().clickable(openDailyScreen)
            )

            Spacer(modifier = GlanceModifier.height(10.dp))

            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(separatorColor)
            )

            Spacer(modifier = GlanceModifier.height(6.dp))

            val rows = content?.rows.orEmpty()
            if (rows.isEmpty()) {
                Column(
                    modifier = GlanceModifier.fillMaxSize().clickable(openDailyScreen),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Text(
                        text = if (content == null) {
                            "Open Emberr to load today's tasks"
                        } else {
                            "Nothing planned for today"
                        },
                        style = TextStyle(color = secondaryTextColor, fontSize = 14.sp)
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items = rows, itemId = { row -> row.blockId.hashCode().toLong() }) { row ->
                        TodayTaskRowItem(
                            context = context,
                            appWidgetId = appWidgetId,
                            row = row,
                            openDailyScreen = openDailyScreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTaskRowItem(
    context: Context,
    appWidgetId: Int,
    row: TodayTaskRow,
    openDailyScreen: Action
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = if (row.isChecked) "☑" else "☐",
            style = TextStyle(color = primaryTextColor, fontSize = 23.sp),
            modifier = GlanceModifier
                .width(checkboxWidth)
                .clickable(
                    actionSendBroadcast(
                        toggleTaskIntent(
                            context = context,
                            appWidgetId = appWidgetId,
                            blockId = row.blockId,
                            isChecked = !row.isChecked
                        )
                    )
                )
        )

        Spacer(modifier = GlanceModifier.width(6.dp))

        Text(
            text = row.title,
            maxLines = 2,
            style = TextStyle(
                color = if (row.isChecked) secondaryTextColor else primaryTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (row.isChecked) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                }
            ),
            modifier = GlanceModifier.defaultWeight().clickable(openDailyScreen)
        )

        if (row.scheduleLabel != null) {
            Spacer(modifier = GlanceModifier.width(8.dp))
            SchedulePill(label = row.scheduleLabel, isOverdue = row.isOverdue)
        }
    }
}

@Composable
private fun SchedulePill(label: String, isOverdue: Boolean) {
    val labelColor = if (isOverdue) primaryTextColor else secondaryTextColor

    Row(
        modifier = GlanceModifier
            .background(surfaceColor)
            .cornerRadius(11.dp)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_clock),
            contentDescription = null,
            colorFilter = ColorFilter.tint(labelColor),
            modifier = GlanceModifier.size(clockIconSize)
        )

        Spacer(modifier = GlanceModifier.width(4.dp))

        Text(
            text = label,
            maxLines = 1,
            style = TextStyle(
                color = labelColor,
                fontSize = 12.sp,
                fontWeight = if (isOverdue) FontWeight.Medium else FontWeight.Normal
            )
        )
    }
}

private fun toggleTaskIntent(
    context: Context,
    appWidgetId: Int,
    blockId: String,
    isChecked: Boolean
): Intent =
    Intent(context, TodayTasksWidgetActionReceiver::class.java).apply {
        action = toggleTodayTaskAction
        data = "emberr-today://task/$appWidgetId/$blockId".toUri()
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        putExtra(taskBlockIdExtra, blockId)
        putExtra(taskIsCheckedExtra, isChecked)
    }

private fun openDailyScreenIntent(context: Context, spaceId: String): Intent {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

    return Intent(context, MainActivity::class.java)
        .setData("emberr://daily/$today".toUri())
        .putExtra(widgetDailyScreenExtra, true)
        .putExtra(widgetDailyDateExtra, today)
        .putExtra(widgetSpaceIdExtra, spaceId)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
