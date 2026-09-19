// The home screen widget itself: draws one chosen note's title and its scrollable content.
package com.emberr.presentation.widget.note

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.emberr.MainActivity
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import androidx.core.net.toUri
import com.emberr.presentation.widget.WidgetLog
import com.emberr.presentation.widget.highlightedTextBackgroundColor
import com.emberr.presentation.widget.primaryTextColor
import com.emberr.presentation.widget.secondaryTextColor
import com.emberr.presentation.widget.separatorColor
import com.emberr.presentation.widget.surfaceColor
import com.emberr.presentation.widget.widgetNoteIdExtra

private const val maximumIndentationLevels = 4
private const val indentationStepInDp = 12
private val recordLabelWidth = 92.dp
private val surfaceHorizontalPadding = 14.dp
private val highlightHorizontalPadding = 2.dp
private const val safeRowWidthFraction = 0.94f

class NoteWidget : GlanceAppWidget(), KoinComponent {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storedNoteId = readSelectedNoteId(context, id)?.takeIf { it.isNotBlank() }

        val content = storedNoteId?.let { noteId ->
            loadAndCacheContent(context, id, noteId) ?: readCachedContent(context, id)
        }

        val appWidgetId = resolveAppWidgetId(context, id)
        val reportedWidgetWidth = readWidgetWidth(context, appWidgetId)

        provideContent {
            WidgetBody(
                context = context,
                chosenNoteId = storedNoteId,
                content = content,
                appWidgetId = appWidgetId,
                reportedWidgetWidth = reportedWidgetWidth
            )
        }
    }

    private suspend fun loadAndCacheContent(
        context: Context,
        id: GlanceId,
        noteId: String
    ): WidgetNoteContent? {
        val contentReader = try {
            get<WidgetContentReader>()
        } catch (cause: Exception) {
            WidgetLog.e("Notes are not reachable yet", cause)
            return null
        }

        val freshContent = contentReader.readNoteContentOnce(noteId) ?: return null

        if (freshContent != readCachedContent(context, id)) {
            writeCachedContent(context, id, freshContent)
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

private fun readWidgetWidth(context: Context, appWidgetId: Int): Dp? {
    if (appWidgetId == INVALID_APPWIDGET_ID) return null

    return try {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val isLandscape =
            context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val widthKey = if (isLandscape) {
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
        } else {
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        }

        options.getInt(widthKey, 0).takeIf { widthInDp -> widthInDp > 0 }?.dp
    } catch (cause: Exception) {
        WidgetLog.e("Could not read the widget width", cause)
        null
    }
}

private fun openPickerIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, NoteWidgetPickerActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

private fun openNoteIntent(context: Context, noteId: String): Intent =
    Intent(context, MainActivity::class.java)
        .setData("emberr://note/$noteId".toUri())
        .putExtra(widgetNoteIdExtra, noteId)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
internal fun WidgetBody(
    context: Context,
    chosenNoteId: String?,
    content: WidgetNoteContent?,
    appWidgetId: Int,
    reportedWidgetWidth: Dp? = null
) {
    NoteSurface {
        when {
            chosenNoteId == null -> CenteredMessage(
                message = "Tap to choose a note",
                tapAction = actionStartActivity(openPickerIntent(context, appWidgetId))
            )

            content == null -> CenteredMessage(
                message = "This note is no longer available. Tap to choose another",
                tapAction = actionStartActivity(openPickerIntent(context, appWidgetId))
            )

            else -> NoteContent(
                content = content,
                openNoteAction = actionStartActivity(openNoteIntent(context, chosenNoteId)),
                reportedWidgetWidth = reportedWidgetWidth
            )
        }
    }
}

@Composable
private fun NoteSurface(content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surfaceColor)
            .cornerRadius(20.dp)
            .padding(horizontal = surfaceHorizontalPadding, vertical = 12.dp)
    ) {
        content()
    }
}

@Composable
private fun CenteredMessage(message: String, tapAction: Action? = null) {
    Column(
        modifier = GlanceModifier.fillMaxSize().thenClickable(tapAction),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = message,
            style = TextStyle(color = secondaryTextColor, fontSize = 15.sp)
        )
    }
}

@Composable
private fun NoteContent(
    content: WidgetNoteContent,
    openNoteAction: Action?,
    reportedWidgetWidth: Dp?
) {
    Text(
        text = content.title,
        maxLines = 1,
        style = TextStyle(
            color = primaryTextColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = GlanceModifier.fillMaxWidth().thenClickable(openNoteAction)
    )

    Spacer(modifier = GlanceModifier.height(8.dp))

    if (content.elements.isEmpty()) {
        Column(
            modifier = GlanceModifier.fillMaxSize().thenClickable(openNoteAction)
        ) {
            Text(
                text = "This note is empty",
                style = TextStyle(color = secondaryTextColor, fontSize = 15.sp)
            )
        }
        return
    }

    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(
            items = content.elements,
            itemId = { element -> element.key.hashCode().toLong() }
        ) { element ->
            ContentElement(
                element = element,
                openNoteAction = openNoteAction,
                reportedWidgetWidth = reportedWidgetWidth
            )
        }
    }
}

