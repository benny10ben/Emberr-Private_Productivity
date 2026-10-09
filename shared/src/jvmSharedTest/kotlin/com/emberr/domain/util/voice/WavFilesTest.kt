package com.emberr.domain.util.voice

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WavFilesTest {

    private val workingDirectory = Files.createTempDirectory("emberr-wav-files").toFile()

    @AfterTest
    fun deleteWorkingDirectory() {
        workingDirectory.deleteRecursively()
    }

    private fun recordingWithAudio(audio: ByteArray): File =
        File(workingDirectory, "voice.wav").apply { writeBytes(ByteArray(WAV_HEADER_SIZE) + audio) }

    private fun readAscii(header: ByteBuffer, offset: Int): String =
        String(ByteArray(4) { header.get(offset + it) }, Charsets.US_ASCII)

    @Test
    fun theHeaderDescribesMono16BitPcmAtTheGivenSampleRate() {
        val audio = ByteArray(1000) { it.toByte() }
        val file = recordingWithAudio(audio)

        writeWavHeader(file, audio.size.toLong(), VOICE_SAMPLE_RATE)

        val header = ByteBuffer.wrap(file.readBytes(), 0, WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", readAscii(header, 0))
        assertEquals(1036, header.getInt(4))
        assertEquals("WAVE", readAscii(header, 8))
        assertEquals("fmt ", readAscii(header, 12))
        assertEquals(16, header.getInt(16))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(22050, header.getInt(24))
        assertEquals(44100, header.getInt(28))
        assertEquals(2, header.getShort(32).toInt())
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", readAscii(header, 36))
        assertEquals(1000, header.getInt(40))
    }

    @Test
    fun writingTheHeaderKeepsTheRecordedAudio() {
        val audio = ByteArray(1000) { (it % 7).toByte() }
        val file = recordingWithAudio(audio)

        writeWavHeader(file, audio.size.toLong(), VOICE_SAMPLE_RATE)

        val savedBytes = file.readBytes()
        assertEquals(WAV_HEADER_SIZE + audio.size, savedBytes.size)
        assertContentEquals(audio, savedBytes.copyOfRange(WAV_HEADER_SIZE, savedBytes.size))
    }
}
