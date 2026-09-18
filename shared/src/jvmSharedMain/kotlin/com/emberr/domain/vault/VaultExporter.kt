// Writes the database out to the vault folder as markdown, either everything or one note at a time.

package com.emberr.domain.vault

import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.data.local.room.dao.FolderDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.dao.SpaceDao
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val GLOBAL_PINNED_NOTE_ID = "global_pinned"

data class VaultExportResult(
    val notesWritten: Int,
    val notesUnchanged: Int,
    val filesRemoved: Int,
    val failures: List<String>,
    val filesEditedOutsideTheApp: List<String> = emptyList()
) {
    val isCompleteSuccess: Boolean get() = failures.isEmpty()
}

private enum class VaultFileWriteOutcome { WRITTEN, ALREADY_MATCHING, SKIPPED_OUTSIDE_EDIT }

private data class PlannedNoteFile(
    val note: NoteMetadataEntity,
    val targetFile: File,
    val folderDepth: Int
)

private class VaultSnapshot(
    val plannedFilesByNoteId: Map<String, PlannedNoteFile>,
    val noteTitlesById: Map<String, String>,
    val categoryNamesById: Map<String, String>,
    val foldersById: Map<String, FolderEntity>,
    val spaceFolderNamesBySpaceId: Map<String, String>
)

