package com.emberr.presentation.rag.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.rag.components.DesktopMenuRowHorizontalPadding
import com.emberr.presentation.rag.components.OptionRowPadding
import com.emberr.presentation.rag.components.OptionRowShape
import com.emberr.presentation.rag.components.OptionRowVerticalSpacing
import com.emberr.presentation.rag.components.RagDesktopMenuItem
import com.emberr.presentation.rag.components.clickableWithoutMobileRipple
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrTextField

@Composable
internal fun ChatHistorySheet(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: RagViewModel
) {
    EmberrBottomSheet(
        expanded = expanded,
        onDismiss = onDismiss,
        title = "Chat History",
    ) { closeAnd ->
        ChatHistoryMenuContent(viewModel = viewModel, closeAnd = closeAnd)
    }
}

@Composable
internal fun ChatHistoryMenuContent(
    viewModel: RagViewModel,
    closeAnd: (() -> Unit) -> Unit
) {
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val rowHorizontalPadding = if (isDesktopPlatform) DesktopMenuRowHorizontalPadding else 0.dp

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Spacer(Modifier.height(10.dp))
        EmberrTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search chats",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = rowHorizontalPadding)
        )
        Spacer(Modifier.height(10.dp))

        if (isDesktopPlatform) {
            RagDesktopMenuItem(
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = "New Chat",
                onClick = { closeAnd { viewModel.clearChat() } }
            )
        } else {
            Surface(
                shape = OptionRowShape,
                color = Color.Transparent,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = OptionRowVerticalSpacing)
                    .clip(OptionRowShape)
                    .clickableWithoutMobileRipple { closeAnd { viewModel.clearChat() } }
            ) {
                Row(
                    modifier = Modifier.padding(OptionRowPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "New Chat",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(
                vertical = 10.dp,
                horizontal = if (isDesktopPlatform) 16.dp else 0.dp
            ),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(10.dp))

        val filteredSessions = remember(sessions, searchQuery) {
            if (searchQuery.isBlank()) sessions
            else sessions.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        Text(
            text = "Recent Chats",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))

        if (filteredSessions.isEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) "No chats yet." else "No chats match your search.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 12.dp, horizontal = rowHorizontalPadding)
            )
        } else {
            Column {
                filteredSessions.forEach { session ->
                    ChatSessionRow(
                        session = session,
                        isActive = session.id == currentSessionId,
                        onClick = { closeAnd { viewModel.loadSession(session.id) } },
                        onRename = { newTitle -> viewModel.renameSession(session.id, newTitle) },
                        onDelete = { viewModel.deleteSession(session.id) }
                    )
                }
            }
        }

        if (!isDesktopPlatform) {
            Spacer(Modifier.height(12.dp))
            EmberrButtonPrimary(
                text = "Close",
                onClick = { closeAnd { } },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
