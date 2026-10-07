package com.emberr.domain.util.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

// Prevents local note saves from running concurrently with background sync operations,
// ensuring a sync never reads or modifies a note while it is mid-update.
object SyncCoordinator {
    val mutex = Mutex()
}

suspend fun <T> withSyncCoordinatorWaitingAtMost(maxWait: Duration, block: suspend () -> T): T? {
    val gotLock = SyncCoordinator.mutex.tryLock() ||
        withTimeoutOrNull(maxWait) { SyncCoordinator.mutex.lock() } != null
    if (!gotLock) return null
    return try {
        block()
    } finally {
        SyncCoordinator.mutex.unlock()
    }
}
