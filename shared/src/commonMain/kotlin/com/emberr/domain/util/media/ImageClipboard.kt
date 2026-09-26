package com.emberr.domain.util.media

sealed interface ClipboardImage {
    data class FromFile(val uriOrPath: String) : ClipboardImage
    class FromPngBytes(val bytes: ByteArray) : ClipboardImage
}

expect object ImageClipboard {
    suspend fun copyImageToClipboard(filePath: String): Boolean
    suspend fun hasImage(): Boolean
    suspend fun readImage(): ClipboardImage?
}
