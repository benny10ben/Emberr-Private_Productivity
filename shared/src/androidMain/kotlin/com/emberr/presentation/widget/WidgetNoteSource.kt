// Supplies the list of notes a widget's setup screen offers for selection.
package com.emberr.presentation.widget

import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.dao.SpaceDao
import com.emberr.data.local.room.entity.NoteMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

data class SelectableWidgetNote(
    val note: NoteMetadataEntity,
    val spaceName: String
)

class WidgetNoteSource(
    private val noteDao: NoteDao,
    private val spaceDao: SpaceDao
) {

    fun observeSelectableNotes(): Flow<List<SelectableWidgetNote>> =
        combine(
            noteDao.getAllNotesAcrossSpacesFlow(),
            spaceDao.getAllSpaces()
        ) { notes, spaces ->
            val spaceNamesById = spaces.associate { space -> space.spaceId to space.displayName }
            notes.map { note ->
                SelectableWidgetNote(
                    note = note,
                    spaceName = spaceNamesById[note.spaceId].orEmpty()
                )
            }
        }
            .flowOn(Dispatchers.IO)
            .catch { cause ->
                WidgetLog.e("Could not list notes for a widget setup screen", cause)
                emit(emptyList())
            }
}
