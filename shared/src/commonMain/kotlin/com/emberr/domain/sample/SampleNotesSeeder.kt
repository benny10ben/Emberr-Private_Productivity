package com.emberr.domain.sample

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteKind
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.CanvasRepository
import com.emberr.domain.canvas.CanvasViewPositionStore
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.generateSnippet
import com.emberr.domain.repository.FavoriteNoteOrderStore
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class SampleNotesSeeder(
    private val repository: NoteRepository,
    private val settingsManager: SettingsManager,
    private val canvasRepository: CanvasRepository,
    private val canvasViewPositionStore: CanvasViewPositionStore,
    private val favoriteNoteOrderStore: FavoriteNoteOrderStore
) {

    private val starterFolderNames = listOf("Fitness", "Finance", "Reading List", "Travel", "Recipes")

    private val fitnessNoteTitles = listOf("Push day", "Pull day", "Leg day")

    private fun folderIdFor(folderName: String): String = "sample_folder_${folderName.lowercase().replace(' ', '_')}"

    suspend fun seedIfNeeded() {
        if (settingsManager.isSampleNotesSeeded()) return

        try {
            if (repository.getAllFolders().first().isNotEmpty() ||
                repository.getAllNotes().first().isNotEmpty()
            ) {
                settingsManager.saveSampleNotesSeeded(true)
                return
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

            for ((listedPosition, title) in fitnessNoteTitles.withIndex()) {
                val noteId = "sample_note_${title.lowercase().replace(' ', '_')}"
                repository.saveNote(
                    metadata = NoteMetadataEntity(
                        noteId = noteId,
                        title = title,
                        folderId = folderIdFor("Fitness"),
                        isFavorite = false,
                        isDaily = false,
                        dateString = null,
                        createdAt = createdAt,
                        updatedAt = createdAt,
                        filePath = "note_$noteId.json",
                        sortOrder = listedPosition + 1
                    ),
                    content = NoteContent(blocks = emptyList())
                )
            }

            val welcomeNoteBlocks = SampleNoteContent.buildBlocks(createdAt)
            repository.saveNote(
                metadata = NoteMetadataEntity(
                    noteId = WELCOME_NOTE_ID,
                    title = "Start here",
                    icon = "👋",
                    folderId = null,
                    isFavorite = true,
                    isDaily = false,
                    dateString = null,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    filePath = "note_$WELCOME_NOTE_ID.json",
                    snippet = generateSnippet(welcomeNoteBlocks),
                    showWordCount = true,
                    sortOrder = 1
                ),
                content = NoteContent(blocks = welcomeNoteBlocks)
            )

            repository.saveNote(
                metadata = NoteMetadataEntity(
                    noteId = CANVAS_NOTE_ID,
                    title = "Canvas tour",
                    folderId = null,
                    isFavorite = true,
                    isDaily = false,
                    dateString = null,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    filePath = "note_$CANVAS_NOTE_ID.json",
                    sortOrder = 2,
                    kind = NoteKind.CANVAS
                ),
                content = NoteContent(blocks = emptyList())
            )
            canvasRepository.saveChanges(CANVAS_NOTE_ID, SampleCanvasContent.build(CANVAS_NOTE_ID, createdAt))
            canvasViewPositionStore.save(CANVAS_NOTE_ID, SampleCanvasContent.startingViewPosition)
            favoriteNoteOrderStore.saveOrder(listOf(WELCOME_NOTE_ID, CANVAS_NOTE_ID))

            settingsManager.saveSampleNotesSeeded(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private companion object {
        const val WELCOME_NOTE_ID = "sample_note_welcome"
        const val CANVAS_NOTE_ID = "sample_note_canvas"
    }
}
