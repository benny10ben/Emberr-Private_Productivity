package com.emberr.data.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.emberr.data.local.room.dao.BlockDao
import com.emberr.data.local.room.dao.BookmarkBlockDao
import com.emberr.data.local.room.dao.CalendarEventExceptionDao
import com.emberr.data.local.room.dao.CanvasDao
import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.dao.CategoryDao
import com.emberr.data.local.room.dao.ChatSessionDao
import com.emberr.data.local.room.dao.DatabaseTemplateDao
import com.emberr.data.local.room.dao.DocumentBlockDao
import com.emberr.data.local.room.dao.FolderDao
import com.emberr.data.local.room.dao.ImageBlockDao
import com.emberr.data.local.room.dao.MediaReferenceDao
import com.emberr.data.local.room.dao.NoteDao
import com.emberr.data.local.room.dao.SelfHostDeletedApiConfigDao
import com.emberr.data.local.room.dao.SelfHostDeletedNoteDao
import com.emberr.data.local.room.dao.SpaceDao
import com.emberr.data.local.room.dao.TagDao
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
import com.emberr.data.local.room.entity.MediaReferenceEntity
import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.SelfHostDeletedApiConfigEntity
import com.emberr.data.local.room.entity.SelfHostDeletedNoteEntity
import com.emberr.data.local.room.entity.SpaceEntity
import com.emberr.data.local.room.entity.TagEntity

@Database(
    entities = [
        SpaceEntity::class,
        NoteMetadataEntity::class,
        FolderEntity::class,
        TagEntity::class,
        NoteBlockEntity::class,
        CalendarTaskEntity::class,
        ImageBlockEntity::class,
        DocumentBlockEntity::class,
        BookmarkBlockEntity::class,
        DatabaseTemplateEntity::class,
        CategoryEntity::class,
        SelfHostDeletedNoteEntity::class,
        ChatSessionEntity::class,
        SelfHostDeletedApiConfigEntity::class,
        CalendarEventExceptionEntity::class,
        MediaReferenceEntity::class,
        CanvasNodeEntity::class,
        CanvasEdgeEntity::class,
        CanvasStrokeEntity::class
    ],
    version = 1,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spaceDao(): SpaceDao
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun blockDao(): BlockDao
    abstract fun calendarTaskDao(): CalendarTaskDao
    abstract fun calendarEventExceptionDao(): CalendarEventExceptionDao
    abstract fun imageBlockDao(): ImageBlockDao
    abstract fun documentBlockDao(): DocumentBlockDao
    abstract fun bookmarkBlockDao(): BookmarkBlockDao
    abstract fun databaseTemplateDao(): DatabaseTemplateDao
    abstract fun categoryDao(): CategoryDao
    abstract fun selfHostDeletedNoteDao(): SelfHostDeletedNoteDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun selfHostDeletedApiConfigDao(): SelfHostDeletedApiConfigDao
    abstract fun mediaReferenceDao(): MediaReferenceDao
    abstract fun canvasDao(): CanvasDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
