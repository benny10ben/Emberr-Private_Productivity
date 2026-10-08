package com.emberr.domain.selfhost.sync

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.CalendarEventExceptionDao
import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.data.local.room.dao.ChatSessionDao
import com.emberr.data.local.room.dao.FolderDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.dao.PropertyTagDao
import com.emberr.data.local.room.dao.CustomPropertyDao
import com.emberr.data.local.room.dao.SelfHostDeletedApiConfigDao
import com.emberr.data.local.room.dao.SelfHostDeletedNoteDao
import com.emberr.data.local.room.dao.SpaceDao
import com.emberr.data.local.room.entity.CalendarEventExceptionEntity
import com.emberr.data.local.room.entity.CategoryEntity
import com.emberr.data.local.room.entity.PropertyTagEntity
import com.emberr.data.local.room.entity.CustomPropertyEntity
import com.emberr.data.local.room.entity.ChatSessionEntity
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.PLACEHOLDER_SPACE_UPDATED_AT
import com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity
import com.emberr.data.local.room.entity.SpaceEntity
import com.emberr.domain.ai.chat.ChatSessionMerge
import com.emberr.domain.ai.external.AiSettingsRepository
import com.emberr.domain.ai.external.ExternalAiProvider
import com.emberr.domain.ai.external.ExternalAiProviderConfig
import com.emberr.domain.canvas.CanvasRepository
import com.emberr.domain.model.BookmarkCategoryOrderBySpace
import com.emberr.domain.model.FavoriteNoteOrderBySpace
import com.emberr.domain.repository.BookmarkCategoryOrderStore
import com.emberr.domain.repository.FavoriteNoteOrderStore
import com.emberr.domain.model.NoteBlockSerializer
import com.emberr.domain.model.NoteContent
import com.emberr.domain.media.MediaReferenceIndex
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.selfhost.merge.NoteMergeHelper
import com.emberr.domain.space.SpaceRepository
import com.emberr.domain.selfhost.translation.EmbeddedBlockPayload
import com.emberr.domain.selfhost.translation.NoteJsonCompiler
import com.emberr.domain.selfhost.translation.NoteJsonParser
import com.emberr.domain.selfhost.translation.NotePayloadSyncException
import com.emberr.domain.selfhost.webdav.WebDavConfigurationException
import com.emberr.domain.selfhost.webdav.WebDavConflictException
import com.emberr.domain.selfhost.webdav.WebDavDecryptionException
import com.emberr.domain.selfhost.webdav.WebDavSyncClient
import com.emberr.domain.selfhost.webdav.WebDavSyncPaths
import com.emberr.domain.sync.MediaTransferPhase
import com.emberr.domain.sync.MediaTransferStatusBus
import com.emberr.domain.sync.withNewerDetailsFrom
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.sync.withSyncCoordinatorWaitingAtMost
import com.emberr.database.EmberrDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

sealed class SelfHostSyncResult {
    data class Success(val notesSynced: Int, val conflicts: Int, val notesNotSynced: Int = 0) : SelfHostSyncResult() {
        val notesNotSyncedMessage: String?
            get() = when (notesNotSynced) {
                0 -> null
                1 -> "1 note couldn't be synced"
                else -> "$notesNotSynced notes couldn't be synced"
            }
    }
    data class Failure(val cause: Throwable) : SelfHostSyncResult()
    data object AlreadyInProgress : SelfHostSyncResult()
    data object NotConfigured : SelfHostSyncResult()
    data object WaitingForAllowedNetwork : SelfHostSyncResult()
}

internal enum class ReconcileOutcome { SYNCED, CONFLICT_SKIPPED, LOCK_BUSY, UNCHANGED, BROKEN_NOTE }

internal suspend fun reconcileOrSkipBrokenNote(
    noteId: String,
    reconcile: suspend () -> ReconcileOutcome
): ReconcileOutcome =
    try {
        reconcile()
    } catch (cause: NotePayloadSyncException) {
        SelfHostSyncLog.e("TextSync: note=$noteId has broken content, skipping it this cycle", cause)
        ReconcileOutcome.BROKEN_NOTE
    } catch (cause: WebDavDecryptionException) {
        SelfHostSyncLog.e("TextSync: note=$noteId could not be decrypted, skipping it this cycle", cause)
        ReconcileOutcome.BROKEN_NOTE
    }

