package com.emberr.domain.util.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

private class TransferableImage(private val image: Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor
    override fun getTransferData(flavor: DataFlavor): Any {
        if (flavor != DataFlavor.imageFlavor) throw UnsupportedFlavorException(flavor)
        return image
    }
}

actual object ImageClipboard {
    actual suspend fun copyImageToClipboard(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext false

            val image = ImageIO.read(file) ?: return@withContext false
            Toolkit.getDefaultToolkit().systemClipboard.setContents(TransferableImage(image), null)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    actual suspend fun hasImage(): Boolean = withContext(Dispatchers.IO) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) || copiedImageFile() != null
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun readImage(): ClipboardImage? = withContext(Dispatchers.IO) {
        try {
            copiedImageFile()?.let { file -> return@withContext ClipboardImage.FromFile(file.absolutePath) }
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) return@withContext null
            val image = clipboard.getData(DataFlavor.imageFlavor) as? Image ?: return@withContext null
            val pngBytes = ByteArrayOutputStream().use { output ->
                if (!ImageIO.write(image.asBufferedImage(), "png", output)) return@withContext null
                output.toByteArray()
            }
            ClipboardImage.FromPngBytes(pngBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun copiedImageFile(): File? {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (!clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) return null
        val files = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*> ?: return null
        return files.filterIsInstance<File>().firstOrNull { it.isFile && it.extension.lowercase() in pastableImageExtensions }
    }

    private fun Image.asBufferedImage(): BufferedImage {
        if (this is BufferedImage) return this
        val buffered = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)
        val graphics = buffered.createGraphics()
        graphics.drawImage(this, 0, 0, null)
        graphics.dispose()
        return buffered
    }
}

private val pastableImageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
