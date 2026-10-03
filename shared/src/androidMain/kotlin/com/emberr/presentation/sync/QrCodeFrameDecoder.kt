package com.emberr.presentation.sync

import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

class QrCodeFrameDecoder {
    private val reader = QRCodeReader()

    fun decode(luminanceBytes: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val luminanceSource = PlanarYUVLuminanceSource(
            luminanceBytes, rowStride, height, 0, 0, width, height, false
        )
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(luminanceSource))).text
        } catch (noQrCodeFound: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
