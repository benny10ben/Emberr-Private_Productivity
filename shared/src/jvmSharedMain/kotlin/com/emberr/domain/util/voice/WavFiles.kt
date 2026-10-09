package com.emberr.domain.util.voice

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val VOICE_SAMPLE_RATE = 22050
const val WAV_HEADER_SIZE = 44

private const val FORMAT_CHUNK_SIZE = 16
private const val PCM_FORMAT_CODE: Short = 1
private const val MONO_CHANNEL_COUNT: Short = 1
private const val BITS_PER_SAMPLE: Short = 16
private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8

fun writeWavHeader(file: File, totalAudioLen: Long, sampleRate: Int) {
    val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".toByteArray(Charsets.US_ASCII))
        .putInt((totalAudioLen + WAV_HEADER_SIZE - 8).toInt())
        .put("WAVE".toByteArray(Charsets.US_ASCII))
        .put("fmt ".toByteArray(Charsets.US_ASCII))
        .putInt(FORMAT_CHUNK_SIZE)
        .putShort(PCM_FORMAT_CODE)
        .putShort(MONO_CHANNEL_COUNT)
        .putInt(sampleRate)
        .putInt(sampleRate * BYTES_PER_SAMPLE)
        .putShort(BYTES_PER_SAMPLE.toShort())
        .putShort(BITS_PER_SAMPLE)
        .put("data".toByteArray(Charsets.US_ASCII))
        .putInt(totalAudioLen.toInt())

    RandomAccessFile(file, "rw").use { raf ->
        raf.seek(0)
        raf.write(header.array())
    }
}
