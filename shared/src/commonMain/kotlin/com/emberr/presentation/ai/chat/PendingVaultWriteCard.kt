// Chat card that previews an AI-proposed vault write and lets the user confirm or reject it.

package com.emberr.presentation.ai.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.tools.VaultPendingWrite
import com.emberr.domain.ai.tools.VaultPendingWriteKind
import com.emberr.domain.ai.tools.VaultPendingWriteStatus
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary

@Composable
internal fun PendingVaultWriteCard(
    write: VaultPendingWrite,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val widthMod = if (isDesktopPlatform) Modifier.fillMaxWidth(0.85f) else Modifier.widthIn(max = 300.dp)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(
            modifier = widthMod
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                text = "${write.kind.actionLabel()} • ${write.relativePath}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            if (write.kind != VaultPendingWriteKind.CREATE && write.previousContent != null) {
                ContentPreviewBlock(label = "Before", content = write.previousContent)
                Spacer(Modifier.height(6.dp))
            }
            if (write.kind != VaultPendingWriteKind.DELETE && write.proposedContent != null) {
                val label = if (write.kind == VaultPendingWriteKind.APPEND) "Appending" else "After"
                ContentPreviewBlock(label = label, content = write.proposedContent)
            }

            Spacer(Modifier.height(10.dp))

            when (write.status) {
                VaultPendingWriteStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmberrButtonSecondary(text = "Reject", onClick = onReject, modifier = Modifier.weight(1f))
                    EmberrButtonPrimary(text = "Confirm", onClick = onConfirm, modifier = Modifier.weight(1f))
                }

                VaultPendingWriteStatus.APPLIED -> StatusLabel("Applied")
                VaultPendingWriteStatus.REJECTED -> StatusLabel("Rejected")
                VaultPendingWriteStatus.FAILED -> StatusLabel("Failed: ${write.failureReason ?: "unknown error"}")
            }
        }
    }
}

@Composable
private fun ContentPreviewBlock(label: String, content: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
}

private fun VaultPendingWriteKind.actionLabel(): String = when (this) {
    VaultPendingWriteKind.CREATE -> "Create note"
    VaultPendingWriteKind.UPDATE -> "Update note"
    VaultPendingWriteKind.APPEND -> "Append to note"
    VaultPendingWriteKind.DELETE -> "Delete note"
}
