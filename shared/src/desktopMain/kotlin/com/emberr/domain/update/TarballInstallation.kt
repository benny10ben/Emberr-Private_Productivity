package com.emberr.domain.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

class TarballInstallation(installDirectory: File) : UpdatableInstallation {

    private val dataDirectory = installDirectory.absoluteFile.parentFile
    private val stagingDirectory = File(dataDirectory, "${installDirectory.name}-update")
    private val partialStagingDirectory = File(dataDirectory, "${installDirectory.name}-update.partial")
    private val installerLogFile = File(dataDirectory, "${installDirectory.name}-update.log")
    private val stagedInstallScript = File(stagingDirectory, "install.sh")
    private val stagedVersionFile = File(stagingDirectory, STAGED_VERSION_FILE_NAME)

    override val downloadFile = File(dataDirectory, "${installDirectory.name}-update.tar.gz")

    override fun downloadFor(release: AppUpdateManifest): AppUpdateDownload? = release.tarball

    override fun canInstall(): Boolean = dataDirectory.canWrite()

    override fun install(release: AppUpdateManifest, download: AppUpdateDownload) {
        requireMatchingSha256(downloadFile, download.sha256)
        try {
            partialStagingDirectory.deleteRecursively()
            partialStagingDirectory.mkdirs()
            unpackTarball(downloadFile, partialStagingDirectory)

            if (!File(partialStagingDirectory, "install.sh").isFile) {
                throw AppUpdateException("The downloaded update has no installer inside, so it was not installed.")
            }
            File(partialStagingDirectory, STAGED_VERSION_FILE_NAME).writeText(release.version)

            stagingDirectory.deleteRecursively()
            Files.move(partialStagingDirectory.toPath(), stagingDirectory.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            partialStagingDirectory.deleteRecursively()
        }
    }

    fun hasNewerStagedUpdate(installedVersion: String?): Boolean {
        if (installedVersion == null || !stagedInstallScript.isFile) return false
        val stagedVersion = stagedVersionFile.takeIf { it.isFile }?.readText()?.trim() ?: return false
        return isNewerVersion(stagedVersion, installedVersion)
    }

    fun installNewerStagedUpdateInsteadOfStarting(installedVersion: String?): Boolean {
        if (hasNewerStagedUpdate(installedVersion)) {
            return installStagedUpdateAfterThisProcessExits(launchAfterInstall = true)
        }
        stagingDirectory.deleteRecursively()
        return false
    }

    fun installStagedUpdateWhenAppQuits(installedVersion: String?) {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                if (hasNewerStagedUpdate(installedVersion)) {
                    installStagedUpdateAfterThisProcessExits(launchAfterInstall = false)
                }
            }
        )
    }

    fun installStagedUpdateAfterThisProcessExits(launchAfterInstall: Boolean): Boolean {
        if (!installerHasStarted.compareAndSet(false, true)) return true
        return try {
            ProcessBuilder(
                "/bin/sh",
                "-c",
                INSTALL_THEN_CLEAN_UP_COMMAND,
                "emberr-update",
                stagedInstallScript.absolutePath,
                ProcessHandle.current().pid().toString(),
                if (launchAfterInstall) "--launch-after" else "",
                stagingDirectory.absolutePath
            )
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(installerLogFile))
                .start()
            true
        } catch (cause: IOException) {
            cause.printStackTrace()
            installerHasStarted.set(false)
            false
        }
    }

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
        private const val STAGED_VERSION_FILE_NAME = "staged-version.txt"
        private const val INSTALL_THEN_CLEAN_UP_COMMAND = "sh \"$1\" --wait-for-pid \"$2\" $3; rm -rf \"$4\""
        private val installerHasStarted = AtomicBoolean(false)

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
