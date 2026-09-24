package com.emberr.domain.update

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TarballInstallation(installDirectory: File) : StagedUpdateInstallation() {

    private val dataDirectory = installDirectory.absoluteFile.parentFile
    private val partialStagingDirectory = File(dataDirectory, "${installDirectory.name}-update.partial")
    override val stagingDirectory = File(dataDirectory, "${installDirectory.name}-update")
    override val installerLogFile = File(dataDirectory, "${installDirectory.name}-update.log")
    override val downloadFile = File(dataDirectory, "${installDirectory.name}-update.tar.gz")

    override fun downloadFor(release: AppUpdateManifest): AppUpdateDownload? = release.tarball

    override fun canInstall(): Boolean = dataDirectory.canWrite()

    override fun install(release: AppUpdateManifest, download: AppUpdateDownload) {
        requireMatchingSha256(downloadFile, download.sha256)
        try {
            partialStagingDirectory.deleteRecursively()
            partialStagingDirectory.mkdirs()
            unpackTarball(downloadFile, partialStagingDirectory)

            if (!File(partialStagingDirectory, INSTALL_SCRIPT_NAME).isFile) {
                throw AppUpdateException("The downloaded update has no installer inside, so it was not installed.")
            }
            writeStagedVersion(partialStagingDirectory, release.version)

            stagingDirectory.deleteRecursively()
            Files.move(partialStagingDirectory.toPath(), stagingDirectory.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            partialStagingDirectory.deleteRecursively()
        }
    }

    override fun installerCommand(appProcessId: Long, launchAfterInstall: Boolean): List<String> = listOf(
        "/bin/sh",
        "-c",
        INSTALL_THEN_CLEAN_UP_COMMAND,
        "emberr-update",
        File(stagingDirectory, INSTALL_SCRIPT_NAME).absolutePath,
        appProcessId.toString(),
        if (launchAfterInstall) "--launch-after" else "",
        stagingDirectory.absolutePath
    )

    private fun unpackTarball(tarballFile: File, destinationDirectory: File) {
        val process = ProcessBuilder(
            "tar",
            "-xzf",
            tarballFile.absolutePath,
            "-C",
            destinationDirectory.absolutePath,
            "--strip-components=1"
        )
            .redirectErrorStream(true)
            .start()
        val tarOutput = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) {
            System.err.println(tarOutput)
            throw AppUpdateException("The downloaded update couldn't be unpacked, so it was not installed.")
        }
    }

    companion object {
        private const val INSTALL_SCRIPT_NAME = "install.sh"
        private const val INSTALL_THEN_CLEAN_UP_COMMAND = "sh \"$1\" --wait-for-pid \"$2\" $3; rm -rf \"$4\""

        fun forRunningAppOrNull(): TarballInstallation? =
            tarballInstallDirectoryFor(javaHome = System.getProperty("java.home"), dataHome = linuxDataHome())
                ?.let(::TarballInstallation)
    }
}

fun tarballInstallDirectoryFor(javaHome: String?, dataHome: File): File? {
    if (javaHome == null) return null
    val installDirectory = File(dataHome, TARBALL_INSTALL_FOLDER_NAME)
    val isRunningFromInstallDirectory = File(javaHome).canonicalPath
        .startsWith(installDirectory.canonicalPath + File.separator)
    return installDirectory.takeIf { isRunningFromInstallDirectory }
}

private fun linuxDataHome(): File =
    System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
        ?: File(System.getProperty("user.home"), ".local/share")

private const val TARBALL_INSTALL_FOLDER_NAME = "emberr"
