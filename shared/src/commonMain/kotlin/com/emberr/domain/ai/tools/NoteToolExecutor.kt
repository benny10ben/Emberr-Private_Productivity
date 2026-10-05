package com.emberr.domain.ai.tools

import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.database.withDatabasesAsTables
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.util.export.ExportEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class NoteToolExecutor(
    private val noteRepository: NoteRepository,
    private val activeSpaceStore: ActiveSpaceStore,
    private val toolCallEvents: NoteToolCallEvents
) {

    suspend fun run(toolName: String, arguments: Map<String, String>): NoteToolResult {
        val result = try {
            when (toolName) {
                NoteTools.listNotes.name -> listNotes(arguments["folder_path"].orEmpty())
                NoteTools.readNote.name -> readNote(arguments["note_id"].orEmpty())
                NoteTools.searchNotes.name -> searchNotes(arguments["query"].orEmpty())
                else -> return NoteToolResult.Failure("Unknown tool: $toolName")
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            NoteToolResult.Failure("Could not read the notes: ${cause.message ?: "unknown error"}")
        }

        toolCallEvents.notifyCalled(NoteToolCallSummary(toolName, describeCall(toolName, arguments, result)))
        return result
    }

    private suspend fun listNotes(folderPath: String): NoteToolResult {
        val folderPathsById = folderPathsById()
        val wantedFolder = folderPath.trim().trim('/')

        if (wantedFolder.isNotEmpty() && folderPathsById.values.none { it.equals(wantedFolder, ignoreCase = true) }) {
            return NoteToolResult.Failure("No folder at \"$folderPath\"")
        }

        val matchingNotes = noteRepository.getAllNotes().first()
            .map { it.toFoundNote(folderPathsById) }
            .filter { wantedFolder.isEmpty() || it.folderPath.isInsideFolder(wantedFolder) }

        return NoteToolResult.Notes(
            notes = matchingNotes.take(MAX_LISTED_NOTES),
            truncated = matchingNotes.size > MAX_LISTED_NOTES
        )
    }

    private suspend fun readNote(noteId: String): NoteToolResult {
        val metadata = noteRepository.getNoteById(noteId)
            ?.takeIf { it.trashedAt == null && !it.isTemplate && activeSpaceStore.isActiveSpace(it.spaceId) }
            ?: return NoteToolResult.Failure("No note with note_id \"$noteId\"")

        val blocks = noteRepository.getNoteContent(noteId)?.blocks.orEmpty().withDatabasesAsTables(noteRepository)
        val noteTitlesById = noteRepository.getAllNotes().first().associate { it.noteId to it.title }
        val categoryNamesById = noteRepository.getAllCategories().first()
            .filter { !it.isDeleted }
            .associate { it.categoryId to it.name }

        val title = metadata.displayTitle()
        val markdown = ExportEngine.generateMarkdown(blocks, title, noteTitlesById, categoryNamesById)
        val limitedMarkdown = if (markdown.length > MAX_NOTE_CHARACTERS) {
            markdown.take(MAX_NOTE_CHARACTERS) + "\n\n[Note truncated - ${markdown.length} characters total]"
        } else {
            markdown
        }

        return NoteToolResult.FullNote(noteId = noteId, title = title, markdown = limitedMarkdown)
    }

    private suspend fun searchNotes(query: String): NoteToolResult {
        if (query.isBlank()) return NoteToolResult.Failure("Search query cannot be blank")

        val folderPathsById = folderPathsById()
        val matchingNotes = noteRepository.searchNotes(query.trim()).map { it.note.toFoundNote(folderPathsById) }

        return NoteToolResult.Notes(
            notes = matchingNotes.take(MAX_LISTED_NOTES),
            truncated = matchingNotes.size > MAX_LISTED_NOTES
        )
    }

    private suspend fun folderPathsById(): Map<String, String> {
        val foldersById = noteRepository.getAllFolders().first().associateBy { it.folderId }
        return foldersById.mapValues { (_, folder) -> pathOf(folder, foldersById) }
    }

    private fun pathOf(folder: FolderEntity, foldersById: Map<String, FolderEntity>): String {
        val names = mutableListOf<String>()
        val visitedFolderIds = mutableSetOf<String>()
        var current: FolderEntity? = folder

        while (current != null && visitedFolderIds.add(current.folderId)) {
            names.add(0, current.name)
            current = current.parentFolderId?.let { foldersById[it] }
        }
        return names.joinToString("/")
    }

    private fun String.isInsideFolder(folder: String): Boolean =
        equals(folder, ignoreCase = true) || startsWith("$folder/", ignoreCase = true)

    private fun NoteMetadataEntity.toFoundNote(folderPathsById: Map<String, String>) = FoundNote(
        noteId = noteId,
        title = displayTitle(),
        folderPath = folderId?.let { folderPathsById[it] }.orEmpty()
    )

    private fun NoteMetadataEntity.displayTitle(): String =
        title.ifBlank { dateString?.takeIf { isDaily } ?: "Untitled" }

    private fun describeCall(toolName: String, arguments: Map<String, String>, result: NoteToolResult): String =
        when (toolName) {
            NoteTools.listNotes.name -> {
                val folderPath = arguments["folder_path"].orEmpty()
                if (folderPath.isBlank()) "Looked at your notes" else "Looked at notes in \"$folderPath\""
            }
            NoteTools.readNote.name -> {
                val title = (result as? NoteToolResult.FullNote)?.title ?: arguments["note_id"].orEmpty()
                "Read \"$title\""
            }
            else -> "Searched notes for \"${arguments["query"].orEmpty()}\""
        }

    private companion object {
        const val MAX_NOTE_CHARACTERS = 20_000
        const val MAX_LISTED_NOTES = 200
    }
}
