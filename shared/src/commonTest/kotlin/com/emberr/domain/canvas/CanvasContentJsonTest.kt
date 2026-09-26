package com.emberr.domain.canvas

import com.emberr.data.local.room.entity.CanvasStrokeEntity
import com.emberr.data.local.room.entity.CanvasStrokeTool
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasContentJsonTest {

    private val lanSyncJson = Json { ignoreUnknownKeys = true }

    private val stroke = CanvasStrokeEntity(
        strokeId = "stroke-1",
        noteId = "canvas-1",
        tool = CanvasStrokeTool.HIGHLIGHTER,
        x = 100f,
        y = 200f,
        points = "0,0 50,25,80",
        width = 24f,
        createdAt = 1L,
        updatedAt = 2L,
        color = "yellow"
    )

    @Test
    fun strokesAndTheirTombstonesSurviveTheLanSyncTrip() {
        val canvas = CanvasContent(strokes = listOf(stroke, stroke.copy(strokeId = "stroke-2", isDeleted = true)))

        assertEquals(canvas, lanSyncJson.decodeFromString<CanvasContent>(lanSyncJson.encodeToString(canvas)))
    }

    @Test
    fun aCanvasFromAnOlderAppWithoutStrokesReadsAsHavingNone() {
        val decoded = lanSyncJson.decodeFromString<CanvasContent>("{\"nodes\":[],\"edges\":[]}")

        assertTrue(decoded.strokes.isEmpty())
    }
}
