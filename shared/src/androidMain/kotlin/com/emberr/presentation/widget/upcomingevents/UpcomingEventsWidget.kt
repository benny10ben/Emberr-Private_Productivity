// The home screen widget itself: a scrollable, date-grouped list of every upcoming event.
package com.emberr.presentation.widget.upcomingevents

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.text.TextStyle
import com.emberr.MainActivity
import com.emberr.R
import com.emberr.presentation.widget.calendarEventUriScheme
import com.emberr.presentation.widget.primaryTextColor
import com.emberr.presentation.widget.secondaryTextColor
import com.emberr.presentation.widget.surfaceColor
import com.emberr.presentation.widget.widgetCalendarScreenExtra
import com.emberr.presentation.widget.widgetNewEventExtra
import com.emberr.presentation.widget.readWidgetSpaceId
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val eventAccentWidth = 3.dp
private val topBarIconSize = 22.dp

class UpcomingEventsWidget : GlanceAppWidget(), KoinComponent {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadAndCacheUpcomingEvents(context, id) ?: readCachedUpcomingEvents(context, id)

        provideContent {
            UpcomingEventsWidgetBody(context = context, content = content)
        }
    }

    private suspend fun loadAndCacheUpcomingEvents(
        context: Context,
        id: GlanceId
    ): UpcomingEventsWidgetContent? {
        val contentReader = runCatching { get<UpcomingEventsWidgetContentReader>() }.getOrNull() ?: return null
        val spaceId = readWidgetSpaceId(context, id)
        val freshContent = contentReader.readContentOnce(spaceId) ?: return null

        if (freshContent != readCachedUpcomingEvents(context, id)) {
            writeCachedUpcomingEvents(context, id, freshContent)
        }
        return freshContent
    }
}

@Composable
internal fun UpcomingEventsWidgetBody(
    context: Context,
    content: UpcomingEventsWidgetContent?
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surfaceColor)
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = "Upcoming Events",
                maxLines = 1,
                style = TextStyle(
                    color = primaryTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openCalendarScreenIntent(context)))
            )

            Image(
                provider = ImageProvider(R.drawable.ic_widget_circle_plus),
                contentDescription = "New event",
                colorFilter = ColorFilter.tint(primaryTextColor),
                modifier = GlanceModifier
                    .size(topBarIconSize)
                    .clickable(actionStartActivity(newEventIntent(context)))
            )
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        EventList(context = context, content = content)
    }
}

@Composable
private fun EventList(context: Context, content: UpcomingEventsWidgetContent?) {
    val events = content?.events.orEmpty()

    if (events.isEmpty()) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            Text(
                text = if (content == null) "Open Emberr to load events" else "No upcoming events",
                maxLines = 2,
                style = TextStyle(color = secondaryTextColor, fontSize = 15.sp)
            )
        }
        return
    }

    val eventsByDate = events.groupBy { event -> event.dateGroupKey }

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        eventsByDate.forEach { (dateGroupKey, eventsOnDate) ->
            item(itemId = dateGroupKey.hashCode().toLong()) {
                DateHeader(label = eventsOnDate.first().dateLabel)
            }

            items(items = eventsOnDate, itemId = { event -> event.blockId.hashCode().toLong() }) { event ->
                EventRow(context = context, event = event)
            }
        }
    }
}

@Composable
private fun DateHeader(label: String) {
    Text(
        text = label,
        maxLines = 1,
        style = TextStyle(
            color = secondaryTextColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = GlanceModifier.padding(top = 6.dp, bottom = 4.dp)
    )
}

@Composable
private fun EventRow(context: Context, event: UpcomingEvent) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(actionStartActivity(openEventIntent(context, event.blockId)))
    ) {
        Box(
            modifier = GlanceModifier
                .width(eventAccentWidth)
                .fillMaxHeight()
                .background(accentColorFrom(event.accentColorHex))
                .cornerRadius(eventAccentWidth)
        ) {}

        Spacer(modifier = GlanceModifier.width(8.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = event.title,
                maxLines = 1,
                style = TextStyle(
                    color = primaryTextColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            )

            if (event.timeLabel != null) {
                Text(
                    text = event.timeLabel,
                    maxLines = 1,
                    style = TextStyle(
                        color = if (event.isOverdue) primaryTextColor else secondaryTextColor,
                        fontSize = 13.sp,
                        fontWeight = if (event.isOverdue) FontWeight.Medium else FontWeight.Normal
                    )
                )
            }
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

private fun openEventIntent(context: Context, blockId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setData("$calendarEventUriScheme://event/$blockId".toUri())
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun openCalendarScreenIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setData("emberr://calendar".toUri())
        .putExtra(widgetCalendarScreenExtra, true)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun newEventIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setData("emberr://calendar/new".toUri())
        .putExtra(widgetCalendarScreenExtra, true)
        .putExtra(widgetNewEventExtra, true)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
