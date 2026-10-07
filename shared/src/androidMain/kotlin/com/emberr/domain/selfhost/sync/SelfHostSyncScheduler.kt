package com.emberr.domain.selfhost.sync

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import com.emberr.domain.sync.AutoSyncTrigger
import com.emberr.presentation.shared.editor.ActiveEditorRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

actual class SelfHostSyncScheduler(
    private val context: Context,
    private val selfHostSyncEngine: SelfHostSyncEngine,
    private val foregroundSyncPoller: ForegroundSyncPoller,
    private val selfHostConnectionState: SelfHostConnectionState
) {

    private val _isSyncActive = MutableStateFlow(false)
    actual val isSyncActive: StateFlow<Boolean> = _isSyncActive.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    actual val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val manualSyncObserver = Observer<List<WorkInfo>> { infos ->
        val active = infos.isNotEmpty() && !infos.all { it.state.isFinished }
        SelfHostSyncLog.d("Scheduler: manual sync WorkInfo update, states=${infos.map { it.state }}, active=$active")
        _isSyncActive.value = active

        val notesNotSyncedMessage = infos.firstNotNullOfOrNull {
            it.outputData.getString(SelfHostSyncWorker.KEY_NOTES_NOT_SYNCED_MESSAGE)
        }
        if (notesNotSyncedMessage != null) {
            _syncError.value = notesNotSyncedMessage
        }

        val failedInfo = infos.firstOrNull { it.state == WorkInfo.State.FAILED }
        if (failedInfo != null) {
            val message = failedInfo.outputData.getString(SelfHostSyncWorker.KEY_ERROR_MESSAGE)
                ?: "Sync failed"
            SelfHostSyncLog.e("Scheduler: manual sync chain failed: $message")
            _syncError.value = message
        }
    }

    private val schedulerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (!selfHostConnectionState.isConnected.value) return
            SelfHostSyncLog.d("Scheduler: app foregrounded, starting poller and running an immediate baseline sync")
            foregroundSyncPoller.start()
            schedulerScope.launch {
                selfHostSyncEngine.runBaselineSync()
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            SelfHostSyncLog.d("Scheduler: app backgrounded, flushing editors to Room before the process can be killed")
            foregroundSyncPoller.stop()

            try {
                // Bounded, not unbounded - flushAllPending() -> performSave() needs SyncCoordinator.mutex,
                // which a concurrent self-host sync pass can hold for as long as its whole run takes
                // (many notes/media files over the network). An unbounded runBlocking here freezes the
                // main thread - every tap, every recomposition - for that entire duration, recoverable
                // only by force-stopping the app. A few seconds of best-effort flush is an acceptable
                // trade for never fully hanging the UI; WorkManager's deferred sync below still catches
                // whatever this timeout didn't get to.
                runBlocking { withTimeoutOrNull(FLUSH_BEFORE_BACKGROUND_TIMEOUT_MS) { ActiveEditorRegistry.flushAllPending() } }
            } catch (cause: Exception) {
                SelfHostSyncLog.e("Scheduler: synchronous flush-before-background failed", cause)
            }

            if (!selfHostConnectionState.isConnected.value) return
            SelfHostSyncLog.d("Scheduler: handing the background push off to WorkManager, no in-process race")
            scheduleDeferredSyncAfterAppClose()
        }
    }

    init {
        mainHandler.post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(WORK_NAME_MANUAL)
                .observeForever(manualSyncObserver)
        }

        AutoSyncTrigger.syncRequests
            .debounce(5.seconds)
            .onEach {
                if (!selfHostConnectionState.isConnected.value) return@onEach
                SelfHostSyncLog.d("Scheduler: 5s idle debounce fired, pushing text then media")
                selfHostSyncEngine.runSync()
                selfHostSyncEngine.syncMedia()
            }
            .launchIn(schedulerScope)

        // debounce() alone has no upper bound - a burst of edits closer together than 5s keeps
        // resetting its timer, so continuous typing can starve the push indefinitely. sample() is a
        // second, independent collector on the same shared flow that guarantees a push at least every
        // 15s while edits are ongoing, without fighting the debounce path (the sync engine's own
        // mutex/tryLock makes a redundant concurrent call from both a harmless no-op).
