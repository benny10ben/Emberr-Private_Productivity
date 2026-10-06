package com.emberr.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

abstract class SyncHmacSignerContract {

    abstract fun createSigner(): SyncHmacSigner

    private val secret = "pairing-secret"
    private val message = "a sync request".encodeToByteArray()

    @Test
    fun aSignatureMatchesTheOneTheOtherPlatformWouldProduce() {
        val signer = createSigner()

        assertEquals(
            CryptoGoldenFixtures.EXPECTED_SIGNATURE,
            signer.sign(
                message = CryptoGoldenFixtures.SIGNED_MESSAGE.encodeToByteArray(),
                secretKey = CryptoGoldenFixtures.SIGNING_SECRET
            )
        )
    }

    @Test
    fun signingTheSameMessageTwiceGivesTheSameSignature() {
        val signer = createSigner()

        assertEquals(signer.sign(message, secret), signer.sign(message, secret))
    }

    @Test
    fun aSignatureIsAlwaysSixtyFourLowercaseHexCharacters() {
        val signer = createSigner()

        val signature = signer.sign(message, secret)

        assertEquals(64, signature.length)
        assertTrue(signature.all { it in "0123456789abcdef" }, "not lowercase hex: $signature")
    }

    @Test
    fun aMessageWithOneChangedByteProducesADifferentSignature() {
        val signer = createSigner()
        val changedMessage = message.copyOf().also { it[0] = (it[0] + 1).toByte() }

        assertNotEquals(signer.sign(message, secret), signer.sign(changedMessage, secret))
    }

    @Test
    fun aDifferentSecretProducesADifferentSignature() {
        val signer = createSigner()

        assertNotEquals(signer.sign(message, secret), signer.sign(message, "a-different-secret"))
    }

    @Test
    fun anySecretLengthIsAcceptedBecauseItIsHashedFirst() {
        val signer = createSigner()

        assertEquals(64, signer.sign(message, "").length)
        assertEquals(64, signer.sign(message, "x").length)
        assertEquals(64, signer.sign(message, "y".repeat(500)).length)
    }
}
