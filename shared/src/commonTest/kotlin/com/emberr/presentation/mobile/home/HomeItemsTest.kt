package com.emberr.presentation.mobile.home

import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeItemsTest {

    private fun folder(
        folderId: String,
        name: String,
        createdAt: Long = 1_000L,
        updatedAt: Long = 0L,
        sortOrder: Int = 0
    ) = HomeItem.Folder(
        FolderEntity(
            folderId = folderId,
            name = name,
            parentFolderId = null,
            createdAt = createdAt,
            sortOrder = sortOrder,
            updatedAt = updatedAt
        )
    )

    private fun note(
        noteId: String,
        title: String,
        createdAt: Long = 1_000L,
        updatedAt: Long = 1_000L,
        sortOrder: Int = 0
    ) = HomeItem.Note(
        NoteMetadataEntity(
            noteId = noteId,
            title = title,
            folderId = null,
            isDaily = false,
            dateString = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
            filePath = "",
            sortOrder = sortOrder
        )
    )

    private fun sortedKeys(
        items: List<HomeItem>,
        sortType: SortType,
        sortOrder: SortOrder = SortOrder.ASCENDING
    ) = items.sortedWith(homeItemComparator(sortType, sortOrder)).map { it.key }

    @Test
    fun folderAndNoteKeysCanNeverBeConfusedForEachOther() {
        val folderKey = HomeItemKey.forFolder("id-1")
        val noteKey = HomeItemKey.forNote("id-1")

        assertTrue(HomeItemKey.isFolder(folderKey))
        assertFalse(HomeItemKey.isNote(folderKey))
        assertTrue(HomeItemKey.isNote(noteKey))
        assertFalse(HomeItemKey.isFolder(noteKey))
    }

    @Test
    fun theOriginalIdCanAlwaysBeReadBackOutOfAKey() {
        assertEquals("folder-1", HomeItemKey.folderIdOf(HomeItemKey.forFolder("folder-1")))
        assertEquals("note-1", HomeItemKey.noteIdOf(HomeItemKey.forNote("note-1")))
    }

    @Test
    fun anItemsKeyIsBuiltFromItsOwnId() {
        assertEquals(HomeItemKey.forFolder("folder-1"), folder("folder-1", "Work").key)
        assertEquals(HomeItemKey.forNote("note-1"), note("note-1", "Ideas").key)
    }

    @Test
    fun sortingByNameIgnoresCapitalisation() {
        val items = listOf(
            note("note-1", "banana"),
            note("note-2", "Apple"),
            note("note-3", "cherry")
        )

        assertEquals(
            listOf("note-2", "note-1", "note-3"),
            sortedKeys(items, SortType.NAME).map { HomeItemKey.noteIdOf(it) }
        )
    }

    @Test
    fun sortingByNameCanRunBackwards() {
        val items = listOf(note("note-1", "banana"), note("note-2", "Apple"))

        assertEquals(
            listOf("note-1", "note-2"),
            sortedKeys(items, SortType.NAME, SortOrder.DESCENDING).map { HomeItemKey.noteIdOf(it) }
        )
    }

    @Test
    fun aNoteWithNoTitleSortsAsUntitled() {
        val items = listOf(
            note("note-1", "zebra"),
            note("note-2", ""),
            note("note-3", "apple")
        )

        assertEquals(
            listOf("note-3", "note-2", "note-1"),
            sortedKeys(items, SortType.NAME).map { HomeItemKey.noteIdOf(it) }
        )
    }

    @Test
    fun sortingByTypeAlwaysPutsFoldersAboveNotes() {
        val items = listOf(
            note("note-1", "aaa"),
            folder("folder-1", "zzz"),
            note("note-2", "bbb"),
            folder("folder-2", "yyy")
        )

        val sorted = sortedKeys(items, SortType.TYPE)

        assertTrue(HomeItemKey.isFolder(sorted[0]))
        assertTrue(HomeItemKey.isFolder(sorted[1]))
        assertTrue(HomeItemKey.isNote(sorted[2]))
        assertTrue(HomeItemKey.isNote(sorted[3]))
    }

    @Test
    fun sortingByTypeStillOrdersEachGroupByName() {
        val items = listOf(
            folder("folder-1", "zzz"),
            folder("folder-2", "aaa"),
            note("note-1", "yyy"),
            note("note-2", "bbb")
        )

        assertEquals(
            listOf(
                HomeItemKey.forFolder("folder-2"),
                HomeItemKey.forFolder("folder-1"),
                HomeItemKey.forNote("note-2"),
                HomeItemKey.forNote("note-1")
            ),
            sortedKeys(items, SortType.TYPE)
        )
    }

    @Test
    fun sortingByTypeKeepsFoldersFirstEvenWhenTheNamesRunBackwards() {
        val items = listOf(note("note-1", "aaa"), folder("folder-1", "zzz"))

        val sorted = sortedKeys(items, SortType.TYPE, SortOrder.DESCENDING)

        assertTrue(HomeItemKey.isFolder(sorted[0]))
    }

    @Test
    fun sortingByCreationDateRunsOldestFirstByDefault() {
        val items = listOf(
            note("note-1", "a", createdAt = 300L),
            note("note-2", "b", createdAt = 100L),
            note("note-3", "c", createdAt = 200L)
        )

        assertEquals(
            listOf("note-2", "note-3", "note-1"),
            sortedKeys(items, SortType.DATE_CREATED).map { HomeItemKey.noteIdOf(it) }
        )
    }

    @Test
    fun itemsCreatedAtTheSameMomentFallBackToNameOrder() {
        val items = listOf(
            note("note-1", "zebra", createdAt = 100L),
            note("note-2", "apple", createdAt = 100L)
        )

        assertEquals(
            listOf("note-2", "note-1"),
            sortedKeys(items, SortType.DATE_CREATED, SortOrder.DESCENDING).map { HomeItemKey.noteIdOf(it) }
        )
    }

    @Test
    fun sortingByLastEditedUsesTheNotesOwnUpdateTime() {
        val items = listOf(
            note("note-1", "a", updatedAt = 100L),
            note("note-2", "b", updatedAt = 300L),
            note("note-3", "c", updatedAt = 200L)
        )

        assertEquals(
            listOf("note-2", "note-3", "note-1"),
            sortedKeys(items, SortType.LAST_EDITED, SortOrder.DESCENDING).map { HomeItemKey.noteIdOf(it) }
        )
    }

    @Test
    fun aFolderThatWasNeverEditedFallsBackToWhenItWasCreated() {
        assertEquals(500L, folder("folder-1", "Work", createdAt = 500L, updatedAt = 0L).folder.lastEditedAt)
        assertEquals(900L, folder("folder-2", "Work", createdAt = 500L, updatedAt = 900L).folder.lastEditedAt)
    }

    @Test
    fun manualOrderPutsUnorderedItemsAtTheBottom() {
        val items = listOf(
            note("note-unordered", "a", sortOrder = 0),
            note("note-second", "b", sortOrder = 2),
            note("note-first", "c", sortOrder = 1)
        )

        assertEquals(
            listOf("note-first", "note-second", "note-unordered"),
            sortedKeys(items, SortType.MANUAL).map { HomeItemKey.noteIdOf(it) }
        )
    }

    @Test
    fun unorderedItemsShowTheNewestFirstAmongThemselves() {
        val items = listOf(
            note("note-old", "a", createdAt = 100L, sortOrder = 0),
            note("note-new", "b", createdAt = 300L, sortOrder = 0),
            note("note-middle", "c", createdAt = 200L, sortOrder = 0)
        )

        assertEquals(
            listOf("note-new", "note-middle", "note-old"),
            sortedKeys(items, SortType.MANUAL).map { HomeItemKey.noteIdOf(it) }
        )
    }

    @Test
    fun manualOrderIgnoresTheAscendingOrDescendingToggle() {
        val items = listOf(
            note("note-second", "a", sortOrder = 2),
            note("note-first", "b", sortOrder = 1)
        )

        assertEquals(
            sortedKeys(items, SortType.MANUAL, SortOrder.ASCENDING),
            sortedKeys(items, SortType.MANUAL, SortOrder.DESCENDING)
        )
    }

    @Test
    fun draggingAnItemDownMovesItToTheTargetPosition() {
        val items = listOf(note("note-1", "a"), note("note-2", "b"), note("note-3", "c"))

        val reordered = items.movedTo(
            draggedKey = HomeItemKey.forNote("note-1"),
            targetKey = HomeItemKey.forNote("note-3")
        )

        assertEquals(listOf("note-2", "note-3", "note-1"), reordered.map { HomeItemKey.noteIdOf(it.key) })
    }

    @Test
    fun draggingAnItemUpMovesItToTheTargetPosition() {
        val items = listOf(note("note-1", "a"), note("note-2", "b"), note("note-3", "c"))

        val reordered = items.movedTo(
            draggedKey = HomeItemKey.forNote("note-3"),
            targetKey = HomeItemKey.forNote("note-1")
        )

        assertEquals(listOf("note-3", "note-1", "note-2"), reordered.map { HomeItemKey.noteIdOf(it.key) })
    }

    @Test
    fun droppingAnItemOnItselfChangesNothing() {
        val items = listOf(note("note-1", "a"), note("note-2", "b"))

        assertEquals(
            items,
            items.movedTo(HomeItemKey.forNote("note-1"), HomeItemKey.forNote("note-1"))
        )
    }

    @Test
    fun draggingSomethingThatIsNotInTheListChangesNothing() {
        val items = listOf(note("note-1", "a"), note("note-2", "b"))

        assertEquals(items, items.movedTo(HomeItemKey.forNote("missing"), HomeItemKey.forNote("note-1")))
        assertEquals(items, items.movedTo(HomeItemKey.forNote("note-1"), HomeItemKey.forNote("missing")))
    }

    @Test
    fun buildingTheHomeListCombinesFoldersAndNotesAndSortsThemTogether() {
        val folders = listOf(
            FolderEntity(folderId = "folder-1", name = "zzz", parentFolderId = null, createdAt = 100L)
        )
        val notes = listOf(
            NoteMetadataEntity(
                noteId = "note-1",
                title = "aaa",
                folderId = null,
                isDaily = false,
                dateString = null,
                createdAt = 200L,
                updatedAt = 200L,
                filePath = ""
            )
        )

        val items = sortedHomeItems(folders, notes, SortType.NAME, SortOrder.ASCENDING)

        assertEquals(
            listOf(HomeItemKey.forNote("note-1"), HomeItemKey.forFolder("folder-1")),
            items.map { it.key }
        )
    }

    @Test
    fun everyItemInASubTreeCarriesTheDepthItWasBuiltAt() {
        val folders = listOf(
            FolderEntity(folderId = "folder-1", name = "Work", parentFolderId = null, createdAt = 100L)
        )
        val notes = listOf(
            NoteMetadataEntity(
                noteId = "note-1",
                title = "Ideas",
                folderId = "folder-1",
                isDaily = false,
                dateString = null,
                createdAt = 200L,
                updatedAt = 200L,
                filePath = ""
            )
        )

        val items = sortedHomeItems(folders, notes, SortType.NAME, SortOrder.ASCENDING, level = 2)

        assertTrue(items.all { it.level == 2 })
    }
}
