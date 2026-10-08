package com.emberr.domain.util.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.UUID

class DesktopMediaStorageHelper : MediaStorageHelper {

    private val mediaStorageDir = File(System.getProperty("user.home"), ".emberr/media").apply {
        mkdirs()
    }

    override suspend fun copyUriToInternalStorage(uriString: String): MediaInfo? = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(uriString)
            if (!sourceFile.exists()) return@withContext null

            val extension = sourceFile.extension
            val displayName = sourceFile.name
            val size = sourceFile.length()

            val localFileName = "media_${UUID.randomUUID()}${if (extension.isNotEmpty()) ".$extension" else ""}"
            val destFile = File(mediaStorageDir, localFileName)

            sourceFile.copyTo(destFile, overwrite = true)

            val mimeType = Files.probeContentType(destFile.toPath()) ?: "application/octet-stream"

            MediaInfo(
                localFileName = localFileName,
                originalName = displayName,
                mimeType = mimeType,
                sizeBytes = size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun saveBytesToInternalStorage(bytes: ByteArray, extension: String, mimeType: String): MediaInfo? =
        withContext(Dispatchers.IO) {
            try {
                val localFileName = "media_${UUID.randomUUID()}${if (extension.isNotEmpty()) ".$extension" else ""}"
                val destFile = File(mediaStorageDir, localFileName)
                destFile.writeBytes(bytes)

                MediaInfo(
                    localFileName = localFileName,
                    originalName = localFileName,
                    mimeType = mimeType,
                    sizeBytes = destFile.length()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    override fun getAbsoluteMediaPath(fileName: String): String {
        // Clean any accidental slashes from the DB string to prevent duplicate "media/" paths
        val cleanName = mediaFileNameOnly(fileName)
        return File(mediaStorageDir, cleanName).absolutePath
    }

    override fun listAllMediaFileNames(): List<String> {
        return mediaStorageDir.listFiles()?.mapNotNull { if (it.isFile) it.name else null } ?: emptyList()
    }
}