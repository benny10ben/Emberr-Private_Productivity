package com.emberr.domain.backup.manual

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.BlockDao
import com.emberr.data.local.room.BookmarkBlockDao
import com.emberr.data.local.room.CalendarEventExceptionDao
import com.emberr.data.local.room.CalendarEventExceptionEntity
import com.emberr.data.local.room.CalendarTaskDao
import com.emberr.data.local.room.CategoryDao
import com.emberr.data.local.room.ChatSessionDao
import com.emberr.data.local.room.ChatSessionEntity
import com.emberr.data.local.room.DatabaseTemplateDao
import com.emberr.data.local.room.DatabaseTemplateEntity
import com.emberr.data.local.room.DocumentBlockDao
import com.emberr.data.local.room.FolderDao
import com.emberr.data.local.room.ImageBlockDao
import com.emberr.data.local.room.MediaReferenceDao
import com.emberr.data.local.room.NoteDao
import com.emberr.data.local.room.SelfHostDeletedNoteDao
import com.emberr.data.local.room.SelfHostDeletedNoteEntity
import com.emberr.data.local.room.SpaceDao
import com.emberr.data.local.room.TagDao
import kotlinx.coroutines.flow.first

class BackupRepositoryImpl(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val blockDao: BlockDao,
    private val calendarTaskDao: CalendarTaskDao,
    private val categoryDao: CategoryDao,
    private val imageBlockDao: ImageBlockDao,
    private val documentBlockDao: DocumentBlockDao,
    private val bookmarkBlockDao: BookmarkBlockDao,
    private val mediaReferenceDao: MediaReferenceDao,
    private val spaceDao: SpaceDao,
    private val chatSessionDao: ChatSessionDao,
    private val databaseTemplateDao: DatabaseTemplateDao,
    private val calendarEventExceptionDao: CalendarEventExceptionDao,
    private val selfHostDeletedNoteDao: SelfHostDeletedNoteDao,
    private val settingsManager: SettingsManager
) : BackupRepository {

    override suspend fun createBackupData(): EmberrBackupData {
        val allNotes = noteDao.getAllNotesForBackup()

        val allSpaces = spaceDao.getAllSpacesForBackup()
        val allFolders = folderDao.getAllFoldersAcrossSpaces().first()
        val allTags = tagDao.getAllTagsAcrossSpaces().first()
        val allCategories = categoryDao.getAllCategoriesOnceAcrossSpaces()
        val allTasks = calendarTaskDao.getAllTasksAcrossSpacesFlow().first()
        val allImages = imageBlockDao.getAllImagesAcrossSpacesFlow().first()
        val allDocuments = documentBlockDao.getAllDocumentsAcrossSpacesFlow().first()
        val allBookmarks = bookmarkBlockDao.getAllBookmarksAcrossSpacesFlow().first()
        val allChatSessions = chatSessionDao.getAllSessionsIncludingDeleted()
        val allDatabaseTemplates = databaseTemplateDao.getAllTemplates().first()
        val allEventExceptions = calendarEventExceptionDao.getAllExceptionsFlow().first()
        val allNoteTombstones = selfHostDeletedNoteDao.getAllTombstones()

        val allBlocks = mutableListOf<com.emberr.data.local.room.NoteBlockEntity>()
        for (note in allNotes) {
            val blocksForNote = blockDao.getAllBlocksForNoteIncludingDeleted(note.noteId)
            allBlocks.addAll(blocksForNote)
        }

        return EmberrBackupData(
            version = 1,
            exportTimestamp = System.currentTimeMillis(),
            spaces = allSpaces,
            notes = allNotes,
            folders = allFolders,
            tags = allTags,
            categories = allCategories,
            blocks = allBlocks,
            calendarTasks = allTasks,
            imageBlocks = allImages,
            documentBlocks = allDocuments,
            bookmarkBlocks = allBookmarks,
            chatSessions = allChatSessions,
            databaseTemplates = allDatabaseTemplates,
            calendarEventExceptions = allEventExceptions,
            noteTombstones = allNoteTombstones
        )
    }

    override suspend fun restoreBackup(backupData: EmberrBackupData) {

        for (backupSpace in backupData.spaces) {
            val localSpace = spaceDao.getSpaceById(backupSpace.spaceId)
            if (localSpace == null || backupSpace.updatedAt > localSpace.updatedAt) {
                spaceDao.insertOrUpdateSpace(backupSpace)
            }
        }

        backupData.folders.forEach { folderDao.insertFolder(it) }
        backupData.tags.forEach { tagDao.insertOrUpdateTag(it) }
        backupData.categories.forEach { categoryDao.insertOrUpdateCategory(it) }

        val noteIdMapping = mutableMapOf<String, String>()

        for (backupNote in backupData.notes) {

            if (backupNote.isDaily && backupNote.dateString != null) {
                val localDailyNote = noteDao.getDailyNoteMetadata(backupNote.spaceId, backupNote.dateString)
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

        restoreChatSessions(backupData.chatSessions)
        restoreDatabaseTemplates(backupData.databaseTemplates)
        restoreEventExceptions(backupData.calendarEventExceptions)
        restoreNoteTombstones(backupData.noteTombstones)

        settingsManager.saveMediaReferenceListBuilt(false)
        mediaReferenceDao.deleteAllReferences()
    }

    private suspend fun restoreChatSessions(sessions: List<ChatSessionEntity>) {
        for (backupSession in sessions) {
            val localSession = chatSessionDao.getSession(backupSession.id)
            if (localSession == null || backupSession.updatedAt > localSession.updatedAt) {
                chatSessionDao.upsertSession(backupSession)
            }
        }
    }

    private suspend fun restoreDatabaseTemplates(templates: List<DatabaseTemplateEntity>) {
        val existingTemplateIds = databaseTemplateDao.getAllTemplates().first().map { it.templateId }.toSet()
        templates
            .filterNot { it.templateId in existingTemplateIds }
            .forEach { databaseTemplateDao.insertTemplate(it) }
    }

    private suspend fun restoreEventExceptions(exceptions: List<CalendarEventExceptionEntity>) {
        for (backupException in exceptions) {
            val localException = calendarEventExceptionDao.getException(
                backupException.blockId,
                backupException.occurrenceDate
            )
            if (localException == null || backupException.updatedAt > localException.updatedAt) {
                calendarEventExceptionDao.upsert(backupException)
            }
        }
    }

    private suspend fun restoreNoteTombstones(tombstones: List<SelfHostDeletedNoteEntity>) {
        for (backupTombstone in tombstones) {
            if (theDeletedNoteIsAliveAgain(backupTombstone)) continue

            val localTombstone = selfHostDeletedNoteDao.getTombstoneByNoteId(backupTombstone.noteId)
            if (localTombstone == null || backupTombstone.deletedAt > localTombstone.deletedAt) {
                selfHostDeletedNoteDao.upsertTombstone(backupTombstone)
            }
        }
    }

    private suspend fun theDeletedNoteIsAliveAgain(tombstone: SelfHostDeletedNoteEntity): Boolean {
        if (noteDao.getNoteById(tombstone.noteId) != null) return true
        if (!tombstone.isDaily || tombstone.dateString == null) return false

        return noteDao.getDailyNoteMetadata(tombstone.spaceId, tombstone.dateString) != null
    }
}
