// The home screen widget itself: draws the list of notes with a title and an add button.
package com.emberr.presentation.widget.notelist

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
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.emberr.MainActivity
import com.emberr.R
import com.emberr.presentation.widget.primaryTextColor
import com.emberr.presentation.widget.secondaryTextColor
import com.emberr.presentation.widget.surfaceColor
import com.emberr.presentation.widget.widgetHomeScreenExtra
import com.emberr.presentation.widget.widgetNewNoteExtra
import com.emberr.presentation.widget.widgetNoteIdExtra
import com.emberr.presentation.widget.readWidgetSpaceId
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private val topBarIconSize = 22.dp

class NoteListWidget : GlanceAppWidget(), KoinComponent {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadAndCacheNoteList(context, id) ?: readCachedNoteList(context, id)

        provideContent {
            NoteListWidgetBody(context = context, content = content)
        }
    }

    private suspend fun loadAndCacheNoteList(context: Context, id: GlanceId): NoteListWidgetContent? {
        val contentReader = runCatching { get<NoteListWidgetContentReader>() }.getOrNull() ?: return null
        val spaceId = readWidgetSpaceId(context, id)
        val freshContent = contentReader.readContentOnce(spaceId) ?: return null

        if (freshContent != readCachedNoteList(context, id)) {
            writeCachedNoteList(context, id, freshContent)
        }
        return freshContent
    }
}

@Composable
internal fun NoteListWidgetBody(context: Context, content: NoteListWidgetContent?) {
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
                text = "Notes",
                maxLines = 1,
                style = TextStyle(
                    color = primaryTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openHomeScreenIntent(context)))
            )

            Image(
                provider = ImageProvider(R.drawable.ic_widget_circle_plus),
                contentDescription = "New note",
                colorFilter = ColorFilter.tint(primaryTextColor),
                modifier = GlanceModifier
                    .size(topBarIconSize)
                    .clickable(actionStartActivity(newNoteIntent(context)))
            )
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        val notes = content?.notes.orEmpty()
        if (notes.isEmpty()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .clickable(actionStartActivity(openHomeScreenIntent(context))),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Text(
                    text = if (content == null) {
                        "Open Emberr to load your notes"
                    } else {
                        "No notes yet. Tap to write one."
                    },
                    style = TextStyle(color = secondaryTextColor, fontSize = 15.sp)
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(items = notes, itemId = { note -> note.noteId.hashCode().toLong() }) { note ->
                    NoteRow(context = context, note = note)
                }
            }
        }
    }
}

@Composable
private fun NoteRow(context: Context, note: NoteListWidgetRow) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(actionStartActivity(openNoteIntent(context, note.noteId)))
    ) {
        Text(
            text = note.title,
            maxLines = 1,
            style = TextStyle(color = primaryTextColor, fontSize = 16.sp)
        )

        if (note.snippet.isNotBlank()) {
            Text(
                text = note.snippet,
                maxLines = 1,
                style = TextStyle(color = secondaryTextColor, fontSize = 13.sp)
            )
        }
    }
}

private fun openHomeScreenIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setData("emberr://notes".toUri())
        .putExtra(widgetHomeScreenExtra, true)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun newNoteIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setData("emberr://notes/new".toUri())
        .putExtra(widgetHomeScreenExtra, true)
        .putExtra(widgetNewNoteExtra, true)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun openNoteIntent(context: Context, noteId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setData("emberr://note/$noteId".toUri())
        .putExtra(widgetNoteIdExtra, noteId)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
