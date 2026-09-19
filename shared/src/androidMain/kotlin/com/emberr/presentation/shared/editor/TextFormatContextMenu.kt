package com.emberr.presentation.shared.editor

import androidx.compose.runtime.Composable

@Composable
actual fun TextFormatContextMenu(
    onToggleFormat: (String) -> Unit,
    content: @Composable () -> Unit
) {
    content()
}
