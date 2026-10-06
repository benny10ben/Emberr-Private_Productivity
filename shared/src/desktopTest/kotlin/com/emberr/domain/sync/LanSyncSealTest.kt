package com.emberr.domain.sync

import com.emberr.core.security.AesGcmEncryptionManager
import com.emberr.core.security.HmacSha256Signer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanSyncSealTest {

    private var pairingKey = "the-pairing-key-from-the-qr-code"
    private val seal = LanSyncSeal(HmacSha256Signer(), AesGcmEncryptionManager()) { pairingKey }

    private val noMediaRequest = ""

    private val changesJson = """{"changes":[{"entityId":"note-1","isDeleted":false}]}"""

    private fun changedOneByte(bytes: ByteArray): ByteArray =
        bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 0x01).toByte() }

    @Test
    fun aLockedMessageOpensBackToTheSameText() {
        assertEquals(changesJson, seal.unlockMessage(seal.lockMessage(changesJson)))
    }

    @Test
    fun aLockedMessageDoesNotShowItsContentsToSomeoneOnTheNetwork() {
        val lockedText = seal.lockMessage(changesJson).decodeToString()

        assertFalse("note-1" in lockedText)
        assertFalse("isDeleted" in lockedText)
    }

    @Test
    fun aChangedLockedMessageCannotBeOpened() {
        val locked = seal.lockMessage(changesJson)

        assertFails { seal.unlockMessage(changedOneByte(locked)) }
    }

    @Test
    fun aRequestIsAcceptedWhenNothingWasChanged() {
        val body = seal.lockMessage(changesJson)
        val signature = seal.signRequest("POST", "/sync/push", 1_000L, noMediaRequest, body)

        assertTrue(seal.isRequestSignatureValid(signature, "POST", "/sync/push", 1_000L, noMediaRequest, body))
    }

    @Test
    fun aRequestWithAChangedBodyIsRejected() {
        val body = seal.lockMessage(changesJson)
        val signature = seal.signRequest("POST", "/sync/push", 1_000L, noMediaRequest, body)

        assertFalse(seal.isRequestSignatureValid(signature, "POST", "/sync/push", 1_000L, noMediaRequest, changedOneByte(body)))
    }

    @Test
    fun aRequestWithTheBodyOfAnOlderRequestIsRejected() {
        val olderBody = seal.lockMessage("""{"changes":[]}""")
        val newerBody = seal.lockMessage(changesJson)
        val newerSignature = seal.signRequest("POST", "/sync/push", 2_000L, noMediaRequest, newerBody)

        assertFalse(seal.isRequestSignatureValid(newerSignature, "POST", "/sync/push", 2_000L, noMediaRequest, olderBody))
    }

    @Test
    fun aRequestWithAChangedSinceValueIsRejected() {
        val signature = seal.signRequest("GET", "/sync/fetch?since=5000", 1_000L, noMediaRequest, ByteArray(0))

        assertFalse(seal.isRequestSignatureValid(signature, "GET", "/sync/fetch?since=0", 1_000L, noMediaRequest, ByteArray(0)))
    }

    @Test
    fun aRequestSentToADifferentAddressIsRejected() {
        val signature = seal.signRequest("POST", "/sync/push", 1_000L, noMediaRequest, ByteArray(0))

        assertFalse(seal.isRequestSignatureValid(signature, "POST", "/sync/unpair", 1_000L, noMediaRequest, ByteArray(0)))
    }

    @Test
    fun aLockedHeaderOpensBackToTheSameText() {
        val mediaRequestJson = """{"fileName":"media_1.jpg","startOffset":0}"""

        assertEquals(mediaRequestJson, seal.unlockMessageFromHeader(seal.lockMessageForHeader(mediaRequestJson)))
    }

    @Test
    fun aLockedMediaRequestDoesNotShowTheFileNameToSomeoneOnTheNetwork() {
        val lockedMediaRequest = seal.lockMessageForHeader("""{"fileName":"media_1.jpg","startOffset":0}""")

        assertFalse("media_1" in lockedMediaRequest)
    }

    @Test
    fun aRequestWhoseMediaRequestWasSwappedForAnotherFileIsRejected() {
        val forFirstFile = seal.lockMessageForHeader("""{"fileName":"media_1.jpg","startOffset":0}""")
        val forSecondFile = seal.lockMessageForHeader("""{"fileName":"media_2.jpg","startOffset":0}""")
        val signature = seal.signRequest("GET", "/sync/media/download", 1_000L, forFirstFile, ByteArray(0))

        assertFalse(seal.isRequestSignatureValid(signature, "GET", "/sync/media/download", 1_000L, forSecondFile, ByteArray(0)))
    }

    @Test
    fun aMediaRequestIsAcceptedWhenNothingWasChanged() {
        val forFirstFile = seal.lockMessageForHeader("""{"fileName":"media_1.jpg","startOffset":0}""")
        val signature = seal.signRequest("GET", "/sync/media/download", 1_000L, forFirstFile, ByteArray(0))

        assertTrue(seal.isRequestSignatureValid(signature, "GET", "/sync/media/download", 1_000L, forFirstFile, ByteArray(0)))
    }

    @Test
    fun aRequestWithAChangedMethodIsRejected() {
        val signature = seal.signRequest("GET", "/sync/unpair", 1_000L, noMediaRequest, ByteArray(0))

        assertFalse(seal.isRequestSignatureValid(signature, "POST", "/sync/unpair", 1_000L, noMediaRequest, ByteArray(0)))
    }

    @Test
    fun aRequestWithAChangedTimeIsRejected() {
        val signature = seal.signRequest("GET", "/sync/fetch?since=0", 1_000L, noMediaRequest, ByteArray(0))

        assertFalse(seal.isRequestSignatureValid(signature, "GET", "/sync/fetch?since=0", 2_000L, noMediaRequest, ByteArray(0)))
    }

    @Test
    fun aRequestSignedWithAnotherPairingKeyIsRejected() {
        val signature = seal.signRequest("GET", "/sync/fetch?since=0", 1_000L, noMediaRequest, ByteArray(0))
        pairingKey = "a-different-pairing-key"

        assertFalse(seal.isRequestSignatureValid(signature, "GET", "/sync/fetch?since=0", 1_000L, noMediaRequest, ByteArray(0)))
    }

    @Test
    fun aReplyIsAcceptedForTheRequestItAnswers() {
        val body = seal.lockMessage(changesJson)
        val replySignature = seal.signReply("request-signature-1", 200, body)

        assertTrue(seal.isReplySignatureValid(replySignature, "request-signature-1", 200, body))
    }

    @Test
    fun anOldReplyCannotBeReusedAsTheAnswerToANewRequest() {
        val body = seal.lockMessage(changesJson)
        val oldReplySignature = seal.signReply("request-signature-1", 200, body)

        assertFalse(seal.isReplySignatureValid(oldReplySignature, "request-signature-2", 200, body))
    }

    @Test
    fun aReplyWhoseStatusWasChangedIsRejected() {
        val replySignature = seal.signReply("request-signature-1", 409, ByteArray(0))

        assertFalse(seal.isReplySignatureValid(replySignature, "request-signature-1", 200, ByteArray(0)))
    }

    @Test
    fun aReplyWithAChangedBodyIsRejected() {
        val body = seal.lockMessage(changesJson)
        val replySignature = seal.signReply("request-signature-1", 200, body)

        assertFalse(seal.isReplySignatureValid(replySignature, "request-signature-1", 200, changedOneByte(body)))
    }

    @Test
    fun aReplyWithoutAnySignatureIsRejected() {
        assertFalse(seal.isReplySignatureValid("", "request-signature-1", 200, ByteArray(0)))
    }

    @Test
    fun aRequestSignatureCannotBePassedOffAsAReplySignature() {
        val requestSignature = seal.signRequest("POST", "/sync/push", 1_000L, noMediaRequest, ByteArray(0))

        assertFalse(seal.isReplySignatureValid(requestSignature, requestSignature, 200, ByteArray(0)))
    }

    @Test
    fun mediaLabelsDifferForDifferentFilesAndStartPositions() {
        val label = lanMediaStreamLabel("media_1.jpg", 0L).decodeToString()

        assertFalse(label == lanMediaStreamLabel("media_2.jpg", 0L).decodeToString())
        assertFalse(label == lanMediaStreamLabel("media_1.jpg", 5_000L).decodeToString())
    }
}
