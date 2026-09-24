package com.emberr.presentation.desktop.update

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
import com.emberr.domain.update.AppUpdateController
import com.emberr.domain.update.AppUpdatePrompt
import com.emberr.domain.util.system.restartApplication
import com.emberr.presentation.shared.components.EmberrAlertDialog
import com.emberr.presentation.shared.components.EmberrButtonPrimary
import com.emberr.presentation.shared.components.EmberrButtonSecondary
import org.koin.compose.koinInject

@Composable
fun AppUpdatePromptDialog() {
    val updateController = koinInject<AppUpdateController>()
    val prompt by updateController.prompt.collectAsState()

    when (val currentPrompt = prompt) {
        null -> Unit

        is AppUpdatePrompt.OfferDownload -> UpdateChoiceDialog(
            title = "Update available",
            message = "Emberr ${currentPrompt.version} is available. You are using " +
                "${updateController.installedVersion}. It downloads in the background while you keep working.",
            confirmText = "Download",
            onConfirm = updateController::startDownload,
            onLater = updateController::dismissPrompt
        )

        is AppUpdatePrompt.OfferRestart -> UpdateChoiceDialog(
            title = "Update ready to install",
            message = "Restart Emberr to start using version ${currentPrompt.version}. " +
                "If you choose Later, the update is used the next time you open Emberr.",
            confirmText = "Restart",
            onConfirm = {
                updateController.dismissPrompt()
                restartApplication()
            },
            onLater = updateController::dismissPrompt
        )

        is AppUpdatePrompt.ReportFailure -> EmberrAlertDialog(
            onDismissRequest = updateController::dismissPrompt,
            title = "Couldn't update Emberr"
        ) {
            UpdateDialogMessage(currentPrompt.message)
            Spacer(Modifier.height(20.dp))
            EmberrButtonPrimary(
                text = "OK",
                onClick = updateController::dismissPrompt,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UpdateChoiceDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onLater: () -> Unit
) {
    EmberrAlertDialog(onDismissRequest = onLater, title = title) {
        UpdateDialogMessage(message)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmberrButtonSecondary(
                text = "Later",
                onClick = onLater,
                modifier = Modifier.weight(1f)
            )
            EmberrButtonPrimary(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UpdateDialogMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    )
}
