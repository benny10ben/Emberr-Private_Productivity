package com.emberr.presentation.desktop.window

import com.emberr.domain.update.RunningAppImage
import com.emberr.domain.update.TarballInstallation
import com.emberr.domain.util.system.appVersionName
import java.io.File

object DesktopAppRelauncher {

    private const val SECONDS_TO_WAIT_FOR_THIS_PROCESS_TO_EXIT = 2

    val isSupported: Boolean
        get() = currentProcessCommand() != null

    fun startNewInstance(): Boolean {
        val tarballInstallation = TarballInstallation.forRunningAppOrNull()
        if (tarballInstallation != null && tarballInstallation.hasNewerStagedUpdate(appVersionName)) {
            return tarballInstallation.installStagedUpdateAfterThisProcessExits(launchAfterInstall = true)
        }

        val command = currentProcessCommand() ?: return false
        return runCatching {
            ProcessBuilder(delayedCommand(command))
                .directory(File(System.getProperty("user.dir")))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            true
        }.getOrDefault(false)
    }

    private fun currentProcessCommand(): List<String>? {
        RunningAppImage.fileOrNull()?.let { appImageFile -> return listOf(appImageFile.absolutePath) }
        val processInfo = ProcessHandle.current().info()
        val executable = processInfo.command().orElse(null) ?: return null
        val arguments = processInfo.arguments().orElse(emptyArray()).toList()
        return listOf(executable) + arguments
    }

    private fun delayedCommand(command: List<String>): List<String> = listOf(
        "/bin/sh",
        "-c",
        "sleep $SECONDS_TO_WAIT_FOR_THIS_PROCESS_TO_EXIT; exec \"$@\"",
        "emberr-restart"
    ) + command
}
