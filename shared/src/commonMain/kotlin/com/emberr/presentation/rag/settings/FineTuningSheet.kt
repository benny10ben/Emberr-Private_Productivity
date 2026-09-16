package com.emberr.presentation.rag.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.KnowledgeMode
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.rag.components.OptionRowPadding
import com.emberr.presentation.rag.components.OptionRowShape
import com.emberr.presentation.rag.components.OptionRowVerticalSpacing
import com.emberr.presentation.rag.components.SelectedOptionDot
import com.emberr.presentation.rag.components.clickableWithoutMobileRipple
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.SelectedOptionBackground

@Composable
internal fun FineTuningSheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel
) {
    val selectedMode by viewModel.knowledgeMode.collectAsState()
    val selectedMaxOutputTokens by viewModel.maxOutputTokens.collectAsState()

    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Fine-tuning",
    ) { closeAnd ->

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "Knowledge Source",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            KnowledgeModeOption(
                title = "Default",
                subtitle = "Combines your notes with real-world knowledge.",
                selected = selectedMode == KnowledgeMode.DEFAULT,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.DEFAULT) } }
            )
            KnowledgeModeOption(
                title = "Only notes knowledge",
                subtitle = "Answers strictly from your notes — no outside knowledge.",
                selected = selectedMode == KnowledgeMode.NOTES_ONLY,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.NOTES_ONLY) } }
            )
            KnowledgeModeOption(
                title = "Only real-world knowledge",
                subtitle = "Acts like a regular chatbot — doesn't use your notes at all.",
                selected = selectedMode == KnowledgeMode.WORLD_ONLY,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.WORLD_ONLY) } }
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Response Length",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            responseLengthOptions.forEach { option ->
                KnowledgeModeOption(
                    title = option.label,
                    subtitle = option.subtitle,
                    selected = selectedMaxOutputTokens == option.tokens,
                    onClick = { closeAnd { viewModel.selectMaxOutputTokens(option.tokens) } }
                )
            }

            EmberrButtonPrimary(
                text = "Close",
                onClick = { closeAnd { } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun KnowledgeModeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = OptionRowShape,
        color = if (selected) SelectedOptionBackground else Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OptionRowVerticalSpacing)
            .clip(OptionRowShape)
            .clickableWithoutMobileRipple(onClick)
    ) {
        Row(
            modifier = Modifier.padding(OptionRowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            if (selected) {
                SelectedOptionDot()
            }
        }
    }
}
