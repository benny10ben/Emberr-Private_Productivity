package com.emberr.domain.selfhost.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SelfHostSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val selfHostSyncEngine: SelfHostSyncEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val scope = inputData.getString(KEY_SYNC_SCOPE) ?: SCOPE_TEXT
        SelfHostSyncLog.d("Worker: doWork() starting, scope=$scope, runAttemptCount=$runAttemptCount")

        try {
            val outcome = if (scope == SCOPE_MEDIA) {
                selfHostSyncEngine.syncMedia()
            } else {
                selfHostSyncEngine.runSync()
            }

            when (outcome) {
                is SelfHostSyncResult.Success -> {
                    SelfHostSyncLog.d(
                        "Worker: scope=$scope succeeded, " +
                                "notesSynced=${outcome.notesSynced}, conflicts=${outcome.conflicts}, " +
                                "brokenNotes=${outcome.brokenNotes}"
                    )
                    outcome.brokenNotesMessage
                        ?.let { message -> Result.success(workDataOf(KEY_BROKEN_NOTES_MESSAGE to message)) }
                        ?: Result.success()
                }

                is SelfHostSyncResult.NotConfigured -> {
                    SelfHostSyncLog.d("Worker: self-host sync is not configured, skipping")
                    Result.success()
                }

                is SelfHostSyncResult.AlreadyInProgress -> {
                    SelfHostSyncLog.d("Worker: a sync is already in progress, skipping this run")
                    Result.success()
                }

                is SelfHostSyncResult.Failure -> {
                    val message =
                        outcome.cause.localizedMessage ?: outcome.cause.message ?: "Sync failed"
                    SelfHostSyncLog.e("Worker: scope=$scope failed: $message", outcome.cause)
                    retryOrGiveUp(message)
                }
            }
        } catch (cause: Exception) {
            val message = cause.localizedMessage ?: cause.message ?: "Unexpected sync error"
            SelfHostSyncLog.e(
                "Worker: unexpected exception during scope=$scope sync: $message",
                cause
            )
            retryOrGiveUp(message)
        }
    }

    private fun retryOrGiveUp(message: String): Result {
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            SelfHostSyncLog.d("Worker: will retry (attempt $runAttemptCount of $MAX_RETRY_ATTEMPTS)")
            return Result.retry()
        }
        SelfHostSyncLog.e("Worker: giving up after $runAttemptCount attempts")
        return Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
    }

    companion object {
        const val KEY_SYNC_SCOPE = "self_host_sync_scope"
        const val SCOPE_TEXT = "text"
        const val SCOPE_MEDIA = "media"
        const val KEY_ERROR_MESSAGE = "self_host_sync_error_message"
        const val KEY_BROKEN_NOTES_MESSAGE = "self_host_sync_broken_notes_message"
        const val MAX_RETRY_ATTEMPTS = 5
    }
}