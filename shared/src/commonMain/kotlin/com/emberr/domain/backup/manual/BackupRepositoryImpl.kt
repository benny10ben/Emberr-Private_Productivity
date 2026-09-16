package com.emberr.domain.backup.manual

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.BlockDao
import com.emberr.data.local.room.BookmarkBlockDao
import com.emberr.data.local.room.CalendarTaskDao
import com.emberr.data.local.room.DocumentBlockDao
import com.emberr.data.local.room.FolderDao
import com.emberr.data.local.room.ImageBlockDao
import com.emberr.data.local.room.MediaReferenceDao
import com.emberr.data.local.room.NoteDao
import com.emberr.data.local.room.TagDao
import kotlinx.coroutines.flow.first

class BackupRepositoryImpl(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val blockDao: BlockDao,
    private val calendarTaskDao: CalendarTaskDao,
    private val imageBlockDao: ImageBlockDao,
    private val documentBlockDao: DocumentBlockDao,
    private val bookmarkBlockDao: BookmarkBlockDao,
    private val mediaReferenceDao: MediaReferenceDao,
    private val settingsManager: SettingsManager
) : BackupRepository {

    override suspend fun createBackupData(): EmberrBackupData {
        val allNotes = noteDao.getAllNotesForBackup()

        val allFolders = folderDao.getAllFolders().first()
        val allTags = tagDao.getAllTags().first()
        val allTasks = calendarTaskDao.getAllTasksFlow().first()
        val allImages = imageBlockDao.getAllImagesFlow().first()
        val allDocuments = documentBlockDao.getAllDocumentsFlow().first()
        val allBookmarks = bookmarkBlockDao.getAllBookmarksFlow().first()

        val allBlocks = mutableListOf<com.emberr.data.local.room.NoteBlockEntity>()
        for (note in allNotes) {
            val blocksForNote = blockDao.getAllBlocksForNoteIncludingDeleted(note.noteId)
            allBlocks.addAll(blocksForNote)
        }

        return EmberrBackupData(
            version = 1,
            exportTimestamp = System.currentTimeMillis(),
            notes = allNotes,
            folders = allFolders,
            tags = allTags,
            blocks = allBlocks,
            calendarTasks = allTasks,
            imageBlocks = allImages,
            documentBlocks = allDocuments,
            bookmarkBlocks = allBookmarks
        )
    }

    override suspend fun restoreBackup(backupData: EmberrBackupData) {

        backupData.folders.forEach { folderDao.insertFolder(it) }
        backupData.tags.forEach { tagDao.insertOrUpdateTag(it) }

        val noteIdMapping = mutableMapOf<String, String>()

        for (backupNote in backupData.notes) {

            if (backupNote.isDaily && backupNote.dateString != null) {
                val localDailyNote = noteDao.getDailyNoteMetadata(backupNote.dateString)
                if (localDailyNote != null && localDailyNote.noteId != backupNote.noteId) {
                    noteIdMapping[backupNote.noteId] = localDailyNote.noteId
                    continue
                }
            }

            val localNote = noteDao.getNoteById(backupNote.noteId)

            val isNoteRescued = localNote != null && localNote.trashedAt != null && backupNote.trashedAt == null

            if (localNote == null || backupNote.updatedAt > localNote.updatedAt || isNoteRescued) {
                noteDao.insertOrUpdateMetadata(backupNote)
            }
        }

        val backupBlocksByNote = backupData.blocks.groupBy { it.noteId }

        for ((originalNoteId, backupBlocks) in backupBlocksByNote) {

            val targetNoteId = noteIdMapping[originalNoteId] ?: originalNoteId
            val localBlocksMap = blockDao.getAllBlocksForNoteIncludingDeleted(targetNoteId).associateBy { it.blockId }
            var maxDisplayOrder = localBlocksMap.values.maxOfOrNull { it.displayOrder } ?: -1

            val blocksToSave = mutableListOf<com.emberr.data.local.room.NoteBlockEntity>()

            for (backupBlock in backupBlocks) {
                val localBlock = localBlocksMap[backupBlock.blockId]
                val mappedBackupBlock = backupBlock.copy(noteId = targetNoteId)

                if (localBlock != null) {

                    val isBlockRescued = localBlock.isDeleted && !mappedBackupBlock.isDeleted

                    if (mappedBackupBlock.updatedAt > localBlock.updatedAt || isBlockRescued) {
                        blocksToSave.add(
                            mappedBackupBlock.copy(
                                displayOrder = localBlock.displayOrder,
                                isDeleted = mappedBackupBlock.isDeleted
                            )
                        )
                    }
                } else {
                    maxDisplayOrder++
                    blocksToSave.add(mappedBackupBlock.copy(displayOrder = maxDisplayOrder))
                }
            }

            if (blocksToSave.isNotEmpty()) {
                blockDao.insertOrUpdateBlocks(blocksToSave)
            }
        }

        if (backupData.calendarTasks.isNotEmpty()) {
            val mappedTasks = backupData.calendarTasks.map {
                it.copy(noteId = noteIdMapping[it.noteId] ?: it.noteId)
            }
            calendarTaskDao.upsertTasks(mappedTasks)
        }
        if (backupData.imageBlocks.isNotEmpty()) {
            val mappedImages = backupData.imageBlocks.map {
                it.copy(noteId = noteIdMapping[it.noteId] ?: it.noteId)
            }
            imageBlockDao.upsertImages(mappedImages)
        }
        if (backupData.documentBlocks.isNotEmpty()) {
            val mappedDocs = backupData.documentBlocks.map {
                it.copy(noteId = noteIdMapping[it.noteId] ?: it.noteId)
            }
            documentBlockDao.upsertDocuments(mappedDocs)
        }
        if (backupData.bookmarkBlocks.isNotEmpty()) {
            val mappedBookmarks = backupData.bookmarkBlocks.map {
                it.copy(noteId = noteIdMapping[it.noteId] ?: it.noteId)
            }
            bookmarkBlockDao.upsertBookmarks(mappedBookmarks)
        }

        settingsManager.saveMediaReferenceListBuilt(false)
        mediaReferenceDao.deleteAllReferences()
    }
}
