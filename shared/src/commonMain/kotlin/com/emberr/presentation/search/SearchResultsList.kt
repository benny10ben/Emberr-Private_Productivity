package com.emberr.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteSearchResult
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.calendar_day
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private const val EMPTY_MESSAGE_DELAY_MILLIS = 350L
private val ResultShape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)

/**
 * The shared result list behind cross-note search. Results arrive in two waves (title/snippet
 * hits first, block-content hits a moment later), so the "nothing found" message waits out a
 * short settle delay instead of flashing between those two emissions.
 */
@Composable
fun SearchResultsList(
    query: String,
    results: List<NoteSearchResult>,
    onNoteClick: (String) -> Unit,
    onDailyNoteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    var hasSettledOnNoResults by remember { mutableStateOf(false) }

    LaunchedEffect(query, results) {
        hasSettledOnNoResults = false
        if (results.isEmpty()) {
            delay(EMPTY_MESSAGE_DELAY_MILLIS)
            hasSettledOnNoResults = true
        }
    }

    Box(modifier = modifier) {
        when {
            query.isBlank() -> SearchMessage("Start typing to search titles, snippets, and note content.")
            results.isEmpty() -> if (hasSettledOnNoResults) SearchMessage("No matching notes found.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.note.noteId }) { result ->
                    SearchResultRow(
                        result = result,
                        query = query,
                        onClick = {
                            if (result.note.isDaily) {
                                result.note.dateString?.let(onDailyNoteClick)
                            } else {
                                onNoteClick(result.note.noteId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
private fun SearchResultRow(
    result: NoteSearchResult,
    query: String,
    onClick: () -> Unit
) {
    val note: NoteMetadataEntity = result.note
    val highlightStyle = defaultHighlightStyle(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))

    // A daily note is stored with the title "Daily: <date>", which the calendar chip below already
    // says - so the title line shows the bare date and the chip carries the "Daily" part.
    val dailyDateString = note.dateString?.takeIf { note.isDaily }
    val titleText = dailyDateString ?: note.title.ifBlank { "Untitled" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ResultShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = highlightMatches(titleText, query, highlightStyle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (result.matchedText.isNotBlank() && result.matchedText != note.title) {
            Text(
                text = highlightMatches(result.matchedText, query, highlightStyle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (dailyDateString != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.calendar_day),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Daily",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
