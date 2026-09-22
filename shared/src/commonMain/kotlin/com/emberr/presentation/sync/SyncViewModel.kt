package com.emberr.presentation.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.core.security.SyncEncryptionManager
import com.emberr.core.security.SyncHmacSigner
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.sync.LanSyncLog
import com.emberr.domain.sync.LanSyncSchemaMismatchException
import com.emberr.domain.sync.LanSyncServerController
import com.emberr.domain.sync.SyncClient
import com.emberr.domain.sync.SyncPairingData
import com.emberr.domain.sync.SyncPairingState
import com.emberr.domain.sync.SyncRepository
import com.emberr.domain.sync.SyncServerStatus
import com.emberr.domain.util.system.isDesktopPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

private const val MEDIA_RECONCILE_INTERVAL_MS = 60_000L

class SyncViewModel(
    private val syncRepository: SyncRepository,
    private val settingsManager: SettingsManager,
    private val hmacSigner: SyncHmacSigner,
    private val syncEncryptionManager: SyncEncryptionManager,
    private val pairingState: SyncPairingState,
    private val lanSyncServerController: LanSyncServerController,
    val serverStatus: StateFlow<SyncServerStatus>? = null
) : ViewModel() {

    private val _syncStatus = MutableStateFlow("Idle")
    val syncStatus = _syncStatus.asStateFlow()

    // Prevents concurrent sync cycles (e.g. manual trigger vs background watchdog) from executing simultaneously.
    private val syncMutex = Mutex()

    // Single client instance reused across all sync cycles to avoid leaking HTTP resources.
    private val syncClient = SyncClient(settingsManager, hmacSigner, syncEncryptionManager)

    // Throttles full media reconciliation passes to run at most once per interval.
    private var lastMediaReconcileAt = 0L

    private suspend fun reconcileMediaIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastMediaReconcileAt < MEDIA_RECONCILE_INTERVAL_MS) return
        lastMediaReconcileAt = now
        try {
            syncRepository.reconcileMedia()
        } catch (e: Exception) {
            LanSyncLog.e("reconcileMediaIfDue: failed: ${e.message}", e)
        }
    }

    override fun onCleared() {
        syncClient.close()
        super.onCleared()
    }

    val isPaired = pairingState.isPaired

    fun startServerForPairing() {
        viewModelScope.launch(Dispatchers.IO) {
            lanSyncServerController.startForPairing()
        }
    }

    fun stopServerUnlessPaired() {
        lanSyncServerController.stopIfNotPaired()
    }

    fun resetSyncStatus() {
        _syncStatus.value = "Idle"
    }

    fun getSyncPort(): Int = settingsManager.getSyncPort()

    fun setSyncPort(port: Int) = settingsManager.saveSyncPort(port)

    fun generatePairingData(): SyncPairingData {
        val token = generateSecureToken()
        val encryptionKey = generateSecureToken() + generateSecureToken()
        settingsManager.saveSyncAuthToken(token)
        settingsManager.saveSyncEncryptionKey(encryptionKey)
        return SyncPairingData(
            ipAddress = getLocalNetworkIp(),
            port = settingsManager.getSyncPort(),
            authToken = token,
            encryptionKey = encryptionKey
        )
    }

    fun applyScannedPairing(pairingData: SyncPairingData) {
        settingsManager.saveSyncIpAddress(pairingData.ipAddress)
        settingsManager.saveSyncPort(pairingData.port)
        settingsManager.saveSyncAuthToken(pairingData.authToken)
        settingsManager.saveSyncEncryptionKey(pairingData.encryptionKey)
        pairingState.markPaired()
    }

    fun unpair() {
        viewModelScope.launch {
            val isCurrentlyPaired = settingsManager.getSyncIpAddress().isNotBlank() &&
                    settingsManager.getSyncAuthToken().isNotBlank()

            if (!isDesktopPlatform && isCurrentlyPaired) {
                withTimeoutOrNull(3_000L.milliseconds) {
                    syncClient.requestUnpair()
                }
            }

            pairingState.unpairLocally()
            lanSyncServerController.stopNow()
            _syncStatus.value = "Idle"
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            val ip = settingsManager.getSyncIpAddress()
            val token = settingsManager.getSyncAuthToken()

            if (ip.isBlank() || token.isBlank()) {
                _syncStatus.value = "Not Paired!"
                return@launch
            }

            if (!syncMutex.tryLock()) {
                _syncStatus.value = "Sync already in progress"
                return@launch
            }

            _syncStatus.value = "Syncing..."

            // Collecting and pushing changes run without holding SyncCoordinator.mutex.
            // Database writes acquire the mutex per envelope inside applyRemoteChanges.
            val syncStart = System.currentTimeMillis()
            val lastSyncTimestamp = settingsManager.getLastSyncTimestamp()

            try {
                val localChanges = syncRepository.collectLocalChanges(lastSyncTimestamp, uploadMedia = true)
                if (localChanges.isNotEmpty()) {
                    syncClient.pushChanges(localChanges)
                }

                _syncStatus.value = "Fetching from Desktop..."
                val remoteChanges = syncClient.fetchChanges(lastSyncTimestamp)
                val appliedCleanly = if (remoteChanges.isNotEmpty()) {
                    syncRepository.applyRemoteChanges(remoteChanges)
                } else true

                if (appliedCleanly) {
                    // Only advance the sync timestamp if all fetched changes applied cleanly.
                    settingsManager.saveLastSyncTimestamp(syncStart)
                    _syncStatus.value = "Success!"

                    // Clean up orphaned media and reconcile files after a successful sync.
                    syncRepository.cleanupOrphanedMedia()
                    syncRepository.reconcileMedia()
                } else {
                    _syncStatus.value = "Partial sync, will retry"
                }

            } catch (e: LanSyncSchemaMismatchException) {
                LanSyncLog.e("manual sync blocked by schema mismatch: ${e.message}", e)
                _syncStatus.value = "Update Emberr on both devices"
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "Failed: ${e.message}"
            } finally {
                syncMutex.unlock()
            }
        }
    }

    fun triggerAutoSync(discoveryManager: com.emberr.domain.sync.discovery.SyncDiscoveryManager) {
        viewModelScope.launch {
            val currentAuth = settingsManager.getSyncAuthToken()
            if (currentAuth.isBlank()) return@launch

            discoveryManager.startScanning()

            for (i in 1..15) {
                kotlinx.coroutines.delay(200.milliseconds)
                val devices = discoveryManager.discoveredDevices.value
                if (devices.isNotEmpty()) {
                    settingsManager.saveSyncIpAddress(devices.first().ipAddress)
                    break
                }
            }

            discoveryManager.stopScanning()

            performSilentSync()
        }
    }

    // Returns null if skipped due to lock contention, preserving the current watchdog backoff state.
    private suspend fun performSilentSync(): Boolean? = withContext(Dispatchers.IO) {
        // Obtains syncMutex to isolate background sync runs without locking local editor saves.
        if (!syncMutex.tryLock()) {
            return@withContext null
        }
        try {
            val syncStart = System.currentTimeMillis()
            val lastSyncTimestamp = settingsManager.getLastSyncTimestamp()
            _syncStatus.value = "Auto-Syncing..."

            val localChanges = syncRepository.collectLocalChanges(lastSyncTimestamp, uploadMedia = true)
            if (localChanges.isNotEmpty()) {
                syncClient.pushChanges(localChanges)
            }

            val remoteChanges = syncClient.fetchChanges(lastSyncTimestamp)
            val appliedCleanly = if (remoteChanges.isNotEmpty()) {
                syncRepository.applyRemoteChanges(remoteChanges)
            } else true

            if (appliedCleanly) {
                settingsManager.saveLastSyncTimestamp(syncStart)
                _syncStatus.value = "Synced Successfully"
                true
            } else {
                _syncStatus.value = "Partial sync, will retry"
                false
            }
        } catch (e: LanSyncSchemaMismatchException) {
            LanSyncLog.e("auto sync blocked by schema mismatch: ${e.message}", e)
            _syncStatus.value = "Update Emberr on both devices"
            false
        } catch (_: java.net.ConnectException) {
            _syncStatus.value = "Desktop Offline"
            false
        } catch (e: Exception) {
            e.printStackTrace()
            _syncStatus.value = "Sync Error: ${e.javaClass.simpleName}"
            false
        } finally {
            syncMutex.unlock()
        }
    }

    fun triggerFastSync() {
        viewModelScope.launch {
            val currentAuth = settingsManager.getSyncAuthToken()
            if (currentAuth.isBlank()) return@launch

            performSilentSync()
        }
    }

    private var watchdogJob: kotlinx.coroutines.Job? = null

    fun startForegroundWatchdog() {
        if (watchdogJob?.isActive == true) return

        watchdogJob = viewModelScope.launch {
            var currentDelay = 1500L
            val maxDelay = 30000L // Cap at 30 seconds

            while (true) {
                kotlinx.coroutines.delay(currentDelay.milliseconds)

                if (settingsManager.getSyncIpAddress().isNotBlank()) {
                    val success = performSilentSync()

                    currentDelay = when (success) {
                        // Reset to aggressive polling if the server is alive
                        true -> 1500L
                        // Back off exponentially if the server is dead
                        false -> (currentDelay * 2).coerceAtMost(maxDelay)
                        // Preserve the delay if the sync was skipped due to lock contention
                        null -> currentDelay
                    }

                    reconcileMediaIfDue()
                }
            }
        }
    }

    fun stopForegroundWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }
}