class VaultExporter(
    val vaultRootDirectory: File,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val spaceDao: SpaceDao,
    private val categoryDao: CategoryDao,
    private val noteRepository: NoteRepository,
    private val fileLedger: VaultFileLedger,
    private val pathMemory: VaultPathMemory
) {

    private val exportMutex = Mutex()
    private var folderDirectoriesByFolderId: Map<String, String> = emptyMap()
    private var spaceDirectoryPaths: Set<String> = emptySet()

    // Shared with the importer so a file is never read while it is half written.
    val vaultMutex: Mutex get() = exportMutex

    suspend fun exportEverything(): VaultExportResult = withContext(Dispatchers.IO) {
        exportMutex.withLock { exportEverythingWhileLocked() }
    }

    suspend fun exportSingleNote(noteId: String): VaultExportResult = withContext(Dispatchers.IO) {
        exportMutex.withLock {
            if (fileLedger.isEmpty) return@withLock exportEverythingWhileLocked()
            if (!prepareVaultRootDirectory()) return@withLock cannotPrepareResult()

            val snapshot = takeVaultSnapshot()

            if (anyOtherNoteNeedsANewFileName(noteId, snapshot)) {
                return@withLock exportEverythingWhileLocked()
            }

            val planned = snapshot.plannedFilesByNoteId[noteId]
                ?: return@withLock removeFileForVanishedNote(noteId)

            val failures = mutableListOf<String>()
            val filesEditedOutsideTheApp = mutableListOf<String>()
            var notesWritten = 0
            var notesUnchanged = 0
            var filesRemoved = 0

            try {
                val markdown = buildMarkdownFor(planned, snapshot)
                val outcome = writeFileWhenContentChanged(planned.targetFile, markdown)
                if (outcome == VaultFileWriteOutcome.WRITTEN) notesWritten++ else notesUnchanged++

                if (outcome == VaultFileWriteOutcome.SKIPPED_OUTSIDE_EDIT) {
                    filesEditedOutsideTheApp.add(planned.targetFile.absolutePath)
                } else {
                    val newPath = planned.targetFile.absolutePath
                    val previousPath = fileLedger.pathForNote(noteId)
                    if (previousPath != null && previousPath != newPath && File(previousPath).delete()) {
                        filesRemoved++
                    }
                    fileLedger.recordWrite(noteId, newPath, markdown, planned.note.updatedAt)
                    pathMemory.save(currentPathSnapshotWhileLocked())
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: IOException) {
                failures.add(describeFailure(planned.note, cause))
            } catch (cause: RuntimeException) {
                failures.add(describeFailure(planned.note, cause))
            }

            if (filesRemoved > 0) removeDirectoriesWeNoLongerOwn(folderDirectoriesByFolderId.values.toSet())

            VaultExportResult(
                notesWritten = notesWritten,
                notesUnchanged = notesUnchanged,
                filesRemoved = filesRemoved,
                failures = failures,
                filesEditedOutsideTheApp = filesEditedOutsideTheApp
            )
        }
    }

    // What the app believes is on disk right now.
    suspend fun currentPathSnapshot(): VaultPathSnapshot = withContext(Dispatchers.IO) {
        exportMutex.withLock { currentPathSnapshotWhileLocked() }
    }

    private fun currentPathSnapshotWhileLocked() = VaultPathSnapshot(
        noteFilesByNoteId = fileLedger.pathsByNote(),
        folderDirectoriesByFolderId = folderDirectoriesByFolderId
    )

    // What each note's file would say if written right now.
    suspend fun renderCurrentMarkdownByNoteId(): Map<String, String> = withContext(Dispatchers.IO) {
        exportMutex.withLock {
            val snapshot = takeVaultSnapshot()
            snapshot.plannedFilesByNoteId.values.associate { planned ->
                planned.note.noteId to buildMarkdownFor(planned, snapshot)
            }
        }
    }

    private suspend fun exportEverythingWhileLocked(): VaultExportResult {
        if (!prepareVaultRootDirectory()) return cannotPrepareResult()

        val snapshot = takeVaultSnapshot()
        val failures = mutableListOf<String>()
        val filesEditedOutsideTheApp = mutableListOf<String>()
        val writtenFilePaths = mutableSetOf<String>()
        val freshWrites = mutableListOf<VaultLedgerWrite>()
        var notesWritten = 0
        var notesUnchanged = 0

        for (planned in snapshot.plannedFilesByNoteId.values) {
            try {
                val markdown = buildMarkdownFor(planned, snapshot)
                val targetPath = planned.targetFile.absolutePath
                writtenFilePaths.add(targetPath)

                val outcome = writeFileWhenContentChanged(planned.targetFile, markdown)
                if (outcome == VaultFileWriteOutcome.WRITTEN) notesWritten++ else notesUnchanged++

                val recordedWrite = if (outcome == VaultFileWriteOutcome.SKIPPED_OUTSIDE_EDIT) {
                    filesEditedOutsideTheApp.add(targetPath)
                    ledgerWriteAlreadyRecordedFor(planned.note.noteId, targetPath)
                } else {
                    VaultLedgerWrite(
                        noteId = planned.note.noteId,
                        path = targetPath,
                        markdown = markdown,
                        noteUpdatedAt = planned.note.updatedAt
                    )
                }
                if (recordedWrite != null) freshWrites.add(recordedWrite)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: IOException) {
                failures.add(describeFailure(planned.note, cause))
            } catch (cause: RuntimeException) {
                failures.add(describeFailure(planned.note, cause))
            }
        }

        spaceDirectoryPaths = createDirectoryForEverySpace(snapshot.spaceFolderNamesBySpaceId)
        val folderDirectories = createDirectoryForEveryFolder(
            snapshot.foldersById,
            snapshot.spaceFolderNamesBySpaceId
        )
        folderDirectoriesByFolderId = folderDirectories
        val filesRemoved = removeStaleFiles(writtenFilePaths, failures)
        removeDirectoriesWeNoLongerOwn(folderDirectories.values.toSet())

        fileLedger.replaceEverything(freshWrites)
        pathMemory.save(
            VaultPathSnapshot(
                noteFilesByNoteId = freshWrites.associate { it.noteId to it.path },
                folderDirectoriesByFolderId = folderDirectories
            )
        )

        return VaultExportResult(
            notesWritten = notesWritten,
            notesUnchanged = notesUnchanged,
            filesRemoved = filesRemoved,
            failures = failures,
            filesEditedOutsideTheApp = filesEditedOutsideTheApp
        )
    }

    private suspend fun takeVaultSnapshot(): VaultSnapshot {
        val foldersById = folderDao.getAllFoldersAcrossSpaces().first().associateBy { it.folderId }
        val spaceFolderNames = VaultPaths.spaceFolderNamesBySpaceId(spaceDao.getAllSpacesOnce())
        val allNotes = noteDao.getAllNotesForBackup()
        val plannedFiles = planNoteFiles(allNotes.filter { isExportable(it) }, foldersById, spaceFolderNames)

        return VaultSnapshot(
            plannedFilesByNoteId = plannedFiles.associateBy { it.note.noteId },
            noteTitlesById = allNotes.associate { it.noteId to it.title },
            categoryNamesById = categoryDao.getAllCategoriesOnceAcrossSpaces()
                .filter { !it.isDeleted }
                .associate { it.categoryId to it.name },
            foldersById = foldersById,
            spaceFolderNamesBySpaceId = spaceFolderNames
        )
    }

    // Even an empty folder gets its directory, so a missing directory means it was deleted.
    private fun createDirectoryForEveryFolder(
        foldersById: Map<String, FolderEntity>,
        spaceFolderNames: Map<String, String>
    ): Map<String, String> {
        val directoriesByFolderId = LinkedHashMap<String, String>(foldersById.size)

        for ((folderId, folder) in foldersById) {
            val spaceFolderName = spaceFolderNames[folder.spaceId] ?: continue
            val segments = VaultPaths.folderSegmentsFor(folderId, foldersById)
            if (segments.isEmpty()) continue

            var directory = File(vaultRootDirectory, spaceFolderName)
            for (segment in segments) {
                directory = File(directory, segment)
            }
            if (directory.isDirectory || directory.mkdirs()) {
                directoriesByFolderId[folderId] = directory.absolutePath
            }
        }
        return directoriesByFolderId
    }

    private fun createDirectoryForEverySpace(spaceFolderNames: Map<String, String>): Set<String> {
        val directoryPaths = mutableSetOf<String>()
        for ((spaceId, folderName) in spaceFolderNames) {
            val directory = File(vaultRootDirectory, folderName)
            if (directory.isDirectory || directory.mkdirs()) {
                directoryPaths.add(directory.absolutePath)
                recordSpaceIdIn(directory, spaceId)
            }
        }
        return directoryPaths
    }

    private fun recordSpaceIdIn(directory: File, spaceId: String) {
        val markerFile = File(directory, VaultPaths.SPACE_ID_FILE_NAME)
        try {
            if (markerFile.isFile && markerFile.readText().trim() == spaceId) return
            markerFile.writeText(spaceId)
        } catch (cause: Exception) {
            VaultLog.e("could not record the space id in ${directory.name}: ${cause.message}")
        }
    }

    private fun removeDirectoriesWeNoLongerOwn(folderDirectoryPaths: Set<String>) {
        vaultRootDirectory.walkBottomUp()
            .filter { it.isDirectory && it.absolutePath != vaultRootDirectory.absolutePath }
            .filterNot { it.absolutePath in folderDirectoryPaths }
            .filterNot { it.absolutePath in spaceDirectoryPaths }
            .filterNot { it.name == VaultPaths.DAILY_FOLDER_NAME || it.name == VaultPaths.SUB_NOTE_FOLDER_NAME }
            .forEach { directory ->
                val remainingFiles = directory.listFiles().orEmpty()
                val onlyHoldsTheSpaceMarker = remainingFiles.size == 1 &&
                    remainingFiles.first().name == VaultPaths.SPACE_ID_FILE_NAME
                if (onlyHoldsTheSpaceMarker) remainingFiles.first().delete()
                if (directory.listFiles()?.isEmpty() == true) directory.delete()
            }
    }

    // Two notes sharing a name both gain an id suffix, so one note's file is not enough here.
    private fun anyOtherNoteNeedsANewFileName(noteId: String, snapshot: VaultSnapshot): Boolean =
        snapshot.plannedFilesByNoteId.any { (otherNoteId, planned) ->
            if (otherNoteId == noteId) return@any false
            val rememberedPath = fileLedger.pathForNote(otherNoteId) ?: return@any false
            rememberedPath != planned.targetFile.absolutePath
        }

    private fun removeFileForVanishedNote(noteId: String): VaultExportResult {
        val rememberedPath = fileLedger.forgetNote(noteId)
            ?: return VaultExportResult(0, 0, 0, emptyList())

        val filesRemoved = if (File(rememberedPath).delete()) 1 else 0
        removeDirectoriesWeNoLongerOwn(folderDirectoriesByFolderId.values.toSet())
        return VaultExportResult(0, 0, filesRemoved, emptyList())
    }

    private fun cannotPrepareResult() = VaultExportResult(
        notesWritten = 0,
        notesUnchanged = 0,
        filesRemoved = 0,
        failures = listOf("Could not create vault directory ${vaultRootDirectory.path}")
    )

    private suspend fun buildMarkdownFor(planned: PlannedNoteFile, snapshot: VaultSnapshot): String {
        val content = noteRepository.getNoteContent(planned.note.noteId)
        return NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = planned.note,
                blocks = content?.blocks.orEmpty(),
                noteTitlesById = snapshot.noteTitlesById,
                categoryNamesById = snapshot.categoryNamesById,
                mediaPathPrefix = VaultPaths.mediaPathPrefixForDepth(planned.folderDepth)
            )
        )
    }

    private fun prepareVaultRootDirectory(): Boolean = try {
        if (vaultRootDirectory.isDirectory) true else vaultRootDirectory.mkdirs()
    } catch (cause: SecurityException) {
        VaultLog.e("Cannot create ${vaultRootDirectory.path}: ${cause.message}")
        false
    }

    // The globally pinned strip is a daily note whose dateString is the marker, not a date.
    private fun isExportable(note: NoteMetadataEntity): Boolean =
        note.trashedAt == null &&
            !note.isTemplate &&
            note.noteId != GLOBAL_PINNED_NOTE_ID &&
            note.dateString != GLOBAL_PINNED_NOTE_ID

    private fun planNoteFiles(
        notes: List<NoteMetadataEntity>,
        foldersById: Map<String, FolderEntity>,
        spaceFolderNames: Map<String, String>
    ): List<PlannedNoteFile> {
        val drafts = notes.mapNotNull { note ->
            val spaceFolderName = spaceFolderNames[note.spaceId]
            if (spaceFolderName == null) {
                VaultLog.e(
                    "note ${note.noteId} (\"${note.title}\") is not exported because its space " +
                        "${note.spaceId} has no live row, so its markdown file will be swept as stale"
                )
                return@mapNotNull null
            }
            Triple(
                note,
                listOf(spaceFolderName) + folderSegmentsFor(note, foldersById),
                baseFileNameFor(note)
            )
        }

        val notesPerTargetPath = drafts
            .groupingBy { (_, segments, baseName) -> (segments + baseName).joinToString("/").lowercase() }
            .eachCount()

        return drafts.map { (note, segments, baseName) ->
            val targetPathKey = (segments + baseName).joinToString("/").lowercase()
            val fileName = if (notesPerTargetPath.getValue(targetPathKey) > 1) {
                "$baseName (${VaultBlockTags.shortTagFor(note.noteId)})${VaultPaths.MARKDOWN_EXTENSION}"
            } else {
                "$baseName${VaultPaths.MARKDOWN_EXTENSION}"
            }

            var directory = vaultRootDirectory
            for (segment in segments) {
                directory = File(directory, segment)
            }

            PlannedNoteFile(
                note = note,
                targetFile = File(directory, fileName),
                folderDepth = segments.size
            )
        }
    }

    private fun folderSegmentsFor(
        note: NoteMetadataEntity,
        foldersById: Map<String, FolderEntity>
    ): List<String> = when {
        note.isDaily -> listOf(VaultPaths.DAILY_FOLDER_NAME)
        note.isSubNote -> listOf(VaultPaths.SUB_NOTE_FOLDER_NAME)
        else -> VaultPaths.folderSegmentsFor(note.folderId, foldersById)
    }

    private fun baseFileNameFor(note: NoteMetadataEntity): String {
        val dateString = note.dateString
        return if (note.isDaily && !dateString.isNullOrBlank()) VaultPaths.sanitiseFileName(dateString)
        else VaultPaths.sanitiseFileName(note.title)
    }

    private fun ledgerWriteAlreadyRecordedFor(noteId: String, path: String): VaultLedgerWrite? {
        val markdown = fileLedger.baseMarkdownForPath(path) ?: return null
        val noteUpdatedAt = fileLedger.noteUpdatedAtForPath(path) ?: return null
        return VaultLedgerWrite(noteId, path, markdown, noteUpdatedAt)
    }

    private fun writeFileWhenContentChanged(targetFile: File, markdown: String): VaultFileWriteOutcome {
        val parentDirectory = targetFile.parentFile
        if (parentDirectory != null && !parentDirectory.isDirectory && !parentDirectory.mkdirs()) {
            throw IOException("Could not create directory ${parentDirectory.path}")
        }

        val contentOnDisk = if (targetFile.isFile) targetFile.readText() else null
        if (contentOnDisk == markdown) return VaultFileWriteOutcome.ALREADY_MATCHING

        val markdownWeLastWrote = fileLedger.baseMarkdownForPath(targetFile.absolutePath)
        if (contentOnDisk != null && markdownWeLastWrote != null && contentOnDisk != markdownWeLastWrote) {
            return VaultFileWriteOutcome.SKIPPED_OUTSIDE_EDIT
        }

        val temporaryFile = File(parentDirectory, targetFile.name + VaultPaths.TEMPORARY_EXTENSION)
        temporaryFile.writeText(markdown)

        try {
            Files.move(
                temporaryFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporaryFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return VaultFileWriteOutcome.WRITTEN
    }

    private fun removeStaleFiles(writtenFilePaths: Set<String>, failures: MutableList<String>): Int {
        var filesRemoved = 0
        val candidates = vaultRootDirectory.walkTopDown()
            .filter { it.isFile }
            .filter { isRemovableVaultFile(it, writtenFilePaths) }
            .toList()

        for (file in candidates) {
            if (file.delete()) filesRemoved++
            else failures.add("Could not delete stale file ${file.path}")
        }
        return filesRemoved
    }

    private fun isRemovableVaultFile(file: File, writtenFilePaths: Set<String>): Boolean {
        if (file.name == VaultPaths.VAULT_RULES_FILE_NAME) return false
        if (file.name.endsWith(VaultPaths.CONFLICT_MARKDOWN_SUFFIX)) return false
        if (file.name.endsWith(VaultPaths.TEMPORARY_EXTENSION)) return true
        if (!file.name.endsWith(VaultPaths.MARKDOWN_EXTENSION)) return false
        return file.absolutePath !in writtenFilePaths
    }

    private fun describeFailure(note: NoteMetadataEntity, cause: Throwable): String =
        "${note.title.ifBlank { "Untitled" }} (${note.noteId}): ${cause.message ?: cause::class.simpleName}"
}
