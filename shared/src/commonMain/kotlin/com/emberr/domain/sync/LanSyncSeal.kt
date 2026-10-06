package com.emberr.domain.sync

import com.emberr.core.security.SyncEncryptionManager
import com.emberr.core.security.SyncHmacSigner
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class LanSyncSeal(
    private val hmacSigner: SyncHmacSigner,
    private val encryptionManager: SyncEncryptionManager,
    private val readPairingKey: () -> String
) {

    fun lockMessage(text: String): ByteArray =
        encryptionManager.encryptBytes(text.encodeToByteArray(), readPairingKey())

    fun unlockMessage(lockedMessage: ByteArray): String =
        encryptionManager.decryptBytes(lockedMessage, readPairingKey()).decodeToString()

    fun lockMessageForHeader(text: String): String = Base64.encode(lockMessage(text))

    fun unlockMessageFromHeader(lockedHeader: String): String = unlockMessage(Base64.decode(lockedHeader))

    fun signRequest(
        method: String,
        pathAndQuery: String,
        timestampMillis: Long,
        lockedMediaRequest: String,
        lockedBody: ByteArray
    ): String = sign("emberr-lan-request\n$method\n$pathAndQuery\n$timestampMillis\n$lockedMediaRequest\n", lockedBody)

    fun isRequestSignatureValid(
        signature: String,
        method: String,
        pathAndQuery: String,
        timestampMillis: Long,
        lockedMediaRequest: String,
        lockedBody: ByteArray
    ): Boolean = signaturesMatch(
        signRequest(method, pathAndQuery, timestampMillis, lockedMediaRequest, lockedBody),
        signature
    )

    fun signReply(requestSignature: String, statusCode: Int, lockedBody: ByteArray): String =
        sign("emberr-lan-reply\n$requestSignature\n$statusCode\n", lockedBody)

    fun isReplySignatureValid(
        replySignature: String,
        requestSignature: String,
        statusCode: Int,
        lockedBody: ByteArray
    ): Boolean = signaturesMatch(signReply(requestSignature, statusCode, lockedBody), replySignature)

    private fun sign(description: String, lockedBody: ByteArray): String =
        hmacSigner.sign(description.encodeToByteArray() + lockedBody, readPairingKey())

    private fun signaturesMatch(expected: String, actual: String): Boolean {
        if (expected.length != actual.length) return false
        var difference = 0
        for (index in expected.indices) {
            difference = difference or (expected[index].code xor actual[index].code)
        }
        return difference == 0
    }
}

fun lanMediaStreamLabel(fileName: String, startOffset: Long): ByteArray =
    "emberr-lan-media\n$fileName\n$startOffset".encodeToByteArray()
