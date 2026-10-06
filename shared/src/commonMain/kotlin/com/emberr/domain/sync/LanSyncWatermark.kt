package com.emberr.domain.sync

const val LAN_SYNC_WATERMARK_OVERLAP_MS = 5_000L

fun sinceWithSafetyOverlap(lastWatermark: Long): Long =
    (lastWatermark - LAN_SYNC_WATERMARK_OVERLAP_MS).coerceAtLeast(0L)

fun mustStartOverWithDesktop(
    lastSyncedDesktopId: String,
    reportedDesktopId: String?,
    thisSyncStartedFromScratch: Boolean
): Boolean =
    reportedDesktopId != null && reportedDesktopId != lastSyncedDesktopId && !thisSyncStartedFromScratch
