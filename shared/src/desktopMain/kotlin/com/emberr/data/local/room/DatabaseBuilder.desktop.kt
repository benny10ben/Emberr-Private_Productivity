package com.emberr.data.local.room

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.emberr.core.desktop.DesktopAppStorage
import java.io.File

/**
 * Desktop-specific implementation that provides the OS file path
 * and binds the bundled KMP SQLite driver.
 */
fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(DesktopAppStorage.emberrDirectory, "emberr_database.db")
    dbFile.parentFile?.mkdirs()

    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    )
}

fun getDatabaseBuilder(dbFilePath: String): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath
    )
}

actual fun getRoomDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .build()
}