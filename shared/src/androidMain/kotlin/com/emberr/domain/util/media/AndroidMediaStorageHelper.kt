package com.emberr.domain.util.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AndroidMediaStorageHelper(private val context: Context) : MediaStorageHelper {

    // Define the new clean media directory
    private val mediaDir = File(context.filesDir, "media").apply { mkdirs() }

    override suspend fun copyUriToInternalStorage(uriString: String): MediaInfo? = withContext(Dispatchers.IO) {
        return@withContext try {
            val uri = Uri.parse(uriString)
            val contentResolver = context.contentResolver
            var displayName = "Unknown_File"
            var size = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) displayName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }

            if (displayName == "Unknown_File" && uriString.contains("camera_")) {
                displayName = "Camera_Photo.jpg"
            }

            val mimeType = contentResolver.getType(uri) ?: if (displayName.lowercase().endsWith(".jpg")) "image/jpeg" else "application/octet-stream"
            val extension = displayName.substringAfterLast('.', "")
            val localFileName = "media_${UUID.randomUUID()}${if (extension.isNotEmpty()) ".$extension" else ".jpg"}"

            // Save the file into the dedicated media folder
            val file = File(mediaDir, localFileName)

            if (mimeType.startsWith("image/") && !mimeType.contains("gif")) {
                var orientation = ExifInterface.ORIENTATION_NORMAL
                try {
                    contentResolver.openInputStream(uri)?.use {
                        val exif = ExifInterface(it)
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    }
                } catch (e: Exception) { e.printStackTrace() }

                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }

                var inSampleSize = 1
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)

                    val reqSize = 1920
                    if (options.outHeight > reqSize || options.outWidth > reqSize) {
                        val halfHeight = options.outHeight / 2
                        val halfWidth = options.outWidth / 2
                        while (halfHeight / inSampleSize >= reqSize && halfWidth / inSampleSize >= reqSize) {
                            inSampleSize *= 2
                        }
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }

                if (bitmap != null) {
                    val rotatedBitmap = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    FileOutputStream(file).use { out -> rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out) }
                    if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
                    bitmap.recycle()
                } else {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(file).use { outputStream -> inputStream.copyTo(outputStream) }
                    }
                }
            } else {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(file).use { outputStream -> inputStream.copyTo(outputStream) }
                }
            }

            size = file.length()

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
                val file = File(mediaDir, localFileName)
                FileOutputStream(file).use { it.write(bytes) }

                MediaInfo(
                    localFileName = localFileName,
                    originalName = localFileName,
                    mimeType = mimeType,
                    sizeBytes = file.length()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    override fun getAbsoluteMediaPath(fileName: String): String {
        val cleanName = fileName.substringAfterLast("/")
        return File(mediaDir, cleanName).absolutePath
    }

    override fun listAllMediaFileNames(): List<String> {
        return mediaDir.listFiles()?.mapNotNull { if (it.isFile) it.name else null } ?: emptyList()
    }
}