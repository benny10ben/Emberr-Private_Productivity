package com.emberr.domain.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.emberr.data.local.prefs.SettingsManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

sealed interface AndroidUpdateCheckState {
    data object Idle : AndroidUpdateCheckState
    data object Checking : AndroidUpdateCheckState
    data object UpToDate : AndroidUpdateCheckState
    data class Available(val version: String) : AndroidUpdateCheckState
    data object Failed : AndroidUpdateCheckState
}

class AndroidUpdateChecker(
    private val context: Context,
    private val settingsManager: SettingsManager,
    val installedVersion: String?
) {
    private val checkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val hasCheckedOnLaunch = AtomicBoolean(false)

    private val httpClient by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
        }
    }

    private val _state = MutableStateFlow<AndroidUpdateCheckState>(AndroidUpdateCheckState.Idle)
    val state: StateFlow<AndroidUpdateCheckState> = _state.asStateFlow()

    val automaticCheckEnabledFlow: Flow<Boolean> = settingsManager.automaticUpdateCheckEnabledFlow

    fun isAutomaticCheckEnabled(): Boolean = settingsManager.isAutomaticUpdateCheckEnabled()

    fun setAutomaticCheckEnabled(enabled: Boolean) = settingsManager.saveAutomaticUpdateCheckEnabled(enabled)

    fun checkOnLaunchOnce() {
        if (!hasCheckedOnLaunch.compareAndSet(false, true)) return
        if (settingsManager.isAutomaticUpdateCheckEnabled()) {
            checkForUpdate(notifyWhenAvailable = true)
        }
    }

    fun checkNow() = checkForUpdate(notifyWhenAvailable = false)

    fun openReleasePage() {
        context.startActivity(releasePageIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun checkForUpdate(notifyWhenAvailable: Boolean) {
        val currentVersion = installedVersion ?: return
        if (_state.value is AndroidUpdateCheckState.Checking) return

        _state.value = AndroidUpdateCheckState.Checking
        checkScope.launch {
            try {
                val response = httpClient.get(LATEST_UPDATE_MANIFEST_URL)
                if (!response.status.isSuccess()) {
                    throw IOException("Server responded with HTTP ${response.status.value}")
                }
                val newerRelease = parseAppUpdateManifest(response.body())
                    .takeIf { release -> release.androidApk != null && isNewerVersion(release.version, currentVersion) }

                if (newerRelease == null) {
                    _state.value = AndroidUpdateCheckState.UpToDate
                } else {
                    _state.value = AndroidUpdateCheckState.Available(newerRelease.version)
                    if (notifyWhenAvailable) showUpdateNotification(newerRelease.version)
                }
            } catch (cause: Exception) {
                cause.printStackTrace()
                _state.value = AndroidUpdateCheckState.Failed
            }
        }
    }

    private fun showUpdateNotification(version: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a new version of Emberr is available"
            }
        )

        val openReleasePage = PendingIntent.getActivity(
            context,
            0,
            releasePageIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Emberr $version is available")
            .setContentText("Tap to download it from GitHub.")
            .setContentIntent(openReleasePage)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun releasePageIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_PAGE_URL))

    private companion object {
        const val CHANNEL_ID = "emberr_app_updates"
        const val NOTIFICATION_ID = 7301
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val SOCKET_TIMEOUT_MS = 60_000L
    }
}
