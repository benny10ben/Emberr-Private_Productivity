package com.emberr.core.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppDataEraserTest {

    private val workingDirectory = Files.createTempDirectory("emberr-app-data-eraser").toFile()
    private val emberrDirectory = File(workingDirectory, ".emberr")
    private val folderOutsideTheApp = File(workingDirectory, "my-synced-folder")

    @AfterTest
    fun deleteWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }

    @Test
    fun withoutARequestNothingIsDeleted() {
        writeAppData()

        eraseAppDataIfRequested(emberrDirectory)

        assertTrue(File(emberrDirectory, "emberr_database.db").isFile)
        assertTrue(File(emberrDirectory, "media/photo.jpg").isFile)
    }

    @Test
    fun requestedEraseDeletesEverythingAndThenTheRequestItself() {
        writeAppData()
        assertTrue(requestAppDataEraseOnNextLaunch(emberrDirectory))

        eraseAppDataIfRequested(emberrDirectory)

        assertEquals(emptyList(), emberrDirectory.list().orEmpty().toList())
    }

    @Test
    fun theSingleInstanceLockFilesAreKept() {
        writeAppData()
        File(emberrDirectory, "instance.lock").writeText("")
        File(emberrDirectory, "instance.socket").writeText("")
        requestAppDataEraseOnNextLaunch(emberrDirectory)

        eraseAppDataIfRequested(emberrDirectory)

        assertEquals(setOf("instance.lock", "instance.socket"), emberrDirectory.list().orEmpty().toSet())
    }

    @Test
    fun aLinkedFolderIsUnlinkedButItsFilesAreKept() {
        writeAppData()
        File(folderOutsideTheApp, "note.md").apply { parentFile.mkdirs() }.writeText("keep me")
        Files.createSymbolicLink(File(emberrDirectory, "vault").toPath(), folderOutsideTheApp.toPath())
        requestAppDataEraseOnNextLaunch(emberrDirectory)

        eraseAppDataIfRequested(emberrDirectory)

        assertFalse(File(emberrDirectory, "vault").exists())
        assertEquals("keep me", File(folderOutsideTheApp, "note.md").readText())
    }

    @Test
    fun requestingAnEraseCreatesTheFolderWhenItIsMissing() {
        assertTrue(requestAppDataEraseOnNextLaunch(emberrDirectory))

        eraseAppDataIfRequested(emberrDirectory)

        assertEquals(emptyList(), emberrDirectory.list().orEmpty().toList())
    }

    private fun writeAppData() {
        File(emberrDirectory, "media").mkdirs()
        File(emberrDirectory, "emberr_database.db").writeText("notes")
        File(emberrDirectory, "emberr_ai_index.db").writeText("index")
        File(emberrDirectory, "media/photo.jpg").writeText("pixels")
    }
}
