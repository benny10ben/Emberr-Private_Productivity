package com.emberr.core.security

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmEncryptionManager : SyncEncryptionManager {

    private val gcmTagLength = 128
    private val ivLength = 12
    private val streamChunkSize = 4 * 1024 * 1024

    private fun getSecretKey(rawKey: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(rawKey.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    override fun encryptPayload(jsonPayload: String, base64Key: String, label: ByteArray): String {
        val secretKey = getSecretKey(base64Key)

        val iv = ByteArray(ivLength)
        SecureRandom().nextBytes(iv)
        val gcmParameterSpec = GCMParameterSpec(gcmTagLength, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
        cipher.updateAAD(label)
        val cipherText = cipher.doFinal(jsonPayload.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decryptPayload(encryptedBase64: String, base64Key: String, label: ByteArray): String {
        val secretKey = getSecretKey(base64Key)

        val combined = Base64.getDecoder().decode(encryptedBase64)
        require(combined.size >= ivLength) {
            "Encrypted payload has ${combined.size} bytes, too short to contain a $ivLength-byte IV"
        }

        val iv = ByteArray(ivLength)
        System.arraycopy(combined, 0, iv, 0, iv.size)
        val gcmParameterSpec = GCMParameterSpec(gcmTagLength, iv)

        val cipherTextSize = combined.size - ivLength
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combined, ivLength, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
        cipher.updateAAD(label)
        val plainTextBytes = cipher.doFinal(cipherText)

        return String(plainTextBytes, Charsets.UTF_8)
    }

    override fun encryptBytes(data: ByteArray, base64Key: String, label: ByteArray): ByteArray {
        val secretKey = getSecretKey(base64Key)

        val iv = ByteArray(ivLength)
        SecureRandom().nextBytes(iv)
        val gcmParameterSpec = GCMParameterSpec(gcmTagLength, iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
        cipher.updateAAD(label)
        val cipherText = cipher.doFinal(data)

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return combined
    }

    override fun decryptBytes(data: ByteArray, base64Key: String, label: ByteArray): ByteArray {
        val secretKey = getSecretKey(base64Key)
        require(data.size >= ivLength) {
            "Encrypted data has ${data.size} bytes, too short to contain a $ivLength-byte IV"
        }

        val iv = ByteArray(ivLength)
        System.arraycopy(data, 0, iv, 0, iv.size)
        val gcmParameterSpec = GCMParameterSpec(gcmTagLength, iv)

        val cipherTextSize = data.size - ivLength
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(data, ivLength, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
        cipher.updateAAD(label)
        return cipher.doFinal(cipherText)
    }

    // Reads from InputStream until the buffer is fully filled or EOF is reached.
    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val bytesRead = input.read(buffer, offset, buffer.size - offset)
            if (bytesRead == -1) break
            offset += bytesRead
        }
        return offset
    }

    // Derives a unique IV per chunk to process large streams in bounded memory.
    private fun deriveChunkIv(ivBase: ByteArray, chunkIndex: Int): ByteArray {
        val chunkIv = ivBase.copyOf()
        val counterOffset = chunkIv.size - 4
        chunkIv[counterOffset] = (chunkIv[counterOffset].toInt() xor (chunkIndex ushr 24)).toByte()
        chunkIv[counterOffset + 1] = (chunkIv[counterOffset + 1].toInt() xor (chunkIndex ushr 16)).toByte()
        chunkIv[counterOffset + 2] = (chunkIv[counterOffset + 2].toInt() xor (chunkIndex ushr 8)).toByte()
        chunkIv[counterOffset + 3] = (chunkIv[counterOffset + 3].toInt() xor chunkIndex).toByte()
        return chunkIv
    }

    // Binds each chunk's authentication tag to its index and finality status to prevent reordering or truncation.
    private fun chunkAad(streamLabel: ByteArray, chunkIndex: Int, isLastChunk: Boolean): ByteArray {
        return streamLabel + byteArrayOf(
            (chunkIndex ushr 24).toByte(), (chunkIndex ushr 16).toByte(), (chunkIndex ushr 8).toByte(), chunkIndex.toByte(),
            if (isLastChunk) 1 else 0
        )
    }

    private fun writeIntBigEndian(output: OutputStream, value: Int) {
        output.write(value ushr 24)
        output.write(value ushr 16)
        output.write(value ushr 8)
        output.write(value)
    }

    private fun readIntBigEndian(bytes: ByteArray): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    override fun encryptStream(input: InputStream, output: OutputStream, base64Key: String, streamLabel: ByteArray) {
        val secretKey = getSecretKey(base64Key)

        val ivBase = ByteArray(ivLength)
        SecureRandom().nextBytes(ivBase)
        // Writes the base IV prefix unencrypted so the receiver can construct per-chunk nonces.
        output.write(ivBase)

        var chunkIndex = 0
        var currentBuffer = ByteArray(streamChunkSize)
        var currentSize = readFully(input, currentBuffer)

        // Uses a one-chunk lookahead to reliably detect and authenticate the final chunk.
        while (true) {
            val nextBuffer = ByteArray(streamChunkSize)
            val nextSize = readFully(input, nextBuffer)
            val isLastChunk = nextSize == 0

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(gcmTagLength, deriveChunkIv(ivBase, chunkIndex)))
            cipher.updateAAD(chunkAad(streamLabel, chunkIndex, isLastChunk))
            val encryptedChunk = cipher.doFinal(currentBuffer, 0, currentSize)

            writeIntBigEndian(output, encryptedChunk.size)
            output.write(encryptedChunk)

            if (isLastChunk) break
            currentBuffer = nextBuffer
            currentSize = nextSize
            chunkIndex++
        }
    }

    private fun readChunkFrame(input: InputStream): ByteArray? {
        val lengthPrefix = ByteArray(4)
        val prefixBytesRead = readFully(input, lengthPrefix)
        if (prefixBytesRead == 0) return null
        require(prefixBytesRead == 4) { "Encrypted stream ended mid chunk-length prefix" }

        val encryptedChunkSize = readIntBigEndian(lengthPrefix)
        require(encryptedChunkSize in 0..(streamChunkSize + (gcmTagLength / 8))) {
            "Implausible chunk size $encryptedChunkSize in encrypted stream"
        }

        val encryptedChunk = ByteArray(encryptedChunkSize)
        val chunkBytesRead = readFully(input, encryptedChunk)
        require(chunkBytesRead == encryptedChunkSize) { "Encrypted stream ended mid chunk" }
        return encryptedChunk
    }

    override fun decryptStream(input: InputStream, output: OutputStream, base64Key: String, streamLabel: ByteArray) {
        val secretKey = getSecretKey(base64Key)

        val ivBase = ByteArray(ivLength)
        val ivBytesRead = readFully(input, ivBase)
        require(ivBytesRead == ivLength) { "Encrypted stream ended before a full IV could be read" }

        var chunkIndex = 0
        var current: ByteArray = readChunkFrame(input) ?: throw IllegalStateException("Encrypted stream contained no chunks")

        // Reads chunks with lookahead to verify position and finality against AAD authentication tags.
        while (true) {
            val next = readChunkFrame(input)
            val isLastChunk = next == null

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(gcmTagLength, deriveChunkIv(ivBase, chunkIndex)))
            cipher.updateAAD(chunkAad(streamLabel, chunkIndex, isLastChunk))
            output.write(cipher.doFinal(current))

            if (isLastChunk) break
            current = next
            chunkIndex++
        }
        output.flush()
    }
}