@Composable
private fun ContentElement(
    element: WidgetElement,
    openNoteAction: Action?,
    reportedWidgetWidth: Dp?
) {
    when (element) {
        is WidgetElement.DividerLine -> Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .thenClickable(openNoteAction)
        ) {
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(separatorColor)
            )
        }

        is WidgetElement.TextLine -> when {
            element.isHighlighted -> HighlightedTextLine(line = element, openNoteAction = openNoteAction)

            element.highlightedRanges.isNotEmpty() -> PartlyHighlightedTextLine(
                line = element,
                openNoteAction = openNoteAction,
                reportedWidgetWidth = reportedWidgetWidth
            )

            else -> Text(
                text = element.text,
                style = resolveTextStyle(element),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(start = startPaddingFor(element.indentationLevel), top = 4.dp, bottom = 4.dp)
                    .thenClickable(openNoteAction)
            )
        }

        is WidgetElement.Record -> RecordElement(record = element, openNoteAction = openNoteAction)
    }
}

@Composable
private fun HighlightedTextLine(line: WidgetElement.TextLine, openNoteAction: Action?) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = startPaddingFor(line.indentationLevel), top = 4.dp, bottom = 4.dp)
            .thenClickable(openNoteAction),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = GlanceModifier
                .background(highlightedTextBackgroundColor(line.highlightColorName))
                .cornerRadius(4.dp)
                .padding(horizontal = 4.dp, vertical = 1.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(text = line.text, style = resolveTextStyle(line))
        }
    }
}

@Composable
private fun PartlyHighlightedTextLine(
    line: WidgetElement.TextLine,
    openNoteAction: Action?,
    reportedWidgetWidth: Dp?
) {
    val context = LocalContext.current
    val pixelsPerDp = context.resources.displayMetrics.density
    val sharedTextStyle = resolveTextStyle(line)
    val indentation = startPaddingFor(line.indentationLevel)
    val widgetWidth = reportedWidgetWidth ?: LocalSize.current.width
    val availableWidth = widgetWidth - surfaceHorizontalPadding * 2 - indentation

    val wrappedRows = wrapRunsIntoRows(
        runs = splitIntoHighlightRuns(line),
        availableWidthInPixels = availableWidth.value * pixelsPerDp * safeRowWidthFraction,
        highlightPaddingInPixels = highlightHorizontalPadding.value * 2 * pixelsPerDp,
        maximumRows = maximumWrappedRows,
        measureTextWidth = buildTextWidthMeasurer(context, line.style)
    )

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = indentation, top = 4.dp, bottom = 4.dp)
            .thenClickable(openNoteAction)
    ) {
        wrappedRows.chunked(maximumChildrenPerGlanceContainer).forEach { rowGroup ->
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                rowGroup.forEach { runsInRow ->
                    HighlightedRunsRow(runsInRow = runsInRow, textStyle = sharedTextStyle)
                }
            }
        }
    }
}

@Composable
private fun HighlightedRunsRow(runsInRow: List<TextRun>, textStyle: TextStyle) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        runsInRow.forEach { run ->
            if (run.isHighlighted) {
                Box(
                    modifier = GlanceModifier
                        .background(highlightedTextBackgroundColor(run.highlightColorName))
                        .cornerRadius(4.dp)
                        .padding(horizontal = highlightHorizontalPadding, vertical = 1.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(text = run.text, maxLines = 1, style = textStyle)
                }
            } else {
                Text(text = run.text, maxLines = 1, style = textStyle)
            }
        }
    }
}

@Composable
private fun RecordElement(record: WidgetElement.Record, openNoteAction: Action?) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = startPaddingFor(record.indentationLevel), top = 7.dp, bottom = 7.dp)
            .thenClickable(openNoteAction)
    ) {
        Spacer(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(separatorColor)
        )

        record.fields.forEachIndexed { fieldIndex, field ->
            Row(modifier = GlanceModifier.fillMaxWidth().padding(top = if (fieldIndex == 0) 7.dp else 4.dp)) {
                Text(
                    text = field.label,
                    maxLines = 1,
                    style = TextStyle(color = secondaryTextColor, fontSize = 13.sp),
                    modifier = GlanceModifier.width(recordLabelWidth)
                )
                Text(
                    text = field.value,
                    maxLines = 3,
                    style = TextStyle(color = primaryTextColor, fontSize = 15.sp),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
        }
    }
}

private fun GlanceModifier.thenClickable(action: Action?): GlanceModifier =
    if (action == null) this else this.clickable(action)

private fun startPaddingFor(indentationLevel: Int) =
    (minOf(indentationLevel, maximumIndentationLevels) * indentationStepInDp).dp

private fun resolveTextStyle(line: WidgetElement.TextLine): TextStyle {
    val appearance = appearanceFor(line.style)

    return TextStyle(
        color = textColorFor(line.style),
        fontSize = appearance.fontSizeInSp.sp,
        fontWeight = glanceFontWeightFor(appearance.fontWeight),
        fontStyle = if (appearance.isItalic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = if (appearance.isMonospace) FontFamily.Monospace else null,
        textDecoration = if (line.isStruckThrough) TextDecoration.LineThrough else TextDecoration.None
    )
}

private fun textColorFor(style: WidgetTextStyleName) = when (style) {
    WidgetTextStyleName.QUOTE, WidgetTextStyleName.SUBTLE -> secondaryTextColor
    else -> primaryTextColor
}

private fun glanceFontWeightFor(fontWeight: Int): FontWeight = when {
    fontWeight >= 700 -> FontWeight.Bold
    fontWeight >= 500 -> FontWeight.Medium
    else -> FontWeight.Normal
}
