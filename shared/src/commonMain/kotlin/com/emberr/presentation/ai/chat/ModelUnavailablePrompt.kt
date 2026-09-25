package com.emberr.presentation.ai.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.models.ModelDownloadProgress
import com.emberr.presentation.ai.components.ModelOptionCard
import com.emberr.presentation.ai.components.UnsupportedHardwareNotice

@Composable
internal fun ModelUnavailablePrompt(
    sidePadding: Dp,
    downloadProgress: ModelDownloadProgress?,
    localAiUnsupportedReason: String?,
    onDownloadClick: () -> Unit,
    onApiKeyClick: () -> Unit
) {
    val isDownloading = downloadProgress is ModelDownloadProgress.Downloading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = sidePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (isDownloading) "Setting up your AI" else "No AI connected yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        if (localAiUnsupportedReason == null) {
            ModelOptionCard(
                title = "Run AI on this device",
                subtitle = when (downloadProgress) {
                    is ModelDownloadProgress.Downloading -> "Downloading… ${(downloadProgress.fraction * 100).toInt()}% — tap to manage"
                    is ModelDownloadProgress.Failed -> "Download stopped. Tap to try again."
                    ModelDownloadProgress.Paused -> "Paused. Tap to resume the download."
                    else -> "Private and offline. Download or add your own model."
                },
                onClick = onDownloadClick,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            )
        } else {
            UnsupportedHardwareNotice(reason = localAiUnsupportedReason)
        }
        Spacer(Modifier.height(10.dp))

        ModelOptionCard(
            title = "Connect a cloud provider",
            subtitle = "Use your own API key with OpenAI, Claude, Gemini and more.",
            onClick = onApiKeyClick,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        )
    }
}
