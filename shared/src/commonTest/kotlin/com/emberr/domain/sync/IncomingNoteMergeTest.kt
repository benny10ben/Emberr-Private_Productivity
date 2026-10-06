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

    private fun note(
        updatedAt: Long,
        trashedAt: Long? = null,
        title: String = "Groceries",
        titleUpdatedAt: Long = 0L,
        folderId: String? = "home",
        folderUpdatedAt: Long = 0L,
        icon: String? = null,
        iconUpdatedAt: Long = 0L,
        isFavorite: Boolean = false,
        favoriteUpdatedAt: Long = 0L,
        coverImagePath: String? = null,
        coverImageUpdatedAt: Long = 0L,
        showWordCount: Boolean = false,
        wordCountUpdatedAt: Long = 0L
    ) = NoteMetadataEntity(
        noteId = "note-1",
        title = title,
        folderId = folderId,
        isDaily = false,
        dateString = null,
        createdAt = 1L,
        updatedAt = updatedAt,
        filePath = "",
        trashedAt = trashedAt,
        icon = icon,
        isFavorite = isFavorite,
        titleUpdatedAt = titleUpdatedAt,
        folderUpdatedAt = folderUpdatedAt,
        iconUpdatedAt = iconUpdatedAt,
        favoriteUpdatedAt = favoriteUpdatedAt,
        coverImagePath = coverImagePath,
        coverImageUpdatedAt = coverImageUpdatedAt,
        showWordCount = showWordCount,
        wordCountUpdatedAt = wordCountUpdatedAt
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

    @Test
    fun aRenameHereSurvivesAMoveAndTypingOnTheOtherDevice() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 100L, title = "Grocery list", titleUpdatedAt = 100L),
            localContent = contentOf(text("block-1", "milk", 1L)),
            remoteMeta = note(updatedAt = 107L, folderId = "errands", folderUpdatedAt = 105L),
            remoteContent = contentOf(text("block-1", "milk", 1L), text("block-2", "eggs", 107L)),
            remoteUpdatedAt = 107L
        )

        assertEquals("Grocery list", merged.metadata.title)
        assertEquals("errands", merged.metadata.folderId)
        assertEquals("eggs", textOf(merged.content, "block-2"))
    }

    @Test
    fun aRenameOnTheOtherDeviceSurvivesTypingHere() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 200L),
            localContent = contentOf(text("block-1", "typed here", 200L)),
            remoteMeta = note(updatedAt = 100L, title = "Grocery list", titleUpdatedAt = 100L),
            remoteContent = contentOf(text("block-1", "milk", 1L)),
            remoteUpdatedAt = 100L
        )

        assertEquals("Grocery list", merged.metadata.title)
        assertEquals("typed here", textOf(merged.content, "block-1"))
        assertTrue(merged.hasChanges)
    }

    @Test
    fun whenBothDevicesRenameTheNoteTheLaterRenameWins() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 300L, title = "Renamed here first", titleUpdatedAt = 100L),
            localContent = contentOf(text("block-1", "milk", 1L)),
            remoteMeta = note(updatedAt = 200L, title = "Renamed there later", titleUpdatedAt = 150L),
            remoteContent = contentOf(text("block-1", "milk", 1L)),
            remoteUpdatedAt = 200L
        )

        assertEquals("Renamed there later", merged.metadata.title)
    }

    @Test
    fun anIconAndFavoriteChangedHereSurviveTypingOnTheOtherDevice() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 100L, icon = "🛒", iconUpdatedAt = 100L, isFavorite = true, favoriteUpdatedAt = 100L),
            localContent = contentOf(text("block-1", "milk", 1L)),
            remoteMeta = note(updatedAt = 200L),
            remoteContent = contentOf(text("block-1", "milk and eggs", 200L)),
            remoteUpdatedAt = 200L
        )

        assertEquals("🛒", merged.metadata.icon)
        assertTrue(merged.metadata.isFavorite)
        assertEquals("milk and eggs", textOf(merged.content, "block-1"))
    }

    @Test
    fun unfavoritingOnTheOtherDeviceIsKeptWhenItIsTheNewerChange() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 300L, isFavorite = true, favoriteUpdatedAt = 100L),
            localContent = contentOf(text("block-1", "typed here", 300L)),
            remoteMeta = note(updatedAt = 200L, isFavorite = false, favoriteUpdatedAt = 200L),
            remoteContent = contentOf(text("block-1", "milk", 1L)),
            remoteUpdatedAt = 200L
        )

        assertFalse(merged.metadata.isFavorite)
    }

    @Test
    fun aCoverImageChangedHereSurvivesTypingOnTheOtherDevice() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 100L, coverImagePath = "beach.jpg", coverImageUpdatedAt = 100L),
            localContent = contentOf(text("block-1", "milk", 1L)),
            remoteMeta = note(updatedAt = 105L, coverImagePath = "old-cover.jpg"),
            remoteContent = contentOf(text("block-1", "milk and eggs", 105L)),
            remoteUpdatedAt = 105L
        )

        assertEquals("beach.jpg", merged.metadata.coverImagePath)
        assertEquals("milk and eggs", textOf(merged.content, "block-1"))
    }

    @Test
    fun turningOnWordCountOnTheOtherDeviceSurvivesTypingHere() {
        val merged = IncomingNoteMerge.merge(
            localMeta = note(updatedAt = 200L),
            localContent = contentOf(text("block-1", "typed here", 200L)),
            remoteMeta = note(updatedAt = 100L, showWordCount = true, wordCountUpdatedAt = 100L),
            remoteContent = contentOf(text("block-1", "milk", 1L)),
            remoteUpdatedAt = 100L
        )

        assertTrue(merged.metadata.showWordCount)
    }
}
