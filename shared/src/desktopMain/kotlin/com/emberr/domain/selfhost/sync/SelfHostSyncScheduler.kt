package com.emberr.domain.selfhost.sync

import com.emberr.domain.sync.AutoSyncTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

actual class SelfHostSyncScheduler(private val selfHostSyncEngine: SelfHostSyncEngine) {

    private val _isSyncActive = MutableStateFlow(false)
    actual val isSyncActive: StateFlow<Boolean> = _isSyncActive.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    actual val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private var textScope: CoroutineScope? = null
    private var mediaScope: CoroutineScope? = null

    actual fun scheduleDailySync() {
        textScope?.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        textScope = newScope

        newScope.launch {
            while (isActive) {
                selfHostSyncEngine.runSync()
                delay(24.hours)
            }
        }

        AutoSyncTrigger.syncRequests
            .debounce(5.seconds)
            .onEach {
                selfHostSyncEngine.runSync()
                selfHostSyncEngine.syncMedia()
            }
            .launchIn(newScope)
    }

    actual fun scheduleDeferredSyncAfterAppClose() = Unit

    actual fun scheduleMediaSync() {
        mediaScope?.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        mediaScope = newScope

        newScope.launch {
            while (isActive) {
                SelfHostSyncLog.d("Scheduler: starting scheduled desktop media sync pass")
                selfHostSyncEngine.syncMedia()
                delay(6.hours)
            }
        }
    }

    actual fun syncNow() {
        SelfHostSyncLog.d("Scheduler: syncNow() running inline on desktop, text sync then media sync")
        CoroutineScope(Dispatchers.Default).launch {
            _isSyncActive.value = true
            _syncError.value = null
            try {
                val textResult = selfHostSyncEngine.runSync()
                if (textResult is SelfHostSyncResult.Failure) {
                    val message = textResult.cause.message ?: "Text sync failed"
                    SelfHostSyncLog.e("Scheduler: syncNow() text sync failed: $message", textResult.cause)
                    _syncError.value = message
                }
                if (textResult is SelfHostSyncResult.Success) {
                    textResult.brokenNotesMessage?.let { message -> _syncError.value = message }
                }

                val mediaResult = selfHostSyncEngine.syncMedia()
                if (mediaResult is SelfHostSyncResult.Failure) {
                    val message = mediaResult.cause.message ?: "Media sync failed"
                    SelfHostSyncLog.e("Scheduler: syncNow() media sync failed: $message", mediaResult.cause)
                    _syncError.value = message
                }
            } catch (cause: Exception) {
                val message = cause.message ?: "Unexpected sync error"
                SelfHostSyncLog.e("Scheduler: syncNow() threw unexpectedly: $message", cause)
                _syncError.value = message
            } finally {
                _isSyncActive.value = false
            }
        }
    }

    actual fun cancelAll() {
        SelfHostSyncLog.d("Scheduler: cancelAll() cancelling desktop sync loops")
        textScope?.cancel()
        textScope = null
        mediaScope?.cancel()
        mediaScope = null
    }
}