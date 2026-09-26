package com.emberr.domain.backup.manual

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.BookmarkBlockDao
import com.emberr.data.local.room.dao.CalendarEventExceptionDao
import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.dao.CanvasDao
import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.data.local.room.dao.ChatSessionDao
import com.emberr.data.local.room.dao.DatabaseTemplateDao
import com.emberr.data.local.room.dao.DocumentBlockDao
import com.emberr.data.local.room.dao.FolderDao
import com.emberr.data.local.room.dao.ImageBlockDao
import com.emberr.data.local.room.dao.MediaReferenceDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.dao.SelfHostDeletedNoteDao
import com.emberr.data.local.room.dao.SpaceDao
import com.emberr.data.local.room.dao.TagDao
import com.emberr.data.local.room.entity.CalendarEventExceptionEntity
import com.emberr.data.local.room.entity.ChatSessionEntity
import com.emberr.data.local.room.entity.DatabaseTemplateEntity
import com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.canvas.CanvasMerge
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
    private val canvasDao: CanvasDao,
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

        val allBlocks = mutableListOf<com.emberr.data.local.room.entity.NoteBlockEntity>()
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
            noteTombstones = allNoteTombstones,
            canvasNodes = canvasDao.getAllNodesForBackup(),
            canvasEdges = canvasDao.getAllEdgesForBackup(),
            canvasStrokes = canvasDao.getAllStrokesForBackup()
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

            val blocksToSave = mutableListOf<com.emberr.data.local.room.entity.NoteBlockEntity>()

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
        restoreCanvases(
            CanvasContent(nodes = backupData.canvasNodes, edges = backupData.canvasEdges, strokes = backupData.canvasStrokes)
        )

        settingsManager.saveMediaReferenceListBuilt(false)
        mediaReferenceDao.deleteAllReferences()
    }

    private suspend fun restoreCanvases(backupCanvas: CanvasContent) {
        val noteIds = backupCanvas.nodes.map { it.noteId } + backupCanvas.edges.map { it.noteId } +
            backupCanvas.strokes.map { it.noteId }
        for (noteId in noteIds.distinct()) {
            if (noteDao.getNoteById(noteId) == null) continue
            val localCanvas = CanvasContent(
                nodes = canvasDao.getAllNodesForNoteIncludingDeleted(noteId),
                edges = canvasDao.getAllEdgesForNoteIncludingDeleted(noteId),
                strokes = canvasDao.getAllStrokesForNoteIncludingDeleted(noteId)
            )
            val backupCanvasForNote = CanvasContent(
                nodes = backupCanvas.nodes.filter { it.noteId == noteId },
                edges = backupCanvas.edges.filter { it.noteId == noteId },
                strokes = backupCanvas.strokes.filter { it.noteId == noteId }
            )
            val newerBackupItems = CanvasMerge.remoteItemsNewerThanLocal(localCanvas, backupCanvasForNote)
            canvasDao.upsertNodes(newerBackupItems.nodes)
            canvasDao.upsertEdges(newerBackupItems.edges)
            canvasDao.upsertStrokes(newerBackupItems.strokes)
        }
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
