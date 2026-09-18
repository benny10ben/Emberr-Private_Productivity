// The shared "choose a note" list that every widget setup screen shows.
package com.emberr.presentation.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.NoteMetadataEntity

@Composable
fun WidgetNoteChooser(
    notes: List<SelectableWidgetNote>,
    onNoteChosen: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Choose a note",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        )

        if (notes.isEmpty()) {
            WidgetSetupNotice("You have no notes yet")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = notes, key = { entry -> entry.note.noteId }) { entry ->
                    NoteChooserRow(
                        note = entry.note,
                        spaceName = entry.spaceName,
                        onClick = { onNoteChosen(entry.note.noteId) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
fun WidgetSetupNotice(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun NoteChooserRow(note: NoteMetadataEntity, spaceName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!note.icon.isNullOrBlank()) {
            Text(text = note.icon, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = note.title.trim().ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val snippet = note.snippet.trim()
            val subtitle = listOf(spaceName.trim(), snippet)
                .filter { part -> part.isNotBlank() }
                .joinToString(separator = "  ·  ")

            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
