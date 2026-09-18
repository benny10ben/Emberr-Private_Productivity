package com.emberr.presentation.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.PendingShare
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.NotePickerDialog
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.download3
import emberr.shared.generated.resources.floppy
import org.jetbrains.compose.resources.painterResource

@Composable
fun ShareReceiverSheet(
    share: PendingShare?,
    linkableNotes: List<NoteMetadataEntity>,
    onSaveToInbox: () -> Unit,
    onNoteSelected: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onCreateBlankNote: () -> Unit,
    onDismiss: () -> Unit
) {
    var lastShare by remember { mutableStateOf<PendingShare?>(null) }
    var showNotePicker by remember { mutableStateOf(false) }

    LaunchedEffect(share) {
        if (share != null) lastShare = share else showNotePicker = false
    }

    EmberrBottomSheet(
        expanded = share != null,
        onDismiss = onDismiss,
        title = "Save Shared Content"
    ) { _ ->
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            lastShare?.let { SharePreviewRow(it) }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            ShareTargetRow(
                icon = painterResource(Res.drawable.download3),
                label = "Save to Overview",
                onClick = onSaveToInbox
            )
            ShareTargetRow(
                icon = painterResource(Res.drawable.floppy),
                label = "Save to a Note",
                onClick = { showNotePicker = true }
            )

            Spacer(Modifier.height(8.dp))

            EmberrButtonPrimary(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 20.dp)
            )
        }
    }

    NotePickerDialog(
        expanded = showNotePicker,
        onDismiss = { showNotePicker = false },
        allLinkableNotes = linkableNotes,
        onNoteSelected = onNoteSelected,
        onCreateNote = onCreateNote,
        onCreateBlankNote = onCreateBlankNote
    )
}

@Composable
private fun SharePreviewRow(share: PendingShare) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (share) {
            is PendingShare.Link -> {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = share.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            is PendingShare.Image -> {
                coil3.compose.AsyncImage(
                    model = share.uriString,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Shared image",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is PendingShare.Document -> {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = share.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            is PendingShare.Multiple -> {
                val allImages = share.items.all { it is PendingShare.Image }
                Icon(
                    if (allImages) Icons.Default.Image else Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${share.items.size} ${if (allImages) "images" else "files"} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ShareTargetRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
