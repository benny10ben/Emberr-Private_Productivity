package com.emberr.domain.update.appimage

import com.emberr.domain.update.requireMatchingSha256
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

fun replaceAppImageWithVerifiedDownload(
    downloadedFile: File,
    appImageFile: File,
    expectedSha256: String
) {
    try {
        requireMatchingSha256(downloadedFile, expectedSha256)

        val originalPermissions = Files.getPosixFilePermissions(appImageFile.toPath())
        Files.setPosixFilePermissions(
            downloadedFile.toPath(),
            originalPermissions + PosixFilePermission.OWNER_EXECUTE
        )
        Files.move(downloadedFile.toPath(), appImageFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        downloadedFile.delete()
    }
}
