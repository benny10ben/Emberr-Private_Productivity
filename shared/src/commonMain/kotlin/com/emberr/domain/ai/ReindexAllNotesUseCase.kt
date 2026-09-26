package com.emberr.domain.ai

import com.emberr.data.local.room.entity.NoteKind
import com.emberr.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class ReindexProgress(val completed: Int, val total: Int)

class ReindexAllNotesUseCase(
    private val noteRepository: NoteRepository
) {
    fun execute(): Flow<ReindexProgress> = flow {
        val notes = noteRepository.getAllNotesAcrossSpaces().filter { it.kind != NoteKind.CANVAS }
        val canvases = noteRepository.getAllCanvasNotesAcrossSpaces()
        val total = notes.size + canvases.size
        emit(ReindexProgress(0, total))

        notes.forEachIndexed { index, metadata ->
            val content = noteRepository.getNoteContent(metadata.noteId)
            if (content != null) {
                noteRepository.indexNote(metadata, content)
            }
            emit(ReindexProgress(index + 1, total))
        }
        canvases.forEachIndexed { index, metadata ->
            noteRepository.indexStoredCanvas(metadata.noteId)
            emit(ReindexProgress(notes.size + index + 1, total))
        }
    }.flowOn(Dispatchers.Default)
}
