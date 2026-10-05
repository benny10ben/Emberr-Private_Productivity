package com.emberr.presentation.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

object FirstContentRenderSignal {

    private val firstContentRendered = CompletableDeferred<Unit>()

    val longestWaitForFirstContent = 3.seconds

    fun reportContentRendered() {
        firstContentRendered.complete(Unit)
    }

    fun hasFirstContent(): Boolean = firstContentRendered.isCompleted

    suspend fun awaitFirstContent() {
        firstContentRendered.await()
    }

    suspend fun awaitFirstContentOrTimeout() {
        withTimeoutOrNull(longestWaitForFirstContent) { firstContentRendered.await() }
    }
}
