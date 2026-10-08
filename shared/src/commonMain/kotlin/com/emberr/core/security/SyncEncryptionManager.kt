package com.emberr.core.security

import java.io.InputStream
import java.io.OutputStream

/**
 * Multiplatform contract for encrypting sync payloads.
 */
interface SyncEncryptionManager {
    fun encryptPayload(jsonPayload: String, base64Key: String, label: ByteArray = ByteArray(0)): String
    fun decryptPayload(encryptedBase64: String, base64Key: String, label: ByteArray = ByteArray(0)): String
    fun encryptBytes(data: ByteArray, base64Key: String, label: ByteArray = ByteArray(0)): ByteArray
    fun decryptBytes(data: ByteArray, base64Key: String, label: ByteArray = ByteArray(0)): ByteArray

    // Encrypts input chunk-by-chunk into output; never buffers the whole stream in memory
    fun encryptStream(input: InputStream, output: OutputStream, base64Key: String, streamLabel: ByteArray = ByteArray(0))

    // Decrypts input chunk-by-chunk into output; never buffers the whole stream in memory
    fun decryptStream(input: InputStream, output: OutputStream, base64Key: String, streamLabel: ByteArray = ByteArray(0))
}