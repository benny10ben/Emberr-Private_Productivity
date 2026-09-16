package com.emberr.domain.media

import com.emberr.domain.util.media.MediaStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMediaGarbageCollector(
    private val mediaReferenceIndex: MediaReferenceIndex,
    private val mediaStorageHelper: MediaStorageHelper
) {

    suspend fun collectAndDeleteOrphanedMedia() = withContext(Dispatchers.IO) {
        try {
            val referencedFileNames = mediaReferenceIndex.loadReferencedFileNames()
            val nowMs = System.currentTimeMillis()
            var deletedCount = 0

            mediaStorageHelper.listAllMediaFileNames()
                .filterNot { it in referencedFileNames }
                .forEach { fileName ->
                    try {
                        val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                        if (file.exists() && isOldEnoughToDelete(file, nowMs) && file.delete()) {
                            deletedCount++
                        }
                    } catch (e: Exception) {
                        LocalMediaGcLog.e("collectAndDeleteOrphanedMedia: failed to delete $fileName: ${e.message}", e)
                    }
                }

            LocalMediaGcLog.d("collectAndDeleteOrphanedMedia: deleted $deletedCount orphaned file(s)")
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
                        if (file.exists() && isOldEnoughToDelete(file, nowMs) && file.delete()) {
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

    private fun isOldEnoughToDelete(file: File, nowMs: Long): Boolean {
        val fileAgeMs = nowMs - file.lastModified()
        return if (file.name.endsWith(TEMP_FILE_SUFFIX)) {
            fileAgeMs > STALE_TEMP_FILE_THRESHOLD_MS
        } else {
            fileAgeMs > NEWLY_WRITTEN_FILE_GRACE_PERIOD_MS
        }
    }

    private companion object {
        const val TEMP_FILE_SUFFIX = ".tmp"
        const val STALE_TEMP_FILE_THRESHOLD_MS = 48L * 60 * 60 * 1000
        const val NEWLY_WRITTEN_FILE_GRACE_PERIOD_MS = 10L * 60 * 1000
    }
}
