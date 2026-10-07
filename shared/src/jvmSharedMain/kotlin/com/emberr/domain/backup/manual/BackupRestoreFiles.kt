package com.emberr.domain.backup.manual

import com.emberr.data.local.room.APP_DATABASE_VERSION
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val SQLITE_HEADER_SIZE = 100
private const val SQLITE_USER_VERSION_OFFSET = 60L
private val SQLITE_FILE_START = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

fun checkBackupDatabaseCanBeRestored(databaseFile: File) {
    val backupDatabaseVersion = readDatabaseVersion(databaseFile)
    check(backupDatabaseVersion <= APP_DATABASE_VERSION) {
        "This backup was made with a newer version of Emberr. Update Emberr on this device, then try again."
    }
}

fun safeMediaFileName(zipEntryName: String, mediaEntryPrefix: String): String? =
    File(zipEntryName.substringAfter(mediaEntryPrefix)).name.takeIf { it.isNotBlank() }

fun moveRestoredMediaInto(mediaDirectory: File, restoredMediaDirectory: File) {
    mediaDirectory.mkdirs()
    restoredMediaDirectory.listFiles()?.forEach { restoredFile ->
        Files.move(
            restoredFile.toPath(),
            File(mediaDirectory, restoredFile.name).toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

private fun readDatabaseVersion(databaseFile: File): Int =
    RandomAccessFile(databaseFile, "r").use { file ->
        check(file.length() >= SQLITE_HEADER_SIZE) { "This file is not an Emberr backup." }
        val fileStart = ByteArray(SQLITE_FILE_START.size)
        file.readFully(fileStart)
        check(fileStart.contentEquals(SQLITE_FILE_START)) { "This file is not an Emberr backup." }
        file.seek(SQLITE_USER_VERSION_OFFSET)
        file.readInt()
    }
