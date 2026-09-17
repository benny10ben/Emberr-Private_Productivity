package com.emberr.domain.selfhost.translation

import com.emberr.data.local.room.DEFAULT_SPACE_ID
import com.emberr.data.local.room.NoteBlockEntity
import com.emberr.data.local.room.NoteMetadataEntity
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TestNoteBlocks
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotePayloadRoundTripTest {

    private val blockJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val metadata = NoteMetadataEntity(
        noteId = "note-1",
        title = "Shopping list",
        icon = "🛒",
        folderId = "folder-1",
        isDaily = false,
        dateString = null,
        createdAt = 1_000L,
        updatedAt = 2_000L,
        filePath = "/local/only/path.json",
        snippet = "Buy milk",
        isFavorite = true,
        coverImagePath = "cover.png",
        trashedAt = null,
        isSubNote = true,
        showWordCount = true,
        sortOrder = 4,
        isTemplate = false,
        selfHostSyncedAt = 9_999L,
        spaceId = "space-1"
    )

    private fun entityFor(block: NoteBlock, displayOrder: Int, isDeleted: Boolean = false) =
        NoteBlockEntity(
            blockId = block.id,
            noteId = metadata.noteId,
            displayOrder = displayOrder,
            blockDataJson = blockJson.encodeToString(NoteBlock.serializer(), block),
            updatedAt = block.updatedAt,
            isDeleted = isDeleted
        )

    @Test
    fun everyBlockTypeSurvivesTheTripToAnotherDeviceAndBack() {
        val blocks = TestNoteBlocks.oneOfEveryBlockType()
        val entities = blocks.mapIndexed { index, block -> entityFor(block, index) }

        val payloadJson = NoteJsonCompiler.compileNoteToJson(metadata, entities)
        val operations = NoteJsonParser.parseJsonToDatabaseOperations(payloadJson)

        assertEquals(entities.size, operations.blockUpserts.size)
        operations.blockUpserts.forEachIndexed { index, restored ->
            val decoded = blockJson.decodeFromString(NoteBlock.serializer(), restored.blockDataJson)

            assertEquals(blocks[index], decoded, "block ${blocks[index]::class.simpleName} changed in transit")
            assertEquals(entities[index].blockId, restored.blockId)
            assertEquals(entities[index].displayOrder, restored.displayOrder)
            assertEquals(entities[index].updatedAt, restored.updatedAt)
            assertTrue(!restored.isDeleted)
        }
    }

    @Test
    fun theNotesOwnDetailsSurviveTheTripApartFromLocalOnlyFields() {
        val payloadJson = NoteJsonCompiler.compileNoteToJson(metadata, emptyList())

        val restored = NoteJsonParser.parseJsonToDatabaseOperations(payloadJson).metadataUpsert

        assertEquals(metadata.copy(filePath = "", selfHostSyncedAt = 0L), restored)
    }

    @Test
    fun theLocalFilePathIsNeverCarriedAcrossBecauseItMeansNothingOnTheOtherDevice() {
        val payloadJson = NoteJsonCompiler.compileNoteToJson(metadata, emptyList())

        val restored = NoteJsonParser.parseJsonToDatabaseOperations(payloadJson).metadataUpsert

        assertEquals("", restored.filePath)
    }

    @Test
    fun deletedBlocksTravelAsTombstonesRatherThanContent() {
        val liveBlock = TestNoteBlocks.oneOfEveryBlockType().first()
        val deletedBlock = TestNoteBlocks.oneOfEveryBlockType()[1]
        val entities = listOf(
            entityFor(liveBlock, 0),
            entityFor(deletedBlock, 1, isDeleted = true)
        )

        val operations = NoteJsonParser.parseJsonToDatabaseOperations(
            NoteJsonCompiler.compileNoteToJson(metadata, entities)
        )

        assertEquals(listOf(liveBlock.id), operations.blockUpserts.map { it.blockId })
        assertEquals(listOf(deletedBlock.id), operations.blockDeletions.map { it.blockId })
        assertEquals(deletedBlock.updatedAt, operations.blockDeletions.single().deletedAt)
    }

    @Test
    fun blocksBelongingToAnotherNoteAreNeverSent() {
        val ownBlock = TestNoteBlocks.oneOfEveryBlockType().first()
        val strayEntity = entityFor(TestNoteBlocks.oneOfEveryBlockType()[1], 1)
            .copy(noteId = "a-different-note")

        val operations = NoteJsonParser.parseJsonToDatabaseOperations(
            NoteJsonCompiler.compileNoteToJson(metadata, listOf(entityFor(ownBlock, 0), strayEntity))
        )

        assertEquals(listOf(ownBlock.id), operations.blockUpserts.map { it.blockId })
        assertTrue(operations.blockDeletions.isEmpty())
    }

    @Test
    fun aBlockWhoseStoredJsonIsCorruptStopsTheSyncInsteadOfSendingRubbish() {
        val corruptEntity = NoteBlockEntity(
            blockId = "block-1",
            noteId = metadata.noteId,
            displayOrder = 0,
            blockDataJson = "this is not json at all",
            updatedAt = 100L,
            isDeleted = false
        )

        assertFailsWith<NotePayloadSyncException> {
            NoteJsonCompiler.compileNoteToJson(metadata, listOf(corruptEntity))
        }
    }

    @Test
    fun anUnreadablePayloadFromTheOtherDeviceIsReportedClearly() {
        assertFailsWith<NotePayloadSyncException> {
            NoteJsonParser.parseJsonToDatabaseOperations("this is not json at all")
        }
        assertFailsWith<NotePayloadSyncException> {
            NoteJsonParser.parseJsonToDatabaseOperations("{}")
        }
    }

    @Test
    fun theNewestTombstoneWinsWhenTheSameBlockIsListedTwice() {
        val payload = NotePayload(
            noteId = metadata.noteId,
            title = metadata.title,
            createdAt = 1_000L,
            updatedAt = 2_000L,
            filePath = "",
            tombstones = listOf(
                BlockTombstone("block-1", deletedAt = 100L),
                BlockTombstone("block-1", deletedAt = 300L),
                BlockTombstone("block-1", deletedAt = 200L),
                BlockTombstone("block-2", deletedAt = 50L)
            )
        )

        val operations = NoteJsonParser.parseJsonToDatabaseOperations(
            blockJson.encodeToString(NotePayload.serializer(), payload)
        )

        assertEquals(2, operations.blockDeletions.size)
        assertEquals(300L, operations.blockDeletions.first { it.blockId == "block-1" }.deletedAt)
        assertEquals(50L, operations.blockDeletions.first { it.blockId == "block-2" }.deletedAt)
    }

    @Test
    fun aPayloadClaimingABlockIsBothAliveAndDeletedIsRefused() {
        val payload = NotePayload(
            noteId = metadata.noteId,
            title = metadata.title,
            createdAt = 1_000L,
            updatedAt = 2_000L,
            filePath = "",
            blocks = listOf(
                NoteBlockPayload(
                    blockId = "block-1",
                    displayOrder = 0,
                    updatedAt = 100L,
                    content = blockJson.parseToJsonElement("{\"type\":\"text\",\"id\":\"block-1\"}")
                )
            ),
            tombstones = listOf(BlockTombstone("block-1", deletedAt = 200L))
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            NoteJsonParser.parseJsonToDatabaseOperations(
                blockJson.encodeToString(NotePayload.serializer(), payload)
            )
        }

        assertTrue("block-1" in failure.message.orEmpty())
    }

    @Test
    fun theWireFormatVersionIsWrittenIntoEveryPayload() {
        val payloadJson = NoteJsonCompiler.compileNoteToJson(metadata, emptyList())

        assertTrue("\"schemaVersion\":$NOTE_PAYLOAD_SCHEMA_VERSION" in payloadJson.replace(" ", ""))
        assertEquals(1, NOTE_PAYLOAD_SCHEMA_VERSION)
    }

    @Test
    fun theSpaceANoteBelongsToTravelsWithIt() {
        val operations = NoteJsonParser.parseJsonToDatabaseOperations(
            NoteJsonCompiler.compileNoteToJson(metadata, emptyList())
        )

        assertEquals("space-1", operations.metadataUpsert.spaceId)
    }

    @Test
    fun aPayloadFromBeforeSpacesExistedLandsInTheDefaultSpace() {
        val payloadWithoutSpace = """
            {"noteId":"note-1","title":"Shopping list","createdAt":1000,"updatedAt":2000,"filePath":""}
        """.trimIndent()

        val operations = NoteJsonParser.parseJsonToDatabaseOperations(payloadWithoutSpace)

        assertEquals(DEFAULT_SPACE_ID, operations.metadataUpsert.spaceId)
    }

    @Test
    fun searchEmbeddingsRideAlongWithTheNoteWhenTheyExist() {
        val embeddings = listOf(
            EmbeddedBlockPayload(blockId = "block-1", chunkText = "Buy milk", embedding = "0.1,0.2")
        )

        val operations = NoteJsonParser.parseJsonToDatabaseOperations(
            NoteJsonCompiler.compileNoteToJson(metadata, emptyList(), embeddings)
        )

        assertEquals(embeddings, operations.embeddedBlocks)
    }
}
