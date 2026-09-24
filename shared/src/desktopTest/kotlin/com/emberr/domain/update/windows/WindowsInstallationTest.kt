package com.emberr.domain.update.windows

import com.emberr.domain.update.AppUpdateDownload
import com.emberr.domain.update.AppUpdateException
import com.emberr.domain.update.AppUpdateManifest
import com.emberr.domain.update.sha256HexOf
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsInstallationTest {

    private val workingDirectory = Files.createTempDirectory("emberr-windows-installation").toFile()
    private val localAppData = File(workingDirectory, "AppData/Local").apply { mkdirs() }
    private val defaultInstallDirectory = File(localAppData, "Emberr").apply { mkdirs() }
    private val stagingDirectory = File(localAppData, "Emberr-update")

    @AfterTest
    fun deleteWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }

    @Test
    fun installedAppLayoutIsRecognisedFromTheRuntimeFolder() {
        val runtimeDirectory = File(defaultInstallDirectory, "runtime").apply { mkdirs() }
        File(defaultInstallDirectory, "Emberr.exe").writeText("launcher")

        assertEquals(
            defaultInstallDirectory.absoluteFile,
            WindowsInstallation.windowsInstallDirectoryFor(runtimeDirectory.path)
        )
    }

    @Test
    fun developmentRunWithoutTheLauncherIsNotAnInstalledApp() {
        val runtimeDirectory = File(workingDirectory, "jdks/jbr-21").apply { mkdirs() }

        assertNull(WindowsInstallation.windowsInstallDirectoryFor(runtimeDirectory.path))
        assertNull(WindowsInstallation.windowsInstallDirectoryFor(null))
    }

    @Test
    fun verifiedInstallerIsStagedWithTheUpdateScript() {
        val installation = WindowsInstallation(defaultInstallDirectory, localAppData)
        installation.downloadFile.writeText("installer bytes")

        installation.install(AppUpdateManifest(version = "1.0.3"), downloadMatching(installation.downloadFile))

        assertEquals("installer bytes", File(stagingDirectory, "Emberr-Setup-x86_64.exe").readText())
        assertTrue(File(stagingDirectory, "install-update.ps1").readText().contains("Wait-Process"))
        assertTrue(installation.hasNewerStagedUpdate(installedVersion = "1.0.2"))
        assertFalse(installation.hasNewerStagedUpdate(installedVersion = "1.0.3"))
    }

    @Test
    fun damagedInstallerIsNotStaged() {
        val installation = WindowsInstallation(defaultInstallDirectory, localAppData)
        installation.downloadFile.writeText("damaged bytes")

        assertFailsWith<AppUpdateException> {
            installation.install(
                AppUpdateManifest(version = "1.0.3"),
                AppUpdateDownload(url = "", sha256 = "0".repeat(64), sizeBytes = 1)
            )
        }

        assertFalse(stagingDirectory.exists())
    }

    @Test
    fun defaultInstallFolderIsNotPassedToTheInstaller() {
        val command = WindowsInstallation(defaultInstallDirectory, localAppData)
            .installerCommand(appProcessId = 42, launchAfterInstall = true)

        assertEquals("no", command.valueAfter("-PassInstallDirectory"))
        assertEquals("yes", command.valueAfter("-LaunchAfterInstall"))
        assertEquals("42", command.valueAfter("-AppProcessId"))
    }

    @Test
    fun customInstallFolderIsPassedToTheInstaller() {
        val customInstallDirectory = File(workingDirectory, "My Apps/Emberr").apply { mkdirs() }

        val command = WindowsInstallation(customInstallDirectory, localAppData)
            .installerCommand(appProcessId = 42, launchAfterInstall = false)

        assertEquals("yes", command.valueAfter("-PassInstallDirectory"))
        assertEquals(customInstallDirectory.absolutePath, command.valueAfter("-InstallDirectory"))
        assertEquals("no", command.valueAfter("-LaunchAfterInstall"))
    }

    private fun downloadMatching(file: File) =
        AppUpdateDownload(url = "", sha256 = sha256HexOf(file), sizeBytes = file.length())

    private fun List<String>.valueAfter(parameterName: String): String = this[indexOf(parameterName) + 1]
}
