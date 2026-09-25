package com.emberr.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteBlockDeepCopyTest {

    @Test
    fun everyBlockTypeGetsAFreshIdAndKeepsEverythingElse() {
        TestNoteBlocks.oneOfEveryBlockType()
            .filter { it !is DatabaseBlock }
            .forEach { original ->
                val copy = original.deepCopyWithNewIds()

                assertNotEquals(original.id, copy.id, "id was reused for ${original::class.simpleName}")
                assertEquals(
                    original,
                    copy.withSameIdAs(original),
                    "copying changed more than the id for ${original::class.simpleName}"
                )
            }
    }

    @Test
    fun copyingAWholeNoteGivesEveryBlockAFreshId() {
        val original = NoteContent(blocks = TestNoteBlocks.oneOfEveryBlockType())

        val copy = original.deepCopyWithNewIds()

        val originalIds = original.blocks.map { it.id }.toSet()
        val copiedIds = copy.blocks.map { it.id }.toSet()

        assertEquals(original.blocks.size, copy.blocks.size)
        assertEquals(original.blocks.size, copiedIds.size)
        assertTrue(originalIds.intersect(copiedIds).isEmpty())
    }

    @Test
    fun copyingADatabaseRepointsItsColumnsAndRowsAtTheNewBlock() {
        val original = TestNoteBlocks.populatedDatabaseBlock()

        val copy = original.deepCopyWithNewIds() as DatabaseBlock

        assertNotEquals(original.id, copy.id)
        assertTrue(copy.columns.all { it.databaseId == copy.id })
        assertTrue(copy.rows.all { it.databaseId == copy.id })
    }

    @Test
    fun copyingADatabaseGivesEveryColumnRowAndViewAFreshId() {
        val original = TestNoteBlocks.populatedDatabaseBlock()

        val copy = original.deepCopyWithNewIds() as DatabaseBlock

        assertTrue(original.columns.map { it.id }.intersect(copy.columns.map { it.id }.toSet()).isEmpty())
        assertTrue(original.rows.map { it.id }.intersect(copy.rows.map { it.id }.toSet()).isEmpty())
        assertTrue(original.views.map { it.id }.intersect(copy.views.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun copyingADatabaseKeepsEachCellPointingAtTheSameColumnByName() {
        val original = TestNoteBlocks.populatedDatabaseBlock()

        val copy = original.deepCopyWithNewIds() as DatabaseBlock

        val originalNameColumnId = original.columns.first { it.name == "Name" }.id
        val copiedNameColumnId = copy.columns.first { it.name == "Name" }.id

        assertEquals(
            original.rows.first().cells.getValue(originalNameColumnId),
            copy.rows.first().cells.getValue(copiedNameColumnId)
        )
    }

    @Test
    fun copyingADatabaseKeepsOrphanedCellsInsteadOfDroppingThem() {
        val original = TestNoteBlocks.populatedDatabaseBlock()
        val knownColumnIds = original.columns.map { it.id }.toSet()
        val orphanedCellKeys = original.rows.first().cells.keys - knownColumnIds

        val copy = original.deepCopyWithNewIds() as DatabaseBlock

        assertTrue(orphanedCellKeys.isNotEmpty())
        assertTrue(copy.rows.first().cells.keys.containsAll(orphanedCellKeys))
        assertEquals(original.rows.first().cells.size, copy.rows.first().cells.size)
    }

    @Test
    fun copyingADatabaseRepointsTheViewsSortsFiltersAndGrouping() {
        val original = TestNoteBlocks.populatedDatabaseBlock()

        val copy = original.deepCopyWithNewIds() as DatabaseBlock

        val copiedColumnIds = copy.columns.map { it.id }.toSet()
        val copiedView = copy.views.single()

        assertTrue(copiedView.groupByColumnId in copiedColumnIds)
        assertTrue(copiedView.activeSorts.all { it.columnId in copiedColumnIds })
        assertTrue(copiedView.activeFilters.all { it.columnId in copiedColumnIds })
        assertEquals(copiedView.id, copy.activeViewId)
    }

    @Test
    fun copyingADatabaseKeepsGroupNamesUntouchedBecauseTheyAreNotColumnIds() {
        val original = TestNoteBlocks.populatedDatabaseBlock()

        val copy = original.deepCopyWithNewIds() as DatabaseBlock

        val originalView = original.views.single()
        val copiedView = copy.views.single()

        assertEquals(originalView.hiddenGroups, copiedView.hiddenGroups)
        assertEquals(originalView.groupOrder, copiedView.groupOrder)
        assertEquals(originalView.galleryCardSize, copiedView.galleryCardSize)
        assertEquals(originalView.name, copiedView.name)
        assertEquals(originalView.type, copiedView.type)
    }

    @Test
    fun copyingADatabaseDropsSortsAndFiltersThatPointAtAColumnThatIsGone() {
        val databaseId = "database-9"
        val survivingColumn = TestNoteBlocks.textColumn("column-alive", databaseId, "Alive")
        val viewWithStaleReferences = DatabaseView(
            id = "view-9",
            name = "Table",
            type = ViewType.TABLE,
            activeSorts = listOf(
                SortConfig(survivingColumn.id, isAscending = true),
                SortConfig("column-that-no-longer-exists", isAscending = false)
            ),
            activeFilters = listOf(
                FilterConfig(survivingColumn.id, "contains", "a"),
                FilterConfig("column-that-no-longer-exists", "contains", "b")
            ),
            groupByColumnId = "column-that-no-longer-exists"
        )
        val original = DatabaseBlock(
            id = databaseId,
            columns = listOf(survivingColumn),
            rows = emptyList(),
            views = listOf(viewWithStaleReferences),
            activeViewId = viewWithStaleReferences.id
        )

        val copy = original.deepCopyWithNewIds() as DatabaseBlock
        val copiedView = copy.views.single()

        assertEquals(1, copiedView.activeSorts.size)
        assertEquals(1, copiedView.activeFilters.size)
        assertEquals(copy.columns.single().id, copiedView.activeSorts.single().columnId)
        assertEquals(copy.columns.single().id, copiedView.activeFilters.single().columnId)
        assertNull(copiedView.groupByColumnId)
    }

    @Test
    fun copyingADatabaseLeavesTheOriginalCompletelyAlone() {
        val original = TestNoteBlocks.populatedDatabaseBlock()
        val snapshot = TestNoteBlocks.populatedDatabaseBlock()

        original.deepCopyWithNewIds()

        assertEquals(snapshot, original)
    }

    private fun NoteBlock.withSameIdAs(other: NoteBlock): NoteBlock = when (this) {
        is TextBlock -> copy(id = other.id)
        is HeadingBlock -> copy(id = other.id)
        is QuoteBlock -> copy(id = other.id)
        is CheckboxBlock -> copy(id = other.id)
        is BulletedListBlock -> copy(id = other.id)
        is NumberedListBlock -> copy(id = other.id)
        is ToggleBlock -> copy(id = other.id)
        is CodeBlock -> copy(id = other.id)
        is BookmarkBlock -> copy(id = other.id)
        is LinkedNoteBlock -> copy(id = other.id)
        is ImageBlock -> copy(id = other.id)
        is DocumentBlock -> copy(id = other.id)
        is DatabaseBlock -> copy(id = other.id)
        is TableBlock -> copy(id = other.id)
        is VoiceBlock -> copy(id = other.id)
        is CanvasBlock -> copy(id = other.id)
        is SolidDividerBlock -> copy(id = other.id)
        is ThreeDotDividerBlock -> copy(id = other.id)
    }
}
