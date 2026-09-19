package com.emberr.presentation.rag.history

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberr.domain.util.system.isDesktopPlatform
import com.emberr.presentation.rag.RagViewModel
import com.emberr.presentation.rag.components.DesktopMenuRowHorizontalPadding
import com.emberr.presentation.rag.components.RagDesktopMenuItem
import com.emberr.presentation.shared.components.EmberrBottomSheet
import com.emberr.presentation.shared.components.EmberrBottomSheetOption
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrTextField

private val SheetEdgePadding = 20.dp

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
        contentHorizontalPadding = 0.dp
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
    val rowHorizontalPadding = if (isDesktopPlatform) DesktopMenuRowHorizontalPadding else SheetEdgePadding
    val sectionLabelHorizontalPadding = if (isDesktopPlatform) 0.dp else SheetEdgePadding

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
            EmberrBottomSheetOption(
                label = "New Chat",
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = { closeAnd { viewModel.clearChat() } }
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(
                vertical = 10.dp,
                horizontal = if (isDesktopPlatform) 16.dp else SheetEdgePadding
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = sectionLabelHorizontalPadding)
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = SheetEdgePadding)
            )
        }
    }
}
