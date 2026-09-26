package com.emberr.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteBlockSerializationTest {

    private val repositoryJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mergeHelperJson = Json {
        ignoreUnknownKeys = true
    }

    private val parserJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val storedTypeNamesByBlockClass = mapOf(
        "TextBlock" to "text",
        "HeadingBlock" to "heading",
        "QuoteBlock" to "quote",
        "CheckboxBlock" to "checkbox",
        "BulletedListBlock" to "bullet",
        "NumberedListBlock" to "number",
        "ToggleBlock" to "toggle",
        "CodeBlock" to "code",
        "BookmarkBlock" to "bookmark",
        "LinkedNoteBlock" to "linked_note",
        "ImageBlock" to "image",
        "DocumentBlock" to "document",
        "TableBlock" to "table",
        "VoiceBlock" to "voice",
        "CanvasBlock" to "canvas",
        "SolidDividerBlock" to "solid_divider",
        "ThreeDotDividerBlock" to "dot_divider"
    )

    private fun storedTypeNameOf(json: Json, block: NoteBlock): String =
        json.parseToJsonElement(json.encodeToString<NoteBlock>(block))
            .jsonObject
            .getValue("type")
            .jsonPrimitive
            .content

    @Test
    fun theFixtureCoversEverySingleBlockType() {
        val coveredClasses = TestNoteBlocks.oneOfEveryBlockType()
            .map { it::class.simpleName }
            .toSet()

        assertEquals(storedTypeNamesByBlockClass.keys, coveredClasses)
    }

    @Test
    fun everyBlockTypeSurvivesAJsonRoundTripUnchanged() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { original ->
            val encoded = repositoryJson.encodeToString<NoteBlock>(original)
            val decoded = repositoryJson.decodeFromString<NoteBlock>(encoded)

            assertEquals(original, decoded, "round trip changed ${original::class.simpleName}")
        }
    }

    @Test
    fun everyBlockTypeKeepsItsStoredTypeName() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            val className = block::class.simpleName

            assertEquals(
                storedTypeNamesByBlockClass.getValue(className!!),
                storedTypeNameOf(repositoryJson, block),
                "stored type name changed for $className"
            )
        }
    }

    @Test
    fun aWholeNoteSurvivesAJsonRoundTripUnchanged() {
        val original = NoteContent(version = 1, blocks = TestNoteBlocks.oneOfEveryBlockType())

        val encoded = repositoryJson.encodeToString(original)

        assertEquals(original, repositoryJson.decodeFromString<NoteContent>(encoded))
    }

    @Test
    fun aBlockWrittenByTheRepositoryIsReadableByTheMergeHelperAndBack() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { original ->
            val writtenByRepository = repositoryJson.encodeToString<NoteBlock>(original)
            val readByMergeHelper = mergeHelperJson.decodeFromString<NoteBlock>(writtenByRepository)

            val writtenByMergeHelper = mergeHelperJson.encodeToString<NoteBlock>(readByMergeHelper)
            val readBackByRepository = repositoryJson.decodeFromString<NoteBlock>(writtenByMergeHelper)

            assertEquals(
                original,
                readBackByRepository,
                "the two Json settings disagree about ${original::class.simpleName}"
            )
        }
    }

    @Test
    fun aBlockWrittenByTheRepositoryIsReadableByTheSyncPayloadParser() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { original ->
            val writtenByRepository = repositoryJson.encodeToString<NoteBlock>(original)

            assertEquals(original, parserJson.decodeFromString<NoteBlock>(writtenByRepository))
        }
    }

    @Test
    fun theMergeHelperOmitsDefaultValuesWhileTheRepositoryWritesThemOut() {
        val plainBlock = TextBlock(id = "text-1", text = "hello")

        val repositoryFields = repositoryJson
            .parseToJsonElement(repositoryJson.encodeToString<NoteBlock>(plainBlock))
            .jsonObject
            .keys
        val mergeHelperFields = mergeHelperJson
            .parseToJsonElement(mergeHelperJson.encodeToString<NoteBlock>(plainBlock))
            .jsonObject
            .keys

        assertTrue(mergeHelperFields.size < repositoryFields.size)
        assertTrue(repositoryFields.containsAll(mergeHelperFields))
        assertTrue("updatedAt" in repositoryFields)
        assertTrue("updatedAt" !in mergeHelperFields)
    }

    @Test
    fun aBlockSavedByANewerVersionStillLoadsAndKeepsTheFieldsWeKnow() {
        val original = TextBlock(id = "text-1", text = "hello", updatedAt = 500L)

        val withAnUnknownField = JsonObject(
            repositoryJson
                .parseToJsonElement(repositoryJson.encodeToString<NoteBlock>(original))
                .jsonObject + ("fieldFromAFutureRelease" to JsonPrimitive("ignore me"))
        )

        val decoded = repositoryJson.decodeFromString<NoteBlock>(withAnUnknownField.toString())

        assertEquals(original, decoded)
    }
}
