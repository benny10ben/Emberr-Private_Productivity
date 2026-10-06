package com.emberr.domain.backup.manual

import com.emberr.data.local.room.APP_DATABASE_VERSION
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BackupRestoreFilesTest {

    private val workingDirectory = Files.createTempDirectory("emberr-restore-files").toFile()

    @AfterTest
    fun deleteWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }

    private fun fakeDatabaseFile(databaseVersion: Int): File {
        val header = ByteBuffer.allocate(100)
        header.put("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
        header.putInt(60, databaseVersion)
        return File(workingDirectory, "backup.db").apply { writeBytes(header.array()) }
    }

    @Test
    fun aBackupFromThisVersionCanBeRestored() {
        checkBackupDatabaseCanBeRestored(fakeDatabaseFile(APP_DATABASE_VERSION))
    }

    @Test
    fun aBackupFromAnOlderVersionCanBeRestored() {
        checkBackupDatabaseCanBeRestored(fakeDatabaseFile(1))
    }

    @Test
    fun aBackupFromANewerVersionIsRefused() {
        assertFailsWith<IllegalStateException> {
            checkBackupDatabaseCanBeRestored(fakeDatabaseFile(APP_DATABASE_VERSION + 1))
        }
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() {
        val notADatabase = File(workingDirectory, "notes.txt").apply { writeText("x".repeat(200)) }

        assertFailsWith<IllegalStateException> { checkBackupDatabaseCanBeRestored(notADatabase) }
    }

    @Test
    fun aMediaFileNameCannotPointOutsideTheMediaFolder() {
        assertEquals("photo.png", safeMediaFileName("media/../../photo.png", "media/"))
    }

    @Test
    fun aMediaEntryWithoutAFileNameIsSkipped() {
        assertNull(safeMediaFileName("media/", "media/"))
    }

    @Test
    fun aNewSafetyCopyReplacesTheOldOne() = runTest {
        val backupsDirectory = File(workingDirectory, "backups")
        replaceSafetyCopy(backupsDirectory) { it.writeText("old data") }

        replaceSafetyCopy(backupsDirectory) { it.writeText("current data") }

        assertEquals("current data", File(backupsDirectory, SAFETY_COPY_FILE_NAME).readText())
    }

    @Test
    fun aFailedSafetyCopyKeepsTheOldOneAndStopsTheRestore() = runTest {
        val backupsDirectory = File(workingDirectory, "backups")
        replaceSafetyCopy(backupsDirectory) { it.writeText("old data") }

        assertFailsWith<IllegalStateException> {
            replaceSafetyCopy(backupsDirectory) { error("disk full") }
        }

        assertEquals("old data", File(backupsDirectory, SAFETY_COPY_FILE_NAME).readText())
    }

    @Test
    fun restoredMediaIsMovedIntoTheMediaFolder() {
        val mediaDirectory = File(workingDirectory, "media")
        val restoredMediaDirectory = File(workingDirectory, "restored").apply { mkdirs() }
        File(restoredMediaDirectory, "photo.png").writeText("image")

        moveRestoredMediaInto(mediaDirectory, restoredMediaDirectory)

        assertEquals("image", File(mediaDirectory, "photo.png").readText())
        assertFalse(File(restoredMediaDirectory, "photo.png").exists())
    }
}
