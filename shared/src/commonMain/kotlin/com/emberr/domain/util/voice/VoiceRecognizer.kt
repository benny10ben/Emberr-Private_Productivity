package com.emberr.domain.util.voice

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