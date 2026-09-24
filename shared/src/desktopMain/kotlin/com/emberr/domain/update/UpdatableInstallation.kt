package com.emberr.domain.update

import java.io.File

interface UpdatableInstallation {
    val downloadFile: File
    fun downloadFor(release: AppUpdateManifest): AppUpdateDownload?
    fun canInstall(): Boolean
    fun install(release: AppUpdateManifest, download: AppUpdateDownload)
}

class AppImageInstallation(private val appImageFile: File) : UpdatableInstallation {

    override val downloadFile = File(appImageFile.parentFile, ".${appImageFile.name}.download")

    override fun downloadFor(release: AppUpdateManifest): AppUpdateDownload? = release.appImage

    override fun canInstall(): Boolean = appImageFile.parentFile?.canWrite() == true

    override fun install(release: AppUpdateManifest, download: AppUpdateDownload) =
        replaceAppImageWithVerifiedDownload(downloadFile, appImageFile, download.sha256)
}

fun detectUpdatableInstallation(): UpdatableInstallation? =
    RunningAppImage.fileOrNull()?.let(::AppImageInstallation) ?: TarballInstallation.forRunningAppOrNull()
