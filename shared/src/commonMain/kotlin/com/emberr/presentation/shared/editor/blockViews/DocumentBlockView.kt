package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.sync.MediaRetryCoordinator
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.editor.DefaultBlockShape
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.circle_x
import emberr.shared.generated.resources.file_text
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

private fun documentFormatLabel(fileName: String, mimeType: String?): String {
    val extension = fileName.substringAfterLast('.', "").uppercase()
    if (extension.isNotEmpty() && extension.length <= 5) return extension
    return when (mimeType) {
        "application/pdf" -> "PDF"
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOCX"
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "XLSX"
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PPTX"
        "image/png" -> "PNG"
        "image/jpeg" -> "JPEG"
        "image/gif" -> "GIF"
        "image/webp" -> "WEBP"
        "text/plain" -> "TXT"
        "application/zip" -> "ZIP"
        else -> "FILE"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentBlockView(
    block: DocumentBlock,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onRequestPicker: () -> Unit,
    onOpenFile: (filePath: String, mimeType: String) -> Unit)
{
    val mediaStorageHelper = koinInject<MediaStorageHelper>()
    val mediaRetryCoordinator = koinInject<MediaRetryCoordinator>()

    if (block.localFilePath == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .defaultMinSize(minHeight = 52.dp)
                .clip(DefaultBlockShape)
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (inSelectionMode) onToggleSelection()
                        else onRequestPicker()
                    },
                    onLongClick = if (isDesktopPlatform) null else onToggleSelection
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                Icon(painterResource(Res.drawable.file_text), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Attach a file", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        }
    } else {
        val absolutePath = remember(block.localFilePath) {
            mediaStorageHelper.getAbsoluteMediaPath(block.localFilePath)
        }
        val fileName = remember(block.localFilePath) { block.localFilePath.substringAfterLast("/") }
        val availability = rememberMediaAvailability(absolutePath, fileName)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(DefaultBlockShape)
                .background(MaterialTheme.colorScheme.surface)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (inSelectionMode) {
                            onToggleSelection()
                        } else when (availability) {
                            MediaAvailability.Available -> block.localFilePath.let { path ->
                                onOpenFile(path, block.mimeType)
                            }
                            MediaAvailability.Failed -> mediaRetryCoordinator.retryMediaDownload(fileName)
                            MediaAvailability.Downloading -> Unit
                        }
                    },
                    onLongClick = if (isDesktopPlatform) null else onToggleSelection
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 48.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = block.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                when (availability) {
                    MediaAvailability.Failed -> Text(
                        text = "Failed to download - tap to retry",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    MediaAvailability.Downloading -> Text(
                        text = "Downloading...    ${documentFormatLabel(block.fileName, block.mimeType)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    MediaAvailability.Available -> Text(
                        text = "${block.fileSizeString}    ${documentFormatLabel(block.fileName, block.mimeType)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            when (availability) {
                MediaAvailability.Failed -> Icon(
                    painterResource(Res.drawable.circle_x),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(18.dp)
                )
                MediaAvailability.Downloading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.outline
                )
                MediaAvailability.Available -> Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(18.dp)
                )
            }
        }
    }
}