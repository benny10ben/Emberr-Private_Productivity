package com.emberr.domain.sync

import com.emberr.core.security.SyncEncryptionManager
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.CategoryEntity
import com.emberr.data.local.room.ChatSessionDao
import com.emberr.data.local.room.ChatSessionEntity
import com.emberr.data.local.room.FolderEntity
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.data.local.room.SelfHostDeletedApiConfigDao
import com.emberr.data.local.room.TagEntity
import com.emberr.domain.ai.external.AiSettingsRepository
import com.emberr.domain.ai.external.ExternalAiProvider
import com.emberr.domain.ai.external.ExternalAiProviderConfig
import com.emberr.domain.model.CellData
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.VoiceBlock
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.model.BOOKMARK_CATEGORY_ORDER_ENTITY_ID
import com.emberr.domain.model.BookmarkCategoryOrder
import com.emberr.domain.model.FAVORITE_NOTE_ORDER_ENTITY_ID
import com.emberr.domain.model.FavoriteNoteOrder
import com.emberr.domain.repository.BookmarkCategoryOrderStore
import com.emberr.domain.repository.FavoriteNoteOrderStore
import com.emberr.domain.selfhost.sync.ApiConfigSyncEntry
import com.emberr.domain.selfhost.translation.EmbeddedBlockPayload
import com.emberr.domain.util.sync.ChatSyncEventBus
import com.emberr.domain.util.media.MediaStorageHelper
import com.emberr.domain.util.sync.SyncEventBus
import com.emberr.domain.util.sync.withSyncCoordinatorOrSkip
import com.emberr.database.EmberrDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class SyncRepositoryImpl(
    private val repository: NoteRepository,
    private val mediaStorageHelper: MediaStorageHelper,
    private val settingsManager: SettingsManager,
    private val encryptionManager: SyncEncryptionManager,
    private val syncClient: SyncClient,
    private val chatSessionDao: ChatSessionDao,
    private val selfHostDeletedApiConfigDao: SelfHostDeletedApiConfigDao,
    private val aiSettingsRepository: AiSettingsRepository,
    private val database: EmberrDatabase,
    private val bookmarkCategoryOrderStore: BookmarkCategoryOrderStore,
    private val favoriteNoteOrderStore: FavoriteNoteOrderStore
) : SyncRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // Coroutine scope for background media transfers to prevent blocking text sync operations.
    private val mediaTransferScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightMediaTransfersMutex = Mutex()
    private val inFlightMediaTransfers = mutableSetOf<String>()

    // Executes a single file transfer on the background scope if it isn't already in flight.
    // Updates MediaTransferStatusBus so UI components stay in sync with real-time transfer states.
    private fun launchMediaTransfer(fileName: String, phase: MediaTransferPhase, block: suspend () -> MediaTransferOutcome) {
        mediaTransferScope.launch {
            val acquired = inFlightMediaTransfersMutex.withLock { inFlightMediaTransfers.add(fileName) }
            if (!acquired) {
                return@launch
            }
            MediaTransferStatusBus.markStarted(fileName, phase)
            var outcome = MediaTransferOutcome.FAILED
            try {
                outcome = block()
            } catch (e: Exception) {
                LanSyncLog.e("mediaSync: $phase of $fileName failed unexpectedly: ${e.message}", e)
            } finally {
                inFlightMediaTransfersMutex.withLock { inFlightMediaTransfers.remove(fileName) }
                when (outcome) {
                    MediaTransferOutcome.SUCCESS -> MediaTransferStatusBus.markFinished(fileName, succeeded = true)
                    MediaTransferOutcome.FAILED -> MediaTransferStatusBus.markFinished(fileName, succeeded = false)
                    MediaTransferOutcome.PEER_NOT_READY -> MediaTransferStatusBus.markDeferred(fileName)
                }
            }
        }
    }

    private fun extractMediaFileNames(content: NoteContent): List<String> {
        val mediaFiles = mutableListOf<String>()

        fun scan(blocks: List<NoteBlock>) {
            blocks.forEach { block ->
                if (block.isDeleted) return@forEach
                when (block) {
                    is ImageBlock -> block.localFilePath?.substringAfterLast("/")?.let { mediaFiles.add(it) }
                    is DocumentBlock -> block.localFilePath?.substringAfterLast("/")?.let { mediaFiles.add(it) }
                    is VoiceBlock -> block.localFilePath?.substringAfterLast("/")?.let { mediaFiles.add(it) }
                    is DatabaseBlock -> {
                        val mediaColIds = block.columns
                            .filter { it.type == ColumnType.FILES || it.type == ColumnType.AUDIO }
                            .map { it.id }.toSet()
                        block.rows.forEach { row ->
                            mediaColIds.forEach { colId ->
                                val files = (row.cells[colId] as? CellData.MediaList)?.files ?: emptyList()
                                files.forEach { media ->
                                    val cleanLocalPath = media.fileName.substringAfterLast("/")
                                    if (cleanLocalPath.isNotBlank()) mediaFiles.add(cleanLocalPath)
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        scan(content.blocks)
        return mediaFiles.distinct()
    }

    private fun downloadMissingMedia(content: NoteContent) {
        extractMediaFileNames(content).forEach { fileName ->
            val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
            if (!file.exists()) {
                launchMediaTransfer(fileName, MediaTransferPhase.DOWNLOADING) {
                    val outcome = syncClient.downloadMedia(fileName, file)
                    if (outcome == MediaTransferOutcome.FAILED) {
                        LanSyncLog.e("downloadMissingMedia: $fileName failed, will retry on the next sync pass")
                    }
                    outcome
                }
            }
        }
    }

    private fun uploadLocalMedia(content: NoteContent, remoteFileNames: Set<String>) {
        extractMediaFileNames(content).forEach { fileName ->
            if (fileName in remoteFileNames) return@forEach
            val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
            if (file.exists()) {
                launchMediaTransfer(fileName, MediaTransferPhase.UPLOADING) {
                    val outcome = syncClient.uploadMedia(fileName, file)
                    if (outcome == MediaTransferOutcome.FAILED) {
                        LanSyncLog.e(
                            "uploadLocalMedia: $fileName (${file.length()} bytes) failed to upload, " +
                                    "note metadata will still be pushed and reference a file the peer doesn't have yet - " +
                                    "it will self-heal once this upload succeeds on a later sync pass"
                        )
                    }
                    outcome
                }
            }
        }
    }

    private suspend fun collectAllReferencedMediaFileNames(): Set<String> {
        val fileNames = mutableSetOf<String>()
        repository.getNotesModifiedSince(0L).forEach { meta ->
            val content = if (meta.isDaily && meta.dateString != null) {
                repository.getDailyNote(meta.dateString)
            } else {
                repository.getNoteContent(meta.noteId)
            }
            if (content != null) fileNames += extractMediaFileNames(content)
            meta.coverImagePath?.substringAfterLast("/")?.let { fileNames.add(it) }
        }
        return fileNames
    }

    override suspend fun cleanupOrphanedMedia() = withContext(Dispatchers.IO) {
        try {
            val referencedFileNames = collectAllReferencedMediaFileNames()
            val remoteMedia = syncClient.listRemoteMedia()
            val nowMs = System.currentTimeMillis()
            // Filters and deletes remote media files that are no longer referenced locally and exceed the grace period.
            val orphaned = remoteMedia.filter {
                it.fileName !in referencedFileNames && (nowMs - it.lastModified) > MEDIA_ORPHAN_GRACE_PERIOD_MS
            }
            orphaned.forEach { entry ->
                try {
                    syncClient.deleteRemoteMedia(entry.fileName)
                } catch (e: Exception) {
                    LanSyncLog.e("cleanupOrphanedMedia: failed to delete orphaned ${entry.fileName}: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            LanSyncLog.e("cleanupOrphanedMedia: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }

    override suspend fun reconcileMedia() = withContext(Dispatchers.IO) {
        try {
            val referencedFileNames = collectAllReferencedMediaFileNames()
            val remoteFileNames = syncClient.listRemoteMedia().map { it.fileName }.toSet()
            val existingLocalFileNames = referencedFileNames.filterTo(mutableSetOf()) { fileExistsLocally(it) }

            // Uploads referenced local files missing on the peer; downloads referenced peer files missing locally.
            val toUpload = existingLocalFileNames - remoteFileNames
            val toDownload = referencedFileNames.filter { it in remoteFileNames && it !in existingLocalFileNames }

            toUpload.forEach { fileName ->
                val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                launchMediaTransfer(fileName, MediaTransferPhase.UPLOADING) {
                    syncClient.uploadMedia(fileName, file)
                }
            }
            toDownload.forEach { fileName ->
                val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
                file.parentFile?.mkdirs()
                launchMediaTransfer(fileName, MediaTransferPhase.DOWNLOADING) {
                    syncClient.downloadMedia(fileName, file)
                }
            }
        } catch (e: Exception) {
            LanSyncLog.e("reconcileMedia: failed with ${e::class.simpleName}: ${e.message}", e)
        }
    }

    override fun retryMediaDownload(fileName: String) {
        val file = File(mediaStorageHelper.getAbsoluteMediaPath(fileName))
        if (file.exists()) return
        file.parentFile?.mkdirs()
        launchMediaTransfer(fileName, MediaTransferPhase.DOWNLOADING) {
            syncClient.downloadMedia(fileName, file)
        }
    }

    private fun fileExistsLocally(fileName: String): Boolean {
        return try {
            File(mediaStorageHelper.getAbsoluteMediaPath(fileName)).exists()
        } catch (e: Exception) {
            LanSyncLog.e("reconcileMedia: could not check local existence for $fileName", e)
            false
        }
    }

    private companion object {
        const val MEDIA_ORPHAN_GRACE_PERIOD_MS = 24L * 60 * 60 * 1000
    }

    private fun adoptRemoteEmbeddingsIfNeeded(
        noteId: String,
        localUpdatedAt: Long?,
        remoteUpdatedAt: Long,
        embeddedBlocksJson: String,
        syncKey: String
    ) {
        if (settingsManager.isAiFeaturesDisabled()) return
        if (embeddedBlocksJson.isEmpty()) return
        val decrypted = encryptionManager.decryptPayload(embeddedBlocksJson, syncKey)
        val remoteEmbeddedBlocks = json.decodeFromString(ListSerializer(EmbeddedBlockPayload.serializer()), decrypted)
        if (remoteEmbeddedBlocks.isEmpty()) return

        val remoteWon = localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt
        val hasLocalEmbeddings = database.vectorStoreQueries.getBlocksForNote(noteId).executeAsList().isNotEmpty()
        if (!remoteWon && hasLocalEmbeddings) return

        database.transaction {
            database.vectorStoreQueries.deleteBlocksForNote(noteId)
            remoteEmbeddedBlocks.forEach { block ->
                database.vectorStoreQueries.insertMetadata(
                    block_id = block.blockId,
                    note_id = noteId,
                    chunk_text = block.chunkText,
                    embedding = block.embedding
                )
            }
        }
    }

    override suspend fun applyRemoteChanges(changes: List<SyncEnvelope>): Boolean =
        withContext(Dispatchers.IO) {
            val syncKey = settingsManager.getSyncEncryptionKey()
            var allSucceeded = true

            changes.forEach { envelope ->
                // Media downloads are queued after releasing the lock so large file downloads do not block editor saves.
                var pendingMediaContent: NoteContent? = null
                var pendingCoverImagePath: String? = null

                val applied = withSyncCoordinatorOrSkip {
                    try {
                        val decryptedMetaJson =
                            encryptionManager.decryptPayload(envelope.metadataJson, syncKey)
                        val decryptedContentJson = if (envelope.contentJson.isNotEmpty()) {
                            encryptionManager.decryptPayload(envelope.contentJson, syncKey)
                        } else null

                        when (envelope.entityType) {

                            // notes
                            SyncType.NOTE -> {
                                val remoteMeta =
                                    json.decodeFromString<NoteMetadataEntity>(decryptedMetaJson)
                                val remoteContent = if (!decryptedContentJson.isNullOrEmpty()) {
                                    json.decodeFromString<NoteContent>(decryptedContentJson)
                                } else NoteContent(blocks = emptyList())

                                val localMeta = repository.getNoteById(envelope.entityId)

                                if (localMeta == null) {
                                    // Prevents re-creating a note if a tombstone already exists.
                                    val tombstone = repository.getNoteTombstone(envelope.entityId)
                                    if (!envelope.isDeleted && (tombstone == null || tombstone.deletedAt < envelope.updatedAt)) {
                                        val newMeta = remoteMeta.copy(selfHostSyncedAt = 0L)
                                        repository.saveNote(
                                            newMeta,
                                            remoteContent,
                                            stampUpdatedAt = false
                                        )

                                        // EXPLICIT AI INDEXING CALL
                                        repository.indexNote(newMeta, remoteContent)
                                        adoptRemoteEmbeddingsIfNeeded(
                                            remoteMeta.noteId, null, envelope.updatedAt,
                                            envelope.embeddedBlocksJson, syncKey
                                        )

                                        SyncEventBus.emitSyncCompleted(envelope.entityId)
                                        pendingMediaContent = remoteContent
                                        pendingCoverImagePath = remoteMeta.coverImagePath
                                    }
                                } else if (envelope.isDeleted && envelope.updatedAt > localMeta.updatedAt) {
                                    val trashedMeta = remoteMeta.copy(
                                        trashedAt = System.currentTimeMillis(),
                                        selfHostSyncedAt = localMeta.selfHostSyncedAt
                                    )
                                    repository.saveNote(
                                        trashedMeta,
                                        remoteContent,
                                        stampUpdatedAt = false
                                    )

                                    // EXPLICIT AI INDEXING CALL
                                    repository.indexNote(trashedMeta, remoteContent)
                                    adoptRemoteEmbeddingsIfNeeded(
                                        trashedMeta.noteId, localMeta.updatedAt, envelope.updatedAt,
                                        envelope.embeddedBlocksJson, syncKey
                                    )

                                    SyncEventBus.emitSyncCompleted(envelope.entityId)
                                } else if (!envelope.isDeleted) {
                                    val localContent = repository.getNoteContent(envelope.entityId)
                                    val mergedContent = NoteMergeHelper.mergeNoteContent(
                                        localContent = localContent,
                                        localUpdatedAt = localMeta.updatedAt,
                                        remoteContent = remoteContent,
                                        remoteUpdatedAt = envelope.updatedAt
                                    )
                                    pendingMediaContent = mergedContent
                                    pendingCoverImagePath = remoteMeta.coverImagePath
                                    val contentChanged = mergedContent != localContent
                                    // Checks for metadata differences ignoring non-sync fields like filePath.
                                    val metadataChanged = localMeta.copy(
                                        updatedAt = remoteMeta.updatedAt,
                                        filePath = remoteMeta.filePath,
                                        selfHostSyncedAt = remoteMeta.selfHostSyncedAt
                                    ) != remoteMeta
                                    if (contentChanged || metadataChanged) {
                                        val resolvedUpdatedAt =
                                            maxOf(localMeta.updatedAt, envelope.updatedAt)
                                        val winningMeta =
                                            if (envelope.updatedAt > localMeta.updatedAt) {
                                                remoteMeta.copy(
                                                    updatedAt = resolvedUpdatedAt,
                                                    selfHostSyncedAt = localMeta.selfHostSyncedAt
                                                )
                                            } else {
                                                localMeta.copy(updatedAt = resolvedUpdatedAt)
                                            }
                                        repository.saveNote(
                                            winningMeta,
                                            mergedContent,
                                            stampUpdatedAt = false
                                        )

                                        // EXPLICIT AI INDEXING CALL
                                        repository.indexNote(winningMeta, mergedContent)
                                        adoptRemoteEmbeddingsIfNeeded(
                                            winningMeta.noteId, localMeta.updatedAt, envelope.updatedAt,
                                            envelope.embeddedBlocksJson, syncKey
                                        )

                                        SyncEventBus.emitSyncCompleted(envelope.entityId)
                                    }
                                }
                            }

                            // Daily notes
                            SyncType.DAILY_NOTE -> {
                                val remoteMeta =
                                    json.decodeFromString<NoteMetadataEntity>(decryptedMetaJson)
                                val remoteContent = if (!decryptedContentJson.isNullOrEmpty()) {
                                    json.decodeFromString<NoteContent>(decryptedContentJson)
                                } else NoteContent(blocks = emptyList())

                                val dateString = envelope.entityId
                                val localMeta = repository.getDailyNoteMetadata(dateString)

                                if (localMeta == null) {
                                    val tombstone = repository.getNoteTombstone(dateString)
                                    if (tombstone == null || tombstone.deletedAt < envelope.updatedAt) {
                                        repository.saveDailyNote(
                                            dateString,
                                            remoteContent,
                                            envelope.updatedAt,
                                            remoteMeta
                                        )

                                        // EXPLICIT AI INDEXING CALL
                                        val finalMeta = repository.getDailyNoteMetadata(dateString)
                                            ?: remoteMeta
                                        repository.indexDailyNote(
                                            dateString,
                                            remoteContent,
                                            finalMeta
                                        )
                                        adoptRemoteEmbeddingsIfNeeded(
                                            finalMeta.noteId, null, envelope.updatedAt,
                                            envelope.embeddedBlocksJson, syncKey
                                        )

                                        SyncEventBus.emitSyncCompleted(dateString)
                                        pendingMediaContent = remoteContent
                                    }
                                } else {
                                    val localContent = repository.getDailyNote(dateString)
                                    val mergedContent = NoteMergeHelper.mergeNoteContent(
                                        localContent = localContent,
                                        localUpdatedAt = localMeta.updatedAt,
                                        remoteContent = remoteContent,
                                        remoteUpdatedAt = envelope.updatedAt
                                    )
                                    pendingMediaContent = mergedContent

                                    val contentChanged = mergedContent != localContent
                                    val metadataChanged =
                                        localMeta.isFavorite != remoteMeta.isFavorite ||
                                                localMeta.coverImagePath != remoteMeta.coverImagePath

                                    if (contentChanged || metadataChanged) {
                                        val resolvedUpdatedAt =
                                            maxOf(localMeta.updatedAt, envelope.updatedAt)
                                        val mergedMeta = localMeta.copy(
                                            isFavorite = localMeta.isFavorite || remoteMeta.isFavorite,
                                            coverImagePath = if (envelope.updatedAt > localMeta.updatedAt) remoteMeta.coverImagePath else localMeta.coverImagePath
                                        )
                                        repository.saveDailyNote(
                                            dateString,
                                            mergedContent,
                                            resolvedUpdatedAt,
                                            mergedMeta
                                        )

                                        // EXPLICIT AI INDEXING CALL
                                        repository.indexDailyNote(
                                            dateString,
                                            mergedContent,
                                            mergedMeta
                                        )
                                        adoptRemoteEmbeddingsIfNeeded(
                                            mergedMeta.noteId, localMeta.updatedAt, envelope.updatedAt,
                                            envelope.embeddedBlocksJson, syncKey
                                        )

                                        SyncEventBus.emitSyncCompleted(dateString)
                                    }
                                }
                            }

                            SyncType.TAG -> {
                                val remoteTag = json.decodeFromString<TagEntity>(decryptedMetaJson)
                                repository.applyRemoteTag(remoteTag)
                            }

                            SyncType.FOLDER -> {
                                val remoteFolder =
                                    json.decodeFromString<FolderEntity>(decryptedMetaJson)
                                repository.applyRemoteFolder(remoteFolder)
                            }

                            SyncType.CATEGORY -> {
                                val remoteCategory =
                                    json.decodeFromString<CategoryEntity>(decryptedMetaJson)
                                repository.applyRemoteCategory(remoteCategory)
                            }

                            SyncType.NOTE_TOMBSTONE -> {
                                val tombstone =
                                    json.decodeFromString<NoteTombstonePayload>(decryptedMetaJson)
                                repository.applyRemoteNoteTombstone(
                                    noteId = tombstone.noteId,
                                    isDaily = tombstone.isDaily,
                                    dateString = tombstone.dateString,
                                    deletedAt = tombstone.deletedAt
                                )
                                SyncEventBus.emitSyncCompleted(
                                    if (tombstone.isDaily) tombstone.dateString
                                        ?: tombstone.noteId else tombstone.noteId
                                )
                            }

                            SyncType.CHAT_SESSION -> {
                                val remoteSession =
                                    json.decodeFromString<ChatSessionEntity>(decryptedMetaJson)
                                val localSession = chatSessionDao.getSession(remoteSession.id)
                                val localUpdatedAt = localSession?.updatedAt ?: 0L
                                if (envelope.updatedAt > localUpdatedAt) {
                                    if (envelope.isDeleted) {
                                        chatSessionDao.softDeleteSession(remoteSession.id, envelope.updatedAt)
                                    } else {
                                        chatSessionDao.upsertSession(remoteSession)
                                    }
                                    ChatSyncEventBus.emitSessionChanged(remoteSession.id)
                                }
                            }

                            SyncType.EXTERNAL_API_CONFIG -> {
                                val remoteEntry =
                                    json.decodeFromString<ApiConfigSyncEntry>(decryptedMetaJson)
                                val provider = ExternalAiProvider.entries.find { it.name == remoteEntry.provider }
                                if (provider != null) {
                                    val localConfig = aiSettingsRepository.getProviderConfig(provider)
                                    val localTombstone = selfHostDeletedApiConfigDao.getTombstoneByProvider(provider.name)
                                    val localUpdatedAt = maxOf(
                                        localConfig?.updatedAt ?: 0L,
                                        localTombstone?.deletedAt ?: 0L
                                    )
                                    if (remoteEntry.updatedAt > localUpdatedAt) {
                                        if (remoteEntry.isDeleted) {
                                            aiSettingsRepository.applyRemoteProviderConfigDeletion(
                                                provider, remoteEntry.updatedAt
                                            )
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
                                    }
                                }
                            }

                            SyncType.BOOKMARK_CATEGORY_ORDER -> {
                                val remoteOrder =
                                    json.decodeFromString<BookmarkCategoryOrder>(decryptedMetaJson)
                                bookmarkCategoryOrderStore.applyRemoteOrder(remoteOrder)
                            }

                            SyncType.FAVORITE_NOTE_ORDER -> {
                                val remoteOrder =
                                    json.decodeFromString<FavoriteNoteOrder>(decryptedMetaJson)
                                favoriteNoteOrderStore.applyRemoteOrder(remoteOrder)
                            }

                        }
                        true
                    } catch (e: Exception) {
                        LanSyncLog.e("applyRemoteChanges: failed to apply change for ${envelope.entityId}: ${e.message}", e)
                        false
                    }
                }

                // Triggers background downloads for media referenced by newly applied notes.
                try {
                    pendingMediaContent?.let { downloadMissingMedia(it) }
                    pendingCoverImagePath?.let { path ->
                        val file = File(mediaStorageHelper.getAbsoluteMediaPath(path))
                        if (!file.exists()) {
                            launchMediaTransfer(path, MediaTransferPhase.DOWNLOADING) {
                                val outcome = syncClient.downloadMedia(path, file)
                                if (outcome == MediaTransferOutcome.FAILED) {
                                    LanSyncLog.e("applyRemoteChanges: cover image $path failed to download for ${envelope.entityId}")
                                }
                                outcome
                            }
                        }
                    }
                } catch (e: Exception) {
                    LanSyncLog.e("applyRemoteChanges: failed to queue media download for ${envelope.entityId}: ${e.message}", e)
                }

                if (applied != true) {
                    allSucceeded = false
                }
            }

            // A synced-in change can tombstone a block (or a whole note) that this device didn't
            // delete itself, bypassing every editor-driven delete path that already requests a
            // cleanup - without this, a deletion arriving via sync would only ever get cleaned up
            // by the next full app restart's launch-time pass.
            com.emberr.domain.media.LocalMediaGcTrigger.requestCleanup()

            allSucceeded
        }

    override suspend fun collectLocalChanges(since: Long, uploadMedia: Boolean): List<SyncEnvelope> =
        withContext(Dispatchers.IO) {
            val lastSyncTime = since
            val syncKey = settingsManager.getSyncEncryptionKey()
            val changes = mutableListOf<SyncEnvelope>()
            val modifiedNotes = repository.getNotesModifiedSince(lastSyncTime)

            // Snapshotting the peer's file listing once per collection pass - rather than trusting
            // the "modified since" watermark alone - means a note that keeps reappearing as locally
            // modified (e.g. its watermark advance was lost to an interrupted sync) no longer forces
            // a real re-upload of a file the peer has already confirmed receiving.
            val remoteFileNames = if (uploadMedia) {
                syncClient.listRemoteMedia().map { it.fileName }.toSet()
            } else {
                emptySet()
            }

            modifiedNotes.forEach { meta ->
                val content = if (meta.isDaily && meta.dateString != null) {
                    repository.getDailyNote(meta.dateString)
                } else {
                    repository.getNoteContent(meta.noteId)
                } ?: NoteContent(blocks = emptyList())

                if (uploadMedia) {
                    uploadLocalMedia(content, remoteFileNames)

                    val coverPath = meta.coverImagePath
                    if (!meta.isDaily && coverPath != null && coverPath !in remoteFileNames) {
                        val file = File(mediaStorageHelper.getAbsoluteMediaPath(coverPath))
                        if (file.exists()) {
                            launchMediaTransfer(coverPath, MediaTransferPhase.UPLOADING) {
                                val outcome = syncClient.uploadMedia(coverPath, file)
                                if (outcome == MediaTransferOutcome.FAILED) {
                                    LanSyncLog.e("collectLocalChanges: cover image $coverPath failed to upload for ${meta.noteId}")
                                }
                                outcome
                            }
                        }
                    }
                }

                val encryptedMeta =
                    encryptionManager.encryptPayload(json.encodeToString(meta), syncKey)
                val encryptedContent =
                    encryptionManager.encryptPayload(json.encodeToString(content), syncKey)
                val type = if (meta.isDaily) SyncType.DAILY_NOTE else SyncType.NOTE
                val eId =
                    if (meta.isDaily && meta.dateString != null) meta.dateString else meta.noteId

                val embeddedBlocks = database.vectorStoreQueries.getBlocksForNote(meta.noteId).executeAsList()
                    .map { EmbeddedBlockPayload(blockId = it.block_id, chunkText = it.chunk_text, embedding = it.embedding) }
                val encryptedEmbeddedBlocks = if (embeddedBlocks.isEmpty()) {
                    ""
                } else {
                    encryptionManager.encryptPayload(
                        json.encodeToString(ListSerializer(EmbeddedBlockPayload.serializer()), embeddedBlocks),
                        syncKey
                    )
                }

                changes.add(
                    SyncEnvelope(
                        entityId = eId,
                        entityType = type,
                        metadataJson = encryptedMeta,
                        contentJson = encryptedContent,
                        updatedAt = meta.updatedAt,
                        isDeleted = meta.trashedAt != null,
                        embeddedBlocksJson = encryptedEmbeddedBlocks
                    )
                )
            }

            // Collects tags modified since lastSyncTime.
            val modifiedTags = repository.getTagsModifiedSince(lastSyncTime)
            modifiedTags.forEach { tag ->
                val encryptedTag =
                    encryptionManager.encryptPayload(json.encodeToString(tag), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = tag.tagId, entityType = SyncType.TAG,
                        metadataJson = encryptedTag, contentJson = "",
                        updatedAt = tag.updatedAt, isDeleted = tag.isDeleted
                    )
                )
            }

            // Collects folders modified since lastSyncTime.
            val modifiedFolders = repository.getFoldersModifiedSince(lastSyncTime)
            modifiedFolders.forEach { folder ->
                val encryptedFolder =
                    encryptionManager.encryptPayload(json.encodeToString(folder), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = folder.folderId, entityType = SyncType.FOLDER,
                        metadataJson = encryptedFolder, contentJson = "",
                        updatedAt = folder.updatedAt, isDeleted = folder.isDeleted
                    )
                )
            }

            // Collects permanently deleted note tombstones.
            val modifiedTombstones = repository.getNoteTombstonesModifiedSince(lastSyncTime)
            modifiedTombstones.forEach { tombstone ->
                val payload = NoteTombstonePayload(
                    noteId = tombstone.noteId,
                    isDaily = tombstone.isDaily,
                    dateString = tombstone.dateString,
                    deletedAt = tombstone.deletedAt
                )
                val encryptedPayload =
                    encryptionManager.encryptPayload(json.encodeToString(payload), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = tombstone.noteId, entityType = SyncType.NOTE_TOMBSTONE,
                        metadataJson = encryptedPayload, contentJson = "",
                        updatedAt = tombstone.deletedAt, isDeleted = true
                    )
                )
            }

            // Collects categories modified since lastSyncTime.
            val modifiedCategories = repository.getCategoriesModifiedSince(lastSyncTime)
            modifiedCategories.forEach { category ->
                val encryptedCategory =
                    encryptionManager.encryptPayload(json.encodeToString(category), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = category.categoryId, entityType = SyncType.CATEGORY,
                        metadataJson = encryptedCategory, contentJson = "",
                        updatedAt = category.updatedAt, isDeleted = category.isDeleted
                    )
                )
            }

            // Collects chat sessions modified since lastSyncTime (so the delete itself propagates as a change).
            val modifiedSessions = chatSessionDao.getSessionsModifiedSince(lastSyncTime)
            modifiedSessions.forEach { session ->
                val encryptedSession =
                    encryptionManager.encryptPayload(json.encodeToString(session), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = session.id, entityType = SyncType.CHAT_SESSION,
                        metadataJson = encryptedSession, contentJson = "",
                        updatedAt = session.updatedAt, isDeleted = session.isDeleted
                    )
                )
            }

            for (provider in ExternalAiProvider.entries) {
                val localConfig = aiSettingsRepository.getProviderConfig(provider)
                val localTombstone = selfHostDeletedApiConfigDao.getTombstoneByProvider(provider.name)
                val isDeleted = localConfig == null ||
                    (localTombstone != null && localTombstone.deletedAt >= localConfig.updatedAt)
                val updatedAt = maxOf(localConfig?.updatedAt ?: 0L, localTombstone?.deletedAt ?: 0L)
                if (updatedAt <= lastSyncTime) continue

                val entry = if (isDeleted) {
                    ApiConfigSyncEntry(
                        provider = provider.name, apiKey = "", model = "", baseUrl = null,
                        updatedAt = updatedAt, isDeleted = true
                    )
                } else {
                    ApiConfigSyncEntry(
                        provider = provider.name,
                        apiKey = localConfig.apiKey,
                        model = localConfig.model,
                        baseUrl = localConfig.baseUrl,
                        updatedAt = localConfig.updatedAt
                    )
                }
                val encryptedEntry = encryptionManager.encryptPayload(json.encodeToString(entry), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = provider.name, entityType = SyncType.EXTERNAL_API_CONFIG,
                        metadataJson = encryptedEntry, contentJson = "",
                        updatedAt = updatedAt, isDeleted = isDeleted
                    )
                )
            }

            val localCategoryOrder = bookmarkCategoryOrderStore.getOrder()
            if (localCategoryOrder.updatedAt > lastSyncTime) {
                val encryptedOrder =
                    encryptionManager.encryptPayload(json.encodeToString(localCategoryOrder), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = BOOKMARK_CATEGORY_ORDER_ENTITY_ID,
                        entityType = SyncType.BOOKMARK_CATEGORY_ORDER,
                        metadataJson = encryptedOrder, contentJson = "",
                        updatedAt = localCategoryOrder.updatedAt, isDeleted = false
                    )
                )
            }

            val localFavoriteOrder = favoriteNoteOrderStore.getOrder()
            if (localFavoriteOrder.updatedAt > lastSyncTime) {
                val encryptedFavoriteOrder =
                    encryptionManager.encryptPayload(json.encodeToString(localFavoriteOrder), syncKey)
                changes.add(
                    SyncEnvelope(
                        entityId = FAVORITE_NOTE_ORDER_ENTITY_ID,
                        entityType = SyncType.FAVORITE_NOTE_ORDER,
                        metadataJson = encryptedFavoriteOrder, contentJson = "",
                        updatedAt = localFavoriteOrder.updatedAt, isDeleted = false
                    )
                )
            }

            return@withContext changes
        }
}