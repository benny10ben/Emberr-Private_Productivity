package com.emberr.domain.selfhost.sync

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.BlockDao
import com.emberr.data.local.room.CalendarEventExceptionDao
import com.emberr.data.local.room.CalendarEventExceptionEntity
import com.emberr.data.local.room.CategoryDao
import com.emberr.data.local.room.CategoryEntity
import com.emberr.data.local.room.ChatSessionDao
import com.emberr.data.local.room.ChatSessionEntity
import com.emberr.data.local.room.FolderDao
import com.emberr.data.local.room.FolderEntity
import com.emberr.data.local.room.NoteBlockEntity
import com.emberr.data.local.room.NoteDao
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.data.local.room.SelfHostDeletedApiConfigDao
import com.emberr.data.local.room.SelfHostDeletedNoteDao
import com.emberr.data.local.room.PLACEHOLDER_SPACE_UPDATED_AT
import com.emberr.data.local.room.SpaceDao
import com.emberr.data.local.room.SpaceEntity
import com.emberr.data.local.room.TagDao
import com.emberr.data.local.room.TagEntity
import com.emberr.domain.ai.external.AiSettingsRepository
import com.emberr.domain.ai.external.ExternalAiProvider
import com.emberr.domain.ai.external.ExternalAiProviderConfig
import com.emberr.domain.model.BookmarkCategoryOrderBySpace
import com.emberr.domain.model.FavoriteNoteOrderBySpace
import com.emberr.domain.repository.BookmarkCategoryOrderStore
import com.emberr.domain.repository.FavoriteNoteOrderStore
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.media.MediaReferenceIndex
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.selfhost.merge.NoteMergeHelper
import com.emberr.domain.space.SpaceRepository
import com.emberr.domain.selfhost.translation.EmbeddedBlockPayload
import com.emberr.domain.selfhost.translation.NoteJsonCompiler
import com.emberr.domain.selfhost.translation.NoteJsonParser
import com.emberr.domain.selfhost.webdav.WebDavConfigurationException
import com.emberr.domain.selfhost.webdav.WebDavConflictException
import com.emberr.domain.selfhost.webdav.WebDavSyncClient
import com.emberr.domain.selfhost.webdav.WebDavSyncPaths
import com.emberr.domain.sync.MediaTransferPhase
import com.emberr.domain.sync.MediaTransferStatusBus
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.sync.withSyncCoordinatorOrSkip
import com.emberr.domain.vault.VaultMirrorTrigger
import com.emberr.database.EmberrDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

sealed class SelfHostSyncResult {
    data class Success(val notesSynced: Int, val conflicts: Int) : SelfHostSyncResult()
    data class Failure(val cause: Throwable) : SelfHostSyncResult()
    data object AlreadyInProgress : SelfHostSyncResult()
    data object NotConfigured : SelfHostSyncResult()
}

