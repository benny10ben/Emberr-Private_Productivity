package com.emberr.core.security

// Multiplatform contract for computing the HMAC-SHA256 signature that authenticates sync requests
interface SyncHmacSigner {
    fun sign(message: ByteArray, secretKey: String): String
}
