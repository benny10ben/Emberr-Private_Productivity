package com.emberr.domain.util.media

import android.graphics.BitmapFactory
import androidx.compose.ui.unit.IntSize

actual fun readImagePixelSize(absolutePath: String): IntSize? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, options)
    return if (options.outWidth > 0 && options.outHeight > 0) IntSize(options.outWidth, options.outHeight) else null
}
