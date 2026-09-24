package com.emberr.data.local.room

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseBackupBeforeUpdateTest {

    private val emberrDirectory = Files.createTempDirectory("emberr-database-backup").toFile()
    private val databaseFile = File(emberrDirectory, "emberr_database.db")
    private val backupsDirectory = File(emberrDirectory, "database-backups")
    private val lastRunVersionFile = File(backupsDirectory, "last-run-version.txt")

    @AfterTest
    fun deleteWorkingDirectory() {
        emberrDirectory.deleteRecursively()
    }

    @Test
    fun newVersionBacksUpTheDatabaseAndItsWriteAheadFiles() {
        writeDatabaseFiles()
        recordLastRunVersion("1.0.0")

        backUp(installedVersion = "1.1.0")

        val backupDirectory = File(backupsDirectory, "before-1.1.0")
        assertEquals("notes", File(backupDirectory, "emberr_database.db").readText())
        assertEquals("recent changes", File(backupDirectory, "emberr_database.db-wal").readText())
        assertEquals("shared memory", File(backupDirectory, "emberr_database.db-shm").readText())
        assertEquals("1.1.0", lastRunVersionFile.readText())
    }

    @Test
    fun firstRunWithAnExistingDatabaseStillBacksItUp() {
        writeDatabaseFiles()

        backUp(installedVersion = "1.0.0")

        assertTrue(File(backupsDirectory, "before-1.0.0/emberr_database.db").isFile)
        assertEquals("1.0.0", lastRunVersionFile.readText())
    }

    @Test
    fun sameVersionAsLastRunMakesNoBackup() {
        writeDatabaseFiles()
        recordLastRunVersion("1.0.0")

        backUp(installedVersion = "1.0.0")

        assertFalse(File(backupsDirectory, "before-1.0.0").exists())
    }

    @Test
    fun freshInstallWithoutADatabaseOnlyRecordsTheVersion() {
        backUp(installedVersion = "1.0.0")

        assertFalse(File(backupsDirectory, "before-1.0.0").exists())
        assertEquals("1.0.0", lastRunVersionFile.readText())
    }

    @Test
    fun unknownVersionFromADevelopmentRunDoesNothing() {
        writeDatabaseFiles()

        backUp(installedVersion = null)

        assertFalse(backupsDirectory.exists())
    }

    @Test
    fun onlyTheTwoNewestBackupsAreKept() {
        writeDatabaseFiles()
        createOldBackup("before-0.8.0", lastModified = 1_000_000L)
        createOldBackup("before-0.9.0", lastModified = 2_000_000L)
        recordLastRunVersion("0.9.0")

        backUp(installedVersion = "1.0.0")

        val remainingBackups = backupsDirectory.listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.toSet()
        assertEquals(setOf("before-0.9.0", "before-1.0.0"), remainingBackups)
    }

    private fun backUp(installedVersion: String?) {
        backUpDatabaseWhenAppVersionChanges(databaseFile, backupsDirectory, installedVersion)
    }

    private fun writeDatabaseFiles() {
        databaseFile.writeText("notes")
        File(databaseFile.path + "-wal").writeText("recent changes")
        File(databaseFile.path + "-shm").writeText("shared memory")
    }

    private fun recordLastRunVersion(version: String) {
        backupsDirectory.mkdirs()
        lastRunVersionFile.writeText(version)
    }

    private fun createOldBackup(folderName: String, lastModified: Long) {
        val backupDirectory = File(backupsDirectory, folderName).apply { mkdirs() }
        File(backupDirectory, "emberr_database.db").writeText("old notes")
        backupDirectory.setLastModified(lastModified)
    }
}
