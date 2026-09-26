package com.emberr.domain.canvas

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.CanvasStrokeTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val ERASER_SETTINGS_NAME = "ERASER"
private const val TEXT_SETTINGS_NAME = "TEXT"
private const val LINE_STYLE_SETTINGS_NAME = "LINE_STYLE"
const val LINE_PATTERN_DOTTED = "dotted"
const val LINE_PATTERN_DASHED = "dashed"

enum class CanvasLineStyle(val pattern: String?, val hasArrowHead: Boolean) {
    ARROW(null, true),
    DOTTED_ARROW(LINE_PATTERN_DOTTED, true),
    DASHED_ARROW(LINE_PATTERN_DASHED, true),
    SOLID_LINE(null, false),
    DOTTED_LINE(LINE_PATTERN_DOTTED, false),
    DASHED_LINE(LINE_PATTERN_DASHED, false)
}
const val DEFAULT_ERASER_RADIUS = 10f

@Serializable
data class CanvasStrokeStyle(
    val colorName: String? = null,
    val width: Float,
    val usesPressure: Boolean,
    val opacity: Float = 1f
)

@Serializable
private data class CanvasEraserSettings(val radius: Float = DEFAULT_ERASER_RADIUS)

val CanvasStrokeTool.defaultStyle: CanvasStrokeStyle
    get() = when (this) {
        CanvasStrokeTool.PEN -> CanvasStrokeStyle(width = 4f, usesPressure = true)
        CanvasStrokeTool.HIGHLIGHTER -> CanvasStrokeStyle(width = 20f, usesPressure = false)
        CanvasStrokeTool.LINE -> CanvasStrokeStyle(width = 2f, usesPressure = false)
    }

class CanvasToolSettingsStore(private val settingsManager: SettingsManager) {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadStrokeStyle(tool: CanvasStrokeTool): CanvasStrokeStyle {
        val rawJson = settingsManager.getCanvasToolSettingsJson(tool.name) ?: return tool.defaultStyle
        return try {
            json.decodeFromString<CanvasStrokeStyle>(rawJson)
        } catch (_: Exception) {
            tool.defaultStyle
        }
    }

    fun saveStrokeStyle(tool: CanvasStrokeTool, style: CanvasStrokeStyle) {
        settingsManager.saveCanvasToolSettingsJson(tool.name, json.encodeToString(style))
    }

    fun loadEraserRadius(): Float {
        val rawJson = settingsManager.getCanvasToolSettingsJson(ERASER_SETTINGS_NAME) ?: return DEFAULT_ERASER_RADIUS
        return try {
            json.decodeFromString<CanvasEraserSettings>(rawJson).radius
        } catch (_: Exception) {
            DEFAULT_ERASER_RADIUS
        }
    }

    fun saveEraserRadius(radius: Float) {
        settingsManager.saveCanvasToolSettingsJson(ERASER_SETTINGS_NAME, json.encodeToString(CanvasEraserSettings(radius)))
    }

    fun loadTextStyle(): CanvasTextStyle {
        val rawJson = settingsManager.getCanvasToolSettingsJson(TEXT_SETTINGS_NAME) ?: return CanvasTextStyle()
        return try {
            json.decodeFromString<CanvasTextStyle>(rawJson)
        } catch (_: Exception) {
            CanvasTextStyle()
        }
    }

    fun saveTextStyle(style: CanvasTextStyle) {
        settingsManager.saveCanvasToolSettingsJson(TEXT_SETTINGS_NAME, json.encodeToString(style))
    }

    fun loadLineStyle(): CanvasLineStyle {
        val savedName = settingsManager.getCanvasToolSettingsJson(LINE_STYLE_SETTINGS_NAME)
        return CanvasLineStyle.entries.firstOrNull { it.name == savedName } ?: CanvasLineStyle.ARROW
    }

    fun saveLineStyle(style: CanvasLineStyle) {
        settingsManager.saveCanvasToolSettingsJson(LINE_STYLE_SETTINGS_NAME, style.name)
    }
}
