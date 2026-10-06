package com.emberr.core.security

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class HmacSha256Signer : SyncHmacSigner {

    // Hashes the raw secret into a fixed-length key so any pairing-secret length is accepted by HmacSHA256
    private fun deriveKey(secretKey: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(secretKey.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "HmacSHA256")
    }

    override fun sign(message: ByteArray, secretKey: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(deriveKey(secretKey))
        return mac.doFinal(message).joinToString(separator = "") { "%02x".format(it) }
    }
}
