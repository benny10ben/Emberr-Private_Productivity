package com.emberr.domain.update

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest

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

fun requireMatchingSha256(file: File, expectedSha256: String) {
    if (!sha256HexOf(file).equals(expectedSha256, ignoreCase = true)) {
        throw AppUpdateException("The downloaded update is damaged, so it was not installed. Try again.")
    }
}

fun sha256HexOf(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    file.inputStream().use { input ->
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
