package com.emberr.presentation.ai.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.AiGenerationMode
import com.emberr.presentation.ai.RagViewModel
import com.emberr.presentation.ai.components.ModelOptionCard
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary

@Composable
internal fun AiSettingsSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel,
    onLocalAiClick: () -> Unit,
    onExternalAiClick: () -> Unit,
    onFineTuningClick: () -> Unit
) {
    val aiGenerationMode by viewModel.aiGenerationMode.collectAsState()
    val selectedExternalAiProvider by viewModel.selectedExternalAiProvider.collectAsState()
    val knowledgeMode by viewModel.knowledgeMode.collectAsState()

    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "AI Settings",
    ) { _ ->

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            ModelOptionCard(
                title = "Local AI",
                subtitle = if (viewModel.localAiUnsupportedReason == null)
                    "Runs fully on-device. Private & offline."
                else
                    "Not supported by this hardware.",
                onClick = onLocalAiClick
            )
            Spacer(Modifier.height(10.dp))
            ModelOptionCard(
                title = "External AI",
                subtitle = if (aiGenerationMode == AiGenerationMode.EXTERNAL)
                    "Active — ${selectedExternalAiProvider.displayName}"
                else
                    "Connect an external provider with your own API key.",
                onClick = onExternalAiClick
            )
            Spacer(Modifier.height(10.dp))
            ModelOptionCard(
                title = "Fine-tuning",
                subtitle = knowledgeMode.displayName,
                onClick = onFineTuningClick
            )

            EmberrButtonPrimary(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}
