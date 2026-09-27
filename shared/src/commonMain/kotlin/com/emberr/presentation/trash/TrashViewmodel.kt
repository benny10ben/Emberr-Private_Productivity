package com.emberr.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Trash screen.
 * Observes all soft-deleted notes and handles restoring them to their original folders
 * or permanently wiping their data and associated files from the device.
 */
class TrashViewModel constructor(
    private val repository: NoteRepository
) : ViewModel() {

    val trashedNotes = repository.getTrashedNotes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val parentTitlesByNoteId = trashedNotes
        .map { notes -> repository.parentTitlesOfSubNotes(notes) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    fun restoreNote(noteId: String) {
        viewModelScope.launch {
            repository.restoreNote(noteId)
        }
    }

    fun permanentlyDelete(noteId: String, filePath: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId, filePath)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val currentTrash = trashedNotes.value
            currentTrash.forEach { note ->
                repository.deleteNote(note.noteId, note.filePath)
            }
        }
    }
}