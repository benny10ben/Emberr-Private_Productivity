package com.emberr.domain.backup.manual

import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.BookmarkBlockDao
import com.emberr.data.local.room.dao.CalendarEventExceptionDao
import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.dao.CanvasDao
import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.data.local.room.dao.ChatSessionDao
import com.emberr.data.local.room.dao.CustomPropertyDao
import com.emberr.data.local.room.dao.DocumentBlockDao
import com.emberr.data.local.room.dao.FolderDao
import com.emberr.data.local.room.dao.ImageBlockDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.dao.PropertyTagDao
import com.emberr.data.local.room.dao.SelfHostDeletedNoteDao
import com.emberr.data.local.room.dao.SpaceDao
import kotlinx.coroutines.flow.first

class BackupRepositoryImpl(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val blockDao: BlockDao,
    private val calendarTaskDao: CalendarTaskDao,
    private val categoryDao: CategoryDao,
    private val propertyTagDao: PropertyTagDao,
    private val customPropertyDao: CustomPropertyDao,
    private val imageBlockDao: ImageBlockDao,
    private val documentBlockDao: DocumentBlockDao,
    private val bookmarkBlockDao: BookmarkBlockDao,
    private val spaceDao: SpaceDao,
    private val chatSessionDao: ChatSessionDao,
    private val calendarEventExceptionDao: CalendarEventExceptionDao,
    private val selfHostDeletedNoteDao: SelfHostDeletedNoteDao,
    private val canvasDao: CanvasDao
) : BackupRepository {

    override suspend fun createBackupData(): EmberrBackupData {
        val allNotes = noteDao.getAllNotesForBackup()

        val allSpaces = spaceDao.getAllSpacesForBackup()
        val allFolders = folderDao.getAllFoldersAcrossSpaces().first()
        val allCategories = categoryDao.getAllCategoriesOnceAcrossSpaces()
        val allPropertyTags = propertyTagDao.getAllTagsForBackup()
        val allCustomProperties = customPropertyDao.getAllPropertiesForBackup()
        val allTasks = calendarTaskDao.getAllTasksAcrossSpacesFlow().first()
        val allImages = imageBlockDao.getAllImagesAcrossSpacesFlow().first()
        val allDocuments = documentBlockDao.getAllDocumentsAcrossSpacesFlow().first()
        val allBookmarks = bookmarkBlockDao.getAllBookmarksAcrossSpacesFlow().first()
        val allChatSessions = chatSessionDao.getAllSessionsIncludingDeleted()
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
            categories = allCategories,
            propertyTags = allPropertyTags,
            customProperties = allCustomProperties,
            blocks = allBlocks,
            calendarTasks = allTasks,
            imageBlocks = allImages,
            documentBlocks = allDocuments,
            bookmarkBlocks = allBookmarks,
            chatSessions = allChatSessions,
            calendarEventExceptions = allEventExceptions,
            noteTombstones = allNoteTombstones,
            canvasNodes = canvasDao.getAllNodesForBackup(),
            canvasEdges = canvasDao.getAllEdgesForBackup(),
            canvasStrokes = canvasDao.getAllStrokesForBackup()
        )
    }
}
