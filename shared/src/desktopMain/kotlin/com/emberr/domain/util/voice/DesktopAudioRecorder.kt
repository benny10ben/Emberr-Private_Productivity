package com.emberr.domain.util.voice

import com.emberr.core.desktop.DesktopAppStorage
import com.emberr.domain.util.media.mediaFileNameOnly
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.sound.sampled.*

private const val READ_CHUNK_BYTES = VOICE_SAMPLE_RATE / 10 * 2
private const val SOUND_CHECK_BYTES = VOICE_SAMPLE_RATE / 2 * 2

class DesktopAudioRecorder : AudioRecorder {

    private class OpenMicrophone(val line: TargetDataLine, val firstAudio: ByteArray)

    private val audioDir = DesktopAppStorage.mediaDirectory.apply {
        mkdirs()
    }

    private val recordingFormat = AudioFormat(VOICE_SAMPLE_RATE.toFloat(), 16, 1, true, false)
    private val microphoneLineInfo = DataLine.Info(TargetDataLine::class.java, recordingFormat)

    @Volatile
    private var isRecording = false
    private var currentFile: File? = null
    private var recordingStartTime = 0L
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var currentClip: Clip? = null

    override fun startRecording() {
        val file = File(audioDir, "voice_${UUID.randomUUID()}.wav")
        currentFile = file
        isRecording = true
        recordingStartTime = System.currentTimeMillis()

        recordingJob = scope.launch {
            try {
                recordInto(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun recordInto(file: File) {
        val microphone = openMicrophoneThatHearsSound()
        if (microphone == null) {
            println("Error: No working microphone found.")
            return
        }

        var totalAudioLen = microphone.firstAudio.size.toLong()
        microphone.line.use { line ->
            FileOutputStream(file).use { out ->
                out.write(ByteArray(WAV_HEADER_SIZE))
                out.write(microphone.firstAudio)

                val data = ByteArray(READ_CHUNK_BYTES)
                while (isRecording) {
                    val read = line.read(data, 0, data.size)
                    if (read > 0) {
                        out.write(data, 0, read)
                        totalAudioLen += read
                    }
                }
            }
        }

        writeWavHeader(file, totalAudioLen, VOICE_SAMPLE_RATE)
    }

    private fun openMicrophoneThatHearsSound(): OpenMicrophone? {
        val candidates = AudioSystem.getMixerInfo().filter { AudioSystem.getMixer(it).isLineSupported(microphoneLineInfo) }

        var silentMicrophone: OpenMicrophone? = null
        for (mixerInfo in candidates) {
            if (!isRecording) break
            val microphone = openMicrophone(mixerInfo) ?: continue
            if (microphone.firstAudio.any { it != 0.toByte() }) {
                silentMicrophone?.line?.close()
                return microphone
            }
            if (silentMicrophone == null) silentMicrophone = microphone else microphone.line.close()
        }
        return silentMicrophone
    }

    private fun openMicrophone(mixerInfo: Mixer.Info): OpenMicrophone? {
        val line = try {
            AudioSystem.getMixer(mixerInfo).getLine(microphoneLineInfo) as TargetDataLine
        } catch (e: Exception) {
            return null
        }

        return try {
            line.open(recordingFormat)
            line.start()
            val firstAudio = ByteArrayOutputStream()
            val data = ByteArray(READ_CHUNK_BYTES)
            while (isRecording && firstAudio.size() < SOUND_CHECK_BYTES) {
                val read = line.read(data, 0, data.size)
                if (read > 0) firstAudio.write(data, 0, read)
            }
            OpenMicrophone(line, firstAudio.toByteArray())
        } catch (e: Exception) {
            line.close()
            null
        }
    }

    override fun stopRecording(cancel: Boolean): Pair<String, Int>? {
        isRecording = false
        runBlocking { recordingJob?.join() }
        recordingJob = null

        val file = currentFile
        currentFile = null
        if (file == null || !file.exists()) return null

        if (cancel) {
            file.delete()
            return null
        }

        val durationSecs = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        return Pair(file.name, durationSecs)
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