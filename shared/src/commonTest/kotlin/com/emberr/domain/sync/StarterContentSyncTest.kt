package com.emberr.domain.sync

import com.emberr.data.local.room.entity.NoteBlockEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.CanvasMerge
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.TextBlock
import com.emberr.domain.sample.STARTER_CONTENT_UPDATED_AT
import com.emberr.domain.sample.SampleCanvasContent
import com.emberr.domain.selfhost.merge.NoteMergeHelper as SelfHostNoteMergeHelper
import com.emberr.domain.selfhost.sync.SelfHostEntryType
import com.emberr.domain.selfhost.sync.SelfHostManifestEntry
import com.emberr.domain.selfhost.sync.tombstoneIdsReplacedByNewerNotes
import com.emberr.domain.selfhost.translation.BlockTombstone
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterContentSyncTest {

    private val laptopEditedAt = 1_000L
    private val blockJson = Json { ignoreUnknownKeys = true }

    private fun startHere(updatedAt: Long, trashedAt: Long? = null) = NoteMetadataEntity(
        noteId = "sample_note_welcome",
        title = "Start here",
        folderId = null,
        isDaily = false,
        dateString = null,
        createdAt = 1L,
        updatedAt = updatedAt,
        filePath = "",
        trashedAt = trashedAt
    )

    private fun welcomeLine(text: String, updatedAt: Long, isDeleted: Boolean = false) =
        TextBlock(id = "sample_note_welcome_line", text = text, updatedAt = updatedAt, isDeleted = isDeleted)

    private fun blockEntity(block: TextBlock) = NoteBlockEntity(
        blockId = block.id,
        noteId = "sample_note_welcome",
        displayOrder = 0,
        blockDataJson = blockJson.encodeToString(NoteBlock.serializer(), block),
        updatedAt = block.updatedAt,
        isDeleted = block.isDeleted
    )

    private fun lanMerge(remoteMeta: NoteMetadataEntity, remoteBlock: TextBlock) = IncomingNoteMerge.merge(
        localMeta = startHere(updatedAt = STARTER_CONTENT_UPDATED_AT),
        localContent = NoteContent(blocks = listOf(welcomeLine("Welcome to Emberr", STARTER_CONTENT_UPDATED_AT))),
        remoteMeta = remoteMeta,
        remoteContent = NoteContent(blocks = listOf(remoteBlock)),
        remoteUpdatedAt = remoteMeta.updatedAt
    )

    @Test
    fun overLanAFreshDevicesStarterCopyDoesNotUndoTextEditedOnTheLaptop() {
        val merged = lanMerge(
            remoteMeta = startHere(updatedAt = laptopEditedAt),
            remoteBlock = welcomeLine("My own notes", laptopEditedAt)
        )

        assertEquals("My own notes", (merged.content.blocks.single() as TextBlock).text)
    }

    @Test
    fun overLanAStarterNoteTrashedOnTheLaptopStaysInTheTrash() {
        val merged = lanMerge(
            remoteMeta = startHere(updatedAt = laptopEditedAt, trashedAt = laptopEditedAt),
            remoteBlock = welcomeLine("Welcome to Emberr", STARTER_CONTENT_UPDATED_AT)
        )

        assertEquals(laptopEditedAt, merged.metadata.trashedAt)
    }

    @Test
    fun overLanAStarterLineDeletedOnTheLaptopStaysDeleted() {
        val merged = lanMerge(
            remoteMeta = startHere(updatedAt = laptopEditedAt),
            remoteBlock = welcomeLine("Welcome to Emberr", laptopEditedAt, isDeleted = true)
        )

        assertTrue(merged.content.blocks.single().isDeleted)
    }

    @Test
    fun overSelfHostAFreshDevicesStarterCopyDoesNotUndoTextEditedOnTheLaptop() {
        val merged = SelfHostNoteMergeHelper.mergeBlocks(
            noteId = "sample_note_welcome",
            localBlocks = listOf(blockEntity(welcomeLine("Welcome to Emberr", STARTER_CONTENT_UPDATED_AT))),
            remoteUpserts = listOf(blockEntity(welcomeLine("My own notes", laptopEditedAt))),
            remoteDeletions = emptyList()
        )

        assertEquals(laptopEditedAt, merged.single().updatedAt)
    }

    @Test
    fun overSelfHostAStarterLineDeletedOnTheLaptopStaysDeleted() {
        val merged = SelfHostNoteMergeHelper.mergeBlocks(
            noteId = "sample_note_welcome",
            localBlocks = listOf(blockEntity(welcomeLine("Welcome to Emberr", STARTER_CONTENT_UPDATED_AT))),
            remoteUpserts = emptyList(),
            remoteDeletions = listOf(BlockTombstone(blockId = "sample_note_welcome_line", deletedAt = laptopEditedAt))
        )

        assertTrue(merged.single().isDeleted)
    }

    @Test
    fun aStarterNotePermanentlyDeletedOnTheLaptopIsNotBroughtBackByAFreshDevice() {
        val deletedOnLaptop = SelfHostManifestEntry(
            entryId = "sample_note_push_day",
            entryType = SelfHostEntryType.NOTE,
            updatedAt = laptopEditedAt,
            isDeleted = true
        )
        val freshDevicesStarterCopy = deletedOnLaptop.copy(updatedAt = STARTER_CONTENT_UPDATED_AT, isDeleted = false)

        val replaced = tombstoneIdsReplacedByNewerNotes(
            tombstones = listOf(deletedOnLaptop),
            serverEntries = listOf(deletedOnLaptop),
            localEntries = listOf(freshDevicesStarterCopy),
            entryIdsStillWaitingToUpload = emptySet()
        )

        assertTrue(replaced.isEmpty())
    }

    @Test
    fun aCanvasCardMovedOnTheLaptopIsNotMovedBackByAFreshDevice() {
        val freshDevicesCanvas = SampleCanvasContent.build(noteId = "sample_note_canvas", createdAt = STARTER_CONTENT_UPDATED_AT)
        val cardMovedOnLaptop = freshDevicesCanvas.nodes.first().let { it.copy(x = it.x + 200f, updatedAt = laptopEditedAt) }

        val itemsToTake = CanvasMerge.remoteItemsNewerThanLocal(
            local = freshDevicesCanvas,
            remote = freshDevicesCanvas.copy(nodes = listOf(cardMovedOnLaptop), edges = emptyList())
        )

        assertEquals(listOf(cardMovedOnLaptop.x), itemsToTake.nodes.map { it.x })
    }
}
