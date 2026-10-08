package com.emberr.presentation.desktop.update

import androidx.compose.runtime.Composable
import com.emberr.domain.update.AppUpdateController
import com.emberr.domain.update.AppUpdateState
import com.emberr.domain.update.LATEST_RELEASE_PAGE_URL
import com.emberr.domain.util.network.openLinkInRunningBrowser
import com.emberr.domain.util.system.restartApplication
import com.emberr.presentation.settings.selfhost.SelfHostUpdateRequiredDialog
import org.koin.compose.koinInject

@Composable
fun SelfHostUpdateRequiredPrompt() {
    val updateController = koinInject<AppUpdateController>()

    SelfHostUpdateRequiredDialog(
        onUpdateClick = {
            when {
                !updateController.canUpdateInApp -> openLinkInRunningBrowser(LATEST_RELEASE_PAGE_URL)
                updateController.state.value is AppUpdateState.ReadyToRestart -> restartApplication()
                else -> updateController.checkNowAndOfferDownload()
            }
        }
    )
}
