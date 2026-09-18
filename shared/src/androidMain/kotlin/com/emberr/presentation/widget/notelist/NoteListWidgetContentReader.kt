// Loads the note list from storage and turns it into rows the widget can draw.
package com.emberr.presentation.widget.notelist

import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.presentation.widget.WidgetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

private const val maximumNotesShown = 30
private const val maximumCharactersPerTitle = 120
private const val maximumCharactersPerSnippet = 120

class NoteListWidgetContentReader(
    private val noteDao: NoteDao
) {

    fun observeNotes(): Flow<List<NoteMetadataEntity>> =
        noteDao.getAllNotesAcrossSpacesFlow()
            .flowOn(Dispatchers.IO)
            .catch { cause -> WidgetLog.e("Stopped observing the note list", cause) }

    suspend fun readContentOnce(spaceId: String): NoteListWidgetContent? =
        withContext(Dispatchers.IO) {
            val notes = try {
                noteDao.getRecentNotes(spaceId, maximumNotesShown)
            } catch (cause: Exception) {
                WidgetLog.e("Could not read the note list", cause)
                return@withContext null
            }
            buildContent(spaceId, notes)
        }

    fun buildContent(spaceId: String, notes: List<NoteMetadataEntity>): NoteListWidgetContent =
        NoteListWidgetContent(
            notes = notes.filter { note -> note.spaceId == spaceId }.take(maximumNotesShown).map { note ->
                NoteListWidgetRow(
                    noteId = note.noteId,
                    title = note.title.trim().take(maximumCharactersPerTitle).ifBlank { "Untitled" },
                    snippet = note.snippet.trim().take(maximumCharactersPerSnippet)
                )
            }
        )
}
