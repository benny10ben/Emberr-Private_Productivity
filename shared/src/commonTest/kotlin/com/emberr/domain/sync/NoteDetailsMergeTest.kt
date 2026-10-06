package com.emberr.domain.sync

import com.emberr.data.local.room.entity.NoteMetadataEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class NoteDetailsMergeTest {

    private fun note(
        title: String = "Groceries",
        titleUpdatedAt: Long = 0L,
        folderId: String? = "home",
        folderUpdatedAt: Long = 0L,
        isFavorite: Boolean = false,
        favoriteUpdatedAt: Long = 0L,
        icon: String? = null,
        iconUpdatedAt: Long = 0L,
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
        updatedAt = 1L,
        filePath = "",
        isFavorite = isFavorite,
        icon = icon,
        titleUpdatedAt = titleUpdatedAt,
        folderUpdatedAt = folderUpdatedAt,
        iconUpdatedAt = iconUpdatedAt,
        favoriteUpdatedAt = favoriteUpdatedAt,
        coverImagePath = coverImagePath,
        coverImageUpdatedAt = coverImageUpdatedAt,
        showWordCount = showWordCount,
        wordCountUpdatedAt = wordCountUpdatedAt
    )

    @Test
    fun theNewerTitleAndTheNewerFolderAreTakenSeparately() {
        val renamed = note(title = "Grocery list", titleUpdatedAt = 100L)
        val moved = note(folderId = "errands", folderUpdatedAt = 105L)

        val merged = moved.withNewerDetailsFrom(renamed)

        assertEquals("Grocery list", merged.title)
        assertEquals(100L, merged.titleUpdatedAt)
        assertEquals("errands", merged.folderId)
        assertEquals(105L, merged.folderUpdatedAt)
    }

    @Test
    fun anEditorHoldingAnOlderTitleCannotUndoANewerRename() {
        val savedAfterRename = note(title = "Grocery list", titleUpdatedAt = 100L)
        val staleEditorCopy = note(title = "Groceries", titleUpdatedAt = 0L)

        val written = staleEditorCopy.withNewerDetailsFrom(savedAfterRename)

        assertEquals("Grocery list", written.title)
    }

    @Test
    fun onATieTheCopyBeingSavedWins() {
        val saved = note(title = "Groceries", titleUpdatedAt = 50L)
        val beingSaved = note(title = "Shopping", titleUpdatedAt = 50L)

        assertEquals("Shopping", beingSaved.withNewerDetailsFrom(saved).title)
    }

    @Test
    fun theNewerIconAndTheNewerFavoriteAreTakenSeparately() {
        val iconChanged = note(icon = "🛒", iconUpdatedAt = 100L)
        val unfavorited = note(isFavorite = false, favoriteUpdatedAt = 105L)
        val favoritedEarlier = iconChanged.copy(isFavorite = true, favoriteUpdatedAt = 50L)

        val merged = unfavorited.withNewerDetailsFrom(favoritedEarlier)

        assertEquals("🛒", merged.icon)
        assertEquals(false, merged.isFavorite)
    }

    @Test
    fun removingAnIconIsKeptWhenItIsTheNewerChange() {
        val iconRemoved = note(icon = null, iconUpdatedAt = 200L)
        val olderIcon = note(icon = "🛒", iconUpdatedAt = 100L)

        assertEquals(null, olderIcon.withNewerDetailsFrom(iconRemoved).icon)
    }

    @Test
    fun otherDetailsComeFromTheCopyBeingSaved() {
        val other = note(title = "Grocery list", titleUpdatedAt = 100L, isFavorite = false)
        val beingSaved = note(isFavorite = true).copy(trashedAt = 300L)

        assertEquals(300L, beingSaved.withNewerDetailsFrom(other).trashedAt)
    }

    @Test
    fun withNoOtherCopyNothingChanges() {
        val beingSaved = note(title = "Groceries")

        assertEquals(beingSaved, beingSaved.withNewerDetailsFrom(null))
    }

    @Test
    fun theNewerCoverImageAndTheNewerWordCountSettingAreTakenSeparately() {
        val coverChanged = note(coverImagePath = "beach.jpg", coverImageUpdatedAt = 100L)
        val wordCountTurnedOn = note(showWordCount = true, wordCountUpdatedAt = 105L)

        val merged = wordCountTurnedOn.withNewerDetailsFrom(coverChanged)

        assertEquals("beach.jpg", merged.coverImagePath)
        assertEquals(true, merged.showWordCount)
    }
}
