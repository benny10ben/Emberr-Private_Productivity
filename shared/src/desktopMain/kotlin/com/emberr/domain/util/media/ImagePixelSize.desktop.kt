package com.emberr.domain.util.media

import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Image
import java.io.File

actual fun readImagePixelSize(absolutePath: String): IntSize? = try {
    Image.makeFromEncoded(File(absolutePath).readBytes()).use { image ->
        if (image.width > 0 && image.height > 0) IntSize(image.width, image.height) else null
    }
} catch (e: Exception) {
    null
}
