package com.emberr.domain.util.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
import java.io.File

actual object ImageClipboard {
    actual suspend fun copyImageToClipboard(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false

            val context = KoinPlatform.getKoin().get<Context>()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val clip = ClipData.newUri(context.contentResolver, "Image", uri)

            withContext(Dispatchers.Main) {
                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(clip)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual suspend fun hasImage(): Boolean = withContext(Dispatchers.Main) {
        val description = clipboardManager().primaryClipDescription ?: return@withContext false
        (0 until description.mimeTypeCount).any { index -> description.getMimeType(index).startsWith("image/") }
    }

    actual suspend fun readImage(): ClipboardImage? = withContext(Dispatchers.Main) {
        if (!hasImage()) return@withContext null
        val clip = clipboardManager().primaryClip ?: return@withContext null
        (0 until clip.itemCount).firstNotNullOfOrNull { index -> clip.getItemAt(index).uri }
            ?.let { uri -> ClipboardImage.FromFile(uri.toString()) }
    }

    private fun clipboardManager(): ClipboardManager {
        val context = KoinPlatform.getKoin().get<Context>()
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}
