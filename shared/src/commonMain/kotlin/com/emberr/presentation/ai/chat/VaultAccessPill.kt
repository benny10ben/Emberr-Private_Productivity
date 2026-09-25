// Pill showing whether the active AI can only read the vault or also write to it.

package com.emberr.presentation.ai.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.AiGenerationMode
import com.emberr.presentation.shared.components.EmberrShadowElevation

@Composable
internal fun VaultAccessPill(aiGenerationMode: AiGenerationMode, externalAiReadOnly: Boolean) {
    val canWriteToVault = aiGenerationMode == AiGenerationMode.EXTERNAL && !externalAiReadOnly
    val label = if (canWriteToVault) "Read + Write" else "Read"

    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = EmberrShadowElevation.None
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
//            Icon(
//                imageVector = if (canWriteToVault) Icons.Default.Edit else Icons.Default.Visibility,
//                contentDescription = if (canWriteToVault) {
//                    "This AI can read and write your vault"
//                } else {
//                    "This AI can only read your vault"
//                },
//                tint = MaterialTheme.colorScheme.onSurface,
//                modifier = Modifier.size(14.dp)
//            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
