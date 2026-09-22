package com.emberr.domain.sync

import com.emberr.core.security.SyncEncryptionManager
import com.emberr.core.security.SyncHmacSigner
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import com.emberr.domain.sync.discovery.SyncDiscoveryManager

class DesktopLanSyncServerController(
    private val settingsManager: SettingsManager,
    private val syncRepository: SyncRepository,
    private val hmacSigner: SyncHmacSigner,
    private val syncEncryptionManager: SyncEncryptionManager,
    private val pairingState: SyncPairingState,
    private val serverAvailability: SyncServerAvailability,
    private val discoveryManager: SyncDiscoveryManager
) : LanSyncServerController {

    private val startStopLock = Any()
    private var runningServer: RunningSyncServer? = null

    override fun startIfPaired() {
        if (!pairingState.isPaired.value) {
            LanSyncLog.d("LanSyncServer: not paired, leaving the sync port closed")
            return
        }
        startServer("this desktop is paired")
    }

    override fun startForPairing() {
        startServer("the pairing dialog is open")
    }

    override fun stopIfNotPaired() {
        if (pairingState.isPaired.value) return
        stopServer("pairing was not completed")
    }

    override fun stopNow() {
        stopServer("this desktop was unpaired")
    }

    private fun startServer(reason: String) {
        synchronized(startStopLock) {
            if (runningServer != null) return

            val startedServer = startSyncServer(
                settingsManager,
                syncRepository,
                hmacSigner,
                syncEncryptionManager,
                pairingState,
                serverAvailability
            )

            if (startedServer == null) {
                LanSyncLog.d("LanSyncServer: could not start even though $reason")
                return
            }

            runningServer = startedServer
            discoveryManager.startBroadcasting(currentSyncPort(), DESKTOP_DEVICE_NAME)
            LanSyncLog.d("LanSyncServer: listening on port ${currentSyncPort()} because $reason")
        }
    }

    private fun stopServer(reason: String) {
        synchronized(startStopLock) {
            val serverToStop = runningServer ?: return

            discoveryManager.stopBroadcasting()
            serverToStop.stop()
            runningServer = null
            serverAvailability.markIdle()
            LanSyncLog.d("LanSyncServer: stopped and stopped broadcasting because $reason")
        }
    }

    private fun currentSyncPort(): Int =
        settingsManager.getSyncPort().let { if (it <= 0) SyncConstants.DEFAULT_PORT else it }

    private companion object {
        const val DESKTOP_DEVICE_NAME = "Emberr Desktop"
    }
}
