package com.emberr.domain.ai.models

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.emberr.R
import kotlinx.coroutines.CancellationException

class ModelDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val modelDownloadManager: ModelDownloadManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val fileName = inputData.getString(KEY_FILE_NAME)
        if (fileName == null) {
            ModelDownloadLog.e("Worker started without a model file name")
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "The download request was missing the model name."))
        }
        val initialFraction = resumeProgressFraction(fileName)
        showDownloadNotification(fileName, initialFraction)
        setProgress(workDataOf(KEY_PROGRESS_FRACTION to initialFraction))

        val downloadFlow = when (fileName) {
            ModelFileNames.EMBEDDER -> modelDownloadManager.downloadEmbeddingModel()
            ModelFileNames.GENERATOR -> modelDownloadManager.downloadGeneratorModel()
            else -> {
                ModelDownloadLog.e("Worker was asked for an unknown model file name $fileName")
                return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "That model is not one Emberr can download."))
            }
        }

        var outcome: ModelDownloadProgress = ModelDownloadProgress.Failed("Download did not complete.")
        var lastNotifiedPercent = (initialFraction * 100).toInt()
        try {
            downloadFlow.collect { progress ->
                outcome = progress
                if (progress is ModelDownloadProgress.Downloading) {
                    setProgress(workDataOf(KEY_PROGRESS_FRACTION to progress.fraction))
                    val percent = (progress.fraction * 100).toInt()
                    if (percent != lastNotifiedPercent) {
                        lastNotifiedPercent = percent
                        showDownloadNotification(fileName, progress.fraction)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Exception) {
            ModelDownloadLog.e("Worker threw while downloading $fileName", cause)
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "The download stopped unexpectedly. Please try again."))
        }

        return when (val result = outcome) {
            ModelDownloadProgress.Completed -> Result.success()

            is ModelDownloadProgress.Failed -> {
                ModelDownloadLog.e("Worker reported a failed $fileName download: ${result.message}")
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to result.message))
            }

            is ModelDownloadProgress.Downloading -> {
                ModelDownloadLog.e("Worker stopped mid download of $fileName at ${result.fraction}")
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to "The download stopped before it finished."))
            }

            ModelDownloadProgress.Paused ->
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to "The download was paused."))
        }
    }

    private suspend fun showDownloadNotification(fileName: String, fraction: Float) {
        try {
            ensureNotificationChannel()
            setForeground(createForegroundInfo(fileName, fraction))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (cause: Exception) {
            ModelDownloadLog.e("Could not show the download notification for $fileName", cause)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun createForegroundInfo(fileName: String, fraction: Float): ForegroundInfo {
        val displayName = if (fileName == ModelFileNames.EMBEDDER) "embedding model" else "AI model"
        val percent = (fraction * 100).toInt()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading $displayName")
            .setContentText("$percent%")
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_FILE_NAME = "file_name"
        const val KEY_PROGRESS_FRACTION = "progress_fraction"
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val CHANNEL_ID = "emberr_model_downloads"
        private const val NOTIFICATION_ID = 4821
    }
}
