package com.emberr.presentation.shared.editor.blockViews

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.emberr.domain.model.VoiceBlock
import com.emberr.domain.sync.MediaRetryCoordinator
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.editor.DefaultBlockShape
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.circle_x
import emberr.shared.generated.resources.pause
import emberr.shared.generated.resources.play
import emberr.shared.generated.resources.square
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds
import emberr.shared.generated.resources.microphone

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioBlockView(
    block: VoiceBlock,
    inSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onRemoveVoice: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: (Boolean) -> Unit,
    onPlayAudio: (String, () -> Unit) -> Unit,
    onStopAudio: () -> Unit
) {
    val mediaStorageHelper = koinInject<MediaStorageHelper>()
    val mediaRetryCoordinator = koinInject<MediaRetryCoordinator>()
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableFloatStateOf(0f) }

    val localFilePath = block.localFilePath
    val fileName = remember(localFilePath) { localFilePath?.substringAfterLast("/") }
    val availability = if (localFilePath != null && fileName != null) {
        rememberMediaAvailability(mediaStorageHelper.getAbsoluteMediaPath(localFilePath), fileName)
    } else null

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                delay(1000.milliseconds)
                recordingDuration++
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val totalSteps = (block.durationSeconds * 10).coerceAtLeast(10)
            for (i in 0..totalSteps) {
                playProgress = i.toFloat() / totalSteps
                delay(100.milliseconds)
                if (!isPlaying) break
            }
            isPlaying = false
            playProgress = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .defaultMinSize(minHeight = 52.dp)
            .clip(DefaultBlockShape)
            .background(
                if (isRecording) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { if (inSelectionMode) onToggleSelection() },
                onLongClick = if (isDesktopPlatform) null else onToggleSelection
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (block.localFilePath == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isRecording) painterResource(Res.drawable.square) else painterResource(Res.drawable.microphone),
                        contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(if (isRecording) 18.dp else 20.dp)
                            .clickable {
                                if (!inSelectionMode) {
                                    if (isRecording) {
                                        isRecording = false
                                        onStopRecording(false)
                                    } else {
                                        isRecording = true
                                        onStartRecording()
                                    }
                                }
                            }
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    if (isRecording) {
                        val mins = recordingDuration / 60
                        val secs = recordingDuration % 60
                        Text(
                            text = "Recording... ${mins}:${secs.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Tap mic to record audio",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else if (availability == MediaAvailability.Failed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { fileName?.let { mediaRetryCoordinator.retryMediaDownload(it) } }
                ) {
                    Icon(
                        painterResource(Res.drawable.circle_x),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Failed to download audio - tap to retry",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else if (availability != MediaAvailability.Available) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Downloading audio...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (isPlaying) painterResource(Res.drawable.pause) else painterResource(Res.drawable.play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                if (!inSelectionMode) {
                                    if (isPlaying) {
                                        isPlaying = false
                                        onStopAudio()
                                    } else {
                                        isPlaying = true
                                        block.localFilePath.let { path ->
                                            onPlayAudio(path) {
                                                isPlaying = false
                                                playProgress = 0f
                                            }
                                        }
                                    }
                                }
                            }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    val barActiveColor = MaterialTheme.colorScheme.onSurface
                    val barInactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .padding(end = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val barWidth = 3.dp.toPx()
                            val gap = 2.dp.toPx()
                            val totalBars = (size.width / (barWidth + gap)).toInt()

                            for (i in 0 until totalBars) {
                                val barProgress = i.toFloat() / totalBars
                                val barColor = if (barProgress <= playProgress) barActiveColor else barInactiveColor

                                val randomHeight = ((block.id.hashCode() + i) % 100) / 100f
                                val barHeight = (size.height * 0.3f) + (size.height * 0.7f * randomHeight)

                                drawLine(
                                    color = barColor,
                                    start = Offset(i * (barWidth + gap), size.height / 2f - barHeight / 2f),
                                    end = Offset(i * (barWidth + gap), size.height / 2f + barHeight / 2f),
                                    strokeWidth = barWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }
                        }
                    }

                    val mins = block.durationSeconds / 60
                    val secs = block.durationSeconds % 60
                    Text(
                        text = "${mins}:${secs.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Icon(
                    painterResource(Res.drawable.circle_x),
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(20.dp)
                        .clickable { if (!inSelectionMode) onRemoveVoice() }
                )
            }
        }
    }
}