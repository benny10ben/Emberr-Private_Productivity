package com.emberr.domain.sample

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteKind
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.CanvasRepository
import com.emberr.domain.canvas.CanvasViewPositionStore
import com.emberr.domain.database.databaseCellBlockId
import com.emberr.domain.database.databaseNoteId
import com.emberr.domain.database.newDatabaseSettings
import com.emberr.domain.database.withColumnAdded
import com.emberr.domain.database.withSettingTimesStamped
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
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
                        updatedAt = STARTER_CONTENT_UPDATED_AT
                    ),
                    stampUpdatedAt = false
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
                        updatedAt = STARTER_CONTENT_UPDATED_AT,
                        filePath = "note_$noteId.json",
                        sortOrder = listedPosition + 1
                    ),
                    content = NoteContent(blocks = emptyList()),
                    stampUpdatedAt = false
                )
            }

            seedSampleDatabase(createdAt)
            val welcomeNoteBlocks = SampleNoteContent.buildBlocks(STARTER_CONTENT_UPDATED_AT)
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
                    updatedAt = STARTER_CONTENT_UPDATED_AT,
                    filePath = "note_$WELCOME_NOTE_ID.json",
                    snippet = generateSnippet(welcomeNoteBlocks),
                    showWordCount = true,
                    sortOrder = 1
                ),
                content = NoteContent(blocks = welcomeNoteBlocks),
                stampUpdatedAt = false
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
                    updatedAt = STARTER_CONTENT_UPDATED_AT,
                    filePath = "note_$CANVAS_NOTE_ID.json",
                    sortOrder = 2,
                    kind = NoteKind.CANVAS
                ),
                content = NoteContent(blocks = emptyList()),
                stampUpdatedAt = false
            )
            canvasRepository.saveChanges(
                CANVAS_NOTE_ID,
                SampleCanvasContent.build(CANVAS_NOTE_ID, STARTER_CONTENT_UPDATED_AT),
                stampNoteUpdatedAt = false
            )
            canvasViewPositionStore.save(CANVAS_NOTE_ID, SampleCanvasContent.startingViewPosition)
            favoriteNoteOrderStore.saveOrder(listOf(WELCOME_NOTE_ID, CANVAS_NOTE_ID), STARTER_CONTENT_UPDATED_AT)

            settingsManager.saveSampleNotesSeeded(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun seedSampleDatabase(createdAt: Long) {
        val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
        val emptySettings = newDatabaseSettings(SampleNoteContent.DATABASE_ID, STARTER_CONTENT_UPDATED_AT)
        val databaseSettings = emptySettings.copy(title = SampleNoteContent.DATABASE_TITLE)
            .withColumnAdded(statusColumn)
            .withSettingTimesStamped(before = emptySettings, now = STARTER_CONTENT_UPDATED_AT)
        repository.saveNote(
            metadata = NoteMetadataEntity(
                noteId = databaseNoteId(SampleNoteContent.DATABASE_ID),
                title = SampleNoteContent.DATABASE_TITLE,
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = createdAt,
                updatedAt = STARTER_CONTENT_UPDATED_AT,
                filePath = "",
                isSubNote = true,
                kind = NoteKind.DATABASE
            ),
            content = NoteContent(blocks = listOf(databaseSettings)),
            stampUpdatedAt = false
        )
        repository.reorderPropertyTags(PropertyType.STATUS.name, SampleNoteContent.databaseStatusOptions)
        SampleNoteContent.databaseRows.forEachIndexed { index, row ->
            val rowNoteId = "sample_note_database_row_${index + 1}"
            repository.saveNote(
                metadata = NoteMetadataEntity(
                    noteId = rowNoteId,
                    title = row.title,
                    folderId = null,
                    isDaily = false,
                    dateString = null,
                    createdAt = createdAt + index,
                    updatedAt = STARTER_CONTENT_UPDATED_AT,
                    filePath = "",
                    isSubNote = true,
                    databaseId = SampleNoteContent.DATABASE_ID
                ),
                content = NoteContent(
                    blocks = listOf(
                        PropertyBlock(
                            id = databaseCellBlockId(statusColumn, rowNoteId),
                            propertyType = PropertyType.STATUS,
                            tags = listOf(row.status),
                            updatedAt = STARTER_CONTENT_UPDATED_AT
                        )
                    )
                ),
                stampUpdatedAt = false
            )
        }
    }

    private companion object {
        const val WELCOME_NOTE_ID = "sample_note_welcome"
        const val CANVAS_NOTE_ID = "sample_note_canvas"
    }
}
