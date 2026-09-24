package com.emberr.data.local.room

import java.io.File
import java.io.IOException
import java.nio.file.Files

private const val BACKUPS_TO_KEEP = 2
private const val BACKUP_FOLDER_PREFIX = "before-"
private const val LAST_RUN_VERSION_FILE_NAME = "last-run-version.txt"
private val DATABASE_FILE_SUFFIXES = listOf("", "-wal", "-shm")

fun backUpDatabaseWhenAppVersionChanges(
    databaseFile: File,
    backupsDirectory: File,
    installedVersion: String?
) {
    if (installedVersion == null) return

    val lastRunVersionFile = File(backupsDirectory, LAST_RUN_VERSION_FILE_NAME)
    val lastRunVersion = lastRunVersionFile.takeIf { it.isFile }?.readText()?.trim()
    if (lastRunVersion == installedVersion) return

    try {
        backupsDirectory.mkdirs()
        if (databaseFile.isFile) {
            copyDatabaseFiles(databaseFile, File(backupsDirectory, "$BACKUP_FOLDER_PREFIX$installedVersion"))
            deleteBackupsBeyondTheNewest(backupsDirectory)
        }
        lastRunVersionFile.writeText(installedVersion)
    } catch (cause: IOException) {
        cause.printStackTrace()
    }
}

private fun copyDatabaseFiles(databaseFile: File, destinationDirectory: File) {
    destinationDirectory.deleteRecursively()
    destinationDirectory.mkdirs()

    DATABASE_FILE_SUFFIXES
        .map { suffix -> File(databaseFile.path + suffix) }
        .filter { it.isFile }
        .forEach { sourceFile ->
            Files.copy(sourceFile.toPath(), File(destinationDirectory, sourceFile.name).toPath())
        }
}

private fun deleteBackupsBeyondTheNewest(backupsDirectory: File) {
    backupsDirectory
        .listFiles { file -> file.isDirectory && file.name.startsWith(BACKUP_FOLDER_PREFIX) }
        .orEmpty()
        .sortedByDescending { it.lastModified() }
        .drop(BACKUPS_TO_KEEP)
        .forEach { it.deleteRecursively() }
}
