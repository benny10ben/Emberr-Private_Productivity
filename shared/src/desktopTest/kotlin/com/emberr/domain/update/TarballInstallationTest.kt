package com.emberr.domain.update

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TarballInstallationTest {

    private val workingDirectory = Files.createTempDirectory("emberr-tarball-installation").toFile()
    private val dataHome = File(workingDirectory, "share").apply { mkdirs() }
    private val installDirectory = File(dataHome, "emberr").apply { mkdirs() }
    private val stagingDirectory = File(dataHome, "emberr-update")
    private val installation = TarballInstallation(installDirectory)

    @AfterTest
    fun deleteWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }

    @Test
    fun appRunningFromTheInstallFolderIsATarballInstall() {
        val javaHome = File(installDirectory, "lib/runtime").apply { mkdirs() }

        assertEquals(installDirectory.canonicalFile, tarballInstallDirectoryFor(javaHome.path, dataHome)?.canonicalFile)
    }

    @Test
    fun appRunningFromAnywhereElseIsNotATarballInstall() {
        val javaHome = File(workingDirectory, "Downloads/emberr/lib/runtime").apply { mkdirs() }

        assertNull(tarballInstallDirectoryFor(javaHome.path, dataHome))
        assertNull(tarballInstallDirectoryFor(null, dataHome))
    }

    @Test
    fun verifiedDownloadIsUnpackedIntoTheStagingFolder() {
        writeReleaseTarball(includeInstallScript = true)

        installation.install(release("1.0.2"), downloadMatching(installation.downloadFile))

        assertTrue(File(stagingDirectory, "install.sh").isFile)
        assertTrue(File(stagingDirectory, "emberr/bin/Emberr").isFile)
        assertTrue(installation.hasNewerStagedUpdate(installedVersion = "1.0.1"))
        assertFalse(File(dataHome, "emberr-update.partial").exists())
    }

    @Test
    fun stagedUpdateIsNotNewerThanTheSameOrALaterVersion() {
        writeReleaseTarball(includeInstallScript = true)

        installation.install(release("1.0.2"), downloadMatching(installation.downloadFile))

        assertFalse(installation.hasNewerStagedUpdate(installedVersion = "1.0.2"))
        assertFalse(installation.hasNewerStagedUpdate(installedVersion = "1.1.0"))
        assertFalse(installation.hasNewerStagedUpdate(installedVersion = null))
    }

    @Test
    fun damagedDownloadIsNotStaged() {
        writeReleaseTarball(includeInstallScript = true)

        assertFailsWith<AppUpdateException> {
            installation.install(release("1.0.2"), AppUpdateDownload(url = "", sha256 = "0".repeat(64), sizeBytes = 1))
        }

        assertFalse(stagingDirectory.exists())
    }

    @Test
    fun downloadWithoutAnInstallerIsNotStaged() {
        writeReleaseTarball(includeInstallScript = false)

        assertFailsWith<AppUpdateException> {
            installation.install(release("1.0.2"), downloadMatching(installation.downloadFile))
        }

        assertFalse(stagingDirectory.exists())
        assertFalse(File(dataHome, "emberr-update.partial").exists())
    }

    @Test
    fun startingWithAnOldStagedUpdateDiscardsItAndStartsNormally() {
        writeReleaseTarball(includeInstallScript = true)
        installation.install(release("1.0.2"), downloadMatching(installation.downloadFile))

        val startedInstallerInstead = installation.installNewerStagedUpdateInsteadOfStarting(installedVersion = "1.0.2")

        assertFalse(startedInstallerInstead)
        assertFalse(stagingDirectory.exists())
    }

    private fun release(version: String) = AppUpdateManifest(version = version)

    private fun downloadMatching(file: File) =
        AppUpdateDownload(url = "", sha256 = sha256HexOf(file), sizeBytes = file.length())

    private fun writeReleaseTarball(includeInstallScript: Boolean) {
        val releaseRoot = File(workingDirectory, "build/emberr-1.0.2-x86_64")
        File(releaseRoot, "emberr/bin").mkdirs()
        File(releaseRoot, "emberr/bin/Emberr").writeText("#!/bin/sh\n")
        File(releaseRoot, "emberr.png").writeText("icon")
        if (includeInstallScript) {
            File(releaseRoot, "install.sh").writeText("#!/bin/sh\n")
        }

        val tarProcess = ProcessBuilder(
            "tar",
            "-czf",
            installation.downloadFile.absolutePath,
            "-C",
            releaseRoot.parentFile.absolutePath,
            releaseRoot.name
        )
            .redirectErrorStream(true)
            .start()
        assertEquals(0, tarProcess.waitFor(), tarProcess.inputStream.bufferedReader().readText())
    }
}
