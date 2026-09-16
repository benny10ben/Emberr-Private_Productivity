package com.emberr.data.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
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
        MediaReferenceEntity::class
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
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
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>