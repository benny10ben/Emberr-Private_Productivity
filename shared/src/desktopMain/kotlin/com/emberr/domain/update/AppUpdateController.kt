package com.emberr.domain.update

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.util.network.openLinkInRunningBrowser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppUpdateController(
    private val settingsManager: SettingsManager,
    private val updateDownloader: AppUpdateDownloader,
    private val installation: UpdatableInstallation?,
    val installedVersion: String?
) {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val _prompt = MutableStateFlow<AppUpdatePrompt?>(null)
    val prompt: StateFlow<AppUpdatePrompt?> = _prompt.asStateFlow()

    val canUpdateInApp: Boolean = installation != null && installedVersion != null

    val automaticCheckEnabledFlow: Flow<Boolean> = settingsManager.automaticUpdateCheckEnabledFlow

    fun isAutomaticCheckEnabled(): Boolean = settingsManager.isAutomaticUpdateCheckEnabled()

    fun setAutomaticCheckEnabled(enabled: Boolean) = settingsManager.saveAutomaticUpdateCheckEnabled(enabled)

    fun checkOnLaunch() {
        if (settingsManager.isAutomaticUpdateCheckEnabled()) {
            checkForUpdate(isRequestedByUser = false)
        }
    }

    fun checkNow() = checkForUpdate(isRequestedByUser = true)

    fun startDownload() {
        val release = (_state.value as? AppUpdateState.Available)?.release ?: return
        val currentInstallation = installation ?: return
        val download = currentInstallation.downloadFor(release) ?: return
        _prompt.value = null

        if (!currentInstallation.canInstall()) {
            openLinkInRunningBrowser(LATEST_RELEASE_PAGE_URL)
            _prompt.value = AppUpdatePrompt.ReportFailure(
                "Emberr can't replace itself in the folder it is installed in. " +
                    "The download page is opening in your browser instead."
            )
            return
        }

        _state.value = AppUpdateState.Downloading(release, progressPercent = 0)
        updateScope.launch {
            try {
                updateDownloader.downloadFile(download, currentInstallation.downloadFile) { percent ->
                    _state.value = AppUpdateState.Downloading(release, percent)
                }
                currentInstallation.install(release, download)
                _state.value = AppUpdateState.ReadyToRestart(release.version)
                _prompt.value = AppUpdatePrompt.OfferRestart(release.version)
            } catch (cause: Exception) {
                cause.printStackTrace()
                _state.value = AppUpdateState.Available(release)
                _prompt.value = AppUpdatePrompt.ReportFailure(
                    userFacingMessageFor(cause, "The update couldn't be downloaded. Check your connection and try again.")
                )
            } finally {
                currentInstallation.downloadFile.delete()
            }
        }
    }

    fun dismissPrompt() {
        _prompt.value = null
    }

    private fun checkForUpdate(isRequestedByUser: Boolean) {
        val currentVersion = installedVersion ?: return
        val currentInstallation = installation ?: return

        val currentState = _state.value
        val isBusy = currentState is AppUpdateState.Checking ||
            currentState is AppUpdateState.Downloading ||
            currentState is AppUpdateState.ReadyToRestart
        if (isBusy) return

        _state.value = AppUpdateState.Checking
        updateScope.launch {
            try {
                val newerRelease = updateDownloader.findNewerRelease(currentVersion)
                    ?.takeIf { release -> currentInstallation.downloadFor(release) != null }
                if (newerRelease == null) {
                    _state.value = AppUpdateState.UpToDate
                } else {
                    _state.value = AppUpdateState.Available(newerRelease)
                    if (!isRequestedByUser) {
                        _prompt.value = AppUpdatePrompt.OfferDownload(newerRelease.version)
                    }
                }
            } catch (cause: Exception) {
                cause.printStackTrace()
                _state.value = AppUpdateState.Failed
                if (isRequestedByUser) {
                    _prompt.value = AppUpdatePrompt.ReportFailure(
                        userFacingMessageFor(cause, "Couldn't check for updates. Check your connection and try again.")
                    )
                }
            }
        }
    }

    private fun userFacingMessageFor(cause: Exception, fallbackMessage: String): String =
        (cause as? AppUpdateException)?.message ?: fallbackMessage
}
