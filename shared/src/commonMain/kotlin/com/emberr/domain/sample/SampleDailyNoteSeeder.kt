package com.emberr.domain.sample

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.NoteKind
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.CanvasRepository
import com.emberr.domain.database.databaseCellBlockId
import com.emberr.domain.database.withColumnAdded
import com.emberr.domain.database.withSettingTimesStamped
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException

class SampleDailyNoteSeeder(
    private val repository: NoteRepository,
    private val settingsManager: SettingsManager,
    private val canvasRepository: CanvasRepository
) {

    fun isSampleDayPending(): Boolean = !settingsManager.isSampleDailyNoteSeeded()

    suspend fun seedSampleDayIfNeeded(dateString: String) {
        if (!isSampleDayPending()) return

        try {
            if (repository.getSavedDailyNoteDates().isNotEmpty()) {
                settingsManager.saveSampleDailyNoteSeeded(true)
                return
            }

            repository.createDatabase(SampleDailyNoteContent.DATABASE_ID)
            val createdAt = System.currentTimeMillis()
            val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
            repository.changeDatabaseSettings(SampleDailyNoteContent.DATABASE_ID) { settings ->
                settings.copy(title = SampleDailyNoteContent.DATABASE_TITLE, updatedAt = createdAt)
                    .withColumnAdded(statusColumn)
                    .withSettingTimesStamped(before = settings, now = createdAt)
            }
            repository.reorderPropertyTags(PropertyType.STATUS.name, SampleDailyNoteContent.databaseStatusOptions)
            SampleDailyNoteContent.databaseRows.forEachIndexed { index, row ->
                val rowNoteId = "sample_daily_database_row_${index + 1}"
                val rowCreatedAt = createdAt + index
                repository.saveNote(
                    metadata = NoteMetadataEntity(
                        noteId = rowNoteId,
                        title = row.title,
                        folderId = null,
                        isDaily = false,
                        dateString = null,
                        createdAt = rowCreatedAt,
                        updatedAt = rowCreatedAt,
                        filePath = "",
                        isSubNote = true,
                        databaseId = SampleDailyNoteContent.DATABASE_ID
                    ),
                    content = NoteContent(
                        blocks = listOf(
                            PropertyBlock(
                                id = databaseCellBlockId(statusColumn, rowNoteId),
                                propertyType = PropertyType.STATUS,
                                tags = listOf(row.status),
                                updatedAt = rowCreatedAt
                            )
                        )
                    )
                )
            }
            repository.saveNote(
                metadata = NoteMetadataEntity(
                    noteId = SampleDailyNoteContent.CANVAS_NOTE_ID,
                    title = "",
                    folderId = null,
                    isDaily = false,
                    dateString = null,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    filePath = "note_${SampleDailyNoteContent.CANVAS_NOTE_ID}.json",
                    isSubNote = true,
                    kind = NoteKind.CANVAS
                ),
                content = NoteContent(blocks = emptyList())
            )
            canvasRepository.saveChanges(
                SampleDailyNoteContent.CANVAS_NOTE_ID,
                SampleCanvasContent.buildDailyCards(SampleDailyNoteContent.CANVAS_NOTE_ID, createdAt)
            )
            repository.saveDailyNote(
                dateString = dateString,
                content = NoteContent(blocks = SampleDailyNoteContent.buildBlocks(createdAt))
            )
            settingsManager.saveSampleDailyNoteSeeded(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
