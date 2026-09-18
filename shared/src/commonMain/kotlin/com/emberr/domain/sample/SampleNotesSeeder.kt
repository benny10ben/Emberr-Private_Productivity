package com.emberr.domain.sample

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteContent
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class SampleNotesSeeder(
    private val repository: NoteRepository,
    private val settingsManager: SettingsManager
) {

    private val starterFolderNames = listOf("Personal", "Work", "Academics", "Others")

    private fun folderIdFor(folderName: String): String = "sample_folder_${folderName.lowercase()}"

    suspend fun seedIfNeededAndReturnFolderToOpen(): String? {
        if (settingsManager.isSampleNotesSeeded()) return null

        return try {
            if (repository.getAllFolders().first().isNotEmpty() ||
                repository.getAllNotes().first().isNotEmpty()
            ) {
                settingsManager.saveSampleNotesSeeded(true)
                return null
            }

            val createdAt = System.currentTimeMillis()
            for ((listedPosition, name) in starterFolderNames.withIndex().reversed()) {
                repository.insertFolder(
                    FolderEntity(
                        folderId = folderIdFor(name),
                        name = name,
                        parentFolderId = null,
                        createdAt = createdAt,
                        sortOrder = listedPosition + 1,
                        updatedAt = createdAt
                    )
                )
            }

            val folderToOpen = folderIdFor(starterFolderNames.first())
            repository.saveNote(
                metadata = NoteMetadataEntity(
                    noteId = WELCOME_NOTE_ID,
                    title = "Start here",
                    icon = "👋",
                    folderId = folderToOpen,
                    isDaily = false,
                    dateString = null,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    filePath = "note_$WELCOME_NOTE_ID.json",
                    showWordCount = true,
                    sortOrder = 1
                ),
                content = NoteContent(blocks = SampleNoteContent.buildBlocks(createdAt))
            )

            settingsManager.saveSampleNotesSeeded(true)
            folderToOpen
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private companion object {
        const val WELCOME_NOTE_ID = "sample_note_welcome"
    }
}
