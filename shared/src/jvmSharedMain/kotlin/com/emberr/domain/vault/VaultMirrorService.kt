// Runs the mirror: exports app changes, imports file changes, and sweeps for things that were deleted.

package com.emberr.domain.vault

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val QUIET_PERIOD_MILLIS = 400L
private const val REMOVAL_SWEEP_MILLIS = 3_000L
private const val SETTLE_CHECK_MILLIS = 150L
private const val MAX_SETTLE_CHECKS = 8

class VaultMirrorService(
    private val vaultExporter: VaultExporter,
    private val vaultImporter: VaultImporter,
    private val folderWatcher: VaultFolderWatcher,
    private val startupReconciler: VaultStartupReconciler,
    private val pathMemory: VaultPathMemory
) {

    private val startupFinished = CompletableDeferred<Unit>()

    // Files edited while the app was closed come in first, before the export writes over them.
    suspend fun refreshEverythingNow() {
        try {
            VaultRulesFile.writeIfMissing(vaultExporter.vaultRootDirectory)
            applyRemovalsMadeWhileClosed()
            catchUpOnOutsideChanges()
            runExport("startup refresh") { vaultExporter.exportEverything() }
        } finally {
            startupFinished.complete(Unit)
        }
    }

    // Anything deleted while the app was shut shows up as a remembered path with nothing behind it.
    private suspend fun applyRemovalsMadeWhileClosed() {
        try {
            val remembered = pathMemory.load()
            if (remembered.isEmpty) return

            val removals = vaultImporter.applyRemovalsFor(remembered)
            if (removals > 0) VaultLog.d("$removals item(s) were deleted while the app was closed")
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            VaultLog.e("could not apply removals made while closed: ${cause.message}")
        }
    }

    private suspend fun catchUpOnOutsideChanges() {
        try {
            val report = startupReconciler.reconcile()
            if (!report.didNothing) {
                VaultLog.d(
                    "caught up while closed: ${report.filesImported} imported, " +
                        "${report.notesCreated} new note(s), ${report.filesRemoved} removed, " +
                        "${report.filesAlreadyInSync} already in step"
                )
            }
            report.failures.forEach { failure -> VaultLog.e("catch-up: $failure") }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            VaultLog.e("catch-up failed: ${cause.message}")
        }
    }

    // Desktop keeps one long-lived scope, so both halves can share it.
    fun startWatching(scope: CoroutineScope) {
        startExportingAppChanges(scope)
        startImportingFileChanges(scope)
    }

    // A background sync can change notes while no window is on screen, so this half belongs on a
    // scope that lives as long as the process.
    fun startExportingAppChanges(scope: CoroutineScope) {
        startListeningForAppChanges(scope)
    }

    // This half holds a watcher thread and wakes on a timer, so it belongs on a scope that ends
    // when the app stops being visible.
    fun startImportingFileChanges(scope: CoroutineScope) {
        startListeningForFileChanges(scope)
        startSweepingForRemovals(scope)
    }

    // The watcher misses a file leaving the vault: a move to the desktop trash is not an unlink,
    // and a deleted directory is not a markdown file. A short sweep over the paths catches both.
    private fun startSweepingForRemovals(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            startupFinished.await()
            while (isActive) {
                delay(REMOVAL_SWEEP_MILLIS)
                try {
                    vaultImporter.applyRemovalsFor(vaultExporter.currentPathSnapshot())
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Exception) {
                    VaultLog.e("removal sweep failed: ${cause.message}")
                }
            }
        }
    }

    private fun startListeningForAppChanges(scope: CoroutineScope) {
        val inbox = Channel<VaultMirrorRequest>(Channel.UNLIMITED)

        scope.launch(Dispatchers.IO) {
            VaultMirrorTrigger.requests.collect { request -> inbox.send(request) }
        }

        scope.launch(Dispatchers.IO) {
            while (isActive) {
                applyBatch(collectOneBurst(inbox))
            }
        }
    }

    private fun startListeningForFileChanges(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            // Watching before the catch-up finishes would let a stale file overwrite a newer note.
            startupFinished.await()
            folderWatcher.start()
            try {
                while (isActive) {
                    val changedFiles = folderWatcher.awaitChangedMarkdownFiles()
                    if (changedFiles.isEmpty()) continue
                    waitForWritesToSettle(changedFiles)

                    // Files that still exist go first, so a move's create is handled before its
                    // delete.
                    val stillPresent = changedFiles.filter { it.isFile }
                    val vanished = changedFiles.filterNot { it.isFile }
                    if (vanished.isNotEmpty()) {
                        VaultLog.d(
                            "noticed ${vanished.size} file(s) gone: " +
                                vanished.joinToString { it.toRelativeString(vaultExporter.vaultRootDirectory) }
                        )
                    }

                    (stillPresent + vanished).forEach { file -> importFile(file) }
                }
            } finally {
                folderWatcher.stop()
            }
        }
    }

    // A save can arrive in several steps, so this waits for size and timestamp to stop moving.
    private suspend fun waitForWritesToSettle(files: Set<File>) {
        var previousSignature = signatureOf(files)

        repeat(MAX_SETTLE_CHECKS) {
            delay(SETTLE_CHECK_MILLIS)
            val signature = signatureOf(files)
            if (signature == previousSignature) return
            previousSignature = signature
        }
    }

    private fun relativePathOf(file: File): String =
        file.toRelativeString(vaultExporter.vaultRootDirectory)

    private fun signatureOf(files: Set<File>): List<Pair<Long, Long>> =
        files.sortedBy { it.absolutePath }.map { it.length() to it.lastModified() }

    private suspend fun importFile(file: File) {
        try {
            val report = vaultImporter.importFile(file)
            when (report.outcome) {
                VaultImportOutcome.IGNORED_OUR_OWN_WRITE, VaultImportOutcome.UNCHANGED -> Unit
                VaultImportOutcome.FAILED ->
                    VaultLog.e("could not import ${relativePathOf(file)}: ${report.detail ?: "unknown reason"}")
                else -> VaultLog.d(
                    "${report.outcome.name.lowercase()} \"${report.noteTitle}\" from ${relativePathOf(file)}" +
                        (report.detail?.let { " ($it)" } ?: "")
                )
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            VaultLog.e("could not import ${relativePathOf(file)}: ${cause.message}")
        }
    }

    // Waits for the first request, then swallows the rest of the burst so one save is one write.
    private suspend fun collectOneBurst(inbox: Channel<VaultMirrorRequest>): Set<VaultMirrorRequest> {
        val batch = mutableSetOf(inbox.receive())

        withTimeoutOrNull(QUIET_PERIOD_MILLIS) {
            while (true) {
                batch.add(inbox.receive())
            }
        }
        return batch
    }

    private suspend fun applyBatch(batch: Set<VaultMirrorRequest>) {
        if (batch.contains(VaultMirrorRequest.Everything)) {
            runExport("folder change") { vaultExporter.exportEverything() }
            return
        }

        for (request in batch) {
            if (request !is VaultMirrorRequest.SingleNote) continue
            runExport("note ${request.noteId}") { vaultExporter.exportSingleNote(request.noteId) }
        }
    }

    private suspend fun runExport(description: String, export: suspend () -> VaultExportResult) {
        try {
            val result = export()
            if (result.notesWritten > 0 || result.filesRemoved > 0) {
                VaultLog.d("$description: ${result.notesWritten} written, ${result.filesRemoved} removed")
            }
            result.failures.forEach { failure -> VaultLog.e("$description: $failure") }
            result.filesEditedOutsideTheApp.forEach { path -> importFile(File(path)) }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            VaultLog.e("$description failed: ${cause.message}")
        }
    }
}
