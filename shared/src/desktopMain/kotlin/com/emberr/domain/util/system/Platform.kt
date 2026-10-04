package com.emberr.domain.util.system

import com.emberr.core.desktop.requestAppDataEraseOnNextLaunch
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.ai.external.SecureAiKeyStorage
import com.emberr.domain.selfhost.crypto.SecureSyncKeyStorage
import com.emberr.presentation.desktop.DesktopRestartBus
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

actual val isDesktopPlatform = true
actual val appVersionName: String?
    get() = System.getProperty("jpackage.app-version")

actual fun showFeedback(message: String) {
    println("Feedback: $message")
}

actual fun triggerHapticFeedback() {}

actual fun restartApplication() {
    DesktopRestartBus.requestRestart()
}

actual suspend fun eraseAllAppData(): Boolean {
    val emberrDirectory = File(System.getProperty("user.home"), ".emberr")
    val wasRequested = withContext(Dispatchers.IO) {
        if (!requestAppDataEraseOnNextLaunch(emberrDirectory)) return@withContext false
        val koin = KoinPlatform.getKoin()
        runCatching { koin.get<SettingsManager>().clearSyncPairing() }
        runCatching { koin.get<SecureSyncKeyStorage>().clearAll() }
        runCatching { koin.get<SecureAiKeyStorage>().clearAll() }
        true
    }
    if (wasRequested) restartApplication()
    return wasRequested
}
