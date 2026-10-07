package com.emberr.core.security

import com.emberr.domain.selfhost.webdav.mediaPieceLabel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

abstract class SyncEncryptionManagerContract {

    abstract fun createEncryptionManager(): SyncEncryptionManager

    private val key = "a-pairing-key-from-the-qr-code"
    private val otherKey = "a-completely-different-pairing-key"

    private val ivLength = 12
    private val authenticationTagLength = 16
    private val chunkLengthPrefixLength = 4
    private val streamChunkSize = 4 * 1024 * 1024

    @Test
    fun aPayloadComesBackExactlyAsItWentIn() {
        val manager = createEncryptionManager()
        val payloads = listOf(
            "",
            "a",
            "{\"noteId\":\"note-1\",\"blocks\":[]}",
            "unicode: éèü 你好 🔥",
            "x".repeat(100_000)
        )

        payloads.forEach { payload ->
            val encrypted = manager.encryptPayload(payload, key)

            assertEquals(payload, manager.decryptPayload(encrypted, key))
        }
    }

    @Test
    fun theSamePayloadEncryptsDifferentlyEveryTimeSoNothingLeaksByComparison() {
        val manager = createEncryptionManager()
        val payload = "{\"noteId\":\"note-1\"}"

        assertNotEquals(
            manager.encryptPayload(payload, key),
            manager.encryptPayload(payload, key)
        )
    }

    @Test
    fun aPayloadCannotBeReadWithTheWrongKey() {
        val manager = createEncryptionManager()
        val encrypted = manager.encryptPayload("secret note", key)

        assertFails { manager.decryptPayload(encrypted, otherKey) }
    }

    @Test
    fun aTamperedPayloadIsRejectedRatherThanSilentlyDecoded() {
        val manager = createEncryptionManager()
        val encrypted = manager.encryptPayload("secret note", key)

        val raw = Base64.getDecoder().decode(encrypted)
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
        val tampered = Base64.getEncoder().encodeToString(raw)

        assertFailsWith<GeneralSecurityException> { manager.decryptPayload(tampered, key) }
    }

    @Test
    fun aPayloadTooShortToHoldAnIvIsRefusedWithAClearMessage() {
        val manager = createEncryptionManager()
        val tooShort = Base64.getEncoder().encodeToString(ByteArray(ivLength - 1))

        val failure = assertFailsWith<IllegalArgumentException> {
            manager.decryptPayload(tooShort, key)
        }

        assertTrue("too short" in failure.message.orEmpty())
    }

    @Test
    fun rawBytesComeBackExactlyAsTheyWentIn() {
        val manager = createEncryptionManager()
        val inputs = listOf(
            ByteArray(0),
            byteArrayOf(0),
            ByteArray(1024) { (it % 256).toByte() },
            ByteArray(100_000) { (it % 251).toByte() }
        )

        inputs.forEach { input ->
            val encrypted = manager.encryptBytes(input, key)

            assertContentEquals(input, manager.decryptBytes(encrypted, key))
        }
    }

    @Test
    fun encryptedBytesAreLaidOutAsAnIvFollowedByTheSealedContent() {
        val manager = createEncryptionManager()
        val input = ByteArray(64) { it.toByte() }

        val encrypted = manager.encryptBytes(input, key)

        assertEquals(ivLength + input.size + authenticationTagLength, encrypted.size)
    }

    @Test
    fun rawBytesCannotBeReadWithTheWrongKey() {
        val manager = createEncryptionManager()
        val encrypted = manager.encryptBytes(ByteArray(64) { it.toByte() }, key)

        assertFails { manager.decryptBytes(encrypted, otherKey) }
    }

    @Test
    fun tamperedBytesAreRejectedRatherThanSilentlyDecoded() {
        val manager = createEncryptionManager()
        val encrypted = manager.encryptBytes(ByteArray(64) { it.toByte() }, key)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0x01).toByte()

