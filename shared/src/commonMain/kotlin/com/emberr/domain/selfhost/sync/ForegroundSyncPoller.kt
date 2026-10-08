package com.emberr.domain.selfhost.sync

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.selfhost.webdav.WebDavSyncClient
import com.emberr.domain.selfhost.webdav.WebDavSyncPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class ForegroundSyncPoller(
    private val webDavSyncClient: WebDavSyncClient,
    private val selfHostSyncEngine: SelfHostSyncEngine,
    private val settingsManager: SettingsManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val manifestJson = Json { ignoreUnknownKeys = true }
    private var pollingJob: Job? = null
    private var pollCount = 0

    fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL)
                try {
                    pollOnce()
                } catch (cause: Exception) {
                    SelfHostSyncLog.e("ForegroundSyncPoller: poll failed, will retry next interval", cause)
                }
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun pollOnce() {
        if (!selfHostSyncEngine.isOnAllowedNetwork()) return
        pollCount++
        val supportsETags = settingsManager.getSelfHostSupportsETags()
            ?: webDavSyncClient.checkETagSupport().also { settingsManager.saveSelfHostSupportsETags(it) }

        // ETags are a fast-path optimization, not a guarantee - some servers/proxies behind them return
        // a weak ETag (e.g. derived from mtime/size) that doesn't reliably change on every mutation,
        // which would make checkViaETag falsely conclude "unchanged" and silently mask real remote
        // changes for as long as the app stays foregrounded. Periodically force the slower but
        // authoritative timestamp check regardless, so a flaky ETag can't permanently hide a pull.
        val useEtag = supportsETags && pollCount % FORCE_TIMESTAMP_CHECK_EVERY != 0
        val remoteChanged = if (useEtag) checkViaETag() else checkViaTimestamp()
        val hasUnsentLocalChanges = selfHostSyncEngine.hasPendingLocalChanges()
        if (remoteChanged || hasUnsentLocalChanges) {
            SelfHostSyncLog.d(
                "ForegroundSyncPoller: remoteChanged=$remoteChanged, hasUnsentLocalChanges=$hasUnsentLocalChanges, " +
                        "triggering text sync then media sync"
            )
            selfHostSyncEngine.runSync()
            selfHostSyncEngine.syncMedia()
        }
    }

    private suspend fun checkViaETag(): Boolean {
        val currentEtag = webDavSyncClient.getResourceInfo(WebDavSyncPaths.MANIFEST_FILE)?.etag
            ?: return checkViaTimestamp()
        val previousEtag = settingsManager.getSelfHostManifestEtag()
        settingsManager.saveSelfHostManifestEtag(currentEtag)
        return previousEtag == null || previousEtag != currentEtag
    }

    private suspend fun checkViaTimestamp(): Boolean {
        val raw = webDavSyncClient.downloadAndDecryptJson(WebDavSyncPaths.MANIFEST_FILE) ?: return false
        val manifest = try {
            manifestJson.decodeFromString(SelfHostManifest.serializer(), raw)
        } catch (cause: Exception) {
            SelfHostSyncLog.e("ForegroundSyncPoller: could not parse manifest during poll", cause)
            return false
        }
        val lastSyncTimestamp = settingsManager.getSelfHostLastSyncTimestamp()
        return manifest.entries.any { it.updatedAt > lastSyncTimestamp }
    }

    private companion object {
        val POLL_INTERVAL = 120.seconds
        const val FORCE_TIMESTAMP_CHECK_EVERY = 5
    }
}
