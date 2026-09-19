package com.emberr.presentation.rag.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.KnowledgeMode
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrBottomSheetOption
import com.emberr.presentation.shared.components.EmberrButtonPrimary

private val SectionLabelHorizontalPadding = 20.dp

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
        contentHorizontalPadding = 0.dp
    ) { closeAnd ->

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "Knowledge Source",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = SectionLabelHorizontalPadding)
            )
            Spacer(Modifier.height(8.dp))
            EmberrBottomSheetOption(
                label = "Default",
                subtitle = "Combines your notes with real-world knowledge.",
                isSelected = selectedMode == KnowledgeMode.DEFAULT,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.DEFAULT) } }
            )
            EmberrBottomSheetOption(
                label = "Only notes knowledge",
                subtitle = "Answers strictly from your notes — no outside knowledge.",
                isSelected = selectedMode == KnowledgeMode.NOTES_ONLY,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.NOTES_ONLY) } }
            )
            EmberrBottomSheetOption(
                label = "Only real-world knowledge",
                subtitle = "Acts like a regular chatbot — doesn't use your notes at all.",
                isSelected = selectedMode == KnowledgeMode.WORLD_ONLY,
                onClick = { closeAnd { viewModel.selectKnowledgeMode(KnowledgeMode.WORLD_ONLY) } }
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Response Length",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = SectionLabelHorizontalPadding)
            )
            Spacer(Modifier.height(8.dp))
            responseLengthOptions.forEach { option ->
                EmberrBottomSheetOption(
                    label = option.label,
                    subtitle = option.subtitle,
                    isSelected = selectedMaxOutputTokens == option.tokens,
                    onClick = { closeAnd { viewModel.selectMaxOutputTokens(option.tokens) } }
                )
            }

            EmberrButtonPrimary(
                text = "Close",
                onClick = { closeAnd { } },
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp)
            )
        }
    }
}
