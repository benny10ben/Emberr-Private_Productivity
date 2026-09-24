package com.emberr.domain.update

import com.emberr.domain.update.tarball.TarballInstallation
import com.emberr.domain.update.windows.WindowsInstallation
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

abstract class StagedUpdateInstallation : UpdatableInstallation {

    protected abstract val stagingDirectory: File
    protected abstract val installerLogFile: File
    internal abstract fun installerCommand(appProcessId: Long, launchAfterInstall: Boolean): List<String>

    fun hasNewerStagedUpdate(installedVersion: String?): Boolean {
        if (installedVersion == null) return false
        val stagedVersion = File(stagingDirectory, STAGED_VERSION_FILE_NAME)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?: return false
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
            ProcessBuilder(installerCommand(ProcessHandle.current().pid(), launchAfterInstall))
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

    protected fun writeStagedVersion(directory: File, version: String) {
        File(directory, STAGED_VERSION_FILE_NAME).writeText(version)
    }

    private companion object {
        const val STAGED_VERSION_FILE_NAME = "staged-version.txt"
        val installerHasStarted = AtomicBoolean(false)
    }
}

fun detectStagedUpdateInstallation(): StagedUpdateInstallation? =
    TarballInstallation.forRunningAppOrNull() ?: WindowsInstallation.forRunningAppOrNull()
