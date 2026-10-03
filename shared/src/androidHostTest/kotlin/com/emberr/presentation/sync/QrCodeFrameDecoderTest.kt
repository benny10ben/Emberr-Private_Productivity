package com.emberr.presentation.sync

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrCodeFrameDecoderTest {

    private val pairingJson =
        """{"ipAddress":"192.168.1.20","port":8765,"authToken":"abc123","encryptionKey":"def456"}"""

    private val frameWidth = 320
    private val frameHeight = 240
    private val paddedRowStride = 336
    private val whitePixel = 0xFF.toByte()
    private val blackPixel = 0x00.toByte()

    @Test
    fun decodesQrCodeFromFrameWithRowPadding() {
        val qrCode = QRCodeWriter().encode(pairingJson, BarcodeFormat.QR_CODE, 200, 200)
        val frame = buildFrame(qrCode, rotateQuarterTurn = false)

        val decodedText = QrCodeFrameDecoder().decode(frame, paddedRowStride, frameWidth, frameHeight)

        assertEquals(pairingJson, decodedText)
    }

    @Test
    fun decodesQrCodeWhenCameraFrameIsRotated() {
        val qrCode = QRCodeWriter().encode(pairingJson, BarcodeFormat.QR_CODE, 200, 200)
        val frame = buildFrame(qrCode, rotateQuarterTurn = true)

        val decodedText = QrCodeFrameDecoder().decode(frame, paddedRowStride, frameWidth, frameHeight)

        assertEquals(pairingJson, decodedText)
    }

    @Test
    fun returnsNullForFrameWithoutQrCode() {
        val blankFrame = ByteArray(paddedRowStride * frameHeight) { whitePixel }

        val decodedText = QrCodeFrameDecoder().decode(blankFrame, paddedRowStride, frameWidth, frameHeight)

        assertNull(decodedText)
    }

    @Test
    fun sameDecoderReadsAgainAfterEmptyFrame() {
        val decoder = QrCodeFrameDecoder()
        val blankFrame = ByteArray(paddedRowStride * frameHeight) { whitePixel }
        val qrCode = QRCodeWriter().encode(pairingJson, BarcodeFormat.QR_CODE, 200, 200)
        val qrFrame = buildFrame(qrCode, rotateQuarterTurn = false)

        decoder.decode(blankFrame, paddedRowStride, frameWidth, frameHeight)
        val decodedText = decoder.decode(qrFrame, paddedRowStride, frameWidth, frameHeight)

        assertEquals(pairingJson, decodedText)
    }

    private fun buildFrame(qrCode: BitMatrix, rotateQuarterTurn: Boolean): ByteArray {
        val frame = ByteArray(paddedRowStride * frameHeight) { whitePixel }
        val leftOffset = 60
        val topOffset = 20
        for (qrY in 0 until qrCode.height) {
            for (qrX in 0 until qrCode.width) {
                val isDark = if (rotateQuarterTurn) {
                    qrCode.get(qrY, qrCode.height - 1 - qrX)
                } else {
                    qrCode.get(qrX, qrY)
                }
                if (isDark) {
                    frame[(topOffset + qrY) * paddedRowStride + leftOffset + qrX] = blackPixel
                }
            }
        }
        return frame
    }
}
