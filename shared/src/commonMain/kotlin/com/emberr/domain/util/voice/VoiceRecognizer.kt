package com.emberr.domain.util.voice

const val VOICE_RECOGNITION_UNAVAILABLE_MESSAGE = "Voice recognition is unavailable on this device."

/**
 * Multiplatform contract for voice-to-text recognition.
 */
interface VoiceRecognizer {
    fun startListening(
        onPartial: (String) -> Unit,
        onSegment: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onPermissionNeeded: () -> Unit
    )
    fun stopListening()
    fun destroy()
}