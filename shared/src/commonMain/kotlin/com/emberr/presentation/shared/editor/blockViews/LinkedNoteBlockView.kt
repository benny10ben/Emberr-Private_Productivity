package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.presentation.shared.editor.DefaultBlockShape
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkedNoteBlockView(
    block: LinkedNoteBlock,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onOpenNote: () -> Unit,
    getNoteMetadata: suspend (String) -> NoteMetadataEntity?
) {
    var metadata by remember(block.linkedNoteId) { mutableStateOf<NoteMetadataEntity?>(null) }
    var isLoading by remember(block.linkedNoteId) { mutableStateOf(true) }

    LaunchedEffect(block.linkedNoteId) {
        isLoading = true
        metadata = getNoteMetadata(block.linkedNoteId)
        isLoading = false
    }

    val mediaStorageHelper: MediaStorageHelper = koinInject()
    val coverImagePath = metadata?.coverImagePath

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(DefaultBlockShape)
            .background(MaterialTheme.colorScheme.surface)
//            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), DefaultBlockShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (inSelectionMode) onToggleSelection() else onOpenNote() },
                onLongClick = onToggleSelection
            )
    ) {
        AnimatedVisibility(
            visible = block.showCoverImage,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(220))
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                if (coverImagePath != null) {
                    val absolutePath = remember(coverImagePath) { mediaStorageHelper.getAbsoluteMediaPath(coverImagePath) }
                    val context = LocalPlatformContext.current
                    val request = remember(absolutePath) {
                        ImageRequest.Builder(context)
                            .data(File(absolutePath))
                            .memoryCacheKey(absolutePath)
                            .diskCacheKey(absolutePath)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = "Cover Image",
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (block.showIcon) {
                val icon = metadata?.icon
                if (icon != null) {
                    Text(text = icon, style = MaterialTheme.typography.titleLarge)
                } else {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            val titleText = when {
                isLoading -> "Loading..."
                metadata == null -> "Note not found"
                else -> metadata?.title?.ifEmpty { "Untitled" } ?: "Untitled"
            }
            val isMissing = !isLoading && metadata == null

            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isMissing) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
