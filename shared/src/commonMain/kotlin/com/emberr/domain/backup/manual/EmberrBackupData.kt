package com.emberr.domain.backup.manual

import com.emberr.data.local.room.entity.BookmarkBlockEntity
import com.emberr.data.local.room.entity.CalendarEventExceptionEntity
import com.emberr.data.local.room.entity.CalendarTaskEntity
import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasStrokeEntity
import com.emberr.data.local.room.entity.CategoryEntity
import com.emberr.data.local.room.entity.ChatSessionEntity
import com.emberr.data.local.room.entity.DatabaseTemplateEntity
import com.emberr.data.local.room.entity.DocumentBlockEntity
import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.ImageBlockEntity
import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity
import com.emberr.data.local.room.entity.SpaceEntity
import com.emberr.data.local.room.entity.TagEntity
import kotlinx.serialization.Serializable

@Serializable
data class EmberrBackupData(
    val version: Int = 1,
    val exportTimestamp: Long,
    val spaces: List<SpaceEntity> = emptyList(),
    val notes: List<NoteMetadataEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val blocks: List<NoteBlockEntity> = emptyList(),
    val calendarTasks: List<CalendarTaskEntity> = emptyList(),
    val imageBlocks: List<ImageBlockEntity> = emptyList(),
    val documentBlocks: List<DocumentBlockEntity> = emptyList(),
    val bookmarkBlocks: List<BookmarkBlockEntity> = emptyList(),
    val chatSessions: List<ChatSessionEntity> = emptyList(),
    val databaseTemplates: List<DatabaseTemplateEntity> = emptyList(),
    val calendarEventExceptions: List<CalendarEventExceptionEntity> = emptyList(),
    val noteTombstones: List<SelfHostDeletedNoteEntity> = emptyList(),
    val canvasNodes: List<CanvasNodeEntity> = emptyList(),
    val canvasEdges: List<CanvasEdgeEntity> = emptyList(),
    val canvasStrokes: List<CanvasStrokeEntity> = emptyList()
)
