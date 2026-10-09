package com.emberr.domain.util.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import com.emberr.domain.util.media.mediaFileNameOnly
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
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

            val sampleRate = VOICE_SAMPLE_RATE
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

                val header = ByteArray(WAV_HEADER_SIZE)
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

        val cleanName = mediaFileNameOnly(fileName)

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