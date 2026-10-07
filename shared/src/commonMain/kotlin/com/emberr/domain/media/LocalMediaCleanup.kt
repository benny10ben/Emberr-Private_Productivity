package com.emberr.domain.media

internal const val LOCAL_MEDIA_UNUSED_WAIT_MS = 7L * 24 * 60 * 60 * 1000

internal data class LocalMediaCleanupPlan(
    val newlyUnusedFileNames: Set<String>,
    val fileNamesToForget: Set<String>,
    val fileNamesToDelete: Set<String>
)

internal fun planLocalMediaCleanup(
    fileNamesOnDisk: Set<String>,
    usedFileNames: Set<String>,
    unusedSinceByFileName: Map<String, Long>,
    nowMs: Long
): LocalMediaCleanupPlan {
    val unusedFileNames = fileNamesOnDisk - usedFileNames
    return LocalMediaCleanupPlan(
        newlyUnusedFileNames = unusedFileNames - unusedSinceByFileName.keys,
        fileNamesToForget = unusedSinceByFileName.keys - unusedFileNames,
        fileNamesToDelete = unusedFileNames.filterTo(mutableSetOf()) { fileName ->
            unusedSinceByFileName[fileName]?.let { nowMs - it > LOCAL_MEDIA_UNUSED_WAIT_MS } == true
        }
    )
}

internal fun isMediaCleanupWaitingForSync(
    waitingForLanSync: Boolean,
    isLanPaired: Boolean,
    waitingForSelfHostSync: Boolean,
    isSelfHostConnected: Boolean
): Boolean = (waitingForLanSync && isLanPaired) || (waitingForSelfHostSync && isSelfHostConnected)
