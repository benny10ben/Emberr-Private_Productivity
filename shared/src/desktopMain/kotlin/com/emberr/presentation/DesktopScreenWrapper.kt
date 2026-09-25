package com.emberr.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.emberr.domain.model.NoteBlock
import com.emberr.presentation.desktop.DesktopMainScreen
import com.emberr.presentation.ai.RagViewModel

@Composable
actual fun DesktopMainScreenWrapper(
    isSidebarVisible: Boolean,
    sidebarWidth: Dp,
    onToggleSidebar: () -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onPickImage: ((String) -> Unit) -> Unit,
    onTakePhoto: ((String) -> Unit) -> Unit,
    onPickDocument: ((String) -> Unit) -> Unit,
    onOpenFile: (String, String) -> Unit,
    onExportMarkdown: (String, String) -> Unit,
    onExportPdf: (String, String, List<NoteBlock>) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackupClick: () -> Unit,
    onAiIconTap: () -> Unit,
    isAiChatVisible: Boolean,
    ragViewModel: RagViewModel?,
    onDismissAiChat: () -> Unit
) {
    DesktopMainScreen(
        isSidebarVisible = isSidebarVisible,
        sidebarWidth = sidebarWidth,
        onToggleSidebar = onToggleSidebar,
        onSelectionModeChange = onSelectionModeChange,
        onPickImage = onPickImage,
        onTakePhoto = onTakePhoto,
        onPickDocument = onPickDocument,
        onOpenFile = onOpenFile,
        onExportMarkdown = onExportMarkdown,
        onExportPdf = onExportPdf,
        onExportBackup = onExportBackup,
        onImportBackupClick = onImportBackupClick,
        onAiIconTap = onAiIconTap,
        isAiChatVisible = isAiChatVisible,
        ragViewModel = ragViewModel,
        onDismissAiChat = onDismissAiChat
    )
}