package com.emberr.domain.update

import java.io.File
import java.security.MessageDigest

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
