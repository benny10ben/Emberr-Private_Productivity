package com.emberr.domain.canvas

import com.emberr.data.local.prefs.SettingsManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CanvasViewPosition(val centerX: Float, val centerY: Float, val zoom: Float)

class CanvasViewPositionStore(private val settingsManager: SettingsManager) {

    private val json = Json { ignoreUnknownKeys = true }

    fun load(canvasNoteId: String): CanvasViewPosition? {
        val rawJson = settingsManager.getCanvasViewPositionJson(canvasNoteId) ?: return null
        return try {
            json.decodeFromString<CanvasViewPosition>(rawJson)
        } catch (_: Exception) {
            null
        }
    }

    fun save(canvasNoteId: String, position: CanvasViewPosition) {
        settingsManager.saveCanvasViewPositionJson(canvasNoteId, json.encodeToString(position))
    }

    fun forgetPositionsOfMissingCanvases(existingNoteIds: Set<String>): Int {
        val missingCanvasIds = settingsManager.getCanvasIdsWithSavedViewPosition() - existingNoteIds
        missingCanvasIds.forEach { canvasNoteId -> settingsManager.removeCanvasViewPosition(canvasNoteId) }
        return missingCanvasIds.size
    }
}
