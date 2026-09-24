package com.emberr.domain.update

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

const val UPDATE_SIGNING_PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAA7GIVSsmoUW1qe/IwGQP/Sfp27A8QcJrjyEzadJ5hpY="

fun isValidManifestSignature(
    manifestBytes: ByteArray,
    signatureBytes: ByteArray,
    publicKeyBase64: String = UPDATE_SIGNING_PUBLIC_KEY_BASE64
): Boolean = try {
    val publicKey = KeyFactory.getInstance("Ed25519")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))
    Signature.getInstance("Ed25519").run {
        initVerify(publicKey)
        update(manifestBytes)
        verify(signatureBytes)
    }
} catch (_: GeneralSecurityException) {
    false
} catch (_: IllegalArgumentException) {
    false
}
