package com.emberr.domain.media

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.dao.MediaReferenceDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.entity.MediaReferenceEntity
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.selfhost.media.MediaReferenceScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaReferenceIndex(
    private val noteRepository: NoteRepository,
    private val noteDao: NoteDao,
    private val mediaReferenceDao: MediaReferenceDao,
    private val settingsManager: SettingsManager
) {

    private val rebuildLock = Mutex()

    suspend fun loadReferencedFileNames(): Set<String> = withContext(Dispatchers.IO) {
        if (!settingsManager.isMediaReferenceListBuilt()) {
            rebuildFromStoredNotes()
        } else {
            mediaReferenceDao.getAllReferencedFileNames().toSet() + loadCoverImageFileNames()
        }
    }

    suspend fun rebuildFromStoredNotes(): Set<String> = withContext(Dispatchers.IO) {
        rebuildLock.withLock {
            val referencedFileNames = mutableSetOf<String>()
            val rebuiltReferences = mutableListOf<MediaReferenceEntity>()

            noteRepository.getNotesModifiedSince(0L).forEach { metadata ->
                val content = if (metadata.isDaily && metadata.dateString != null) {
                    noteRepository.getDailyNoteInSpace(metadata.spaceId, metadata.dateString)
                } else {
                    noteRepository.getNoteContent(metadata.noteId)
                }

                val fileNamesInNote = MediaReferenceScanner.extractMediaFileNames(content?.blocks.orEmpty())
                referencedFileNames += fileNamesInNote
                rebuiltReferences += fileNamesInNote.map { fileName ->
                    MediaReferenceEntity(noteId = metadata.noteId, fileName = fileName)
                }
            }

            mediaReferenceDao.deleteAllReferences()
            if (rebuiltReferences.isNotEmpty()) {
                mediaReferenceDao.upsertReferences(rebuiltReferences)
            }

            settingsManager.saveMediaReferenceListBuilt(true)

            LocalMediaGcLog.d("rebuildFromStoredNotes: recorded ${rebuiltReferences.size} block media reference(s)")
            referencedFileNames + loadCoverImageFileNames()
        }
    }

    private suspend fun loadCoverImageFileNames(): Set<String> =
        noteDao.getAllCoverImagePaths()
            .filterNotNull()
            .map { it.substringAfterLast("/") }
            .filter { it.isNotBlank() }
            .toSet()
}
