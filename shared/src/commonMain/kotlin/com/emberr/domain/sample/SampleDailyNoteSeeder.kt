package com.emberr.domain.sample

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.domain.model.NoteContent
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.CancellationException

class SampleDailyNoteSeeder(
    private val repository: NoteRepository,
    private val settingsManager: SettingsManager
) {

    fun isSampleDayPending(): Boolean = !settingsManager.isSampleDailyNoteSeeded()

    suspend fun seedSampleDayIfNeeded(dateString: String) {
        if (!isSampleDayPending()) return

        try {
            if (repository.getSavedDailyNoteDates().isNotEmpty()) {
                settingsManager.saveSampleDailyNoteSeeded(true)
                return
            }

            val createdAt = System.currentTimeMillis()
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
