package com.emberr.domain.util.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

class AndroidAudioRecorder(private val context: Context) : AudioRecorder {
    private var player: MediaPlayer? = null
    private var audioRecord: AudioRecord? = null
    private var currentFile: File? = null
    private var recordingStartTime = 0L
    private var isRecording = false
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mediaStorageDir = File(context.filesDir, "media").apply {
        mkdirs()
    }

    @SuppressLint("MissingPermission")
    override fun startRecording() {
        try {
            val fileName = "voice_${UUID.randomUUID()}.wav"
            currentFile = File(mediaStorageDir, fileName)

            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()
            isRecording = true
            recordingStartTime = System.currentTimeMillis()

            recordingJob = scope.launch {
                val data = ByteArray(bufferSize)
                val out = FileOutputStream(currentFile)

                val header = ByteArray(44)
                out.write(header)

                var totalAudioLen = 0L
                while (isRecording) {
                    val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                    if (read > 0) {
                        out.write(data, 0, read)
                        totalAudioLen += read
                    }
                }
                out.close()

                writeWavHeader(currentFile!!, totalAudioLen, sampleRate)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            audioRecord = null
        }
    }

    private fun writeWavHeader(file: File, totalAudioLen: Long, sampleRate: Int) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * 16 * 1 / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1
        header[21] = 0; header[22] = 1
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = 2
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(header)
        }
    }

    override fun stopRecording(cancel: Boolean): Pair<String, Int>? {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
        }

        runBlocking { recordingJob?.join() }
        recordingJob = null

        if (cancel) {
            currentFile?.delete()
            return null
        }

        val durationSecs = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        val savedName = currentFile?.name
        currentFile = null

        return savedName?.let { Pair(it, durationSecs) }
    }

    override fun play(fileName: String, onCompletion: () -> Unit) {
        player?.release()

        val cleanName = fileName.substringAfterLast("/")

        val targetFile = File(mediaStorageDir, cleanName)

        if (!targetFile.exists()) {
            onCompletion()
            return
        }

        player = MediaPlayer().apply {
            setDataSource(targetFile.absolutePath)
            prepare()
            start()
            setOnCompletionListener {
                onCompletion()
                release()
                player = null
            }
        }
    }

    override fun stopPlaying() {
        player?.apply {
            if (isPlaying) stop()
            release()
        }
        player = null
    }
}