package com.emberr.domain.sync

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteContent
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncomingNoteMergeTest {

    private fun note(updatedAt: Long, trashedAt: Long? = null) = NoteMetadataEntity(
        noteId = "note-1",
        title = "Groceries",
        folderId = null,
        isDaily = false,
        dateString = null,
        createdAt = 1L,
        updatedAt = updatedAt,
        filePath = "",
        trashedAt = trashedAt
    )

    private fun text(id: String, body: String, updatedAt: Long) =
        TextBlock(id = id, text = body, updatedAt = updatedAt)

    private fun contentOf(vararg blocks: TextBlock) = NoteContent(blocks = blocks.toList())

    private fun textOf(content: NoteContent, blockId: String) =
        (content.blocks.single { it.id == blockId } as TextBlock).text

    @Test
    fun aNoteTrashedOnTheOtherDeviceKeepsTheNewerBlockEditedHere() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 150L),
            localContent = contentOf(text("block-1", "edited here", 150L)),
            remoteMeta = note(updatedAt = 200L, trashedAt = 200L),
            remoteContent = contentOf(text("block-1", "old version", 100L)),
            remoteUpdatedAt = 200L
        )

        assertEquals("edited here", textOf(merged.content, "block-1"))
        assertTrue(merged.hasChanges)
    }

    @Test
    fun aNoteTrashedOnTheOtherDeviceKeepsBlocksOnlyThisDeviceHas() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 150L),
            localContent = contentOf(text("block-1", "shared", 100L), text("block-2", "only here", 150L)),
            remoteMeta = note(updatedAt = 200L, trashedAt = 200L),
            remoteContent = contentOf(text("block-1", "shared", 100L)),
            remoteUpdatedAt = 200L
        )

        val keptBlock = merged.content.blocks.single { it.id == "block-2" }
        assertFalse(keptBlock.isDeleted)
        assertEquals("only here", textOf(merged.content, "block-2"))
    }

    @Test
    fun aNoteTrashedOnTheOtherDeviceKeepsItsOriginalTrashTime() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 150L),
            localContent = contentOf(text("block-1", "body", 100L)),
            remoteMeta = note(updatedAt = 200L, trashedAt = 200L),
            remoteContent = contentOf(text("block-1", "body", 100L)),
            remoteUpdatedAt = 200L
        )

        assertEquals(200L, merged.metadata.trashedAt)
    }

    @Test
    fun anOlderTrashFromTheOtherDeviceDoesNotTrashANoteEditedHereLater() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 300L),
            localContent = contentOf(text("block-1", "edited here", 300L)),
            remoteMeta = note(updatedAt = 200L, trashedAt = 200L),
            remoteContent = contentOf(text("block-1", "old version", 100L)),
            remoteUpdatedAt = 200L
        )

        assertNull(merged.metadata.trashedAt)
        assertEquals("edited here", textOf(merged.content, "block-1"))
    }

    @Test
    fun aNoteRestoredFromTrashOnTheOtherDeviceComesBackHereWithAllItsBlocks() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 200L, trashedAt = 200L),
            localContent = contentOf(text("block-1", "shared", 100L), text("block-2", "only here", 150L)),
            remoteMeta = note(updatedAt = 300L),
            remoteContent = contentOf(text("block-1", "shared", 100L)),
            remoteUpdatedAt = 300L
        )

        assertNull(merged.metadata.trashedAt)
        assertEquals(listOf("block-1", "block-2"), merged.content.blocks.map { it.id })
    }
}
