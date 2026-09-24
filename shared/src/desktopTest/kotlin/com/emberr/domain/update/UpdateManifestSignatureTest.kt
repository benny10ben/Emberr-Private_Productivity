package com.emberr.domain.update

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateManifestSignatureTest {

    private val manifestBytes = """{"version":"1.1.0"}""".encodeToByteArray()

    @Test
    fun signatureFromTheMatchingKeyIsValid() {
        val keyPair = newSigningKeyPair()

        assertTrue(isValidManifestSignature(manifestBytes, sign(manifestBytes, keyPair), publicKeyBase64Of(keyPair)))
    }

    @Test
    fun changedManifestIsRejected() {
        val keyPair = newSigningKeyPair()
        val signature = sign(manifestBytes, keyPair)
        val changedManifestBytes = """{"version":"9.9.9"}""".encodeToByteArray()

        assertFalse(isValidManifestSignature(changedManifestBytes, signature, publicKeyBase64Of(keyPair)))
    }

    @Test
    fun signatureFromAnotherKeyIsRejected() {
        val signature = sign(manifestBytes, newSigningKeyPair())

        assertFalse(isValidManifestSignature(manifestBytes, signature, publicKeyBase64Of(newSigningKeyPair())))
    }

    @Test
    fun garbageSignatureOrKeyIsRejectedWithoutThrowing() {
        val keyPair = newSigningKeyPair()

        assertFalse(isValidManifestSignature(manifestBytes, ByteArray(10), publicKeyBase64Of(keyPair)))
        assertFalse(isValidManifestSignature(manifestBytes, sign(manifestBytes, keyPair), "not a key"))
    }

    private fun newSigningKeyPair(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    private fun publicKeyBase64Of(keyPair: KeyPair): String =
        Base64.getEncoder().encodeToString(keyPair.public.encoded)

    private fun sign(bytes: ByteArray, keyPair: KeyPair): ByteArray =
        Signature.getInstance("Ed25519").run {
            initSign(keyPair.private)
            update(bytes)
            sign()
        }
}
