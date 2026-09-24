package com.emberr.domain.update.appimage

import com.emberr.domain.update.AppUpdateDownload
import com.emberr.domain.update.AppUpdateManifest
import com.emberr.domain.update.UpdatableInstallation
import java.io.File

class AppImageInstallation(private val appImageFile: File) : UpdatableInstallation {

    override val downloadFile = File(appImageFile.parentFile, ".${appImageFile.name}.download")

    override fun downloadFor(release: AppUpdateManifest): AppUpdateDownload? = release.appImage

    override fun canInstall(): Boolean = appImageFile.parentFile?.canWrite() == true

    override fun install(release: AppUpdateManifest, download: AppUpdateDownload) =
        replaceAppImageWithVerifiedDownload(downloadFile, appImageFile, download.sha256)
}
