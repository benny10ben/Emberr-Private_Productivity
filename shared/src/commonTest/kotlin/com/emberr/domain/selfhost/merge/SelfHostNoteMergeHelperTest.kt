package com.emberr.domain.selfhost.merge

import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.selfhost.translation.BlockTombstone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelfHostNoteMergeHelperTest {

    private val noteId = "note-1"
    private val blockJson = Json { ignoreUnknownKeys = true }

    private fun blockEntity(
        blockId: String,
        text: String,
        updatedAt: Long,
        displayOrder: Int = 0,
        noteId: String = this.noteId,
        isDeleted: Boolean = false
    ) = NoteBlockEntity(
        blockId = blockId,
        noteId = noteId,
        displayOrder = displayOrder,
        blockDataJson = blockJson.encodeToString(
            NoteBlock.serializer(),
            TextBlock(id = blockId, text = text, updatedAt = updatedAt)
        ),
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun decodedText(entity: NoteBlockEntity): String =
        (blockJson.decodeFromString(NoteBlock.serializer(), entity.blockDataJson) as TextBlock).text

    private fun decoded(entity: NoteBlockEntity): NoteBlock =
        blockJson.decodeFromString(NoteBlock.serializer(), entity.blockDataJson)

    @Test
    fun aBlockOnlyTheOtherDeviceHasIsAdded() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = emptyList(),
            remoteUpserts = listOf(blockEntity("block-1", "from the other device", 100L)),
            remoteDeletions = emptyList()
        )

        assertEquals(1, merged.size)
        assertEquals("from the other device", decodedText(merged.single()))
    }

    @Test
    fun aBlockOnlyThisDeviceHasIsKept() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "mine only", 100L)),
            remoteUpserts = emptyList(),
            remoteDeletions = emptyList()
        )

        assertEquals("mine only", decodedText(merged.single()))
    }

    @Test
    fun theNewerEditWinsWhenTheOtherDeviceEditedLast() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "mine", 100L)),
            remoteUpserts = listOf(blockEntity("block-1", "theirs", 200L)),
            remoteDeletions = emptyList()
        )

        assertEquals("theirs", decodedText(merged.single()))
    }

    @Test
    fun theNewerEditWinsWhenThisDeviceEditedLast() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "mine", 200L)),
            remoteUpserts = listOf(blockEntity("block-1", "theirs", 100L)),
            remoteDeletions = emptyList()
        )

        assertEquals("mine", decodedText(merged.single()))
    }

    @Test
    fun onAnExactTieTheLocalEditIsKeptSoTypingIsNeverSilentlyThrownAway() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "mine", 100L)),
            remoteUpserts = listOf(blockEntity("block-1", "theirs", 100L)),
            remoteDeletions = emptyList()
        )

        assertEquals("mine", decodedText(merged.single()))
    }

    @Test
    fun aRemoteDeletionAtTheSameMomentAsALocalEditStillDeletes() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "mine", 100L)),
            remoteUpserts = emptyList(),
            remoteDeletions = listOf(BlockTombstone("block-1", deletedAt = 100L))
        )

        assertTrue(merged.single().isDeleted)
    }

    @Test
    fun aRemoteDeletionOlderThanALocalEditIsIgnored() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "edited after the delete", 200L)),
            remoteUpserts = emptyList(),
            remoteDeletions = listOf(BlockTombstone("block-1", deletedAt = 100L))
        )

        assertTrue(!merged.single().isDeleted)
        assertEquals("edited after the delete", decodedText(merged.single()))
    }

    @Test
    fun aDeletedBlockKeepsItsPositionSoNothingElseShuffles() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "mine", 100L, displayOrder = 7)),
            remoteUpserts = emptyList(),
            remoteDeletions = listOf(BlockTombstone("block-1", deletedAt = 200L))
        )

        assertEquals(7, merged.single().displayOrder)
        assertEquals(200L, merged.single().updatedAt)
    }

    @Test
    fun aDeletedBlockStaysReadableJsonSoLaterReadsNeverBreak() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "goodbye", 100L)),
            remoteUpserts = emptyList(),
            remoteDeletions = listOf(BlockTombstone("block-1", deletedAt = 200L))
        )

        val tombstoned = decoded(merged.single())

        assertTrue(tombstoned.isDeleted)
        assertEquals("block-1", tombstoned.id)
    }

    @Test
    fun aDeletionOfABlockWeNeverHadIsStillRecordedAsReadableJson() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = emptyList(),
            remoteUpserts = emptyList(),
            remoteDeletions = listOf(BlockTombstone("block-unknown", deletedAt = 200L))
        )

        val tombstoned = decoded(merged.single())

        assertTrue(tombstoned.isDeleted)
        assertEquals("block-unknown", tombstoned.id)
        assertEquals(0, merged.single().displayOrder)
    }

    @Test
    fun aDeletionOfABlockWhoseStoredJsonIsCorruptStillProducesReadableJson() {
        val corruptLocalBlock = NoteBlockEntity(
            blockId = "block-1",
            noteId = noteId,
            displayOrder = 3,
            blockDataJson = "this is not json at all",
            updatedAt = 100L,
            isDeleted = false
        )

        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(corruptLocalBlock),
            remoteUpserts = emptyList(),
            remoteDeletions = listOf(BlockTombstone("block-1", deletedAt = 200L))
        )

        val tombstoned = decoded(merged.single())

        assertTrue(tombstoned.isDeleted)
        assertEquals("block-1", tombstoned.id)
        assertEquals(3, merged.single().displayOrder)
    }

    @Test
    fun blocksBelongingToAnotherNoteAreNeverMergedIn() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(
                blockEntity("block-mine", "mine", 100L),
                blockEntity("block-stray-local", "stray", 100L, noteId = "another-note")
            ),
            remoteUpserts = listOf(
                blockEntity("block-stray-remote", "stray", 100L, noteId = "another-note")
            ),
            remoteDeletions = emptyList()
        )

        assertEquals(listOf("block-mine"), merged.map { it.blockId })
    }

    @Test
    fun localOrderIsPreservedAndNewRemoteBlocksAreAppended() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(
                blockEntity("block-1", "one", 100L, displayOrder = 0),
                blockEntity("block-2", "two", 100L, displayOrder = 1)
            ),
            remoteUpserts = listOf(
                blockEntity("block-2", "two updated", 200L, displayOrder = 1),
                blockEntity("block-3", "three", 200L, displayOrder = 2)
            ),
            remoteDeletions = emptyList()
        )

        assertEquals(listOf("block-1", "block-2", "block-3"), merged.map { it.blockId })
        assertEquals("two updated", decodedText(merged[1]))
    }

    @Test
    fun mergingWithNothingOnEitherSideProducesNothing() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = emptyList(),
            remoteUpserts = emptyList(),
            remoteDeletions = emptyList()
        )

        assertTrue(merged.isEmpty())
    }

    @Test
    fun aRemoteEditAndARemoteDeletionOfTheSameBlockEndUpDeleted() {
        val merged = NoteMergeHelper.mergeBlocks(
            noteId = noteId,
            localBlocks = listOf(blockEntity("block-1", "mine", 100L)),
            remoteUpserts = listOf(blockEntity("block-1", "theirs", 200L)),
            remoteDeletions = listOf(BlockTombstone("block-1", deletedAt = 300L))
        )

        assertTrue(merged.single().isDeleted)
        assertEquals(300L, merged.single().updatedAt)
    }
}
