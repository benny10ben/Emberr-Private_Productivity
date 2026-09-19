package com.emberr.presentation.rag.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.AiGenerationMode
import com.emberr.domain.ai.KnowledgeMode
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.rag.components.RagDesktopMenuItem
import com.emberr.presentation.shared.components.EmberrDesktopMenuOption
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.chevron_left
import org.jetbrains.compose.resources.painterResource

private enum class AiSettingsMenuLevel { MAIN, FINE_TUNING }

@Composable
internal fun AiSettingsMenuContent(
    viewModel: RagViewModel,
    onLocalAiClick: () -> Unit,
    onExternalAiClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val aiGenerationMode by viewModel.aiGenerationMode.collectAsState()
    val selectedExternalAiProvider by viewModel.selectedExternalAiProvider.collectAsState()
    val knowledgeMode by viewModel.knowledgeMode.collectAsState()
    val selectedMaxOutputTokens by viewModel.maxOutputTokens.collectAsState()
    var currentMenu by remember { mutableStateOf(AiSettingsMenuLevel.MAIN) }

    Column(modifier = Modifier.width(260.dp).padding(vertical = 4.dp)) {
        when (currentMenu) {
            AiSettingsMenuLevel.MAIN -> {
                RagDesktopMenuItem(
                    text = if (viewModel.localAiUnsupportedReason == null)
                        "Local AI"
                    else
                        "Local AI — unavailable",
                    onClick = onLocalAiClick
                )
                RagDesktopMenuItem(
                    text = if (aiGenerationMode == AiGenerationMode.EXTERNAL)
                        "External AI — ${selectedExternalAiProvider.displayName}"
                    else
                        "External AI",
                    onClick = onExternalAiClick
                )
                RagDesktopMenuItem(
                    text = "Fine-tuning",
                    onClick = { currentMenu = AiSettingsMenuLevel.FINE_TUNING }
                )
            }

            AiSettingsMenuLevel.FINE_TUNING -> {
                RagDesktopMenuItem(
                    icon = { Icon(painterResource(Res.drawable.chevron_left), contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = "Back to Options",
                    onClick = { currentMenu = AiSettingsMenuLevel.MAIN }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
                Text(
                    text = "Knowledge Source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                EmberrDesktopMenuOption(
                    label = "Default",
                    isSelected = knowledgeMode == KnowledgeMode.DEFAULT,
                    onClick = { onDismiss(); viewModel.selectKnowledgeMode(KnowledgeMode.DEFAULT) }
                )
                EmberrDesktopMenuOption(
                    label = "Only notes knowledge",
                    isSelected = knowledgeMode == KnowledgeMode.NOTES_ONLY,
                    onClick = { onDismiss(); viewModel.selectKnowledgeMode(KnowledgeMode.NOTES_ONLY) }
                )
                EmberrDesktopMenuOption(
                    label = "Only real-world knowledge",
                    isSelected = knowledgeMode == KnowledgeMode.WORLD_ONLY,
                    onClick = { onDismiss(); viewModel.selectKnowledgeMode(KnowledgeMode.WORLD_ONLY) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
                Text(
                    text = "Response Length",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                responseLengthOptions.forEach { option ->
                    EmberrDesktopMenuOption(
                        label = option.label,
                        isSelected = selectedMaxOutputTokens == option.tokens,
                        onClick = { onDismiss(); viewModel.selectMaxOutputTokens(option.tokens) }
                    )
                }
            }
        }
    }
}
