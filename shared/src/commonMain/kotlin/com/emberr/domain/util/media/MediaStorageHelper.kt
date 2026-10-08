package com.emberr.domain.util.media

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

fun mediaFileNameOnly(path: String): String =
    path.substringAfterLast('/').substringAfterLast('\\')

data class MediaInfo(
    val localFileName: String,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (log10(sizeBytes.toDouble()) / log10(1024.0)).toInt()
            val value = sizeBytes / 1024.0.pow(digitGroups.toDouble())
            val roundedValue = (value * 10.0).roundToInt() / 10.0
            return "$roundedValue ${units[digitGroups]}"
        }
}

interface MediaStorageHelper {
    suspend fun copyUriToInternalStorage(uriString: String): MediaInfo?
    suspend fun saveBytesToInternalStorage(bytes: ByteArray, extension: String, mimeType: String): MediaInfo?
    fun getAbsoluteMediaPath(fileName: String): String
    fun listAllMediaFileNames(): List<String>
}