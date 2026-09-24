package com.emberr.domain.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class WindowsInstallation(
    private val installDirectory: File,
    private val localAppDataDirectory: File
) : StagedUpdateInstallation() {

    private val partialStagingDirectory = File(localAppDataDirectory, "Emberr-update.partial")
    override val stagingDirectory = File(localAppDataDirectory, "Emberr-update")
    override val installerLogFile = File(localAppDataDirectory, "Emberr-update.log")
    override val downloadFile = File(localAppDataDirectory, "Emberr-update.download")

    override fun downloadFor(release: AppUpdateManifest): AppUpdateDownload? = release.windowsInstaller

    override fun canInstall(): Boolean = localAppDataDirectory.canWrite() && installDirectory.canWrite()

    override fun install(release: AppUpdateManifest, download: AppUpdateDownload) {
        requireMatchingSha256(downloadFile, download.sha256)
        try {
            partialStagingDirectory.deleteRecursively()
            partialStagingDirectory.mkdirs()

            Files.move(downloadFile.toPath(), File(partialStagingDirectory, INSTALLER_FILE_NAME).toPath())
            copyInstallScriptInto(partialStagingDirectory)
            writeStagedVersion(partialStagingDirectory, release.version)

            stagingDirectory.deleteRecursively()
            Files.move(partialStagingDirectory.toPath(), stagingDirectory.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            partialStagingDirectory.deleteRecursively()
        }
    }

    override fun installerCommand(appProcessId: Long, launchAfterInstall: Boolean): List<String> = listOf(
        "powershell.exe",
        "-NoProfile",
        "-NonInteractive",
        "-ExecutionPolicy", "Bypass",
        "-WindowStyle", "Hidden",
        "-File", File(stagingDirectory, INSTALL_SCRIPT_NAME).absolutePath,
        "-AppProcessId", appProcessId.toString(),
        "-InstallerPath", File(stagingDirectory, INSTALLER_FILE_NAME).absolutePath,
        "-InstallDirectory", installDirectory.absolutePath,
        "-PassInstallDirectory", if (isInDefaultLocation()) "no" else "yes",
        "-StagingDirectory", stagingDirectory.absolutePath,
        "-LaunchAfterInstall", if (launchAfterInstall) "yes" else "no"
    )

    private fun isInDefaultLocation(): Boolean =
        installDirectory.canonicalFile == File(localAppDataDirectory, APP_FOLDER_NAME).canonicalFile

    private fun copyInstallScriptInto(directory: File) {
        val scriptStream = WindowsInstallation::class.java.classLoader.getResourceAsStream(INSTALL_SCRIPT_RESOURCE)
            ?: throw IOException("The Windows update script is missing from the app.")
        scriptStream.use { input ->
            File(directory, INSTALL_SCRIPT_NAME).outputStream().use { output -> input.copyTo(output) }
        }
    }

    companion object {
        private const val APP_FOLDER_NAME = "Emberr"
        private const val LAUNCHER_FILE_NAME = "Emberr.exe"
        private const val INSTALLER_FILE_NAME = "Emberr-Setup-x86_64.exe"
        private const val INSTALL_SCRIPT_NAME = "install-update.ps1"
        private const val INSTALL_SCRIPT_RESOURCE = "update/install-windows-update.ps1"

        fun forRunningAppOrNull(): WindowsInstallation? {
            if (!System.getProperty("os.name").orEmpty().lowercase().contains("win")) return null
            val localAppDataDirectory = System.getenv("LOCALAPPDATA")?.let(::File) ?: return null
            val installDirectory = windowsInstallDirectoryFor(System.getProperty("java.home")) ?: return null
            return WindowsInstallation(installDirectory, localAppDataDirectory)
        }

        fun windowsInstallDirectoryFor(javaHome: String?): File? {
            if (javaHome == null) return null
            val runtimeDirectory = File(javaHome).absoluteFile
            val installDirectory = runtimeDirectory.parentFile ?: return null
            val isInstalledAppLayout = runtimeDirectory.name == "runtime" &&
                File(installDirectory, LAUNCHER_FILE_NAME).isFile
            return installDirectory.takeIf { isInstalledAppLayout }
        }
    }
}
