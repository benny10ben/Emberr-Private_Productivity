package com.emberr.presentation.ai.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.emberr.domain.ai.chat.ChatMessage
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.shared.components.MarkdownText
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.copy
import emberr.shared.generated.resources.pen
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun ChatBubble(
    message: ChatMessage,
    onEditClick: (() -> Unit)? = null,
    onConfirmPendingWrite: (() -> Unit)? = null,
    onRejectPendingWrite: (() -> Unit)? = null
) {
    val toolCallSummary = message.toolCallSummary
    if (toolCallSummary != null) {
        ToolCallIndicator(text = toolCallSummary)
        return
    }

    val pendingWrite = message.pendingVaultWrite
    if (pendingWrite != null) {
        PendingVaultWriteCard(
            write = pendingWrite,
            onConfirm = { onConfirmPendingWrite?.invoke() },
            onReject = { onRejectPendingWrite?.invoke() }
        )
        return
    }

    if (message.text.isEmpty() && !message.isUser) return

    val isUser = message.isUser
    val bgColor = if (isUser) MaterialTheme.colorScheme.surface else Color.Transparent
    val textColor = MaterialTheme.colorScheme.onSurface
    val shape = if (isUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    val boxAlign = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val columnAlign = if (isUser) Alignment.End else Alignment.Start
    val widthMod = if (isDesktopPlatform) Modifier.fillMaxWidth(0.85f) else Modifier.widthIn(max = 300.dp)
    val clipboardManager = LocalClipboardManager.current

    Box(Modifier.fillMaxWidth(), contentAlignment = boxAlign) {
        Column(horizontalAlignment = columnAlign) {
            Box(
                modifier = Modifier
                    .then(widthMod)
                    .clip(shape)
                    .background(bgColor)
                    .animateContentSize(animationSpec = tween(120))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                MarkdownText(
                    text = message.text,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MessageActionIcon(
                    icon = painterResource(Res.drawable.copy),
                    contentDescription = "Copy",
                    onClick = { clipboardManager.setText(AnnotatedString(message.text)) }
                )
                if (onEditClick != null) {
                    MessageActionIcon(
                        icon = painterResource(Res.drawable.pen),
                        contentDescription = "Edit prompt",
                        onClick = onEditClick
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionIcon(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(15.dp)
        )
    }
}
