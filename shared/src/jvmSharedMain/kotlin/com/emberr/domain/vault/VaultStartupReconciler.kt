// On launch, pulls in files changed while the app was closed before the export can overwrite them.

package com.emberr.domain.vault

import com.emberr.data.local.room.dao.NoteDao
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

// A file's time and a note's updatedAt are written moments apart for the same change.
private const val CLOCK_TOLERANCE_MILLIS = 2_000L

private data class VaultFileContents(val text: String, val noteId: String?)

data class VaultReconcileReport(
    val filesImported: Int = 0,
    val notesCreated: Int = 0,
    val filesRemoved: Int = 0,
    val filesAlreadyInSync: Int = 0,
    val failures: List<String> = emptyList()
) {
    val didNothing: Boolean get() = filesImported == 0 && notesCreated == 0 && filesRemoved == 0
}

class VaultStartupReconciler(
    private val noteDao: NoteDao,
    private val noteRepository: NoteRepository,
    private val vaultImporter: VaultImporter,
    private val vaultExporter: VaultExporter
) {

    suspend fun reconcile(): VaultReconcileReport = withContext(Dispatchers.IO) {
        val vaultRootDirectory = vaultExporter.vaultRootDirectory
        if (!vaultRootDirectory.isDirectory) return@withContext VaultReconcileReport()

        val notesById = noteDao.getAllNotesForBackup().associateBy { it.noteId }
        val currentMarkdownByNoteId = vaultExporter.renderCurrentMarkdownByNoteId()
        val vaultFiles = vaultRootDirectory.walkTopDown()
            .filter { it.isFile && VaultPaths.isImportableMarkdownFile(it.name) }
            .sortedBy { it.absolutePath }
            .toList()

        val failures = mutableListOf<String>()
        var filesImported = 0
        var notesCreated = 0
        var filesRemoved = 0
        var filesAlreadyInSync = 0

        for (file in vaultFiles) {
            val contents = readContentsOf(file, failures) ?: continue
            val noteIdInFile = contents.noteId
            val note = noteIdInFile?.let { notesById[it] }

            // The app writes these files itself, so a newer file time means little. Only a file
            // whose text no longer matches its note can be an outside edit.
            // A missing entry means the note is trashed or a template, and the export removes the
            // file anyway.
            if (note != null) {
                val expectedMarkdown = currentMarkdownByNoteId[note.noteId]
                if (expectedMarkdown == null || expectedMarkdown == contents.text) {
                    filesAlreadyInSync++
                    continue
                }
            }

            try {
                when {
                    noteIdInFile != null && note == null && wasPermanentlyDeleted(noteIdInFile) -> {
                        if (file.delete()) filesRemoved++
                        else failures.add("Could not delete ${file.name} for a deleted note")
                    }
                    note == null -> {
                        if (importFile(file, failures) == VaultImportOutcome.CREATED) notesCreated++
                        else filesImported++
                    }
                    file.lastModified() > note.updatedAt + CLOCK_TOLERANCE_MILLIS -> {
                        importFile(file, failures)
                        filesImported++
                    }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                failures.add("${file.name}: ${cause.message ?: cause::class.simpleName}")
            }
        }

        VaultReconcileReport(
            filesImported = filesImported,
            notesCreated = notesCreated,
            filesRemoved = filesRemoved,
            filesAlreadyInSync = filesAlreadyInSync,
            failures = failures
        )
    }

    private suspend fun importFile(file: File, failures: MutableList<String>): VaultImportOutcome {
        val report = vaultImporter.importFile(file)
        if (report.outcome == VaultImportOutcome.FAILED) {
            failures.add("${file.name}: ${report.detail ?: "could not be read"}")
        }
        return report.outcome
    }

    private suspend fun wasPermanentlyDeleted(noteId: String): Boolean =
        noteRepository.getNoteTombstone(noteId) != null

    // Null means the file could not be read, which is different from having no id in it.
    private fun readContentsOf(file: File, failures: MutableList<String>): VaultFileContents? {
        val text = try {
            file.readText()
        } catch (cause: IOException) {
            failures.add("${file.name}: ${cause.message}")
            return null
        }
        val (frontMatter, _) = VaultMarkdownScanner.splitFrontMatter(text)
        return VaultFileContents(text = text, noteId = frontMatter.noteId)
    }
}
