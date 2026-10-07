package com.emberr.domain.media

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.dao.UnusedMediaFileDao
import com.emberr.data.local.room.entity.UnusedMediaFileEntity
import com.emberr.domain.selfhost.sync.SelfHostConnectionState
import com.emberr.domain.util.media.MediaStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMediaGarbageCollector(
    private val mediaReferenceIndex: MediaReferenceIndex,
    private val mediaStorageHelper: MediaStorageHelper,
    private val unusedMediaFileDao: UnusedMediaFileDao,
    private val settingsManager: SettingsManager,
    private val selfHostConnectionState: SelfHostConnectionState
) {

    suspend fun collectAndDeleteOrphanedMedia() = withContext(Dispatchers.IO) {
        deleteExpiredTempFiles()
        try {
            val isWaitingForSync = isMediaCleanupWaitingForSync(
                waitingForLanSync = settingsManager.isMediaCleanupWaitingForLanSync(),
                isLanPaired = settingsManager.isSyncPairingConfirmed(),
                waitingForSelfHostSync = settingsManager.isMediaCleanupWaitingForSelfHostSync(),
                isSelfHostConnected = selfHostConnectionState.isConnected.value
            )
            if (isWaitingForSync) {
                LocalMediaGcLog.d("collectAndDeleteOrphanedMedia: skipped, waiting for sync to bring notes back after a restore")
                return@withContext
            }

            val nowMs = System.currentTimeMillis()
            val plan = planLocalMediaCleanup(
                fileNamesOnDisk = mediaStorageHelper.listAllMediaFileNames()
                    .filterNot { it.endsWith(TEMP_FILE_SUFFIX) }
                    .toSet(),
                usedFileNames = mediaReferenceIndex.loadReferencedFileNames(),
                unusedSinceByFileName = unusedMediaFileDao.getAll().associate { it.fileName to it.unusedSince },
                nowMs = nowMs
            )

            if (plan.fileNamesToForget.isNotEmpty()) unusedMediaFileDao.forget(plan.fileNamesToForget.toList())
            if (plan.newlyUnusedFileNames.isNotEmpty()) {
                unusedMediaFileDao.markUnused(plan.newlyUnusedFileNames.map { UnusedMediaFileEntity(it, unusedSince = nowMs) })
            }

            var deletedCount = 0
            plan.fileNamesToDelete.forEach { fileName ->
                try {
                    val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                    if (file.exists() && file.delete()) {
                        deletedCount++
                    }
                } catch (e: Exception) {
                    LocalMediaGcLog.e("collectAndDeleteOrphanedMedia: failed to delete $fileName: ${e.message}", e)
                }
            }

            LocalMediaGcLog.d(
                "collectAndDeleteOrphanedMedia: ${plan.newlyUnusedFileNames.size} file(s) newly unused, " +
                        "deleted $deletedCount file(s) unused for over 7 days"
            )
        } catch (e: Exception) {
            LocalMediaGcLog.e("collectAndDeleteOrphanedMedia: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }

    suspend fun deleteExpiredTempFiles() = withContext(Dispatchers.IO) {
        try {
            val nowMs = System.currentTimeMillis()
            var deletedCount = 0

            mediaStorageHelper.listAllMediaFileNames()
                .filter { it.endsWith(TEMP_FILE_SUFFIX) }
                .forEach { fileName ->
                    try {
                        val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                        if (file.exists() && nowMs - file.lastModified() > STALE_TEMP_FILE_THRESHOLD_MS && file.delete()) {
                            deletedCount++
                        }
                    } catch (e: Exception) {
                        LocalMediaGcLog.e("deleteExpiredTempFiles: failed to delete $fileName: ${e.message}", e)
                    }
                }

            LocalMediaGcLog.d("deleteExpiredTempFiles: deleted $deletedCount expired temp file(s)")
        } catch (e: Exception) {
            LocalMediaGcLog.e("deleteExpiredTempFiles: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }

    private companion object {
        const val TEMP_FILE_SUFFIX = ".tmp"
        const val STALE_TEMP_FILE_THRESHOLD_MS = 48L * 60 * 60 * 1000
    }
}
