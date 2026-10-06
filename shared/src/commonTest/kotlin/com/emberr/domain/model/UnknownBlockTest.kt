package com.emberr.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UnknownBlockTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mapBlockFromANewerVersion =
        """{"type":"map","id":"map-1","latitude":52.5,"placeName":"Berlin","updatedAt":100}"""

    @Test
    fun aBlockTypeFromANewerVersionIsKeptInsteadOfFailing() {
        val block = json.decodeFromString(NoteBlockSerializer, mapBlockFromANewerVersion)

        val unknownBlock = assertIs<UnknownBlock>(block)
        assertEquals("map-1", unknownBlock.id)
        assertEquals(100L, unknownBlock.updatedAt)
    }

    @Test
    fun aBlockTypeFromANewerVersionIsWrittenBackExactlyAsItArrived() {
        val block = json.decodeFromString(NoteBlockSerializer, mapBlockFromANewerVersion)

        val writtenBack = json.parseToJsonElement(json.encodeToString(NoteBlockSerializer, block))

        assertEquals(json.parseToJsonElement(mapBlockFromANewerVersion), writtenBack)
    }

    @Test
    fun deletingABlockFromANewerVersionKeepsItsDataAndMarksItDeleted() {
        val block = json.decodeFromString(NoteBlockSerializer, mapBlockFromANewerVersion)

        val deleted = block.withDeleted(deleted = true, now = 500L)
        val writtenBack = json.parseToJsonElement(json.encodeToString(NoteBlockSerializer, deleted)).jsonObject

        assertEquals(JsonPrimitive("map"), writtenBack["type"])
        assertEquals(JsonPrimitive("Berlin"), writtenBack["placeName"])
        assertEquals(JsonPrimitive(true), writtenBack["isDeleted"])
        assertEquals(JsonPrimitive(500L), writtenBack["updatedAt"])
    }

    @Test
    fun aNoteWithABlockFromANewerVersionStillLoadsEveryBlock() {
        val noteJson = """{"version":1,"blocks":[{"type":"text","id":"text-1","text":"hello"},$mapBlockFromANewerVersion]}"""

        val content = json.decodeFromString<NoteContent>(noteJson)

        assertEquals(listOf("text-1", "map-1"), content.blocks.map { it.id })
        assertIs<TextBlock>(content.blocks[0])
        assertIs<UnknownBlock>(content.blocks[1])
    }

    @Test
    fun everyKnownBlockTypeStillLoadsAsItself() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { original ->
            val encoded = json.encodeToString(NoteBlockSerializer, original)

            assertEquals(original, json.decodeFromString(NoteBlockSerializer, encoded), "round trip changed ${original::class.simpleName}")
        }
    }

    @Test
    fun knownBlockTypesAreSavedInExactlyTheSameFormatAsBefore() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            assertEquals(
                json.encodeToString(NoteBlock.serializer(), block),
                json.encodeToString(NoteBlockSerializer, block),
                "saved format changed for ${block::class.simpleName}"
            )
        }
    }
}
