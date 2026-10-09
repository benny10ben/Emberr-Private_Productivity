package com.emberr.domain.util.voice

import com.emberr.core.desktop.DesktopAppStorage
import com.emberr.domain.util.media.mediaFileNameOnly
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import javax.sound.sampled.*

class DesktopAudioRecorder : AudioRecorder {

    private val audioDir = DesktopAppStorage.mediaDirectory.apply {
        mkdirs()
    }

    private var targetDataLine: TargetDataLine? = null
    private var currentFile: File? = null
    private var recordingStartTime = 0L
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var currentClip: Clip? = null

    override fun startRecording() {
        try {
            val fileName = "voice_${UUID.randomUUID()}.wav"
            currentFile = File(audioDir, fileName)

            // Standard CD-quality audio format: 44100 Hz, 16 bit, Mono, Signed, Little-Endian
            val format = AudioFormat(44100f, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)

            if (!AudioSystem.isLineSupported(info)) {
                println("Error: Microphone not supported on this device.")
                return
            }

            targetDataLine = AudioSystem.getLine(info) as TargetDataLine
            targetDataLine?.open(format)
            targetDataLine?.start()

            recordingStartTime = System.currentTimeMillis()

            recordingJob = scope.launch {
                try {
                    val audioStream = AudioInputStream(targetDataLine)
                    AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, currentFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stopRecording(cancel: Boolean): Pair<String, Int>? {
        try {
            targetDataLine?.stop()
            targetDataLine?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            targetDataLine = null
            recordingJob?.cancel()
            recordingJob = null
        }

        if (cancel) {
            currentFile?.delete()
            currentFile = null
            return null
        }

        val durationSecs = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()

        val savedName = currentFile?.name
        currentFile = null

        return savedName?.let { Pair(it, durationSecs) }
    }

    override fun play(fileName: String, onCompletion: () -> Unit) {
        stopPlaying()

        try {
            val file = File(audioDir, mediaFileNameOnly(fileName))
            if (!file.exists()) {
                println("Error: Audio file not found at ${file.absolutePath}")
                onCompletion()
                return
            }

            val audioStream = AudioSystem.getAudioInputStream(file)
            currentClip = AudioSystem.getClip()

            currentClip?.addLineListener { event ->
                if (event.type == LineEvent.Type.STOP) {
                    currentClip?.close()
                    onCompletion()
                }
            }

            currentClip?.open(audioStream)
            currentClip?.start()

        } catch (e: Exception) {
            e.printStackTrace()
            onCompletion()
        }
    }

    override fun stopPlaying() {
        try {
            currentClip?.apply {
                if (isRunning) stop()
                close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentClip = null
        }
    }
}