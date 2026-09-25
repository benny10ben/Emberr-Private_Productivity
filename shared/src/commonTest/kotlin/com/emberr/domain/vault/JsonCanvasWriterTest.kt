package com.emberr.domain.vault

import com.emberr.data.local.room.entity.CanvasEdgeEntity
import com.emberr.data.local.room.entity.CanvasNodeEntity
import com.emberr.data.local.room.entity.CanvasSide
import com.emberr.domain.canvas.CanvasContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonCanvasWriterTest {

    private fun node(nodeId: String, text: String, isDeleted: Boolean = false) = CanvasNodeEntity(
        nodeId = nodeId,
        noteId = "canvas-1",
        x = 10.4f,
        y = -20.6f,
        width = 250f,
        height = 60f,
        text = text,
        createdAt = 1L,
        updatedAt = 1L,
        isDeleted = isDeleted
    )

    @Test
    fun theFileFollowsTheJsonCanvasFormatThatObsidianReads() {
        val canvas = CanvasContent(
            nodes = listOf(node("a", "First \"quoted\"\nline"), node("b", "Second")),
            edges = listOf(
                CanvasEdgeEntity(
                    edgeId = "e", noteId = "canvas-1", fromNodeId = "a", fromSide = CanvasSide.RIGHT,
                    toNodeId = "b", toSide = CanvasSide.LEFT, createdAt = 1L, updatedAt = 1L
                )
            )
        )

        val written = Json.parseToJsonElement(JsonCanvasWriter.write(canvas)).jsonObject

        val firstNode = written.getValue("nodes").jsonArray.first().jsonObject
        assertEquals("a", firstNode.getValue("id").jsonPrimitive.content)
        assertEquals("text", firstNode.getValue("type").jsonPrimitive.content)
        assertEquals("First \"quoted\"\nline", firstNode.getValue("text").jsonPrimitive.content)
        assertEquals(10, firstNode.getValue("x").jsonPrimitive.int)
        assertEquals(-21, firstNode.getValue("y").jsonPrimitive.int)
        assertEquals(250, firstNode.getValue("width").jsonPrimitive.int)

        val edge = written.getValue("edges").jsonArray.single().jsonObject
        assertEquals("a", edge.getValue("fromNode").jsonPrimitive.content)
        assertEquals("right", edge.getValue("fromSide").jsonPrimitive.content)
        assertEquals("b", edge.getValue("toNode").jsonPrimitive.content)
        assertEquals("left", edge.getValue("toSide").jsonPrimitive.content)
    }

    @Test
    fun aColoredBoxIsWrittenWithItsHexColorAndAPlainBoxHasNoColor() {
        val canvas = CanvasContent(nodes = listOf(node("a", "Blue").copy(color = "blue"), node("b", "Plain")))

        val nodes = Json.parseToJsonElement(JsonCanvasWriter.write(canvas)).jsonObject.getValue("nodes").jsonArray

        assertEquals("#90CAF9", nodes[0].jsonObject.getValue("color").jsonPrimitive.content)
        assertEquals(null, nodes[1].jsonObject["color"])
    }

    @Test
    fun aGroupIsWrittenFirstAsAJsonCanvasGroupWithItsTitleAsLabel() {
        val group = node("g", "Ideas").copy(type = com.emberr.data.local.room.entity.CanvasNodeType.GROUP)
        val canvas = CanvasContent(nodes = listOf(node("a", "Inside"), group))

        val nodes = Json.parseToJsonElement(JsonCanvasWriter.write(canvas)).jsonObject.getValue("nodes").jsonArray
        val writtenGroup = nodes[0].jsonObject

        assertEquals("group", writtenGroup.getValue("type").jsonPrimitive.content)
        assertEquals("Ideas", writtenGroup.getValue("label").jsonPrimitive.content)
        assertEquals(null, writtenGroup["text"])
        assertEquals("text", nodes[1].jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun deletedBoxesAndTheirArrowsAreLeftOutOfTheFile() {
        val canvas = CanvasContent(
            nodes = listOf(node("a", "Kept"), node("b", "Deleted", isDeleted = true)),
            edges = listOf(
                CanvasEdgeEntity(
                    edgeId = "e", noteId = "canvas-1", fromNodeId = "a", fromSide = CanvasSide.RIGHT,
                    toNodeId = "b", toSide = CanvasSide.LEFT, createdAt = 1L, updatedAt = 1L
                )
            )
        )

        val written = Json.parseToJsonElement(JsonCanvasWriter.write(canvas)).jsonObject

        assertEquals(1, written.getValue("nodes").jsonArray.size)
        assertEquals(0, written.getValue("edges").jsonArray.size)
    }
}