        assertFailsWith<GeneralSecurityException> { manager.decryptBytes(encrypted, key) }
    }

    @Test
    fun bytesTooShortToHoldAnIvAreRefusedWithAClearMessage() {
        val manager = createEncryptionManager()

        val failure = assertFailsWith<IllegalArgumentException> {
            manager.decryptBytes(ByteArray(ivLength - 1), key)
        }

        assertTrue("too short" in failure.message.orEmpty())
    }

    @Test
    fun aStreamComesBackExactlyAsItWentInAtEverySizeThatMatters() {
        val manager = createEncryptionManager()
        val sizes = listOf(
            0,
            1,
            1024,
            streamChunkSize - 1,
            streamChunkSize,
            streamChunkSize + 1
        )

        sizes.forEach { size ->
            val input = ByteArray(size) { (it % 251).toByte() }
            val encrypted = encryptToBytes(manager, input)

            assertContentEquals(input, decryptToBytes(manager, encrypted), "stream of $size bytes changed")
        }
    }

    @Test
    fun aStreamSmallEnoughForOneChunkIsFramedAsAnIvThenOneLengthPrefixedChunk() {
        val manager = createEncryptionManager()
        val input = ByteArray(1024) { it.toByte() }

        val encrypted = encryptToBytes(manager, input)

        assertEquals(
            ivLength + chunkLengthPrefixLength + input.size + authenticationTagLength,
            encrypted.size
        )
        assertEquals(1, chunksOf(encrypted).size)
    }

    @Test
    fun aStreamLargerThanOneChunkIsSplitIntoSeveralChunks() {
        val manager = createEncryptionManager()
        val input = ByteArray(streamChunkSize + 1) { (it % 251).toByte() }

        val encrypted = encryptToBytes(manager, input)

        assertEquals(2, chunksOf(encrypted).size)
    }

    @Test
    fun aStreamCannotBeReadWithTheWrongKey() {
        val manager = createEncryptionManager()
        val encrypted = encryptToBytes(manager, ByteArray(1024) { it.toByte() })

        assertFails { decryptToBytes(manager, encrypted, otherKey) }
    }

    @Test
    fun aTamperedStreamIsRejectedRatherThanSilentlyDecoded() {
        val manager = createEncryptionManager()
        val encrypted = encryptToBytes(manager, ByteArray(1024) { it.toByte() })
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0x01).toByte()

        assertFailsWith<GeneralSecurityException> { decryptToBytes(manager, encrypted) }
    }

    @Test
    fun droppingTheFinalChunkOfAStreamIsDetected() {
        val manager = createEncryptionManager()
        val input = ByteArray(streamChunkSize + 1) { (it % 251).toByte() }
        val encrypted = encryptToBytes(manager, input)

        val chunks = chunksOf(encrypted)
        val truncated = rebuildStream(encrypted.copyOf(ivLength), chunks.dropLast(1))

        assertFailsWith<GeneralSecurityException> { decryptToBytes(manager, truncated) }
    }

    @Test
    fun swappingTwoChunksOfAStreamIsDetected() {
        val manager = createEncryptionManager()
        val input = ByteArray(streamChunkSize + 1) { (it % 251).toByte() }
        val encrypted = encryptToBytes(manager, input)

        val chunks = chunksOf(encrypted)
        val reordered = rebuildStream(encrypted.copyOf(ivLength), chunks.reversed())

        assertFailsWith<GeneralSecurityException> { decryptToBytes(manager, reordered) }
    }

    @Test
    fun aStreamThatEndsBeforeItsIvIsRefused() {
        val manager = createEncryptionManager()

        assertFailsWith<IllegalArgumentException> {
            decryptToBytes(manager, ByteArray(ivLength - 1))
        }
    }

    @Test
    fun aStreamHoldingAnIvButNoChunksIsRefused() {
        val manager = createEncryptionManager()

        assertFailsWith<IllegalStateException> {
            decryptToBytes(manager, ByteArray(ivLength))
        }
    }

    @Test
    fun aStreamThatEndsPartWayThroughAChunkIsRefused() {
        val manager = createEncryptionManager()
        val encrypted = encryptToBytes(manager, ByteArray(1024) { it.toByte() })

        assertFailsWith<IllegalArgumentException> {
            decryptToBytes(manager, encrypted.copyOf(encrypted.size - 8))
        }
    }

    @Test
    fun aPayloadSealedByTheOtherPlatformStillOpensHere() {
        val manager = createEncryptionManager()

        assertEquals(
            CryptoGoldenFixtures.PAYLOAD_PLAIN_TEXT,
            manager.decryptPayload(CryptoGoldenFixtures.PAYLOAD_BASE64, CryptoGoldenFixtures.KEY)
        )
    }

    @Test
    fun aStreamSealedByTheOtherPlatformStillOpensHere() {
        val manager = createEncryptionManager()
        val encrypted = Base64.getDecoder().decode(CryptoGoldenFixtures.STREAM_BASE64)

        val decrypted = decryptToBytes(manager, encrypted, CryptoGoldenFixtures.KEY)

        assertEquals(CryptoGoldenFixtures.STREAM_PLAIN_TEXT, String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun aStreamLockedWithALabelOpensWithTheSameLabel() {
        val manager = createEncryptionManager()
        val input = ByteArray(1024) { it.toByte() }
        val label = "media_1.jpg from byte 0".encodeToByteArray()

        val decrypted = decryptToBytes(manager, encryptToBytes(manager, input, label), streamLabel = label)

        assertContentEquals(input, decrypted)
    }

    @Test
    fun aStreamLockedForOneLabelCannotBeOpenedWithAnother() {
        val manager = createEncryptionManager()
        val encrypted = encryptToBytes(manager, ByteArray(1024) { it.toByte() }, "media_1.jpg".encodeToByteArray())

        assertFails { decryptToBytes(manager, encrypted, streamLabel = "media_2.jpg".encodeToByteArray()) }
    }

    @Test
    fun aStreamLockedWithALabelCannotBeOpenedWithoutIt() {
        val manager = createEncryptionManager()
        val encrypted = encryptToBytes(manager, ByteArray(1024) { it.toByte() }, "media_1.jpg".encodeToByteArray())

        assertFails { decryptToBytes(manager, encrypted) }
    }

    @Test
    fun aMediaPieceOpensOnlyInItsOwnPlace() {
        val manager = createEncryptionManager()
        val piece = ByteArray(1024) { it.toByte() }
        val encrypted = encryptToBytes(manager, piece, mediaPieceLabel("video.mp4", 1, 3))

        assertContentEquals(piece, decryptToBytes(manager, encrypted, streamLabel = mediaPieceLabel("video.mp4", 1, 3)))
    }

    @Test
    fun aMediaPieceMovedToAnotherPositionCannotBeOpened() {
        val manager = createEncryptionManager()
        val encrypted = encryptToBytes(manager, ByteArray(1024) { it.toByte() }, mediaPieceLabel("video.mp4", 0, 3))

        assertFails { decryptToBytes(manager, encrypted, streamLabel = mediaPieceLabel("video.mp4", 1, 3)) }
    }

    @Test
    fun aMediaPieceFromAnotherFileCannotBeOpened() {
        val manager = createEncryptionManager()
        val encrypted = encryptToBytes(manager, ByteArray(1024) { it.toByte() }, mediaPieceLabel("video.mp4", 0, 3))

        assertFails { decryptToBytes(manager, encrypted, streamLabel = mediaPieceLabel("photo.jpg", 0, 3)) }
    }

    @Test
    fun aMediaPieceCannotBeReusedInAFileWithADifferentNumberOfPieces() {
        val manager = createEncryptionManager()
        val encrypted = encryptToBytes(manager, ByteArray(1024) { it.toByte() }, mediaPieceLabel("video.mp4", 0, 3))

        assertFails { decryptToBytes(manager, encrypted, streamLabel = mediaPieceLabel("video.mp4", 0, 2)) }
    }

    private fun encryptToBytes(
        manager: SyncEncryptionManager,
        input: ByteArray,
        streamLabel: ByteArray = ByteArray(0)
    ): ByteArray {
        val output = ByteArrayOutputStream()
        manager.encryptStream(ByteArrayInputStream(input), output, key, streamLabel)
        return output.toByteArray()
    }

    private fun decryptToBytes(
        manager: SyncEncryptionManager,
        encrypted: ByteArray,
        decryptionKey: String = key,
        streamLabel: ByteArray = ByteArray(0)
    ): ByteArray {
        val output = ByteArrayOutputStream()
        manager.decryptStream(ByteArrayInputStream(encrypted), output, decryptionKey, streamLabel)
        return output.toByteArray()
    }

    private fun chunksOf(encrypted: ByteArray): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var position = ivLength
        while (position < encrypted.size) {
            val chunkSize = readIntBigEndian(encrypted, position)
            position += chunkLengthPrefixLength
            chunks.add(encrypted.copyOfRange(position, position + chunkSize))
            position += chunkSize
        }
        return chunks
    }

    private fun rebuildStream(iv: ByteArray, chunks: List<ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(iv)
        chunks.forEach { chunk ->
            output.write(chunk.size ushr 24)
            output.write(chunk.size ushr 16)
            output.write(chunk.size ushr 8)
            output.write(chunk.size)
            output.write(chunk)
        }
        return output.toByteArray()
    }

    private fun readIntBigEndian(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
