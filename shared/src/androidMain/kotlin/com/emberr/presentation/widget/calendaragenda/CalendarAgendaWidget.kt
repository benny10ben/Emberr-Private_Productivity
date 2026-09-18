// The home screen widget itself: a compact month on the left and that month's events on the right.
package com.emberr.presentation.widget.calendaragenda

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
import com.emberr.presentation.widget.calendarEventUriScheme
import com.emberr.presentation.widget.widgetSpaceIdExtra
import com.emberr.presentation.widget.highlightColor
import com.emberr.presentation.widget.onHighlightColor
import com.emberr.presentation.widget.primaryTextColor
import com.emberr.presentation.widget.secondaryTextColor
import com.emberr.presentation.widget.surfaceColor
import com.emberr.presentation.widget.readWidgetSpaceId
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import androidx.core.graphics.toColorInt

private val chevronSize = 18.dp
private val dayHighlightSize = 17.dp
private val eventAccentWidth = 3.dp
private val headerHeight = 24.dp

class CalendarAgendaWidget : GlanceAppWidget(), KoinComponent {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val shownMonth = readAgendaShownMonth(context, id)
        val content = loadAndCacheAgenda(context, id, shownMonth) ?: readCachedAgenda(context, id)
        val appWidgetId = resolveAppWidgetId(context, id)
        val spaceId = readWidgetSpaceId(context, id)

        provideContent {
            CalendarAgendaWidgetBody(
                context = context,
                appWidgetId = appWidgetId,
                spaceId = spaceId,
                content = content
            )
        }
    }

    private suspend fun loadAndCacheAgenda(
        context: Context,
        id: GlanceId,
        shownMonth: String?
    ): CalendarAgendaWidgetContent? {
        val contentReader = runCatching { get<CalendarAgendaWidgetContentReader>() }.getOrNull() ?: return null
        val spaceId = readWidgetSpaceId(context, id)
        val freshContent = contentReader.readContentOnce(spaceId, shownMonth) ?: return null

        if (freshContent != readCachedAgenda(context, id)) {
            writeCachedAgenda(context, id, freshContent)
        }
        return freshContent
    }

    private fun resolveAppWidgetId(context: Context, id: GlanceId): Int =
        try {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        } catch (cause: Exception) {
            WidgetLog.e("Could not resolve the widget id", cause)
            INVALID_APPWIDGET_ID
        }
}

@Composable
internal fun CalendarAgendaWidgetBody(
    context: Context,
    appWidgetId: Int,
    spaceId: String,
    content: CalendarAgendaWidgetContent?
) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surfaceColor)
            .cornerRadius(24.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            MonthGrid(content = content)
        }

        Spacer(modifier = GlanceModifier.width(14.dp))

        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = content?.monthLabel.orEmpty(),
                    maxLines = 1,
                    style = TextStyle(
                        color = primaryTextColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                Image(
                    provider = ImageProvider(R.drawable.ic_widget_chevron_left),
                    contentDescription = "Previous month",
                    colorFilter = ColorFilter.tint(secondaryTextColor),
                    modifier = GlanceModifier
                        .size(chevronSize)
                        .clickable(
                            actionSendBroadcast(agendaMonthStepIntent(context, appWidgetId, isForward = false))
                        )
                )

                Spacer(modifier = GlanceModifier.width(6.dp))

                Image(
                    provider = ImageProvider(R.drawable.ic_widget_chevron_right),
                    contentDescription = "Next month",
                    colorFilter = ColorFilter.tint(secondaryTextColor),
                    modifier = GlanceModifier
                        .size(chevronSize)
                        .clickable(
                            actionSendBroadcast(agendaMonthStepIntent(context, appWidgetId, isForward = true))
                        )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            EventList(context = context, spaceId = spaceId, content = content)
        }
    }
}

@Composable
private fun ColumnScope.MonthGrid(content: CalendarAgendaWidgetContent?) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(headerHeight),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        content?.weekdayLabels.orEmpty().forEach { label ->
            Box(
                modifier = GlanceModifier.defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    style = TextStyle(color = secondaryTextColor, fontSize = 10.sp)
                )
            }
        }
    }

    content?.weeks.orEmpty().forEach { week ->
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            week.forEach { cell ->
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.Center
                ) {
                    DayCell(cell = cell)
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: AgendaDayCell) {
    if (cell.dayNumber == null) {
        Spacer(modifier = GlanceModifier.size(dayHighlightSize))
        return
    }

    Box(
        modifier = GlanceModifier
            .size(dayHighlightSize)
            .then(
                if (cell.isToday) {
                    GlanceModifier.background(highlightColor).cornerRadius(6.dp)
                } else {
                    GlanceModifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cell.dayNumber.toString(),
            maxLines = 1,
            style = TextStyle(
                color = when {
                    cell.isToday -> onHighlightColor
                    cell.hasEvents -> primaryTextColor
                    else -> secondaryTextColor
                },
                fontSize = 11.sp,
                fontWeight = if (cell.isToday || cell.hasEvents) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

@Composable
private fun EventList(context: Context, spaceId: String, content: CalendarAgendaWidgetContent?) {
    val events = content?.events.orEmpty()

    if (events.isEmpty()) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                text = if (content == null) "Open Emberr to load events" else "No events this month",
                maxLines = 2,
                style = TextStyle(color = secondaryTextColor, fontSize = 15.sp)
            )
        }
        return
    }

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(items = events, itemId = { event -> event.blockId.hashCode().toLong() }) { event ->
            EventRow(context = context, spaceId = spaceId, event = event)
        }
    }
}

@Composable
private fun EventRow(context: Context, spaceId: String, event: AgendaEvent) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .clickable(actionStartActivity(openEventIntent(context, event.blockId, spaceId)))
    ) {
        Box(
            modifier = GlanceModifier
                .width(eventAccentWidth)
                .fillMaxHeight()
                .background(accentColorFrom(event.accentColorHex))
                .cornerRadius(eventAccentWidth)
        ) {}

        Spacer(modifier = GlanceModifier.width(7.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title,
                maxLines = 1,
                style = TextStyle(
                    color = if (event.isDone) secondaryTextColor else primaryTextColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (event.isDone) TextDecoration.LineThrough else TextDecoration.None
                )
            )

            Text(
                text = event.whenLabel,
                maxLines = 1,
                style = TextStyle(color = secondaryTextColor, fontSize = 13.sp)
            )
        }
    }
}

private fun accentColorFrom(colorHex: String?): Color {
    val parsed = colorHex?.let { hex ->
        try {
            Color(hex.toColorInt())
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    return parsed ?: Color(0xFF848484)
}

private fun agendaMonthStepIntent(context: Context, appWidgetId: Int, isForward: Boolean): Intent =
    Intent(context, CalendarAgendaWidgetActionReceiver::class.java).apply {
        action = if (isForward) showNextAgendaMonthAction else showPreviousAgendaMonthAction
        data = "emberr-agenda://step/$appWidgetId/$isForward".toUri()
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

private fun openEventIntent(context: Context, blockId: String, spaceId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setData("$calendarEventUriScheme://event/$blockId".toUri())
        .putExtra(widgetSpaceIdExtra, spaceId)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
