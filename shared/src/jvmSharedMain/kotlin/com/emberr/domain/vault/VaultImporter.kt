// Applies a change made to a vault file back into the database, including conflicts and deletions.

package com.emberr.domain.vault

import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.data.local.room.dao.FolderDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.entity.CategoryEntity
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.space.SpaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

private val reservedDirectoryNames = setOf(VaultPaths.DAILY_FOLDER_NAME, VaultPaths.SUB_NOTE_FOLDER_NAME)
private const val INBOX_NOTE_TITLE = "Inbox"

enum class VaultImportOutcome { IGNORED_OUR_OWN_WRITE, UNCHANGED, IMPORTED, IMPORTED_WITH_CONFLICT, CREATED, FAILED }

data class VaultImportReport(
    val outcome: VaultImportOutcome,
    val noteTitle: String,
    val detail: String? = null
)

interface VaultNoteImporter {
    suspend fun importFile(file: File): VaultImportReport
}

private data class VaultFileLocation(val spaceId: String, val folderId: String?)

class VaultImporter(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val categoryDao: CategoryDao,
    private val noteRepository: NoteRepository,
    private val spaceRepository: SpaceRepository,
    private val activeSpaceStore: ActiveSpaceStore,
    private val spaceDirectories: VaultSpaceDirectories,
    private val fileLedger: VaultFileLedger,
    private val vaultExporter: VaultExporter
) : VaultNoteImporter {

    override suspend fun importFile(file: File): VaultImportReport = withContext(Dispatchers.IO) {
        vaultExporter.vaultMutex.withLock { importFileWhileLocked(file) }
    }

    // Catches what the folder watcher never reported. A recorded path with nothing behind it
    // means the file or folder was removed.
    suspend fun applyRemovalsFor(expected: VaultPathSnapshot): Int = withContext(Dispatchers.IO) {
        var removals = 0

        for ((_, path) in expected.noteFilesByNoteId) {
            if (path.endsWith(VaultPaths.CANVAS_EXTENSION)) continue
            if (File(path).isFile) continue
            if (importFile(File(path)).outcome == VaultImportOutcome.IMPORTED) removals++
        }

        for ((folderId, path) in expected.folderDirectoriesByFolderId) {
            if (File(path).isDirectory) continue
            if (deleteFolderForRemovedDirectory(folderId, File(path).name)) removals++
        }
        removals
    }

    private suspend fun deleteFolderForRemovedDirectory(folderId: String, directoryName: String): Boolean {
        val folders = folderDao.getAllFoldersAcrossSpaces().first()
        val folder = folders.firstOrNull { it.folderId == folderId } ?: return false
        if (directoryStillExistsFor(folder, folders.associateBy { it.folderId })) return false

        noteRepository.deleteFolder(folderId)
        VaultLog.d("folder \"$directoryName\" removed because its directory was deleted")
        return true
    }

    private suspend fun directoryStillExistsFor(
        folder: FolderEntity,
        foldersById: Map<String, FolderEntity>
    ): Boolean {
        val spaceDirectory = spaceDirectories.directoryFor(folder.spaceId) ?: return false
        var directory = spaceDirectory
        for (segment in VaultPaths.folderSegmentsFor(folder.folderId, foldersById)) {
            directory = File(directory, segment)
        }
        return directory.isDirectory
    }

    private suspend fun importFileWhileLocked(file: File): VaultImportReport {
        val path = file.absolutePath

        if (!file.isFile) return removeNoteForDeletedFile(path)

        val markdownOnDisk = try {
            file.readText()
        } catch (cause: IOException) {
            return VaultImportReport(VaultImportOutcome.FAILED, vaultPathOf(file), cause.message)
        }

        if (fileLedger.wasWrittenByUs(path, markdownOnDisk)) {
            return VaultImportReport(VaultImportOutcome.IGNORED_OUR_OWN_WRITE, vaultPathOf(file))
        }

        val (frontMatter, _) = VaultMarkdownScanner.splitFrontMatter(markdownOnDisk)
        val existingNote = frontMatter.noteId?.let { noteDao.getNoteById(it) }

        return try {
            if (existingNote == null) {
                createNoteFromFile(file, markdownOnDisk, frontMatter)
            } else {
                updateNoteFromFile(file, markdownOnDisk, frontMatter, existingNote)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: IOException) {
            VaultImportReport(VaultImportOutcome.FAILED, vaultPathOf(file), cause.message)
        } catch (cause: RuntimeException) {
            VaultImportReport(VaultImportOutcome.FAILED, vaultPathOf(file), cause.message)
        }
    }

    private suspend fun updateNoteFromFile(
        file: File,
        markdownOnDisk: String,
        frontMatter: VaultFrontMatter,
        existingNote: NoteMetadataEntity
    ): VaultImportReport {
        val path = file.absolutePath
        val baseMarkdown = fileLedger.baseMarkdownForPath(path)
        val noteUpdatedAtWhenWritten = fileLedger.noteUpdatedAtForPath(path)
        val currentBlocks = noteRepository.getNoteContent(existingNote.noteId)?.blocks.orEmpty()

        val appAlsoChangedTheNote = noteUpdatedAtWhenWritten != null &&
            existingNote.updatedAt > noteUpdatedAtWhenWritten

        var hadConflict = false
        val markdownToApply = if (baseMarkdown != null && appAlsoChangedTheNote) {
            val currentMarkdown = renderCurrentMarkdown(existingNote, currentBlocks, file)
            val merged = VaultMarkdownMerge.merge(baseMarkdown, markdownOnDisk, currentMarkdown)
            if (merged.hasRealConflict) {
                hadConflict = true
                writeConflictCopy(file, currentMarkdown)
            }
            merged.mergedMarkdown
        } else {
            markdownOnDisk
        }

        val readResult = readBlocks(markdownToApply, currentBlocks, existingNote.spaceId)
        val noteAlreadyMatchesTheFile = readResult.blocks == currentBlocks &&
            !metadataChanged(existingNote, frontMatter, file)

        if (noteAlreadyMatchesTheFile) {
            fileLedger.recordWrite(existingNote.noteId, path, markdownOnDisk, existingNote.updatedAt)
            return VaultImportReport(VaultImportOutcome.UNCHANGED, existingNote.title)
        }

        noteRepository.saveNote(
            metadata = existingNote.copy(
                title = frontMatter.title?.takeIf { it.isNotBlank() } ?: existingNote.title,
                icon = frontMatter.icon ?: existingNote.icon,
                isFavorite = frontMatter.isFavorite,
                folderId = folderIdForExistingNote(existingNote, file),
                // The file is here, so the note is not in the trash.
                trashedAt = null
            ),
            content = NoteContent(blocks = readResult.blocks)
        )
        fileLedger.recordWrite(existingNote.noteId, path, markdownOnDisk, existingNote.updatedAt)

        readResult.problems.forEach { problem -> VaultLog.e("${vaultPathOf(file)}: $problem") }

        return VaultImportReport(
            outcome = if (hadConflict) VaultImportOutcome.IMPORTED_WITH_CONFLICT else VaultImportOutcome.IMPORTED,
            noteTitle = existingNote.title
        )
    }

    private suspend fun createNoteFromFile(
        file: File,
        markdownOnDisk: String,
        frontMatter: VaultFrontMatter
    ): VaultImportReport {
        val title = frontMatter.title?.takeIf { it.isNotBlank() }
            ?: file.name.removeSuffix(VaultPaths.MARKDOWN_EXTENSION)

        val now = System.currentTimeMillis()
        val location = locationOfFile(file)
        val readResult = readBlocks(markdownOnDisk, emptyList(), location.spaceId)

        val metadata = NoteMetadataEntity(
            noteId = UUID.randomUUID().toString(),
            title = title,
            icon = frontMatter.icon,
            folderId = location.folderId,
            isDaily = false,
            dateString = null,
            createdAt = frontMatter.createdAt ?: now,
            updatedAt = now,
            filePath = "",
            isFavorite = frontMatter.isFavorite,
            spaceId = location.spaceId
        )

        fileLedger.recordWrite(
            noteId = metadata.noteId,
            path = file.absolutePath,
            markdown = markdownOnDisk,
            noteUpdatedAt = metadata.updatedAt
        )
        noteRepository.saveNoteInSpace(
            spaceId = location.spaceId,
            metadata = metadata,
            content = NoteContent(blocks = readResult.blocks)
        )
        readResult.problems.forEach { problem -> VaultLog.e("${vaultPathOf(file)}: $problem") }

        return VaultImportReport(VaultImportOutcome.CREATED, title)
    }

    private suspend fun removeNoteForDeletedFile(path: String): VaultImportReport {
        val fileName = vaultPathOf(File(path))

        val noteId = fileLedger.noteIdForPath(path)
        if (noteId == null) {
            VaultLog.d("$fileName is gone, but no note was recorded against that path - ignoring")
            return VaultImportReport(VaultImportOutcome.UNCHANGED, fileName)
        }

        val note = noteDao.getNoteById(noteId)
        if (note == null) {
            VaultLog.d("$fileName is gone, and its note no longer exists - ignoring")
            return VaultImportReport(VaultImportOutcome.UNCHANGED, fileName)
        }

        val fileElsewhere = findVaultFileCarrying(noteId, excludedPath = path)
        if (fileElsewhere != null) {
            fileLedger.forgetNote(noteId)
            VaultLog.d("\"${note.title}\" moved from $fileName to ${vaultPathOf(fileElsewhere)}")
            return VaultImportReport(
                outcome = VaultImportOutcome.UNCHANGED,
                noteTitle = note.title,
                detail = "moved to ${vaultPathOf(fileElsewhere)}"
            )
        }

        if (isTheInboxNote(note)) return emptyInboxForDeletedFile(note, fileName)

        fileLedger.forgetNote(noteId)
        noteRepository.saveNote(
            metadata = note.copy(trashedAt = System.currentTimeMillis()),
            content = NoteContent(blocks = noteRepository.getNoteContent(noteId)?.blocks.orEmpty())
        )
        VaultLog.d("\"${note.title}\" moved to Trash because $fileName was deleted")
        return VaultImportReport(VaultImportOutcome.IMPORTED, note.title, "moved to Trash")
    }

    private fun isTheInboxNote(note: NoteMetadataEntity): Boolean =
        !note.isDaily && !note.isSubNote && note.title.equals(INBOX_NOTE_TITLE, ignoreCase = true)

    private suspend fun emptyInboxForDeletedFile(
        note: NoteMetadataEntity,
        fileName: String
    ): VaultImportReport {
        fileLedger.forgetNote(note.noteId)

        val blocksStillHeld = noteRepository.getNoteContent(note.noteId)?.blocks.orEmpty()
            .filterNot { it.isDeleted }

        if (blocksStillHeld.isEmpty()) {
            VaultMirrorTrigger.requestNoteRefresh(note.noteId)
            VaultLog.d("$fileName was deleted, the Inbox was already empty - writing the file back")
            return VaultImportReport(VaultImportOutcome.UNCHANGED, note.title, "written back empty")
        }

        noteRepository.saveNote(metadata = note, content = NoteContent(blocks = emptyList()))
        VaultLog.d("Inbox emptied because $fileName was deleted - the note itself stays")
        return VaultImportReport(VaultImportOutcome.IMPORTED, note.title, "emptied instead of trashed")
    }

    // Vault-relative, so a move between directories is visible in the log.
    private fun vaultPathOf(file: File): String =
        file.toRelativeString(vaultExporter.vaultRootDirectory)

    private fun findVaultFileCarrying(noteId: String, excludedPath: String): File? =
        vaultExporter.vaultRootDirectory.walkTopDown()
            .filter { it.isFile && VaultPaths.isImportableMarkdownFile(it.name) }
            .filter { it.absolutePath != excludedPath }
            .firstOrNull { candidate ->
                val text = try {
                    candidate.readText()
                } catch (_: IOException) {
                    return@firstOrNull false
                }
                VaultMarkdownScanner.splitFrontMatter(text).first.noteId == noteId
            }

    private suspend fun readBlocks(
        markdown: String,
        existingBlocks: List<NoteBlock>,
        spaceId: String
    ) = NoteMarkdownReader.readNote(
            VaultNoteReadRequest(
                markdown = markdown,
                existingBlocks = existingBlocks,
                timestamp = System.currentTimeMillis(),
                generateBlockId = { UUID.randomUUID().toString() },
                noteIdsByLowercaseTitle = noteIdsByLowercaseTitle(spaceId),
                categoryIdsByLowercaseName = categoryIdsByLowercaseName(spaceId)
            )
        )

    private suspend fun renderCurrentMarkdown(
        note: NoteMetadataEntity,
        blocks: List<NoteBlock>,
        file: File
    ): String = NoteMarkdownWriter.writeNote(
        VaultNoteWriteRequest(
            metadata = note,
            blocks = blocks,
            noteTitlesById = notesInSpace(note.spaceId).associate { it.noteId to it.title },
            categoryNamesById = liveCategories(note.spaceId).associate { it.categoryId to it.name },
            mediaPathPrefix = VaultPaths.mediaPathPrefixForDepth(folderDepthOf(file))
        )
    )

    private fun folderDepthOf(file: File): Int {
        val parent = file.parentFile ?: return 0
        val relative = parent.toRelativeString(vaultExporter.vaultRootDirectory)
        if (relative.isBlank() || relative == ".") return 0
        return relative.split(File.separatorChar).count { it.isNotBlank() }
    }

    private suspend fun notesInSpace(spaceId: String): List<NoteMetadataEntity> =
        noteDao.getAllNotesForBackup().filter { it.spaceId == spaceId }

    private suspend fun liveCategories(spaceId: String): List<CategoryEntity> =
        categoryDao.getAllCategoriesOnceAcrossSpaces()
            .filter { !it.isDeleted && it.spaceId == spaceId }

    private suspend fun categoryIdsByLowercaseName(spaceId: String): Map<String, String> =
        liveCategories(spaceId).associate { it.name.lowercase() to it.categoryId }

    private suspend fun noteIdsByLowercaseTitle(spaceId: String): Map<String, String> =
        notesInSpace(spaceId)
            .filter { it.trashedAt == null }
            .associate { it.title.lowercase() to it.noteId }

    private suspend fun locationOfFile(file: File): VaultFileLocation {
        val rootPath = vaultExporter.vaultRootDirectory.absolutePath
        val parentPath = file.parentFile?.absolutePath
            ?: return VaultFileLocation(activeSpaceStore.currentActiveSpaceId(), null)

        val segments = parentPath.removePrefix(rootPath).trim('/').split('/').filter { it.isNotBlank() }
        val topSegment = segments.firstOrNull()

        val spaceId = when {
            topSegment == null -> activeSpaceStore.currentActiveSpaceId()
            VaultPaths.looksLikeSpaceFolderName(topSegment) -> findOrCreateSpaceForFolderName(topSegment)
            else -> activeSpaceStore.currentActiveSpaceId()
        }

        val folderSegments = if (topSegment != null && VaultPaths.looksLikeSpaceFolderName(topSegment)) {
            segments.drop(1)
        } else {
            segments
        }

        if (folderSegments.isEmpty()) return VaultFileLocation(spaceId, null)

        // Daily notes and sub-notes live in directories the exporter owns, not folders.
        if (folderSegments.first() in reservedDirectoryNames) return VaultFileLocation(spaceId, null)

        var parentFolderId: String? = null
        for (segment in folderSegments) {
            parentFolderId = findOrCreateFolderNamed(segment, parentFolderId, spaceId)
        }
        return VaultFileLocation(spaceId, parentFolderId)
    }

    private suspend fun findOrCreateSpaceForFolderName(directoryName: String): String {
        spaceIdRecordedInDirectory(directoryName)?.let { return it }
        spaceDirectories.spaceIdForFolderName(directoryName)?.let { return it }

        val displayName = VaultPaths.displayNameFromSpaceFolderName(directoryName)
        val newSpaceId = spaceRepository.createSpace(displayName)
        VaultLog.d("created space \"$displayName\" from the vault")
        return newSpaceId
    }

    private suspend fun spaceIdRecordedInDirectory(directoryName: String): String? {
        val recordedSpaceId = spaceDirectories.spaceIdRecordedIn(directoryName) ?: return null
        if (spaceDirectories.folderNamesBySpaceId().containsKey(recordedSpaceId)) return recordedSpaceId
        if (spaceRepository.getSpace(recordedSpaceId) != null) return null

        val displayName = VaultPaths.displayNameFromSpaceFolderName(directoryName)
        spaceRepository.ensureSpaceExists(recordedSpaceId, displayName)
        VaultLog.d("adopted space \"$displayName\" ($recordedSpaceId) from the vault")
        return recordedSpaceId
    }

    private suspend fun findOrCreateFolderNamed(
        name: String,
        parentFolderId: String?,
        spaceId: String
    ): String {
        val existing = folderDao.getAllFoldersAcrossSpaces().first().firstOrNull { folder ->
            folder.spaceId == spaceId &&
                folder.name.equals(name, ignoreCase = true) &&
                folder.parentFolderId == parentFolderId
        }
        if (existing != null) return existing.folderId

        val now = System.currentTimeMillis()
        val newFolder = FolderEntity(
            folderId = UUID.randomUUID().toString(),
            name = name,
            parentFolderId = parentFolderId,
            createdAt = now,
            updatedAt = now,
            spaceId = spaceId
        )
        noteRepository.insertFolderInSpace(spaceId, newFolder)
        VaultLog.d("created folder \"$name\" from the vault")
        return newFolder.folderId
    }

    private suspend fun metadataChanged(
        note: NoteMetadataEntity,
        frontMatter: VaultFrontMatter,
        file: File
    ): Boolean {
        val titleFromFile = frontMatter.title?.takeIf { it.isNotBlank() } ?: note.title
        return titleFromFile != note.title ||
            frontMatter.isFavorite != note.isFavorite ||
            (frontMatter.icon ?: note.icon) != note.icon ||
            folderIdForExistingNote(note, file) != note.folderId ||
            note.trashedAt != null
    }

    // The directory a file sits in decides the note's folder. Daily notes and sub-notes keep
    // whichever folder they already have.
    private suspend fun folderIdForExistingNote(note: NoteMetadataEntity, file: File): String? = when {
        note.isDaily || note.isSubNote -> note.folderId
        else -> {
            val location = locationOfFile(file)
            if (location.spaceId == note.spaceId) location.folderId else note.folderId
        }
    }

    private fun writeConflictCopy(file: File, currentMarkdown: String) {
        val conflictFile = File(
            file.parentFile,
            file.name.removeSuffix(VaultPaths.MARKDOWN_EXTENSION) + VaultPaths.CONFLICT_MARKDOWN_SUFFIX
        )
        try {
            conflictFile.writeText(currentMarkdown)
            VaultLog.e("both sides changed the same block - kept the app's version in ${conflictFile.name}")
        } catch (cause: IOException) {
            VaultLog.e("Could not write ${conflictFile.name}: ${cause.message}")
        }
    }
}
