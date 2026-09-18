// Loads one note's name and icon for the widget to draw.
package com.emberr.presentation.widget.noteshortcut

import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.presentation.widget.WidgetLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoteShortcutWidgetContentReader(private val noteDao: NoteDao) {

    suspend fun readContentOnce(noteId: String): NoteShortcutWidgetContent? =
        withContext(Dispatchers.IO) {
            val note = try {
                noteDao.getNoteById(noteId)
            } catch (cause: Exception) {
                WidgetLog.e("Could not read the note behind the shortcut", cause)
                return@withContext null
            }
            if (note == null || note.trashedAt != null) null else buildContent(note)
        }

    fun buildContent(note: NoteMetadataEntity): NoteShortcutWidgetContent =
        NoteShortcutWidgetContent(
            noteId = note.noteId,
            title = note.title.trim().ifBlank { "Untitled" },
            icon = note.icon?.trim()?.takeIf { it.isNotBlank() }
        )
}
