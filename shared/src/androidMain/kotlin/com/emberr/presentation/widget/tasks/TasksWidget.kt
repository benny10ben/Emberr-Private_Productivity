// The home screen widget itself: draws the task list with the same title and top right icons as the Tasks screen.
package com.emberr.presentation.widget.tasks

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import androidx.glance.text.TextStyle
import com.emberr.MainActivity
import com.emberr.R
import com.emberr.presentation.widget.WidgetLog
import com.emberr.presentation.widget.primaryTextColor
import com.emberr.presentation.widget.secondaryTextColor
import com.emberr.presentation.widget.surfaceColor
import com.emberr.presentation.widget.taskBlockIdExtra
import com.emberr.presentation.widget.taskIsCheckedExtra
import com.emberr.presentation.widget.widgetNewTaskExtra
import com.emberr.presentation.widget.widgetSpaceIdExtra
import com.emberr.presentation.widget.widgetTasksScreenExtra
import com.emberr.presentation.widget.readWidgetSpaceId
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val topBarIconSize = 22.dp
private val checkboxWidth = 26.dp

class TasksWidget : GlanceAppWidget(), KoinComponent {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isShowingCompleted = readShowingCompleted(context, id)

        val content = loadAndCacheTasks(context, id, isShowingCompleted)
            ?: readCachedTasks(context, id)

        val appWidgetId = resolveAppWidgetId(context, id)
        val spaceId = readWidgetSpaceId(context, id)

        provideContent {
            TasksWidgetBody(
                context = context,
                appWidgetId = appWidgetId,
                spaceId = spaceId,
                content = content
            )
        }
    }

    private suspend fun loadAndCacheTasks(
        context: Context,
        id: GlanceId,
        isShowingCompleted: Boolean
    ): TasksWidgetContent? {
        val contentReader = runCatching { get<TasksWidgetContentReader>() }.getOrNull() ?: return null
        val spaceId = readWidgetSpaceId(context, id)
        val freshContent = contentReader.readContentOnce(spaceId, isShowingCompleted) ?: return null

        if (freshContent != readCachedTasks(context, id)) {
            writeCachedTasks(context, id, freshContent)
        }
        return freshContent
    }

    private suspend fun resolveAppWidgetId(context: Context, id: GlanceId): Int =
        try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (cause: Exception) {
            WidgetLog.e("Could not resolve the widget id", cause)
            INVALID_APPWIDGET_ID
        }
}

@Composable
internal fun TasksWidgetBody(
    context: Context,
    appWidgetId: Int,
    spaceId: String,
    content: TasksWidgetContent?
) {
    val isShowingCompleted = content?.isShowingCompleted == true
    val openTasksScreen = actionStartActivity(openTasksScreenIntent(context, spaceId))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surfaceColor)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = if (isShowingCompleted) "Completed" else "Tasks",
                maxLines = 1,
                style = TextStyle(
                    color = primaryTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight().clickable(openTasksScreen)
            )

            Image(
                provider = ImageProvider(R.drawable.ic_widget_check_square),
                contentDescription = "Completed tasks",
                colorFilter = ColorFilter.tint(
                    if (isShowingCompleted) primaryTextColor else secondaryTextColor
                ),
                modifier = GlanceModifier
                    .size(topBarIconSize)
                    .clickable(actionSendBroadcast(toggleCompletedViewIntent(context, appWidgetId)))
            )

            Spacer(modifier = GlanceModifier.width(14.dp))

            Image(
                provider = ImageProvider(R.drawable.ic_widget_circle_plus),
                contentDescription = "Add task",
                colorFilter = ColorFilter.tint(primaryTextColor),
                modifier = GlanceModifier
                    .size(topBarIconSize)
                    .clickable(actionStartActivity(addTaskIntent(context, spaceId)))
            )
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        val rows = content?.rows.orEmpty()
        if (rows.isEmpty()) {
            Column(
                modifier = GlanceModifier.fillMaxSize().clickable(openTasksScreen),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Text(
                    text = when {
                        content == null -> "Open Emberr to load your tasks"
                        isShowingCompleted -> "No completed tasks yet."
                        else -> "All caught up!"
                    },
                    style = TextStyle(color = secondaryTextColor, fontSize = 15.sp)
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items = rows, itemId = { row -> row.key.hashCode().toLong() }) { row ->
                    when (row) {
                        is TasksWidgetRow.SectionHeading -> SectionHeadingRow(row)
                        is TasksWidgetRow.Task -> TaskRow(
                            task = row,
                            appWidgetId = appWidgetId,
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeadingRow(heading: TasksWidgetRow.SectionHeading) {
    Text(
        text = heading.label,
        maxLines = 1,
        style = TextStyle(
            color = secondaryTextColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        ),
        modifier = GlanceModifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun TaskRow(task: TasksWidgetRow.Task, appWidgetId: Int, context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        Text(
            text = if (task.isChecked) "☑" else "☐",
            style = TextStyle(color = primaryTextColor, fontSize = 16.sp),
            modifier = GlanceModifier
                .width(checkboxWidth)
                .clickable(
                    actionSendBroadcast(
                        toggleTaskIntent(
                            context = context,
                            appWidgetId = appWidgetId,
                            blockId = task.blockId,
                            isChecked = !task.isChecked
                        )
                    )
                )
        )

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = task.text,
                maxLines = 2,
                style = TextStyle(
                    color = primaryTextColor,
                    fontSize = 16.sp,
                    textDecoration = if (task.isChecked) TextDecoration.LineThrough else TextDecoration.None
                )
            )

            if (task.timeLabel != null) {
                Text(
                    text = task.timeLabel,
                    maxLines = 1,
                    style = TextStyle(color = secondaryTextColor, fontSize = 13.sp)
                )
            }
        }
    }
}

private fun openTasksScreenIntent(context: Context, spaceId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setData("emberr://tasks".toUri())
        .putExtra(widgetTasksScreenExtra, true)
        .putExtra(widgetSpaceIdExtra, spaceId)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun addTaskIntent(context: Context, spaceId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setData("emberr://tasks/new".toUri())
        .putExtra(widgetTasksScreenExtra, true)
        .putExtra(widgetNewTaskExtra, true)
        .putExtra(widgetSpaceIdExtra, spaceId)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun toggleCompletedViewIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, TasksWidgetActionReceiver::class.java).apply {
        action = toggleCompletedViewAction
        data = "emberr-tasks://view/$appWidgetId".toUri()
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

private fun toggleTaskIntent(
    context: Context,
    appWidgetId: Int,
    blockId: String,
    isChecked: Boolean
): Intent =
    Intent(context, TasksWidgetActionReceiver::class.java).apply {
        action = toggleTaskAction
        data = "emberr-tasks://task/$appWidgetId/$blockId".toUri()
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        putExtra(taskBlockIdExtra, blockId)
        putExtra(taskIsCheckedExtra, isChecked)
    }
