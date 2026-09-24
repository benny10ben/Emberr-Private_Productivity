package com.emberr.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.emberr.domain.update.AndroidUpdateCheckState
import com.emberr.domain.update.AndroidUpdateChecker
import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.download
import emberr.shared.generated.resources.refresh_cw
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
actual fun AppUpdateSettingsSection() {
    val updateChecker = koinInject<AndroidUpdateChecker>()
    if (updateChecker.installedVersion == null) return

    val checkState by updateChecker.state.collectAsState()
    val isAutomaticCheckEnabled by updateChecker.automaticCheckEnabledFlow
        .collectAsState(initial = updateChecker.isAutomaticCheckEnabled())

    SettingsGroup(title = "Updates") {
        SettingsActionRow(
            icon = painterResource(Res.drawable.download),
            title = actionTitleFor(checkState),
            trailingLabel = statusLabelFor(checkState, updateChecker.installedVersion),
            onClick = {
                when (checkState) {
                    is AndroidUpdateCheckState.Available -> updateChecker.openReleasePage()
                    is AndroidUpdateCheckState.Checking -> Unit
                    else -> updateChecker.checkNow()
                }
            }
        )
        SettingsDivider()
        SettingsToggleRow(
            icon = painterResource(Res.drawable.refresh_cw),
            title = "Check for updates on launch",
            isChecked = isAutomaticCheckEnabled,
            onCheckedChange = updateChecker::setAutomaticCheckEnabled
        )
    }
}

private fun actionTitleFor(checkState: AndroidUpdateCheckState): String = when (checkState) {
    is AndroidUpdateCheckState.Checking -> "Checking for updates..."
    is AndroidUpdateCheckState.Available -> "Download Emberr ${checkState.version}"
    else -> "Check for updates"
}

private fun statusLabelFor(checkState: AndroidUpdateCheckState, installedVersion: String?): String? = when (checkState) {
    is AndroidUpdateCheckState.UpToDate -> "Up to date"
    is AndroidUpdateCheckState.Available -> "New"
    is AndroidUpdateCheckState.Failed -> "Failed"
    else -> installedVersion?.let { "v$it" }
}