//        AutoSyncTrigger.syncRequests
//            .sample(15.seconds)
//            .onEach {
//                SelfHostSyncLog.d("Scheduler: 15s upper-bound sample fired during a continuous edit burst")
//                selfHostSyncEngine.runSync()
//                selfHostSyncEngine.syncMedia()
//            }
//            .launchIn(schedulerScope)
    }

    actual fun scheduleDailySync() {
        val request = PeriodicWorkRequestBuilder<SelfHostSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(anyNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SelfHostSyncWorker.KEY_SYNC_SCOPE to SelfHostSyncWorker.SCOPE_TEXT))
            .addTag(TAG_TEXT_SYNC)
            .build()

        SelfHostSyncLog.d("Scheduler: enqueueing daily text sync (policy=UPDATE)")
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME_DAILY, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    actual fun scheduleDeferredSyncAfterAppClose() {
        val textRequest = OneTimeWorkRequestBuilder<SelfHostSyncWorker>()
            .setConstraints(anyNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SelfHostSyncWorker.KEY_SYNC_SCOPE to SelfHostSyncWorker.SCOPE_TEXT))
            .addTag(TAG_TEXT_SYNC)
            .build()

        val mediaRequest = OneTimeWorkRequestBuilder<SelfHostSyncWorker>()
            .setConstraints(anyNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SelfHostSyncWorker.KEY_SYNC_SCOPE to SelfHostSyncWorker.SCOPE_MEDIA))
            .addTag(TAG_MEDIA_SYNC)
            .build()

        SelfHostSyncLog.d("Scheduler: enqueueing deferred post-close text sync then media sync")
        WorkManager.getInstance(context)
            .beginUniqueWork(WORK_NAME_DEFERRED, ExistingWorkPolicy.REPLACE, textRequest)
            .then(mediaRequest)
            .enqueue()
    }

    actual fun scheduleMediaSync() {
        val request = PeriodicWorkRequestBuilder<SelfHostSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(anyNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SelfHostSyncWorker.KEY_SYNC_SCOPE to SelfHostSyncWorker.SCOPE_MEDIA))
            .addTag(TAG_MEDIA_SYNC)
            .build()

        SelfHostSyncLog.d("Scheduler: enqueueing periodic media sync (policy=UPDATE)")
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME_MEDIA, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    actual fun syncNow() {
        // REPLACE would cancel an already-running manual sync mid-transfer (e.g. partway through
        // uploading several media files), leaving the remote manifest and local disk mismatched until
        // the next full cycle reconciles it. KEEP below is defense in depth for the same reason - this
        // guard only covers the common case where _isSyncActive's LiveData update hasn't lagged behind
        // WorkManager's real state.
        if (_isSyncActive.value) {
            SelfHostSyncLog.d("Scheduler: syncNow() ignored, a sync is already in progress")
            return
        }
        _syncError.value = null

        val textRequest = OneTimeWorkRequestBuilder<SelfHostSyncWorker>()
            .setConstraints(anyNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SelfHostSyncWorker.KEY_SYNC_SCOPE to SelfHostSyncWorker.SCOPE_TEXT))
            .addTag(TAG_TEXT_SYNC)
            .build()

        val mediaRequest = OneTimeWorkRequestBuilder<SelfHostSyncWorker>()
            .setConstraints(anyNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(SelfHostSyncWorker.KEY_SYNC_SCOPE to SelfHostSyncWorker.SCOPE_MEDIA))
            .addTag(TAG_MEDIA_SYNC)
            .build()

        SelfHostSyncLog.d("Scheduler: syncNow() chaining text sync -> media sync, media will not dispatch until text finishes")
        WorkManager.getInstance(context)
            .beginUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, textRequest)
            .then(mediaRequest)
            .enqueue()
    }

    actual fun cancelAll() {
        SelfHostSyncLog.d("Scheduler: cancelAll() cancelling all self-host WorkManager jobs")
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(WORK_NAME_DAILY)
            cancelUniqueWork(WORK_NAME_DEFERRED)
            cancelUniqueWork(WORK_NAME_MEDIA)
            cancelUniqueWork(WORK_NAME_MANUAL)
        }
        foregroundSyncPoller.stop()
    }

    private fun anyNetworkConstraints(): Constraints =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    private companion object {
        const val WORK_NAME_DAILY = "EmberrSelfHostDailyTextSync"
        const val WORK_NAME_DEFERRED = "EmberrSelfHostDeferredTextSync"
        const val WORK_NAME_MEDIA = "EmberrSelfHostMediaSync"
        const val WORK_NAME_MANUAL = "EmberrSelfHostManualSync"
        const val TAG_TEXT_SYNC = "EmberrSelfHostTextSync"
        const val TAG_MEDIA_SYNC = "EmberrSelfHostMediaSync"
        const val FLUSH_BEFORE_BACKGROUND_TIMEOUT_MS = 3000L
    }
}