class SelfHostSyncEngine(
    private val webDavSyncClient: WebDavSyncClient,
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val folderDao: FolderDao,
    private val categoryDao: CategoryDao,
    private val propertyTagDao: PropertyTagDao,
    private val customPropertyDao: CustomPropertyDao,
    private val calendarEventExceptionDao: CalendarEventExceptionDao,
    private val spaceDao: SpaceDao,
    private val spaceRepository: SpaceRepository,
    private val settingsManager: SettingsManager,
    private val mediaStorageHelper: MediaStorageHelper,
    private val noteRepository: NoteRepository,
    private val selfHostDeletedNoteDao: SelfHostDeletedNoteDao,
    private val chatSessionDao: ChatSessionDao,
    private val selfHostDeletedApiConfigDao: SelfHostDeletedApiConfigDao,
    private val aiSettingsRepository: AiSettingsRepository,
    private val database: EmberrDatabase,
    private val bookmarkCategoryOrderStore: BookmarkCategoryOrderStore,
    private val favoriteNoteOrderStore: FavoriteNoteOrderStore,
    private val mediaReferenceIndex: MediaReferenceIndex,
    private val canvasRepository: CanvasRepository,
    private val meteredNetworkChecker: MeteredNetworkChecker
) {

    private val textSyncMutex = Mutex()
    private val mediaSyncMutex = Mutex()
    private val manifestWriteMutex = Mutex()
    private val manifestJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val blockJson = Json { ignoreUnknownKeys = true }
    private val collectionJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaRetryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        const val MAX_MANIFEST_UPLOAD_RETRIES = 3
        val SYNC_LOCK_MAX_WAIT = 10.seconds
    }

    // wire this up in the app ui so let users know if any self host sync is pending
    suspend fun hasPendingLocalChanges(): Boolean =
        noteDao.getNotesNeedingSelfHostSync().isNotEmpty()

    // textSyncMutex prevents two text syncs from overlapping, but doesn't block local editor saves.
    // To prevent saves from reading incomplete data mid-sync, we use `SyncCoordinator.mutex`
    // to lock each note individually inside `runSyncLocked`.
    fun isOnAllowedNetwork(): Boolean =
        SelfHostSyncNetwork.fromStoredName(settingsManager.getSelfHostSyncNetwork())
            .allowsSync(meteredNetworkChecker.isOnMeteredNetwork())

    suspend fun runSync(): SelfHostSyncResult {
        SelfHostSyncLog.d("runSync() called")
        if (!isOnAllowedNetwork()) {
            SelfHostSyncLog.d("runSync() skipped, this network is not one the user picked for sync")
            return SelfHostSyncResult.WaitingForAllowedNetwork
        }
        if (!textSyncMutex.tryLock()) {
            SelfHostSyncLog.d("runSync() skipped, a sync is already in progress")
            return SelfHostSyncResult.AlreadyInProgress
        }
        return try {
            runSyncLocked()
        } finally {
            textSyncMutex.unlock()
        }
    }

    suspend fun forgetServerSyncProgress() = textSyncMutex.withLock {
        noteDao.forgetSelfHostSyncProgressForAllNotes()
        settingsManager.saveSelfHostLastSyncTimestamp(0L)
        settingsManager.saveSelfHostSupportsETags(null)
        settingsManager.saveSelfHostManifestEtag(null)
    }

    suspend fun syncMedia(): SelfHostSyncResult {
        SelfHostSyncLog.d("syncMedia() called")
        if (!isOnAllowedNetwork()) {
            SelfHostSyncLog.d("syncMedia() skipped, this network is not one the user picked for sync")
            return SelfHostSyncResult.WaitingForAllowedNetwork
        }
        if (!mediaSyncMutex.tryLock()) {
            SelfHostSyncLog.d("syncMedia() skipped, a sync is already in progress")
            return SelfHostSyncResult.AlreadyInProgress
        }
        return try {
            // Media sync only touches files on disk and the remote manifest, never note data, so it
            // doesn't need SyncCoordinator.mutex - mediaSyncMutex already prevents two media
            // passes from overlapping. Holding SyncCoordinator.mutex here would instead
            // freeze every local editor save for as long as a large attachment takes to transfer.
            syncMediaLocked()
        } finally {
            mediaSyncMutex.unlock()
        }
    }

    // Lets UI explicitly retry one specific file on demand (e.g. a "tap to retry" affordance on a
    // failed block) rather than only waiting for the next scheduled/foreground-polled media sync.
    // Fire-and-forget and independent of the sync locks above, mirroring SyncRepositoryImpl's LAN
    // equivalent, so a single retry tap can't block on or be blocked by a whole sync pass.
    fun retryMediaDownload(fileName: String) {
        val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
        if (file.exists()) return
        file.parentFile?.mkdirs()
        mediaRetryScope.launch {
            MediaTransferStatusBus.markStarted(fileName, MediaTransferPhase.DOWNLOADING)
            var succeeded = false
            try {
                val entry = downloadManifest().entries.firstOrNull {
                    it.entryType == SelfHostEntryType.MEDIA && it.entryId == fileName && it.trashedAt == null
                }
                val sizeBytes = entry?.mediaSizeBytes
                if (sizeBytes != null) {
                    succeeded = webDavSyncClient.downloadMediaInPieces(fileName, sizeBytes, file)
                }
            } catch (cause: WebDavConfigurationException) {
                // Not configured - a normal, expected outcome when self-host isn't set up, not an error.
            } catch (cause: Exception) {
                SelfHostSyncLog.e("retryMediaDownload: $fileName failed: ${cause.message}", cause)
            } finally {
                MediaTransferStatusBus.markFinished(fileName, succeeded)
            }
        }
    }

    suspend fun runBaselineSync(): SelfHostSyncResult {
        SelfHostSyncLog.d("runBaselineSync() called")
        val textResult = runSync()

        if (textResult is SelfHostSyncResult.Success) {
            try {
                // Media sync only touches files on disk and the remote manifest, never note data,
                // so it doesn't need SyncCoordinator.mutex - holding it here would otherwise freeze
                // every local editor save for as long as a large attachment takes to transfer.
                when (val mediaResult = syncMedia()) {
                    is SelfHostSyncResult.Failure -> SelfHostSyncLog.e(
                        "runBaselineSync(): baseline media sync failed, will retry via background worker: ${mediaResult.cause.message}",
                        mediaResult.cause
                    )
                    else -> SelfHostSyncLog.d("runBaselineSync(): baseline media sync finished with $mediaResult")
                }
            } catch (cause: Exception) {
                SelfHostSyncLog.e(
                    "runBaselineSync(): baseline media sync threw unexpectedly, will retry via background worker",
                    cause
                )
            }
        } else {
            SelfHostSyncLog.d("runBaselineSync(): skipping baseline media sync, text sync did not succeed ($textResult)")
        }

        return textResult
    }

    private suspend fun syncMediaLocked(): SelfHostSyncResult {
        return try {
            webDavSyncClient.ensureRemoteLayoutExists()

            val manifest = downloadManifest()
            val manifestMediaEntries = manifest.entries.filter { it.entryType == SelfHostEntryType.MEDIA }
            val remoteMediaFileNames = manifestMediaEntries.map { it.entryId }.toSet()

            val referencedFileNames = collectReferencedMediaFileNames()
            // Check disk presence for all files tracked in the manifest.
            // This prevents re-downloading files that are already on disk but aren't
            // referenced by local note blocks yet.
            val existingLocalFileNames = (referencedFileNames + remoteMediaFileNames)
                .filterTo(mutableSetOf()) { fileExistsLocally(it) }

            SelfHostSyncLog.d(
                "MediaSync: ${referencedFileNames.size} media file(s) referenced locally, " +
                        "${existingLocalFileNames.size} actually present on disk, " +
                        "${remoteMediaFileNames.size} tracked on server"
            )

            val toUpload = existingLocalFileNames - remoteMediaFileNames
            val toDownload = mediaFilesToDownload(manifestMediaEntries, referencedFileNames, existingLocalFileNames)
            SelfHostSyncLog.d("MediaSync: ${toUpload.size} to upload, ${toDownload.size} to download")

            var uploadedCount = 0
            var failedCount = 0
            val successfullyUploaded = mutableMapOf<String, Long>()

            for (fileName in toUpload) {
                val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                if (!file.exists()) {
                    failedCount++
                    SelfHostSyncLog.e("MediaSync Error: local file missing for $fileName at path=${file.path}")
                    continue
                }
                // Publishes to the same MediaTransferStatusBus the LAN sync path uses, so a note's
                // Image/Document/Audio block shows a real "downloading"/"failed" state regardless of
                // which sync mechanism (LAN or self-host) is actually fetching its attachment.
                MediaTransferStatusBus.markStarted(fileName, MediaTransferPhase.UPLOADING)
                var uploadSucceeded = false
                try {
                    successfullyUploaded[fileName] = webDavSyncClient.uploadMediaInPieces(fileName, file)
                    uploadSucceeded = true
                    uploadedCount++
                    SelfHostSyncLog.d("MediaSync: uploaded $fileName (${file.length()} bytes)")
                } catch (cause: Exception) {
                    failedCount++
                    SelfHostSyncLog.e("MediaSync Error: failed to upload $fileName: ${cause.message}", cause)
                } finally {
                    MediaTransferStatusBus.markFinished(fileName, uploadSucceeded)
                }
            }

            var downloadedCount = 0
            val missingOnServer = mutableSetOf<String>()
            val mediaSizesByFileName = manifestMediaEntries.associate { it.entryId to it.mediaSizeBytes }
            for (fileName in toDownload) {
                val sizeBytes = mediaSizesByFileName[fileName]
                if (sizeBytes == null) {
                    failedCount++
                    SelfHostSyncLog.e("MediaSync Error: $fileName has no size in the manifest, skipping")
                    continue
                }
                SelfHostSyncLog.d("MediaSync: Downloading missing local file $fileName")
                val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                file.parentFile?.mkdirs()
                MediaTransferStatusBus.markStarted(fileName, MediaTransferPhase.DOWNLOADING)
                var downloadSucceeded = false
                try {
                    val downloaded = webDavSyncClient.downloadMediaInPieces(fileName, sizeBytes, file)
                    if (!downloaded) {
                        missingOnServer += fileName
                        SelfHostSyncLog.e(
                            "MediaSync: $fileName is listed in the manifest but missing on the server, " +
                                    "removing it from the list so a device with a copy uploads it again"
                        )
                    } else {
                        downloadSucceeded = true
                        downloadedCount++
                        SelfHostSyncLog.d("MediaSync: downloaded $fileName (${file.length()} bytes)")
                    }
                } catch (cause: Exception) {
                    failedCount++
                    SelfHostSyncLog.e("MediaSync Error: failed to download $fileName: ${cause.message}", cause)
                } finally {
                    MediaTransferStatusBus.markFinished(fileName, downloadSucceeded)
                }
            }

            val nowMs = Clock.System.now().toEpochMilliseconds()
            val decisions = decideMediaCleanup(
                mediaEntries = manifestMediaEntries,
                claimedFileNames = mediaClaimedByLiveNotes(manifest.entries) + referencedFileNames,
                nowMs = nowMs
            )
            val changes = carryOutMediaCleanup(decisions).copy(
                newlyUploaded = successfullyUploaded,
                missingOnServer = missingOnServer
            )
            val abandonedUploadsDeleted = deleteAbandonedUploads(
                finishedFileNames = remoteMediaFileNames,
                fileNamesUploadedThisRun = toUpload,
                nowMs = nowMs
            )

            manifestWriteMutex.withLock {
                val (latestManifest, latestManifestEtag) = downloadManifestWithEtag()
                uploadMediaManifestEntries(latestManifest, changes, referencedFileNames, latestManifestEtag)
            }

            SelfHostSyncLog.d(
                "MediaSync: complete, uploaded=$uploadedCount downloaded=$downloadedCount " +
                        "movedToTrash=${changes.movedToTrash.size} restoredFromTrash=${changes.restoredFromTrash.size} " +
                        "goneFromServer=${changes.goneFromServer.size} missingOnServer=${missingOnServer.size} " +
                        "abandonedUploadsDeleted=$abandonedUploadsDeleted " +
                        "failed=$failedCount"
            )
            SelfHostSyncResult.Success(notesSynced = uploadedCount + downloadedCount, conflicts = failedCount)
        } catch (cause: WebDavConfigurationException) {
            SelfHostSyncLog.d("MediaSync: not configured (${cause.message})")
            SelfHostSyncResult.NotConfigured
        } catch (cause: Exception) {
            SelfHostSyncLog.e("MediaSync: sync failed with ${cause::class.simpleName}", cause)
            SelfHostSyncResult.Failure(cause)
        }
    }

    private fun fileExistsLocally(fileName: String): Boolean {
        return try {
            File(mediaStorageHelper.getAbsoluteMediaPath(fileName)).exists()
        } catch (cause: Exception) {
            SelfHostSyncLog.e("MediaSync: could not check local existence for $fileName", cause)
            false
        }
    }

    private suspend fun collectReferencedMediaFileNames(): Set<String> {
        val fileNames = mediaReferenceIndex.loadReferencedFileNames()
        SelfHostSyncLog.d("MediaSync: ${fileNames.size} distinct media file(s) referenced")
        return fileNames
    }

    private suspend fun carryOutMediaCleanup(decisions: MediaCleanupDecisions): MediaChangesThisRun {
        val movedToTrash = mutableSetOf<String>()
        val restoredFromTrash = mutableSetOf<String>()
        val goneFromServer = mutableSetOf<String>()

        for (fileName in decisions.filesToMoveToTrash) {
            try {
                webDavSyncClient.moveFile(WebDavSyncPaths.mediaFolderPath(fileName), WebDavSyncPaths.trashedMediaFolderPath(fileName))
                movedToTrash += fileName
                SelfHostSyncLog.d("MediaSync: moved $fileName to the server trash, no note has used it for a day")
            } catch (cause: Exception) {
                SelfHostSyncLog.e("MediaSync Error: failed to move $fileName to the server trash: ${cause.message}", cause)
            }
        }

        for (fileName in decisions.filesToRestoreFromTrash) {
            try {
                val movedBack = webDavSyncClient.moveFile(
                    WebDavSyncPaths.trashedMediaFolderPath(fileName),
                    WebDavSyncPaths.mediaFolderPath(fileName)
                )
                if (movedBack || webDavSyncClient.fileExists(WebDavSyncPaths.mediaFolderPath(fileName))) {
                    restoredFromTrash += fileName
                    SelfHostSyncLog.d("MediaSync: restored $fileName from the server trash, a note uses it again")
                } else {
                    goneFromServer += fileName
                    SelfHostSyncLog.e("MediaSync: $fileName is used again but is no longer on the server, a device with a copy will upload it")
                }
            } catch (cause: Exception) {
                SelfHostSyncLog.e("MediaSync Error: failed to restore $fileName from the server trash: ${cause.message}", cause)
            }
        }

        for (fileName in decisions.filesToEmptyFromTrash) {
            try {
                webDavSyncClient.deleteFile(WebDavSyncPaths.trashedMediaFolderPath(fileName))
                goneFromServer += fileName
                SelfHostSyncLog.d("MediaSync: emptied $fileName from the server trash after 30 days")
            } catch (cause: Exception) {
                SelfHostSyncLog.e("MediaSync Error: failed to empty $fileName from the server trash: ${cause.message}", cause)
            }
        }

        return MediaChangesThisRun(
            movedToTrash = movedToTrash,
            restoredFromTrash = restoredFromTrash,
            goneFromServer = goneFromServer
        )
    }

    private suspend fun deleteAbandonedUploads(
        finishedFileNames: Set<String>,
        fileNamesUploadedThisRun: Set<String>,
        nowMs: Long
    ): Int {
        var deletedCount = 0
        val unfinishedUploadFileNames = try {
            webDavSyncClient.listDirectory(WebDavSyncPaths.MEDIA_DIR)
                .filter { it.isCollection }
                .mapNotNull { WebDavSyncPaths.mediaIdFromFolderName(WebDavSyncPaths.lastPathSegment(it.href)) }
                .filter { it !in finishedFileNames && it !in fileNamesUploadedThisRun }
        } catch (cause: Exception) {
            SelfHostSyncLog.e("MediaSync Error: failed to list the media folder to find unfinished uploads: ${cause.message}", cause)
            return 0
        }

        for (fileName in unfinishedUploadFileNames) {
            try {
                val folderPath = WebDavSyncPaths.mediaFolderPath(fileName)
                val lastProgressAtMs = lastUploadProgressAt(webDavSyncClient.listDirectory(folderPath))
                if (isAbandonedUpload(lastProgressAtMs, nowMs)) {
                    webDavSyncClient.deleteFile(folderPath)
                    deletedCount++
                    SelfHostSyncLog.d("MediaSync: deleted the unfinished upload of $fileName, no new piece arrived for 7 days")
                }
            } catch (cause: Exception) {
                SelfHostSyncLog.e("MediaSync Error: failed to check the unfinished upload of $fileName: ${cause.message}", cause)
            }
        }
        return deletedCount
    }

    private suspend fun uploadMediaManifestEntries(
        previousManifest: SelfHostManifest,
        changes: MediaChangesThisRun,
        referencedFileNames: Set<String>,
        previousManifestEtag: String? = null,
        attempt: Int = 0
    ) {
        val nonMediaEntries = previousManifest.entries.filter { it.entryType != SelfHostEntryType.MEDIA }
        val mediaEntries = updatedMediaEntries(
            mediaEntries = previousManifest.entries.filter { it.entryType == SelfHostEntryType.MEDIA },
            claimedFileNames = mediaClaimedByLiveNotes(previousManifest.entries) + referencedFileNames,
            changes = changes,
            nowMs = Clock.System.now().toEpochMilliseconds()
        )
        val newManifest = SelfHostManifest(entries = nonMediaEntries + mediaEntries)

        try {
            webDavSyncClient.uploadEncryptedJson(
                WebDavSyncPaths.MANIFEST_FILE,
                manifestJson.encodeToString(SelfHostManifest.serializer(), newManifest),
                previousManifestEtag
            )
        } catch (cause: WebDavConflictException) {
            if (attempt >= MAX_MANIFEST_UPLOAD_RETRIES) throw cause
            val (freshManifest, freshEtag) = downloadManifestWithEtag()
            uploadMediaManifestEntries(freshManifest, changes, referencedFileNames, freshEtag, attempt + 1)
        }
    }

    private suspend fun runSyncLocked(): SelfHostSyncResult {
        return try {
            webDavSyncClient.ensureRemoteLayoutExists()

            try {
                val dedupedCount = noteRepository.dedupeDuplicateDailyNotes()
                if (dedupedCount > 0) {
                    SelfHostSyncLog.d("TextSync: deduped $dedupedCount duplicate daily note row(s) left over from earlier syncs")
                }
            } catch (cause: Exception) {
                SelfHostSyncLog.e("TextSync: dedupeDuplicateDailyNotes failed, continuing sync anyway", cause)
            }

            val syncStartTimestamp = Clock.System.now().toEpochMilliseconds()
            val manifest = downloadManifest()

            // Sync candidacy is determined per note using its `selfHostSyncedAt` value.
            // If a local row doesn't exist (e.g., a new note or wiped data), it defaults to 0
            // and safely syncs as a new entry.
            val remoteTextEntries = manifest.entries.filter {
                it.entryType == SelfHostEntryType.NOTE || it.entryType == SelfHostEntryType.DAILY
            }
            val localRowsByNoteId = noteDao
                .getNotesByIdsIncludingTemplates(
                    remoteTextEntries.filterNot { it.entryType == SelfHostEntryType.DAILY }.map { it.entryId }
                )
                .associateBy { it.noteId }
            val localDailyRowsByEntryId = noteDao.getAllDailyNoteMetadataAcrossSpaces()
                .filter { it.dateString != null }
                .associateBy { dailyManifestEntryId(it.spaceId, it.dateString.orEmpty()) }

            fun localRowForEntry(entry: SelfHostManifestEntry): NoteMetadataEntity? =
                if (entry.entryType == SelfHostEntryType.DAILY) {
                    localDailyRowsByEntryId[entry.entryId]
                } else {
                    localRowsByNoteId[entry.entryId]
                }

            val remoteChangedEntries = remoteTextEntries
                .filter { it.updatedAt > (localRowForEntry(it)?.selfHostSyncedAt ?: 0L) }

            val candidates = LinkedHashMap<String, SelfHostManifestEntry?>()
            remoteChangedEntries.forEach { entry ->
                candidates[localRowForEntry(entry)?.noteId ?: entry.entryId] = entry
            }
            val localChangedNotes = noteDao.getNotesNeedingSelfHostSync()
            localChangedNotes.forEach { note ->
                if (!candidates.containsKey(note.noteId)) candidates[note.noteId] = null
            }

            SelfHostSyncLog.d(
                "TextSync: remoteChanged=${remoteChangedEntries.size}, localChanged=${localChangedNotes.size}, " +
                        "candidates=${candidates.size}"
            )

            var syncLockWaitForThisRun = SYNC_LOCK_MAX_WAIT
            suspend fun <T> withSyncLock(block: suspend () -> T): T? =
                withSyncCoordinatorWaitingAtMost(syncLockWaitForThisRun, block)
                    .also { result -> if (result == null) syncLockWaitForThisRun = Duration.ZERO }

            if (withSyncLock { reconcileSpaces() } == null) {
                SelfHostSyncLog.d("TextSync: spaces skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }

            var syncedCount = 0
            var conflictCount = 0
            var skippedBusyCount = 0
            var brokenNoteCount = 0
            val noteIdsStillWaitingToUpload = mutableSetOf<String>()

            for ((noteId, remoteEntry) in candidates) {
                // Lock each note individually for reconciliation.
                val outcome = reconcileOrSkipBrokenNote(noteId) {
                    withSyncLock {
                        reconcileNote(noteId, remoteEntry = remoteEntry)
                    } ?: run {
                        SelfHostSyncLog.d("TextSync: note=$noteId skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
                        ReconcileOutcome.LOCK_BUSY
                    }
                }
                SelfHostSyncLog.d("TextSync: note=$noteId outcome=$outcome")
                when (outcome) {
                    ReconcileOutcome.SYNCED -> syncedCount++
                    ReconcileOutcome.CONFLICT_SKIPPED -> { conflictCount++; noteIdsStillWaitingToUpload += noteId }
                    ReconcileOutcome.LOCK_BUSY -> { skippedBusyCount++; noteIdsStillWaitingToUpload += noteId }
                    ReconcileOutcome.UNCHANGED -> Unit
                    ReconcileOutcome.BROKEN_NOTE -> { brokenNoteCount++; noteIdsStillWaitingToUpload += noteId }
                }
            }

            if (withSyncLock { reconcileFolders() } == null) {
                SelfHostSyncLog.d("TextSync: folders skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncLock { reconcileCategories() } == null) {
                SelfHostSyncLog.d("TextSync: categories skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncLock { reconcilePropertyTags() } == null) {
                SelfHostSyncLog.d("TextSync: property tags skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncLock { reconcileCustomProperties() } == null) {
                SelfHostSyncLog.d("TextSync: custom properties skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncLock { reconcileEventExceptions() } == null) {
                SelfHostSyncLog.d("TextSync: event exceptions skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncLock { reconcileApiConfigs() } == null) {
                SelfHostSyncLog.d("TextSync: api configs skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncLock { reconcileBookmarkCategoryOrder() } == null) {
                SelfHostSyncLog.d("TextSync: bookmark category order skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncLock { reconcileFavoriteNoteOrder() } == null) {
                SelfHostSyncLog.d("TextSync: favorite note order skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            val chatSessionEntries = withSyncLock { reconcileChatSessions(manifest) } ?: run {
                SelfHostSyncLog.d("TextSync: chat sessions skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
                manifest.entries.filter { it.entryType == SelfHostEntryType.CHAT_SESSION }
            }

            // Always publish the manifest, even if some individual notes had conflicts.
            // Conflicted notes retain their downloaded state until locally resolved and pushed.
            // This runs without locks to read a fresh, unlocked database snapshot.
            manifestWriteMutex.withLock {
                val (latestManifest, latestManifestEtag) = downloadManifestWithEtag()
                uploadManifest(
                    previousManifest = latestManifest,
                    noteIdsStillWaitingToUpload = noteIdsStillWaitingToUpload,
                    chatSessionEntries = chatSessionEntries,
                    previousManifestEtag = latestManifestEtag
                )
            }

            // Update the global last sync timestamp purely for UI and polling checks.
            // It's safe to advance this even if some notes had conflicts.
            settingsManager.saveSelfHostLastSyncTimestamp(syncStartTimestamp)
            if (brokenNoteCount + conflictCount + skippedBusyCount == 0) {
                settingsManager.saveMediaCleanupWaitingForSelfHostSync(false)
            }

            SelfHostSyncLog.d(
                "TextSync: complete, synced=$syncedCount conflicts=$conflictCount skippedBusy=$skippedBusyCount " +
                        "broken=$brokenNoteCount"
            )
            SelfHostSyncResult.Success(
                notesSynced = syncedCount,
                conflicts = conflictCount,
                notesNotSynced = brokenNoteCount + conflictCount
            )
        } catch (cause: WebDavConfigurationException) {
            SelfHostSyncLog.d("TextSync: not configured (${cause.message})")
            SelfHostSyncResult.NotConfigured
        } catch (cause: Exception) {
            SelfHostSyncLog.e("TextSync: sync failed with ${cause::class.simpleName}", cause)
            SelfHostSyncResult.Failure(cause)
        }
    }

    private suspend fun reconcileSpaces() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.SPACES_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteSpaces = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(SpaceEntity.serializer()), it) }
                .orEmpty()
            val localSpaces = spaceDao.getSpacesModifiedSince(PLACEHOLDER_SPACE_UPDATED_AT)

            val merged = LinkedHashMap<String, SpaceEntity>()
            localSpaces.forEach { merged[it.spaceId] = it }
            remoteSpaces.forEach { remote ->
                val local = merged[remote.spaceId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.spaceId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { spaceDao.insertOrUpdateSpace(it) }
            spaceRepository.moveActiveSpaceIfItNoLongerExists()

            if (mergedList.toSet() != remoteSpaces.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.SPACES_FILE,
                    collectionJson.encodeToString(ListSerializer(SpaceEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("SpaceSync: complete, ${mergedList.size} space(s) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("SpaceSync: remote spaces.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("SpaceSync: failed to sync spaces: ${cause.message}", cause)
        }
    }

    // Folders and categories sync as single encrypted JSON files since they are small.
    // We merge them entity-by-entity using a "last-write-wins" approach based on updatedAt.
    private suspend fun reconcileFolders() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.FOLDERS_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteFolders = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(FolderEntity.serializer()), it) }
                .orEmpty()
            val localFolders = folderDao.getFoldersModifiedSince(0L)

            val merged = LinkedHashMap<String, FolderEntity>()
            localFolders.forEach { merged[it.folderId] = it }
            remoteFolders.forEach { remote ->
                val local = merged[remote.folderId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.folderId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { spaceRepository.ensureSpaceExists(it.spaceId) }
            mergedList.forEach { folderDao.insertFolder(it) }

            if (mergedList.toSet() != remoteFolders.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.FOLDERS_FILE,
                    collectionJson.encodeToString(ListSerializer(FolderEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("FolderSync: complete, ${mergedList.size} folder(s) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("FolderSync: remote folders.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("FolderSync: failed to sync folders: ${cause.message}", cause)
        }
    }

    private suspend fun reconcileEventExceptions() {
        try {
            val remoteJsonWithEtag =
                webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.EVENT_EXCEPTIONS_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteExceptions = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(CalendarEventExceptionEntity.serializer()), it) }
                .orEmpty()
            val localExceptions = calendarEventExceptionDao.getExceptionsModifiedSince(0L)

            val merged = LinkedHashMap<String, CalendarEventExceptionEntity>()
            localExceptions.forEach { merged[occurrenceKeyOf(it)] = it }
            remoteExceptions.forEach { remote ->
                val local = merged[occurrenceKeyOf(remote)]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[occurrenceKeyOf(remote)] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { calendarEventExceptionDao.upsert(it) }

            if (mergedList.toSet() != remoteExceptions.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.EVENT_EXCEPTIONS_FILE,
                    collectionJson.encodeToString(
                        ListSerializer(CalendarEventExceptionEntity.serializer()),
                        mergedList
                    ),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("EventExceptionSync: complete, ${mergedList.size} occurrence override(s) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("EventExceptionSync: remote event_exceptions.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("EventExceptionSync: failed to sync event exceptions: ${cause.message}", cause)
        }
    }

    private fun occurrenceKeyOf(exception: CalendarEventExceptionEntity): String =
        "${exception.blockId}|${exception.occurrenceDate}"

    private suspend fun reconcileCategories() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.CATEGORIES_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteCategories = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(CategoryEntity.serializer()), it) }
                .orEmpty()
            val localCategories = categoryDao.getCategoriesModifiedSince(0L)

            val merged = LinkedHashMap<String, CategoryEntity>()
            localCategories.forEach { merged[it.categoryId] = it }
            remoteCategories.forEach { remote ->
                val local = merged[remote.categoryId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.categoryId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { spaceRepository.ensureSpaceExists(it.spaceId) }
            mergedList.forEach { categoryDao.insertOrUpdateCategory(it) }

            if (mergedList.toSet() != remoteCategories.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.CATEGORIES_FILE,
                    collectionJson.encodeToString(ListSerializer(CategoryEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("CategorySync: complete, ${mergedList.size} categor(y/ies) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("CategorySync: remote categories.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("CategorySync: failed to sync categories: ${cause.message}", cause)
        }
    }

    private suspend fun reconcilePropertyTags() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.PROPERTY_TAGS_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteTags = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(PropertyTagEntity.serializer()), it) }
                .orEmpty()
            val localTags = propertyTagDao.getTagsModifiedSince(0L)

            val merged = LinkedHashMap<String, PropertyTagEntity>()
            localTags.forEach { merged[it.tagId] = it }
            remoteTags.forEach { remote ->
                val local = merged[remote.tagId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.tagId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { spaceRepository.ensureSpaceExists(it.spaceId) }
            mergedList.forEach { propertyTagDao.insertOrUpdateTag(it) }

            if (mergedList.toSet() != remoteTags.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.PROPERTY_TAGS_FILE,
                    collectionJson.encodeToString(ListSerializer(PropertyTagEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("PropertyTagSync: complete, ${mergedList.size} tag(s) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("PropertyTagSync: remote property_tags.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("PropertyTagSync: failed to sync property tags: ${cause.message}", cause)
        }
    }

    private suspend fun reconcileCustomProperties() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.CUSTOM_PROPERTIES_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteProperties = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(CustomPropertyEntity.serializer()), it) }
                .orEmpty()
            val localProperties = customPropertyDao.getPropertiesModifiedSince(0L)

            val merged = LinkedHashMap<String, CustomPropertyEntity>()
            localProperties.forEach { merged[it.propertyId] = it }
            remoteProperties.forEach { remote ->
                val local = merged[remote.propertyId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.propertyId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { spaceRepository.ensureSpaceExists(it.spaceId) }
            mergedList.forEach { customPropertyDao.insertOrUpdateProperty(it) }

            if (mergedList.toSet() != remoteProperties.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.CUSTOM_PROPERTIES_FILE,
                    collectionJson.encodeToString(ListSerializer(CustomPropertyEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("CustomPropertySync: complete, ${mergedList.size} propert(y/ies) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("CustomPropertySync: remote custom_properties.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("CustomPropertySync: failed to sync custom properties: ${cause.message}", cause)
        }
    }

    // API provider configs are few (one per ExternalAiProvider entry) and small, so they sync as a
    // single whole-file JSON blob, merged provider-by-provider by "last-write-wins" on updatedAt -
    // the same approach as folders/categories above. Deletions use a dedicated tombstone table
    // (rather than an in-place isDeleted flag) since the config itself lives in encrypted key/value
    // storage, not a Room table we can just flag a row on.
    private suspend fun reconcileApiConfigs() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.API_CONFIGS_FILE)
            val remoteEntriesByProvider = remoteJsonWithEtag?.first
                ?.let { collectionJson.decodeFromString(ListSerializer(ApiConfigSyncEntry.serializer()), it) }
                .orEmpty()
                .associateBy { it.provider }
            val localTombstonesByProvider = selfHostDeletedApiConfigDao.getAllTombstones().associateBy { it.provider }

            val mergedEntries = mutableListOf<ApiConfigSyncEntry>()

            for (provider in ExternalAiProvider.entries) {
                val localConfig = aiSettingsRepository.getProviderConfig(provider)
                val localTombstone = localTombstonesByProvider[provider.name]
                val localIsDeleted = localConfig == null ||
                    (localTombstone != null && localTombstone.deletedAt >= localConfig.updatedAt)
                val localUpdatedAt = maxOf(localConfig?.updatedAt ?: 0L, localTombstone?.deletedAt ?: 0L)
                val remoteEntry = remoteEntriesByProvider[provider.name]

                when {
                    remoteEntry != null && remoteEntry.updatedAt > localUpdatedAt -> {
                        if (remoteEntry.isDeleted) {
                            aiSettingsRepository.applyRemoteProviderConfigDeletion(provider, remoteEntry.updatedAt)
                        } else {
                            aiSettingsRepository.saveProviderConfig(
                                provider,
                                ExternalAiProviderConfig(
                                    apiKey = remoteEntry.apiKey,
                                    model = remoteEntry.model,
                                    baseUrl = remoteEntry.baseUrl,
                                    updatedAt = remoteEntry.updatedAt
                                )
                            )
                        }
                        mergedEntries += remoteEntry
                    }
                    !localIsDeleted -> mergedEntries += ApiConfigSyncEntry(
                        provider = provider.name,
                        apiKey = localConfig.apiKey,
                        model = localConfig.model,
                        baseUrl = localConfig.baseUrl,
                        updatedAt = localConfig.updatedAt
                    )
                    localTombstone != null -> mergedEntries += ApiConfigSyncEntry(
                        provider = provider.name,
                        apiKey = "",
                        model = "",
                        baseUrl = null,
                        updatedAt = localTombstone.deletedAt,
                        isDeleted = true
                    )
                }
            }

            if (mergedEntries.toSet() != remoteEntriesByProvider.values.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.API_CONFIGS_FILE,
                    collectionJson.encodeToString(ListSerializer(ApiConfigSyncEntry.serializer()), mergedEntries),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("ApiConfigSync: complete, ${mergedEntries.size} provider config(s) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("ApiConfigSync: remote api_configs.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("ApiConfigSync: failed to sync api configs: ${cause.message}", cause)
        }
    }

    // The bookmark category pill order is a single small list shared by every device, so it syncs as
    // one whole-file JSON blob resolved purely by "last-write-wins" on updatedAt. There is nothing to
    // merge element-by-element here: a half-merged ordering of two different drags would be an order
    // neither device asked for, so the newer drag simply wins outright.
    private suspend fun reconcileBookmarkCategoryOrder() {
        try {
            val remoteJsonWithEtag =
                webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.BOOKMARK_CATEGORY_ORDER_FILE)
            val remoteOrders = remoteJsonWithEtag?.first
                ?.let { collectionJson.decodeFromString(BookmarkCategoryOrderBySpace.serializer(), it) }

            if (remoteOrders != null) bookmarkCategoryOrderStore.applyRemoteOrders(remoteOrders)

            val mergedOrders = bookmarkCategoryOrderStore.getAllOrders()
            if (mergedOrders.ordersBySpaceId.isNotEmpty() && mergedOrders != remoteOrders) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.BOOKMARK_CATEGORY_ORDER_FILE,
                    collectionJson.encodeToString(BookmarkCategoryOrderBySpace.serializer(), mergedOrders),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("BookmarkCategoryOrderSync: complete, ${mergedOrders.ordersBySpaceId.size} space(s) ordered")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("BookmarkCategoryOrderSync: remote bookmark_category_order.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("BookmarkCategoryOrderSync: failed to sync bookmark category order: ${cause.message}", cause)
        }
    }

    // The favorites sidebar order is the same shape of problem as the bookmark pill order above: one
    // small list every device shares, so it travels as a whole-file JSON blob and the newer drag wins
    // outright. Note ids that no longer exist locally are harmless - they are simply never matched
    // when the sidebar sorts, and the next local drag rewrites the list without them.
    private suspend fun reconcileFavoriteNoteOrder() {
        try {
            val remoteJsonWithEtag =
                webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.FAVORITE_NOTE_ORDER_FILE)
            val remoteOrders = remoteJsonWithEtag?.first
                ?.let { collectionJson.decodeFromString(FavoriteNoteOrderBySpace.serializer(), it) }

            if (remoteOrders != null) favoriteNoteOrderStore.applyRemoteOrders(remoteOrders)

            val mergedOrders = favoriteNoteOrderStore.getAllOrders()
            if (mergedOrders.ordersBySpaceId.isNotEmpty() && mergedOrders != remoteOrders) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.FAVORITE_NOTE_ORDER_FILE,
                    collectionJson.encodeToString(FavoriteNoteOrderBySpace.serializer(), mergedOrders),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("FavoriteNoteOrderSync: complete, ${mergedOrders.ordersBySpaceId.size} space(s) ordered")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("FavoriteNoteOrderSync: remote favorite_note_order.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("FavoriteNoteOrderSync: failed to sync favorite note order: ${cause.message}", cause)
        }
    }

    private suspend fun reconcileChatSessions(manifest: SelfHostManifest): List<SelfHostManifestEntry> {
        val remoteEntriesById = manifest.entries
            .filter { it.entryType == SelfHostEntryType.CHAT_SESSION }
            .associateBy { it.entryId }
        val localSessionsById = chatSessionDao.getAllSessionsIncludingDeleted().associateBy { it.id }
        val candidateIds = remoteEntriesById.keys + localSessionsById.keys

        val entries = mutableListOf<SelfHostManifestEntry>()
        for (sessionId in candidateIds) {
            try {
                val entry = reconcileChatSession(sessionId, localSessionsById[sessionId], remoteEntriesById[sessionId])
                if (entry != null) entries += entry
            } catch (cause: Exception) {
                SelfHostSyncLog.e("ChatSessionSync: failed to reconcile $sessionId: ${cause.message}", cause)
                remoteEntriesById[sessionId]?.let { entries += it }
            }
        }
        SelfHostSyncLog.d("ChatSessionSync: complete, ${entries.size} session(s) reconciled")
        return entries
    }

    private suspend fun reconcileChatSession(
        sessionId: String,
        localSession: ChatSessionEntity?,
        remoteEntry: SelfHostManifestEntry?
    ): SelfHostManifestEntry? {
        val localUpdatedAt = localSession?.updatedAt ?: 0L

        if (remoteEntry == null) {
            return localSession?.let { uploadChatSession(it, ifMatchEtag = null) }
        }

        if (localUpdatedAt == remoteEntry.updatedAt) {
            return remoteEntry
        }

        if (localSession != null && !localSession.isDeleted && !remoteEntry.isDeleted) {
            return mergeChatSessionWithServerCopy(localSession)
        }

        if (localSession != null && localUpdatedAt > remoteEntry.updatedAt) {
            return uploadChatSession(localSession, ifMatchEtag = null)
        }

        // Remote is newer than what we have locally.
        if (remoteEntry.isDeleted) {
            chatSessionDao.softDeleteSession(sessionId, remoteEntry.updatedAt)
        } else {
            val remoteJson = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.chatSessionPath(sessionId))?.first
            val remoteSession = remoteJson?.let { collectionJson.decodeFromString(ChatSessionEntity.serializer(), it) }
            if (remoteSession != null) {
                spaceRepository.ensureSpaceExists(remoteSession.spaceId)
                chatSessionDao.upsertSession(remoteSession)
            }
        }
        com.emberr.domain.util.sync.ChatSyncEventBus.emitSessionChanged(sessionId)
        return remoteEntry
    }

    private suspend fun mergeChatSessionWithServerCopy(localSession: ChatSessionEntity): SelfHostManifestEntry {
        val (remoteJson, remoteEtag) = webDavSyncClient.downloadAndDecryptJsonWithEtag(
            WebDavSyncPaths.chatSessionPath(localSession.id)
        ) ?: return uploadChatSession(localSession, ifMatchEtag = null)
        val remoteSession = collectionJson.decodeFromString(ChatSessionEntity.serializer(), remoteJson)
        val sessionToKeep = ChatSessionMerge.mergeWithServerCopy(localSession, remoteSession)

        if (sessionToKeep != remoteSession) {
            uploadChatSession(sessionToKeep, remoteEtag)
        }

        val localSessionUnchangedDuringSync = chatSessionDao.getSession(localSession.id) == localSession
        if (sessionToKeep != localSession && localSessionUnchangedDuringSync) {
            spaceRepository.ensureSpaceExists(sessionToKeep.spaceId)
            chatSessionDao.upsertSession(sessionToKeep)
            com.emberr.domain.util.sync.ChatSyncEventBus.emitSessionChanged(sessionToKeep.id)
        }
        return chatSessionManifestEntry(sessionToKeep)
    }

    private suspend fun uploadChatSession(session: ChatSessionEntity, ifMatchEtag: String?): SelfHostManifestEntry {
        webDavSyncClient.uploadEncryptedJson(
            WebDavSyncPaths.chatSessionPath(session.id),
            collectionJson.encodeToString(ChatSessionEntity.serializer(), session),
            ifMatchEtag
        )
        return chatSessionManifestEntry(session)
    }

    private fun chatSessionManifestEntry(session: ChatSessionEntity) = SelfHostManifestEntry(
        entryId = session.id,
        entryType = SelfHostEntryType.CHAT_SESSION,
        spaceId = session.spaceId,
        updatedAt = session.updatedAt,
        isDeleted = session.isDeleted
    )

    private suspend fun reconcileNote(candidateId: String, remoteEntry: SelfHostManifestEntry?): ReconcileOutcome {
        if (remoteEntry?.isDeleted == true) {
            return applyRemoteTombstone(candidateId, remoteEntry)
        }

        return try {
            val isDaily = remoteEntry?.entryType == SelfHostEntryType.DAILY
            val remoteDateString = remoteEntry?.dateString
            val remoteSpaceId = remoteEntry?.spaceId

            val localMetadata = if (isDaily && remoteDateString != null && remoteSpaceId != null) {
                noteDao.getDailyNoteMetadata(remoteSpaceId, remoteDateString)
            } else {
                noteDao.getNoteById(candidateId)
            }

            val remoteJsonWithEtag = when {
                remoteEntry == null -> null
                isDaily -> {
                    val dateString = remoteDateString ?: localMetadata?.dateString
                    val spaceId = remoteSpaceId ?: localMetadata?.spaceId
                    if (dateString != null && spaceId != null) {
                        webDavSyncClient.downloadDailyWithEtag(spaceId, dateString)
                    } else {
                        null
                    }
                }
                else -> webDavSyncClient.downloadNoteWithEtag(localMetadata?.noteId ?: candidateId)
            }
            val remoteJson = remoteJsonWithEtag?.first
            // Capture the ETag from the download. The final push must condition on this exact ETag
            // to detect concurrent writes from other devices and prevent silent overwrites.
            val downloadTimeEtag = remoteJsonWithEtag?.second
            val remoteOps = remoteJson?.let { NoteJsonParser.parseJsonToDatabaseOperations(it) }

            val newerMetadata = pickNewerMetadata(localMetadata, remoteOps?.metadataUpsert)
                ?: return ReconcileOutcome.UNCHANGED

            val targetSpaceId = localMetadata?.spaceId ?: newerMetadata.spaceId
            val noteId = localMetadata?.noteId
                ?: adoptableNoteIdInSpace(remoteOps?.metadataUpsert?.noteId, targetSpaceId)
                ?: candidateId.takeUnless { isDaily }
                ?: UUID.randomUUID().toString()

            val mergedMetadata = newerMetadata.copy(
                noteId = noteId,
                spaceId = targetSpaceId,
                kind = localMetadata?.kind ?: newerMetadata.kind
            )

            val localBlocks = blockDao.getAllBlocksForNoteIncludingDeleted(noteId).filter { block ->
                val belongsToNote = block.noteId == noteId
                if (!belongsToNote) {
                    SelfHostSyncLog.e(
                        "SelfHostSyncEngine: query for note $noteId returned block ${block.blockId} " +
                                "belonging to note ${block.noteId}, discarding it"
                    )
                }
                belongsToNote
            }
            val remoteUpserts = remoteOps?.blockUpserts.orEmpty().map { it.copy(noteId = noteId) }
            val remoteDeletions = remoteOps?.blockDeletions.orEmpty()
            val mergedBlocks = NoteMergeHelper.mergeBlocks(
                noteId = noteId,
                localBlocks = localBlocks,
                remoteUpserts = remoteUpserts,
                remoteDeletions = remoteDeletions
            )

            spaceRepository.ensureSpaceExists(mergedMetadata.spaceId)
            noteDao.insertOrUpdateMetadata(mergedMetadata.copy(filePath = ""))
            blockDao.insertOrUpdateBlocks(mergedBlocks)
            remoteOps?.canvas?.let { remoteCanvas ->
                canvasRepository.applyRemoteCanvasWhileSyncLockHeld(noteId, remoteCanvas)
            }

            // Explicitly sort blocks by displayOrder.
            // This ensures live UI caches reflect the correct order immediately,
            // without waiting for a fresh database read.
            val refreshedContent = NoteContent(
                blocks = mergedBlocks.sortedBy { it.displayOrder }.mapNotNull { entity ->
                    try {
                        blockJson.decodeFromString(NoteBlockSerializer, entity.blockDataJson)
                    } catch (cause: Exception) {
                        SelfHostSyncLog.e(
                            "SelfHostSyncEngine: could not decode block ${entity.blockId} while refreshing note cache",
                            cause
                        )
                        null
                    }
                }
            )

            if (mergedMetadata.isDaily) {
                mergedMetadata.dateString?.let { dateString ->
                    noteRepository.refreshDailyNoteCache(
                        mergedMetadata.spaceId,
                        dateString,
                        refreshedContent
                    )
                }
            }
            noteRepository.refreshNoteContentCache(noteId, refreshedContent)
            noteRepository.refreshProjectionsForNote(mergedMetadata, refreshedContent.blocks)

            // Emit an event so open editors immediately refresh title, cover, and pinned states.
            // This happens before pushing, since local database/cache merges are already committed.
            com.emberr.domain.util.sync.SyncEventBus.emitSyncCompleted(
                if (mergedMetadata.isDaily) mergedMetadata.dateString ?: noteId else noteId,
                mergedMetadata.spaceId
            )

            adoptRemoteEmbeddingsIfWinning(
                noteId = noteId,
                spaceId = mergedMetadata.spaceId,
                localMetadata = localMetadata,
                remoteMetadata = remoteOps?.metadataUpsert,
                remoteEmbeddedBlocks = remoteOps?.embeddedBlocks.orEmpty()
            )

            pushMergedNote(mergedMetadata, mergedBlocks, downloadTimeEtag)

            // Only update `selfHostSyncedAt` if the push succeeds.
            // If the push fails, the note remains a sync candidate for the next cycle.
            noteDao.updateSelfHostSyncedAt(noteId, mergedMetadata.updatedAt)
            ReconcileOutcome.SYNCED
        } catch (cause: WebDavConflictException) {
            ReconcileOutcome.CONFLICT_SKIPPED
        }
    }

    // `isDeleted=true` means another device permanently deleted this note.
    // We use tombstones to ensure devices with a local copy delete it instead of re-uploading it.
    // No content download is needed since the note is being removed.
    private suspend fun applyRemoteTombstone(candidateId: String, remoteEntry: SelfHostManifestEntry): ReconcileOutcome {
        val isDaily = remoteEntry.entryType == SelfHostEntryType.DAILY
        val localMetadata = if (isDaily && remoteEntry.dateString != null) {
            noteDao.getDailyNoteMetadata(remoteEntry.spaceId, remoteEntry.dateString)
                ?: findLocalNoteInSpace(candidateId, remoteEntry.spaceId)
        } else {
            noteDao.getNoteById(candidateId)
        }

        if (localMetadata == null) {
            return ReconcileOutcome.UNCHANGED
        }

        if (localMetadata.updatedAt > remoteEntry.updatedAt) {
            return reconcileNote(localMetadata.noteId, remoteEntry = null)
        }

        val noteId = localMetadata.noteId
        noteRepository.hardDeleteLocalNote(noteId)
        SelfHostSyncLog.d("TextSync: applied remote tombstone for $noteId, hard-deleted local copy")
        com.emberr.domain.util.sync.SyncEventBus.emitSyncCompleted(
            if (isDaily) remoteEntry.dateString ?: noteId else noteId,
            localMetadata.spaceId
        )
        return ReconcileOutcome.SYNCED
    }

    // A note's embeddings are only meaningful on a device that has actually run them locally, so a
    // device that never opened this note (and thus never indexed it) has nothing of its own to lose
    // by adopting whatever another device already computed. If the remote note content just won this
    // merge, its embeddings describe that exact winning content, so we adopt those too rather than
    // leaving this device's block_metadata rows out of date until it happens to re-index locally.
    private suspend fun adoptRemoteEmbeddingsIfWinning(
        noteId: String,
        spaceId: String,
        localMetadata: NoteMetadataEntity?,
        remoteMetadata: NoteMetadataEntity?,
        remoteEmbeddedBlocks: List<EmbeddedBlockPayload>
    ) {
        if (remoteEmbeddedBlocks.isEmpty()) return
        if (settingsManager.isAiFeaturesDisabled()) return

        val remoteMetadataWon = remoteMetadata != null &&
            (localMetadata == null || remoteMetadata.updatedAt > localMetadata.updatedAt)
        val hasLocalEmbeddings = database.vectorStoreQueries.getBlocksForNote(noteId).executeAsList().isNotEmpty()

        if (!remoteMetadataWon && hasLocalEmbeddings) return

        database.transaction {
            database.vectorStoreQueries.deleteBlocksForNote(noteId)
            remoteEmbeddedBlocks.forEach { block ->
                database.vectorStoreQueries.insertMetadata(
                    block_id = block.blockId,
                    note_id = noteId,
                    space_id = spaceId,
                    chunk_text = block.chunkText,
                    embedding = block.embedding
                )
            }
        }
        SelfHostSyncLog.d("TextSync: adopted ${remoteEmbeddedBlocks.size} synced embedding(s) for note $noteId")
    }

    private suspend fun pushMergedNote(
        metadata: NoteMetadataEntity,
        blocks: List<NoteBlockEntity>,
        ifMatchEtag: String?
    ) {
        val embeddedBlocks = database.vectorStoreQueries.getBlocksForNote(metadata.noteId).executeAsList()
            .map { EmbeddedBlockPayload(blockId = it.block_id, chunkText = it.chunk_text, embedding = it.embedding) }
        val canvas = canvasRepository.loadCanvasIncludingDeleted(metadata.noteId)
        val json = NoteJsonCompiler.compileNoteToJson(metadata, blocks, embeddedBlocks, canvas)

        if (metadata.isDaily) {
            webDavSyncClient.uploadDaily(
                metadata.spaceId,
                metadata.dateString ?: metadata.noteId,
                json,
                ifMatchEtag
            )
        } else {
            webDavSyncClient.uploadNote(metadata.noteId, json, ifMatchEtag)
        }
    }

    private suspend fun findLocalNoteInSpace(noteId: String, spaceId: String): NoteMetadataEntity? =
        noteDao.getNoteById(noteId)?.takeIf { it.spaceId == spaceId }

    private suspend fun adoptableNoteIdInSpace(noteId: String?, spaceId: String): String? {
        if (noteId == null) return null
        val spaceHoldingThatId = noteDao.getNoteById(noteId)?.spaceId

        return if (spaceHoldingThatId == null || spaceHoldingThatId == spaceId) noteId else null
    }

    private fun manifestEntryForTombstone(tombstone: SelfHostDeletedNoteEntity): SelfHostManifestEntry {
        val isDailyTombstone = tombstone.isDaily && tombstone.dateString != null
        return SelfHostManifestEntry(
            entryId = if (isDailyTombstone) {
                dailyManifestEntryId(tombstone.spaceId, tombstone.dateString.orEmpty())
            } else {
                tombstone.noteId
            },
            entryType = if (tombstone.isDaily) SelfHostEntryType.DAILY else SelfHostEntryType.NOTE,
            spaceId = tombstone.spaceId,
            updatedAt = tombstone.deletedAt,
            dateString = tombstone.dateString,
            isDeleted = true
        )
    }

    private fun dailyManifestEntryId(spaceId: String, dateString: String): String =
        "daily_${spaceId}_$dateString"

    private fun manifestEntryIdFor(note: NoteMetadataEntity): String =
        if (note.isDaily && note.dateString != null) {
            dailyManifestEntryId(note.spaceId, note.dateString)
        } else {
            note.noteId
        }

    private fun pickNewerMetadata(local: NoteMetadataEntity?, remote: NoteMetadataEntity?): NoteMetadataEntity? {
        return when {
            remote == null -> local
            local == null -> remote
            remote.updatedAt > local.updatedAt -> remote.withNewerDetailsFrom(local)
            else -> local.withNewerDetailsFrom(remote)
        }
    }

    // Treat a missing manifest as completely empty.
    // If a manifest exists but fails to decode, allow the error to propagate.
    // This forces a retry instead of treating corrupted data as empty and orphaning notes.
    private suspend fun downloadManifest(): SelfHostManifest = downloadManifestWithEtag().first

    private suspend fun downloadManifestWithEtag(): Pair<SelfHostManifest, String?> {
        val result = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.MANIFEST_FILE)
            ?: return SelfHostManifest() to null
        val (raw, etag) = result
        val manifest = try {
            decodeSelfHostManifest(raw)
        } catch (cause: SelfHostManifestTooNewException) {
            SelfHostSyncLog.e("Manifest schema version ${cause.serverSchemaVersion} is newer than this app understands, pausing sync")
            SelfHostUpdateRequiredSignal.markUpdateRequired()
            throw cause
        }
        return manifest to etag
    }

    private suspend fun uploadManifest(
        previousManifest: SelfHostManifest,
        noteIdsStillWaitingToUpload: Set<String> = emptySet(),
        chatSessionEntries: List<SelfHostManifestEntry> = emptyList(),
        previousManifestEtag: String? = null,
        attempt: Int = 0
    ) {
        // Preserve existing tombstones in the manifest.
        // This ensures offline or lagging devices eventually see the deletion and don't
        // accidentally resurrect deleted notes.
        val previousTombstones = previousManifest.entries.filter { it.isDeleted }
        val localTombstones = selfHostDeletedNoteDao.getAllTombstones()
        val localNotes = noteDao.getAllNotesForBackup()
        val mediaFileNamesByNoteId = mediaReferenceIndex.loadReferencedFileNamesByNoteId()
        val localEntries = localNotes.map { note ->
            SelfHostManifestEntry(
                entryId = manifestEntryIdFor(note),
                entryType = if (note.isDaily) SelfHostEntryType.DAILY else SelfHostEntryType.NOTE,
                spaceId = note.spaceId,
                updatedAt = note.updatedAt,
                dateString = note.dateString.takeIf { note.isDaily },
                mediaFileNames = mediaFileNamesByNoteId[note.noteId].orEmpty()
            )
        }
        val entryIdsStillWaitingToUpload = localNotes
            .filter { it.noteId in noteIdsStillWaitingToUpload }
            .map { manifestEntryIdFor(it) }
            .toSet()

        val localTombstoneEntriesByNoteId = localTombstones.associate { it.noteId to manifestEntryForTombstone(it) }
        val replacedTombstoneIds = tombstoneIdsReplacedByNewerNotes(
            tombstones = previousTombstones + localTombstoneEntriesByNoteId.values,
            serverEntries = previousManifest.entries,
            localEntries = localEntries,
            entryIdsStillWaitingToUpload = entryIdsStillWaitingToUpload
        )
        val (replacedLocalTombstones, activeLocalTombstones) = localTombstones.partition { tombstone ->
            localTombstoneEntriesByNoteId.getValue(tombstone.noteId).entryId in replacedTombstoneIds
        }
        for (tombstone in replacedLocalTombstones) {
            selfHostDeletedNoteDao.deleteTombstone(tombstone.noteId)
            SelfHostSyncLog.d("TextSync: note ${tombstone.noteId} was edited after it was deleted, keeping the edit")
        }

        // Perform a one-time remote file cleanup for notes deleted by this device.
        // This prevents orphaned files from wasting server storage.
        // Success is tracked to avoid repeating, and failures are retried next cycle.
        for (tombstone in activeLocalTombstones) {
            if (tombstone.remoteFileDeleted) continue
            try {
                val remotePath = if (tombstone.isDaily) {
                    WebDavSyncPaths.dailyPath(tombstone.spaceId, tombstone.dateString ?: tombstone.noteId)
                } else {
                    WebDavSyncPaths.notePath(tombstone.noteId)
                }
                webDavSyncClient.deleteFile(remotePath)
                selfHostDeletedNoteDao.markRemoteFileDeleted(tombstone.noteId)
            } catch (cause: Exception) {
                SelfHostSyncLog.e(
                    "TextSync: failed to delete remote file for permanently-deleted note ${tombstone.noteId}, will retry next cycle",
                    cause
                )
            }
        }

        val mergedTombstonesById = LinkedHashMap<String, SelfHostManifestEntry>()
        previousTombstones
            .filter { it.entryId !in replacedTombstoneIds }
            .forEach { mergedTombstonesById[it.entryId] = it }
        activeLocalTombstones.map { localTombstoneEntriesByNoteId.getValue(it.noteId) }.forEach { entry ->
            val existing = mergedTombstonesById[entry.entryId]
            if (existing == null || entry.updatedAt > existing.updatedAt) {
                mergedTombstonesById[entry.entryId] = entry
            }
        }
        val tombstoneIds = mergedTombstonesById.keys
        val noteEntries = mergeNoteManifestEntries(
            serverEntries = previousManifest.entries,
            localEntries = localEntries,
            entryIdsStillWaitingToUpload = entryIdsStillWaitingToUpload,
            tombstoneIds = tombstoneIds
        )
        val preservedMediaEntries = previousManifest.entries.filter { it.entryType == SelfHostEntryType.MEDIA }
        val newManifest = SelfHostManifest(
            entries = noteEntries + mergedTombstonesById.values + preservedMediaEntries + chatSessionEntries
        )

        // The If-Match ETag check prevents concurrent manifest uploads from overwriting each other.
        // On a conflict (412 status), we abort and retry with a fresh download.
        try {
            webDavSyncClient.uploadEncryptedJson(
                WebDavSyncPaths.MANIFEST_FILE,
                manifestJson.encodeToString(SelfHostManifest.serializer(), newManifest),
                previousManifestEtag
            )
        } catch (cause: WebDavConflictException) {
            if (attempt >= MAX_MANIFEST_UPLOAD_RETRIES) throw cause
            val (freshManifest, freshEtag) = downloadManifestWithEtag()
            uploadManifest(
                freshManifest,
                noteIdsStillWaitingToUpload,
                chatSessionEntries,
                freshEtag,
                attempt + 1
            )
        }
    }
}