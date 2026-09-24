package com.emberr.domain.update

import com.emberr.domain.update.appimage.AppImageInstallation
import com.emberr.domain.update.appimage.RunningAppImage
import java.io.File

interface UpdatableInstallation {
    val downloadFile: File
    fun downloadFor(release: AppUpdateManifest): AppUpdateDownload?
    fun canInstall(): Boolean
    fun install(release: AppUpdateManifest, download: AppUpdateDownload)
}

fun detectUpdatableInstallation(): UpdatableInstallation? =
    RunningAppImage.fileOrNull()?.let(::AppImageInstallation) ?: detectStagedUpdateInstallation()
