package com.emberr.presentation.settings.selfhost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emberr.domain.selfhost.sync.SELF_HOST_UPDATE_REQUIRED_MESSAGE
import com.emberr.domain.selfhost.sync.SelfHostUpdateRequiredSignal
import com.emberr.presentation.shared.components.EmberrAlertDialog
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary

@Composable
fun SelfHostUpdateRequiredDialog(onUpdateClick: () -> Unit) {
    val shouldShowDialog by SelfHostUpdateRequiredSignal.shouldShowDialog.collectAsState()
    if (!shouldShowDialog) return

    EmberrAlertDialog(
        onDismissRequest = SelfHostUpdateRequiredSignal::dismissDialog,
        title = "Self-host sync paused"
    ) {
        Text(
            text = "$SELF_HOST_UPDATE_REQUIRED_MESSAGE Your notes are safe and keep saving on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmberrButtonSecondary(
                text = "Later",
                onClick = SelfHostUpdateRequiredSignal::dismissDialog,
                modifier = Modifier.weight(1f)
            )
            EmberrButtonPrimary(
                text = "Update",
                onClick = {
                    SelfHostUpdateRequiredSignal.dismissDialog()
                    onUpdateClick()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