class SelfHostSyncEngine(
    private val webDavSyncClient: WebDavSyncClient,
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
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
    private val mediaReferenceIndex: MediaReferenceIndex
) {

    private enum class ReconcileOutcome { SYNCED, CONFLICT_SKIPPED, LOCK_BUSY, UNCHANGED }

    private val mutex = Mutex()
    private val manifestJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val blockJson = Json { ignoreUnknownKeys = true }
    private val collectionJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaRetryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        const val MAX_MANIFEST_UPLOAD_RETRIES = 3

        // Unreferenced media files are candidates for deletion.
        // We wait a full day (grace period) to allow other devices to sync and claim them,
        // preventing accidental deletion if another device just hasn't synced yet.
        const val MEDIA_ORPHAN_GRACE_PERIOD_MS = 24L * 60 * 60 * 1000
    }

    // wire this up in the app ui so let users know if any self host sync is pending
    suspend fun hasPendingLocalChanges(): Boolean =
        noteDao.getNotesNeedingSelfHostSync().isNotEmpty()

    // The local mutex prevents concurrent background syncs, but doesn't block local editor saves.
    // To prevent saves from reading incomplete data mid-sync, we use `SyncCoordinator.mutex`
    // to lock each note individually inside `runSyncLocked`.
    suspend fun runSync(): SelfHostSyncResult {
        SelfHostSyncLog.d("runSync() called")
        if (!mutex.tryLock()) {
            SelfHostSyncLog.d("runSync() skipped, a sync is already in progress")
            return SelfHostSyncResult.AlreadyInProgress
        }
        return try {
            runSyncLocked()
        } finally {
            mutex.unlock()
        }
    }

    suspend fun syncMedia(): SelfHostSyncResult {
        SelfHostSyncLog.d("syncMedia() called")
        if (!mutex.tryLock()) {
            SelfHostSyncLog.d("syncMedia() skipped, a sync is already in progress")
            return SelfHostSyncResult.AlreadyInProgress
        }
        return try {
            // Media sync only touches files on disk and the remote manifest, never note data, so it
            // doesn't need SyncCoordinator.mutex - this class's own mutex above already prevents two
            // self-host sync passes from overlapping. Holding SyncCoordinator.mutex here would instead
            // freeze every local editor save for as long as a large attachment takes to transfer.
            syncMediaLocked()
        } finally {
            mutex.unlock()
        }
    }

    // Lets UI explicitly retry one specific file on demand (e.g. a "tap to retry" affordance on a
    // failed block) rather than only waiting for the next scheduled/foreground-polled media sync.
    // Fire-and-forget and independent of the mutex above, mirroring SyncRepositoryImpl's LAN
    // equivalent, so a single retry tap can't block on or be blocked by a whole sync pass.
    fun retryMediaDownload(fileName: String) {
        val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
        if (file.exists()) return
        file.parentFile?.mkdirs()
        mediaRetryScope.launch {
            MediaTransferStatusBus.markStarted(fileName, MediaTransferPhase.DOWNLOADING)
            var succeeded = false
            try {
                succeeded = webDavSyncClient.downloadMediaToFile(fileName, file)
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
        if (!mutex.tryLock()) {
            SelfHostSyncLog.d("runBaselineSync() skipped, a sync is already in progress")
            return SelfHostSyncResult.AlreadyInProgress
        }
        return try {
            val textResult = runSyncLocked()

            if (textResult is SelfHostSyncResult.Success) {
                try {
                    // Media sync only touches files on disk and the remote manifest, never note data,
                    // so it doesn't need SyncCoordinator.mutex - holding it here would otherwise freeze
                    // every local editor save for as long as a large attachment takes to transfer.
                    when (val mediaResult = syncMediaLocked()) {
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

            textResult
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun syncMediaLocked(): SelfHostSyncResult {
        return try {
            webDavSyncClient.ensureRemoteLayoutExists()

            val (manifest, manifestEtag) = downloadManifestWithEtag()
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
            val toDownload = remoteMediaFileNames - existingLocalFileNames
            SelfHostSyncLog.d("MediaSync: ${toUpload.size} to upload, ${toDownload.size} to download")

            var uploadedCount = 0
            var failedCount = 0
            val successfullyUploaded = mutableSetOf<String>()

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
                    webDavSyncClient.uploadMedia(fileName, file)
                    uploadSucceeded = true
                    successfullyUploaded.add(fileName)
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
            for (fileName in toDownload) {
                SelfHostSyncLog.d("MediaSync: Downloading missing local file $fileName")
                val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                file.parentFile?.mkdirs()
                MediaTransferStatusBus.markStarted(fileName, MediaTransferPhase.DOWNLOADING)
                var downloadSucceeded = false
                try {
                    val downloaded = webDavSyncClient.downloadMediaToFile(fileName, file)
                    if (!downloaded) {
                        SelfHostSyncLog.d("MediaSync: $fileName is listed in the manifest but missing on the server, skipping")
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
            val orphanedFileNames = manifestMediaEntries
                .filter { it.entryId !in referencedFileNames }
                .filter { entry -> (nowMs - (entry.orphanedAt ?: nowMs)) > MEDIA_ORPHAN_GRACE_PERIOD_MS }
                .map { it.entryId }
                .toSet()

            var deletedCount = 0
            for (fileName in orphanedFileNames) {
                try {
                    webDavSyncClient.deleteFile(WebDavSyncPaths.mediaPath(fileName))
                    deletedCount++
                    SelfHostSyncLog.d("MediaSync: deleted orphaned remote $fileName (unreferenced for over ${MEDIA_ORPHAN_GRACE_PERIOD_MS / 3_600_000}h)")
                } catch (cause: Exception) {
                    SelfHostSyncLog.e("MediaSync Error: failed to delete orphaned $fileName: ${cause.message}", cause)
                }
            }

            uploadMediaManifestEntries(
                manifest,
                (remoteMediaFileNames + successfullyUploaded) - orphanedFileNames,
                referencedFileNames,
                manifestEtag
            )

            SelfHostSyncLog.d(
                "MediaSync: complete, uploaded=$uploadedCount downloaded=$downloadedCount " +
                        "deleted=$deletedCount failed=$failedCount"
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

    private suspend fun uploadMediaManifestEntries(
        previousManifest: SelfHostManifest,
        mediaFileNames: Set<String>,
        referencedFileNames: Set<String>,
        previousManifestEtag: String? = null,
        attempt: Int = 0
    ) {
        val nonMediaEntries = previousManifest.entries.filter { it.entryType != SelfHostEntryType.MEDIA }
        val previousMediaEntriesById = previousManifest.entries
            .filter { it.entryType == SelfHostEntryType.MEDIA }
            .associateBy { it.entryId }
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val mediaEntries = mediaFileNames.map { fileName ->
            val previous = previousMediaEntriesById[fileName]
            val isReferenced = fileName in referencedFileNames
            SelfHostManifestEntry(
                entryId = fileName,
                entryType = SelfHostEntryType.MEDIA,
                updatedAt = previous?.updatedAt ?: nowMs,
                orphanedAt = if (isReferenced) null else (previous?.orphanedAt ?: nowMs)
            )
        }
        val newManifest = SelfHostManifest(entries = nonMediaEntries + mediaEntries)

        try {
            webDavSyncClient.uploadEncryptedJson(
                WebDavSyncPaths.MANIFEST_FILE,
                manifestJson.encodeToString(SelfHostManifest.serializer(), newManifest),
                previousManifestEtag
            )
        } catch (cause: WebDavConflictException) {
            if (attempt >= MAX_MANIFEST_UPLOAD_RETRIES) {
                SelfHostSyncLog.e(
                    "MediaSync: manifest upload conflict-skipped after $attempt retries, deferring to next cycle",
                    cause
                )
                return
            }
            val (freshManifest, freshEtag) = downloadManifestWithEtag()
            uploadMediaManifestEntries(freshManifest, mediaFileNames, referencedFileNames, freshEtag, attempt + 1)
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
            val (manifest, manifestEtag) = downloadManifestWithEtag()

            // Sync candidacy is determined per note using its `selfHostSyncedAt` value.
            // If a local row doesn't exist (e.g., a new note or wiped data), it defaults to 0
            // and safely syncs as a new entry.
            val remoteTextEntries = manifest.entries.filter { it.entryType != SelfHostEntryType.MEDIA }
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

            if (withSyncCoordinatorOrSkip { reconcileSpaces() } == null) {
                SelfHostSyncLog.d("TextSync: spaces skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }

            var syncedCount = 0
            var conflictCount = 0
            var skippedBusyCount = 0
            val conflictedNoteIds = mutableSetOf<String>()

            for ((noteId, remoteEntry) in candidates) {
                // Lock each note individually for reconciliation.
                // If the lock is busy (e.g., user is editing), we skip it so other notes aren't delayed.
                // Skipped notes will be retried on the next sync pass.
                val outcome = withSyncCoordinatorOrSkip {
                    reconcileNote(noteId, remoteEntry = remoteEntry)
                } ?: run {
                    SelfHostSyncLog.d("TextSync: note=$noteId skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
                    ReconcileOutcome.LOCK_BUSY
                }
                SelfHostSyncLog.d("TextSync: note=$noteId outcome=$outcome")
                when (outcome) {
                    ReconcileOutcome.SYNCED -> syncedCount++
                    ReconcileOutcome.CONFLICT_SKIPPED -> { conflictCount++; conflictedNoteIds += noteId }
                    ReconcileOutcome.LOCK_BUSY -> skippedBusyCount++
                    ReconcileOutcome.UNCHANGED -> Unit
                }
            }

            if (withSyncCoordinatorOrSkip { reconcileFolders() } == null) {
                SelfHostSyncLog.d("TextSync: folders skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncCoordinatorOrSkip { reconcileTags() } == null) {
                SelfHostSyncLog.d("TextSync: tags skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncCoordinatorOrSkip { reconcileCategories() } == null) {
                SelfHostSyncLog.d("TextSync: categories skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncCoordinatorOrSkip { reconcileEventExceptions() } == null) {
                SelfHostSyncLog.d("TextSync: event exceptions skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncCoordinatorOrSkip { reconcileApiConfigs() } == null) {
                SelfHostSyncLog.d("TextSync: api configs skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncCoordinatorOrSkip { reconcileBookmarkCategoryOrder() } == null) {
                SelfHostSyncLog.d("TextSync: bookmark category order skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            if (withSyncCoordinatorOrSkip { reconcileFavoriteNoteOrder() } == null) {
                SelfHostSyncLog.d("TextSync: favorite note order skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
            }
            val chatSessionEntries = withSyncCoordinatorOrSkip { reconcileChatSessions(manifest) } ?: run {
                SelfHostSyncLog.d("TextSync: chat sessions skipped this cycle, SyncCoordinator.mutex busy - will retry next trigger")
                manifest.entries.filter { it.entryType == SelfHostEntryType.CHAT_SESSION }
            }

            // Always publish the manifest, even if some individual notes had conflicts.
            // Conflicted notes retain their downloaded state until locally resolved and pushed.
            // This runs without locks to read a fresh, unlocked database snapshot.
            uploadManifest(
                previousManifest = manifest,
                conflictedNoteIds = conflictedNoteIds,
                chatSessionEntries = chatSessionEntries,
                previousManifestEtag = manifestEtag
            )

            // Update the global last sync timestamp purely for UI and polling checks.
            // It's safe to advance this even if some notes had conflicts.
            settingsManager.saveSelfHostLastSyncTimestamp(syncStartTimestamp)

            SelfHostSyncLog.d(
                "TextSync: complete, synced=$syncedCount conflicts=$conflictCount skippedBusy=$skippedBusyCount"
            )
            SelfHostSyncResult.Success(notesSynced = syncedCount, conflicts = conflictCount)
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

            if (mergedList.toSet() != localSpaces.toSet()) {
                VaultMirrorTrigger.requestFullRefresh()
            }

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

    // Folders, tags, and categories sync as single encrypted JSON files since they are small.
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

            // Folders decide the vault's directory layout, so a changed folder can move any note's
            // file. Only re-export when the merge actually changed something locally.
            if (mergedList.toSet() != localFolders.toSet()) {
                VaultMirrorTrigger.requestFullRefresh()
            }

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

    private suspend fun reconcileTags() {
        try {
            val remoteJsonWithEtag = webDavSyncClient.downloadAndDecryptJsonWithEtag(WebDavSyncPaths.TAGS_FILE)
            val remoteJson = remoteJsonWithEtag?.first
            val remoteTags = remoteJson
                ?.let { collectionJson.decodeFromString(ListSerializer(TagEntity.serializer()), it) }
                .orEmpty()
            val localTags = tagDao.getTagsModifiedSince(0L)

            val merged = LinkedHashMap<String, TagEntity>()
            localTags.forEach { merged[it.tagId] = it }
            remoteTags.forEach { remote ->
                val local = merged[remote.tagId]
                if (local == null || remote.updatedAt >= local.updatedAt) {
                    merged[remote.tagId] = remote
                }
            }
            val mergedList = merged.values.toList()
            mergedList.forEach { spaceRepository.ensureSpaceExists(it.spaceId) }
            mergedList.forEach { tagDao.insertOrUpdateTag(it) }

            if (mergedList.toSet() != remoteTags.toSet()) {
                webDavSyncClient.uploadEncryptedJson(
                    WebDavSyncPaths.TAGS_FILE,
                    collectionJson.encodeToString(ListSerializer(TagEntity.serializer()), mergedList),
                    remoteJsonWithEtag?.second
                )
            }
            SelfHostSyncLog.d("TagSync: complete, ${mergedList.size} tag(s) reconciled")
        } catch (cause: WebDavConflictException) {
            SelfHostSyncLog.d("TagSync: remote tags.json changed concurrently, will retry next cycle")
        } catch (cause: Exception) {
            SelfHostSyncLog.e("TagSync: failed to sync tags: ${cause.message}", cause)
        }
    }

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

    // API provider configs are few (one per ExternalAiProvider entry) and small, so they sync as a
    // single whole-file JSON blob, merged provider-by-provider by "last-write-wins" on updatedAt -
    // the same approach as folders/tags/categories above. Deletions use a dedicated tombstone table
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

    // Each chat session is its own encrypted file on the server (mirroring notes), tracked as a
    // CHAT_SESSION entry in the same manifest notes use. Unlike notes there is no block-level merge -
    // a session's message list is replaced wholesale by whichever side has the newer `updatedAt`,
    // since two devices editing the exact same conversation at the same instant is not a realistic
    // case worth the complexity full note merging needs.
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

        if (remoteEntry == null || localUpdatedAt > remoteEntry.updatedAt) {
            if (localSession == null) return null
            webDavSyncClient.uploadEncryptedJson(
                WebDavSyncPaths.chatSessionPath(sessionId),
                collectionJson.encodeToString(ChatSessionEntity.serializer(), localSession),
                null
            )
            return SelfHostManifestEntry(
                entryId = sessionId,
                entryType = SelfHostEntryType.CHAT_SESSION,
                spaceId = localSession.spaceId,
                updatedAt = localSession.updatedAt,
                isDeleted = localSession.isDeleted
            )
        }

        if (localUpdatedAt == remoteEntry.updatedAt) {
            return remoteEntry
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
                spaceId = targetSpaceId
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

            // Explicitly sort blocks by displayOrder.
            // This ensures live UI caches reflect the correct order immediately,
            // without waiting for a fresh database read.
            val refreshedContent = NoteContent(
                blocks = mergedBlocks.sortedBy { it.displayOrder }.mapNotNull { entity ->
                    try {
                        blockJson.decodeFromString(NoteBlock.serializer(), entity.blockDataJson)
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
            VaultMirrorTrigger.requestNoteRefresh(noteId)

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
            // The local note was edited after the remote tombstone was created.
            // We preserve the live edit; it will be pushed and clear the tombstone on the next sync.
            return ReconcileOutcome.UNCHANGED
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
        val json = NoteJsonCompiler.compileNoteToJson(metadata, blocks, embeddedBlocks)

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
            remote.updatedAt > local.updatedAt -> remote
            else -> local
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
        return manifestJson.decodeFromString(SelfHostManifest.serializer(), raw) to etag
    }

    private suspend fun uploadManifest(
        previousManifest: SelfHostManifest,
        conflictedNoteIds: Set<String> = emptySet(),
        chatSessionEntries: List<SelfHostManifestEntry> = emptyList(),
        previousManifestEtag: String? = null,
        attempt: Int = 0
    ) {
        // Preserve all existing tombstones in the manifest forever.
        // This ensures offline or lagging devices eventually see the deletion and don't
        // accidentally resurrect deleted notes.
        val previousTombstones = previousManifest.entries.filter { it.isDeleted }
        val localTombstones = selfHostDeletedNoteDao.getAllTombstones()

        // Perform a one-time remote file cleanup for notes deleted by this device.
        // This prevents orphaned files from wasting server storage.
        // Success is tracked to avoid repeating, and failures are retried next cycle.
        for (tombstone in localTombstones) {
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

        val localTombstoneEntries = localTombstones.map { tombstone ->
            val isDailyTombstone = tombstone.isDaily && tombstone.dateString != null
            SelfHostManifestEntry(
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
        val mergedTombstonesById = LinkedHashMap<String, SelfHostManifestEntry>()
        previousTombstones.forEach { mergedTombstonesById[it.entryId] = it }
        localTombstoneEntries.forEach { entry ->
            val existing = mergedTombstonesById[entry.entryId]
            if (existing == null || entry.updatedAt > existing.updatedAt) {
                mergedTombstonesById[entry.entryId] = entry
            }
        }
        val tombstoneIds = mergedTombstonesById.keys
        val previousEntriesById = previousManifest.entries.associateBy { it.entryId }

        val noteEntries = noteDao.getAllNotesForBackup()
            .filter { manifestEntryIdFor(it) !in tombstoneIds }
            .map { note ->
                val rebuiltEntry = SelfHostManifestEntry(
                    entryId = manifestEntryIdFor(note),
                    entryType = if (note.isDaily) SelfHostEntryType.DAILY else SelfHostEntryType.NOTE,
                    spaceId = note.spaceId,
                    updatedAt = note.updatedAt,
                    dateString = note.dateString.takeIf { note.isDaily }
                )

                // If a note conflicted, its push was rejected.
                // We preserve the downloaded manifest entry for it, rather than rebuilding it from local state,
                // because the local state hasn't been successfully accepted by the server yet.
                if (note.noteId in conflictedNoteIds) {
                    previousEntriesById[rebuiltEntry.entryId] ?: rebuiltEntry
                } else {
                    rebuiltEntry
                }
            }
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
            if (attempt >= MAX_MANIFEST_UPLOAD_RETRIES) {
                SelfHostSyncLog.e(
                    "TextSync: manifest upload conflict-skipped after $attempt retries, deferring to next cycle",
                    cause
                )
                return
            }
            val (freshManifest, freshEtag) = downloadManifestWithEtag()
            uploadManifest(freshManifest, conflictedNoteIds, chatSessionEntries, freshEtag, attempt + 1)
        }
    }
}