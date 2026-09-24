package com.emberr.domain.update.appimage

import com.emberr.domain.update.AppUpdateException
import com.emberr.domain.update.sha256HexOf
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppImageReplacementTest {

    private val workingDirectory = Files.createTempDirectory("emberr-appimage-replacement").toFile()
    private val appImageFile = File(workingDirectory, "Emberr-x86_64.AppImage")
    private val downloadedFile = File(workingDirectory, ".Emberr-x86_64.AppImage.download")

    @AfterTest
    fun deleteWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }

    @Test
    fun verifiedDownloadReplacesTheAppImageAndStaysExecutable() {
        writeInstalledAppImage("old version")
        downloadedFile.writeText("new version")

        replaceAppImageWithVerifiedDownload(downloadedFile, appImageFile, sha256HexOf(downloadedFile))

        assertEquals("new version", appImageFile.readText())
        assertTrue(appImageFile.canExecute())
        assertFalse(downloadedFile.exists())
    }

    @Test
    fun hashCheckIgnoresLetterCase() {
        writeInstalledAppImage("old version")
        downloadedFile.writeText("new version")

        replaceAppImageWithVerifiedDownload(downloadedFile, appImageFile, sha256HexOf(downloadedFile).uppercase())

        assertEquals("new version", appImageFile.readText())
    }

    @Test
    fun damagedDownloadLeavesTheInstalledAppImageUntouched() {
        writeInstalledAppImage("old version")
        downloadedFile.writeText("damaged download")

        assertFailsWith<AppUpdateException> {
            replaceAppImageWithVerifiedDownload(downloadedFile, appImageFile, "0".repeat(64))
        }

        assertEquals("old version", appImageFile.readText())
        assertTrue(appImageFile.canExecute())
        assertFalse(downloadedFile.exists())
    }

    @Test
    fun sha256MatchesAKnownValue() {
        downloadedFile.writeText("abc")

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256HexOf(downloadedFile)
        )
    }

    private fun writeInstalledAppImage(content: String) {
        appImageFile.writeText(content)
        Files.setPosixFilePermissions(appImageFile.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
    }
}
