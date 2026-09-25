package com.emberr.presentation.ai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.models.ModelDownloadProgress
import com.emberr.domain.ai.models.ModelFileNames
import com.emberr.presentation.ai.LocalModelUploadState
import com.emberr.presentation.ai.RagViewModel
import com.emberr.presentation.ai.components.ModelOptionCard
import com.emberr.presentation.ai.components.UnsupportedHardwareNotice
import com.emberr.presentation.shared.components.EmberrAlertDialog
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrBottomSheetOption
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrTextField

@Composable
internal fun LocalAiSettingsSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    onPickDocument: (onPathSelected: (String) -> Unit) -> Unit
) {
    var isResumable by remember { mutableStateOf(false) }
    val downloadProgress by viewModel.localGeneratorDownloadProgress.collectAsState()
    val uploadState by viewModel.localModelUploadState.collectAsState()
    val uploadProgress by viewModel.localModelUploadProgress.collectAsState()
    val installedLocalModels by viewModel.installedLocalModels.collectAsState()
    val selectedLocalModelFileName by viewModel.selectedLocalModelFileName.collectAsState()
    val pendingDeletionFileNames by viewModel.pendingDeletionLocalModelFileNames.collectAsState()
    val localContextLength by viewModel.localContextLength.collectAsState()
    val finetuneWarning by viewModel.uploadedModelFinetuneWarning.collectAsState()
    var showUploadWarning by remember { mutableStateOf(false) }
    var contextLengthInput by remember(localContextLength) { mutableStateOf(localContextLength.toString()) }

    val isDefaultModelInstalled = installedLocalModels.any { it.fileName == ModelFileNames.GENERATOR }

    LaunchedEffect(expanded) {
        if (expanded) {
            viewModel.refreshInstalledLocalModels()
            isResumable = viewModel.hasResumableGeneratorDownload()
        } else {
            viewModel.resetLocalModelUploadState()
        }
    }

    LaunchedEffect(downloadProgress) {
        if (downloadProgress is ModelDownloadProgress.Failed || downloadProgress is ModelDownloadProgress.Paused) {
            isResumable = viewModel.hasResumableGeneratorDownload()
        }
        if (downloadProgress is ModelDownloadProgress.Completed) {
            isResumable = false
        }
    }

    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Local AI",
        subtitle = "Manage the on-device models.",
    ) { _ ->
        val unsupportedHardwareReason = viewModel.localAiUnsupportedReason
        if (unsupportedHardwareReason != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                UnsupportedHardwareNotice(reason = unsupportedHardwareReason)
                EmberrButtonPrimary(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            }
            return@EmberrBottomSheet
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            val currentDownloadProgress = downloadProgress
            val isDownloading = currentDownloadProgress is ModelDownloadProgress.Downloading

            ModelOptionCard(
                icon = null,
                title = "qwen2.5-1.5b-instruct-q8_0",
                subtitle = when (currentDownloadProgress) {
                    is ModelDownloadProgress.Downloading -> "Downloading… ${(currentDownloadProgress.fraction * 100).toInt()}%"
                    is ModelDownloadProgress.Failed -> currentDownloadProgress.message
                    ModelDownloadProgress.Paused -> "Paused."
                    else -> if (isDefaultModelInstalled)
                        "Recommended - already downloaded."
                    else
                        "Recommended - balanced quality and speed."
                },
                onClick = {}
            )

            if (isDownloading || !isDefaultModelInstalled) {
                Spacer(Modifier.height(10.dp))
                EmberrButtonPrimary(
                    text = when {
                        isDownloading -> "Pause Download"
                        isResumable -> "Resume Download"
                        else -> "Download"
                    },
                    onClick = {
                        if (isDownloading) {
                            viewModel.pauseGeneratorModelDownload()
                        } else {
                            viewModel.downloadGeneratorModel()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            val currentUploadState = uploadState
            val isUploading = currentUploadState is LocalModelUploadState.Uploading

            EmberrButtonPrimary(
                text = if (isUploading)
                    "Copying model file… ${(uploadProgress * 100).toInt()}%"
                else
                    "Upload your own model",
                enabled = !isUploading,
                onClick = { showUploadWarning = true },
                modifier = Modifier.fillMaxWidth()
            )

            val uploadStatusMessage = when (currentUploadState) {
                LocalModelUploadState.Success -> "Uploaded successfully."
                is LocalModelUploadState.Failed -> currentUploadState.message
                else -> null
            }
            if (uploadStatusMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = uploadStatusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Context Length",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            EmberrTextField(
                value = contextLengthInput,
                onValueChange = { input ->
                    val digitsOnly = input.filter { it.isDigit() }
                    contextLengthInput = digitsOnly
                    digitsOnly.toIntOrNull()?.let { tokens ->
                        if (tokens > 0) viewModel.selectLocalContextLength(tokens)
                    }
                },
                placeholder = "4096",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Applies to the selected model. 4096 suits Qwen - for other models, use their training context length.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (installedLocalModels.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Installed models",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))

                installedLocalModels.forEach { model ->
                    val isPendingDeletion = model.fileName in pendingDeletionFileNames
                    EmberrBottomSheetOption(
                        label = model.displayName,
                        labelMaxLines = 1,
                        isSelected = !isPendingDeletion && selectedLocalModelFileName == model.fileName,
                        subtitle = if (isPendingDeletion)
                            "Deleted — will be removed permanently on next app restart."
                        else
                            null,
                        outerHorizontalPadding = 0.dp,
                        onClick = {
                            if (!isPendingDeletion) viewModel.selectLocalModel(model.fileName)
                        },
                        trailing = {
                            if (isPendingDeletion) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = "Restore model",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.restoreLocalModel(model.fileName) }
                                )
                                Spacer(Modifier.width(5.dp))
                            } else {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete model",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { viewModel.deleteLocalModel(model.fileName) }
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                        }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            EmberrButtonPrimary(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }

    if (showUploadWarning) {
        EmberrAlertDialog(
            onDismissRequest = { showUploadWarning = false },
            title = "Uploading a custom model"
        ) {
            Text(
                text = "Heavy or high-parameter models can use a lot of RAM and may freeze or crash your device. Only upload a model you know your device can handle.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EmberrButtonSecondary(
                    text = "Cancel",
                    onClick = { showUploadWarning = false },
                    modifier = Modifier.weight(1f)
                )
                EmberrButtonPrimary(
                    text = "Proceed",
                    onClick = {
                        showUploadWarning = false
                        onPickDocument { path -> viewModel.uploadGeneratorModel(path) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (finetuneWarning != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUploadedModelFinetuneWarning() },
            title = {
                Text("Unverified model type", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(finetuneWarning.orEmpty(), style = MaterialTheme.typography.labelSmall)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissUploadedModelFinetuneWarning() }) {
                    Text("Got it", style = MaterialTheme.typography.bodyLarge)
                }
            }
        )
    }
}
