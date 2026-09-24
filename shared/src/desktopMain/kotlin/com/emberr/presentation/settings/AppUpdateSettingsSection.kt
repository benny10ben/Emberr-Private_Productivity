package com.emberr.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.emberr.domain.update.AppUpdateController
import com.emberr.domain.update.AppUpdateState
import com.emberr.domain.util.system.restartApplication
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.download
import emberr.shared.generated.resources.refresh_cw
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
actual fun AppUpdateSettingsSection() {
    val updateController = koinInject<AppUpdateController>()
    if (!updateController.canUpdateInApp) return

    val updateState by updateController.state.collectAsState()
    val isAutomaticCheckEnabled by updateController.automaticCheckEnabledFlow
        .collectAsState(initial = updateController.isAutomaticCheckEnabled())

    SettingsGroup(title = "Updates") {
        SettingsActionRow(
            icon = painterResource(Res.drawable.download),
            title = actionTitleFor(updateState),
            trailingLabel = statusLabelFor(updateState, updateController.installedVersion),
            onClick = {
                when (updateState) {
                    is AppUpdateState.Available -> updateController.startDownload()
                    is AppUpdateState.ReadyToRestart -> restartApplication()
                    is AppUpdateState.Checking, is AppUpdateState.Downloading -> Unit
                    else -> updateController.checkNow()
                }
            }
        )
        SettingsDivider()
        SettingsToggleRow(
            icon = painterResource(Res.drawable.refresh_cw),
            title = "Check for updates on launch",
            isChecked = isAutomaticCheckEnabled,
            onCheckedChange = updateController::setAutomaticCheckEnabled
        )
    }
}

private fun actionTitleFor(updateState: AppUpdateState): String = when (updateState) {
    is AppUpdateState.Checking -> "Checking for updates..."
    is AppUpdateState.Available -> "Download Emberr ${updateState.release.version}"
    is AppUpdateState.Downloading -> "Downloading update..."
    is AppUpdateState.ReadyToRestart -> "Restart to update"
    else -> "Check for updates"
}

private fun statusLabelFor(updateState: AppUpdateState, installedVersion: String?): String? = when (updateState) {
    is AppUpdateState.UpToDate -> "Up to date"
    is AppUpdateState.Available -> "New"
    is AppUpdateState.Downloading -> "${updateState.progressPercent}%"
    is AppUpdateState.ReadyToRestart -> "v${updateState.version}"
    is AppUpdateState.Failed -> "Failed"
    else -> installedVersion?.let { "v$it" }
}
