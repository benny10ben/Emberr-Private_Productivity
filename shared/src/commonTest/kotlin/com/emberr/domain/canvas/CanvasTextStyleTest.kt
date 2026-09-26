package com.emberr.domain.canvas

import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasNodeType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasTextStyleTest {

    private val freeText = CanvasNodeEntity(
        nodeId = "text-1",
        noteId = "canvas-1",
        x = 0f,
        y = 0f,
        width = 120f,
        height = 32f,
        text = "Hello",
        createdAt = 1L,
        updatedAt = 1L,
        type = CanvasNodeType.FREE_TEXT
    )

    @Test
    fun textSavedBeforeStylesExistedKeepsTheAppFontWithDefaultWeightAndSize() {
        assertEquals(CanvasTextStyle(fontFamily = null), freeText.textStyle)
    }

    @Test
    fun newTextStartsInFuzzyBubbles() {
        assertEquals("fuzzybubbles", CanvasTextStyle().fontFamily)
    }

    @Test
    fun aSavedStyleThatNeverPickedAFontLoadsAsFuzzyBubblesButAPickedDefaultFontStays() {
        val settingsJson = Json { ignoreUnknownKeys = true }

        assertEquals("fuzzybubbles", settingsJson.decodeFromString<CanvasTextStyle>("{\"fontSize\":24.0}").fontFamily)
        val appFontStyle = CanvasTextStyle(fontFamily = null)
        assertEquals(appFontStyle, settingsJson.decodeFromString<CanvasTextStyle>(settingsJson.encodeToString(appFontStyle)))
    }

    @Test
    fun aStyleSurvivesBeingWrittenOntoTheTextAndReadBack() {
        val style = CanvasTextStyle(
            fontFamily = "serif",
            fontWeight = 700,
            textColor = "red",
            backgroundColor = "yellow",
            fontSize = 32f,
            textAlign = "center"
        )

        assertEquals(style, freeText.withTextStyle(style).textStyle)
    }

    @Test
    fun theBackgroundLivesInTheSameColorFieldThatBoxesUse() {
        val styled = freeText.withTextStyle(CanvasTextStyle(backgroundColor = "blue"))

        assertEquals("blue", styled.color)
        assertEquals("Hello", styled.text)
    }
}
