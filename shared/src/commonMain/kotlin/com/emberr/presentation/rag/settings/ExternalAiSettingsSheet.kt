package com.emberr.presentation.rag.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.external.ExternalAiProvider
import com.emberr.domain.ai.external.ExternalAiProviderConfig
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.rag.components.clickableWithoutMobileRipple
import com.emberr.presentation.settings.SettingsToggleRow
import com.emberr.presentation.shared.components.EmberrAlertDialog
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import com.emberr.presentation.shared.components.EmberrShadowElevation
import com.emberr.presentation.shared.components.EmberrTextField
import kotlin.time.Clock

@Composable
internal fun ExternalAiSettingsSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel
) {
    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "External AI",
        subtitle = "Connect an external provider using your own API key.",
    ) { closeAnd ->
        var isReady by remember { mutableStateOf(false) }
        var selectedProvider by remember { mutableStateOf(ExternalAiProvider.OPENAI) }
        var loadedConfig by remember { mutableStateOf<ExternalAiProviderConfig?>(null) }
        var apiKeyInput by remember { mutableStateOf("") }
        var modelInput by remember { mutableStateOf("") }
        var baseUrlInput by remember { mutableStateOf("") }
        var isApiKeyVisible by remember { mutableStateOf(false) }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            selectedProvider = viewModel.selectedExternalAiProvider.value
            isReady = true
        }

        LaunchedEffect(selectedProvider, isReady) {
            if (!isReady) return@LaunchedEffect
            val config = viewModel.getExternalAiConfig(selectedProvider)
            loadedConfig = config
            modelInput = config?.model.orEmpty()
            baseUrlInput = config?.baseUrl.orEmpty()
            apiKeyInput = ""
        }

        val hasUsableKey = apiKeyInput.isNotBlank() || loadedConfig?.apiKey?.isNotBlank() == true
        val hasUsableEndpoint = selectedProvider != ExternalAiProvider.CUSTOM || baseUrlInput.isNotBlank()
        val onSave: () -> Unit = {
            if (hasUsableKey && hasUsableEndpoint) {
                val configToSave = ExternalAiProviderConfig(
                    apiKey = apiKeyInput.ifBlank { loadedConfig?.apiKey.orEmpty() },
                    model = modelInput.ifBlank { selectedProvider.defaultModel.orEmpty() },
                    baseUrl = baseUrlInput.ifBlank { null },
                    updatedAt = Clock.System.now().toEpochMilliseconds()
                )
                viewModel.saveExternalAiConfig(selectedProvider, configToSave)
                closeAnd { }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = "Provider",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExternalAiProvider.entries.forEach { provider ->
                    ProviderChip(
                        label = provider.displayName,
                        selected = provider == selectedProvider,
                        onClick = { selectedProvider = provider }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            val isReadOnly by viewModel.externalAiReadOnly.collectAsState()
            SettingsToggleRow(
                icon = rememberVectorPainter(Icons.Default.Lock),
                title = "Read-only vault access",
                isChecked = isReadOnly,
                onCheckedChange = viewModel::selectExternalAiReadOnly
            )

            if (selectedProvider == ExternalAiProvider.CUSTOM) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Base URL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                EmberrTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    placeholder = "https://your-endpoint.example.com/v1",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    onSubmit = onSave
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Model",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            EmberrTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                placeholder = selectedProvider.defaultModel ?: "Enter a model name",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                onSubmit = onSave
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "API Key",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            EmberrTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                placeholder = if (loadedConfig?.apiKey?.isNotBlank() == true)
                    "API key saved — enter a new key to replace"
                else
                    "Enter your ${selectedProvider.displayName} API key",
                singleLine = true,
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                        Icon(
                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isApiKeyVisible) "Hide API key" else "Show API key",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                onSubmit = onSave
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (loadedConfig != null) {
                    EmberrButtonSecondary(
                        text = "Delete",
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                EmberrButtonPrimary(
                    text = "Save",
                    enabled = hasUsableKey && hasUsableEndpoint,
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                )
            }

            EmberrButtonPrimary(
                text = "Close",
                onClick = { closeAnd { } },
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            if (showDeleteConfirm) {
                EmberrAlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = "Delete this API key?"
                ) {
                    Text(
                        text = "The saved key and settings for this provider will be removed from this device.",
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
                            onClick = { showDeleteConfirm = false },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                viewModel.deleteExternalAiConfig(selectedProvider)
                                loadedConfig = null
                                apiKeyInput = ""
                                modelInput = ""
                                baseUrlInput = ""
                                showDeleteConfirm = false
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = EmberrShadowElevation.None)
                        ) {
                            Text("Delete", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = EmberrShadowElevation.None,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickableWithoutMobileRipple(onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
