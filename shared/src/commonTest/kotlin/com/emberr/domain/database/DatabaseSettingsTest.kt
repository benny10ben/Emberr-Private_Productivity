package com.emberr.domain.database

import com.emberr.domain.model.DEFAULT_VIEW_ID
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseSettingsTest {

    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val dueDateColumn = DatabaseColumnTarget.Property(PropertyType.DUE_DATE)

    private val settings = DatabaseBlock(
        id = databaseSettingsBlockId("books"),
        databaseId = "books",
        title = "Reading list",
        columns = listOf(statusColumn, dueDateColumn),
        customProperties = listOf(DatabaseCustomProperty(id = "pages", name = "Pages", valueType = PropertyValueType.NUMBER)),
        formulas = mapOf("pages" to "1 + 1"),
        defaultTemplateId = "template-1",
        updatedAt = 100L
    )

    private val view = DatabaseBlock(id = "view-block", databaseId = "books", updatedAt = 100L)

    private fun DatabaseBlock.editedAt(time: Long, change: (DatabaseBlock) -> DatabaseBlock): DatabaseBlock =
        change(this).copy(updatedAt = time).withSettingTimesStamped(before = this, now = time)

    @Test
    fun onlyTheBlockInsideTheDatabaseNoteHoldsTheSharedSettings() {
        assertTrue(settings.holdsSharedSettings)
        assertFalse(view.holdsSharedSettings)
        assertEquals("database-books", databaseNoteId("books"))
    }

    @Test
    fun aViewShowsTheSharedPropertiesAndTemplatesButKeepsItsOwnViews() {
        val viewWithBoard = view.copy(views = listOf(DatabaseView(id = "board", name = "Board", type = DatabaseViewType.BOARD)))

        val shown = viewWithBoard.withSharedSettingsFrom(settings)

        assertEquals("Reading list", shown.title)
        assertEquals(settings.columns, shown.columns)
        assertEquals(settings.customProperties, shown.customProperties)
        assertEquals(settings.formulas, shown.formulas)
        assertEquals("template-1", shown.defaultTemplateId)
        assertEquals(listOf("board"), shown.views.map { it.id })
        assertEquals("view-block", shown.id)
    }

    @Test
    fun changingAFilterInAViewLeavesTheSharedSettingsAlone() {
        val result = view.changedThroughView(settings) {
            it.withViewChanged(DEFAULT_VIEW_ID) { defaultView ->
                defaultView.copy(filters = listOf(DatabaseFilter(id = "f1", target = statusColumn, condition = DatabaseFilterCondition.IS_EMPTY)))
            }
        }

        assertEquals(settings, result.settings)
        assertEquals(1, result.viewBlock.views.single().filters.size)
        assertEquals(emptyList(), result.viewBlock.columns)
        assertEquals("", result.viewBlock.title)
    }

    @Test
    fun renamingOrAddingAPropertyInAViewChangesOnlyTheSharedSettings() {
        val property = DatabaseCustomProperty(id = "author", name = "Author", valueType = PropertyValueType.TEXT)

        val result = view.changedThroughView(settings) { it.copy(title = "Books").withDatabasePropertyCreated(property) }

        assertEquals("Books", result.settings.title)
        assertTrue(DatabaseColumnTarget.CustomProperty("author") in result.settings.columns)
        assertEquals(view, result.viewBlock)
    }

    @Test
    fun removingAColumnTakesItOutOfTheSharedSettingsAndOutOfThatViewsFilters() {
        val viewWithFilter = view.copy(
            views = listOf(
                DatabaseView(
                    id = DEFAULT_VIEW_ID,
                    name = "Table",
                    type = DatabaseViewType.TABLE,
                    filters = listOf(DatabaseFilter(id = "f1", target = statusColumn, condition = DatabaseFilterCondition.IS_EMPTY))
                )
            )
        )

        val result = viewWithFilter.changedThroughView(settings) { it.withColumnRemoved(statusColumn) }

        assertEquals(listOf(dueDateColumn), result.settings.columns)
        assertEquals(emptyList(), result.viewBlock.views.single().filters)
    }

    @Test
    fun aDatabaseFromBeforeTheSettingsMovedOutKeepsItsSettingsWhenTheyAreMovedOut() {
        val oldStyleBlock = settings.editedAt(200L) { it.copy(title = "Books") }.copy(id = "old-block", isPinned = true)

        val movedOut = oldStyleBlock.sharedSettingsMovedOut()

        assertTrue(oldStyleBlock.keepsSharedSettingsInside())
        assertFalse(view.keepsSharedSettingsInside())
        assertEquals(databaseSettingsBlockId("books"), movedOut.id)
        assertEquals("Books", movedOut.title)
        assertEquals(settings.columns, movedOut.columns)
        assertEquals(oldStyleBlock.settingTimes, movedOut.settingTimes)
        assertEquals(200L, movedOut.updatedAt)
        assertFalse(movedOut.isPinned)
    }

    @Test
    fun copiedSettingsBelongToTheCopiedDatabase() {
        val copy = settings.sharedSettingsCopiedTo("books-copy", now = 300L)

        assertEquals(databaseSettingsBlockId("books-copy"), copy.id)
        assertEquals("books-copy", copy.databaseId)
        assertEquals("Reading list", copy.title)
        assertEquals(settings.columns, copy.columns)
        assertEquals(300L, copy.updatedAt)
    }

    @Test
    fun undoingASharedChangePutsTheSettingsBack() {
        val renamed = settings.editedAt(200L) { it.copy(title = "Books") }
        val change = DatabaseSettingsChange(before = settings, after = renamed)

        val undone = change.settingsToWrite(HistoryDirection.UNDO, savedSettings = renamed, now = 300L)

        assertEquals("Reading list", undone?.title)
        assertEquals(300L, undone?.updatedAt)
        assertEquals(300L, undone?.settingTimes?.get("title")?.updatedAt)
    }

    @Test
    fun redoingASharedChangeAppliesItAgain() {
        val renamed = settings.editedAt(200L) { it.copy(title = "Books") }
        val change = DatabaseSettingsChange(before = settings, after = renamed)

        assertEquals("Books", change.settingsToWrite(HistoryDirection.REDO, savedSettings = settings, now = 300L)?.title)
    }

    @Test
    fun undoIsSkippedWhenTheSharedSettingsWereChangedSinceOrDeleted() {
        val renamed = settings.editedAt(200L) { it.copy(title = "Books") }
        val change = DatabaseSettingsChange(before = settings, after = renamed)

        assertNull(change.settingsToWrite(HistoryDirection.UNDO, savedSettings = renamed.copy(title = "Films"), now = 300L))
        assertNull(change.settingsToWrite(HistoryDirection.UNDO, savedSettings = renamed.copy(isDeleted = true), now = 300L))
    }

    @Test
    fun onlyATitleChangeCountsAsTitleTyping() {
        val renamed = settings.editedAt(200L) { it.copy(title = "Books") }
        val locked = settings.editedAt(200L) { it.copy(title = "Books", isLocked = true) }

        assertTrue(DatabaseSettingsChange(before = settings, after = renamed).changesOnlyTheTitle)
        assertFalse(DatabaseSettingsChange(before = settings, after = locked).changesOnlyTheTitle)
    }

    @Test
    fun savingEditsKeepsWhatAnotherDeviceChangedMeanwhile() {
        val editedHere = settings.editedAt(200L) { it.copy(title = "Books") }
        val savedFromOtherDevice = settings.editedAt(300L) { it.copy(isLocked = true) }

        val written = savedFromOtherDevice.withEditsFrom(editedHere, now = 400L)

        assertEquals("Books", written?.title)
        assertEquals(true, written?.isLocked)
        assertEquals(400L, written?.updatedAt)
    }

    @Test
    fun savingEditsThatAreAlreadySavedChangesNothing() {
        val editedHere = settings.editedAt(200L) { it.copy(title = "Books") }

        assertSame(editedHere, editedHere.withEditsFrom(editedHere, now = 400L))
    }

    @Test
    fun deletedSettingsAreNeverWrittenBackToLife() {
        val editedHere = settings.editedAt(200L) { it.copy(title = "Books") }

        assertNull(settings.copy(isDeleted = true, updatedAt = 300L).withEditsFrom(editedHere, now = 400L))
    }

    @Test
    fun theDatabasesShownInANoteAreItsLiveViews() {
        val deletedView = view.copy(id = "deleted-view", databaseId = "films", isDeleted = true)
        val blocks = listOf(TextBlock(id = "text"), view, deletedView, settings)

        assertEquals(setOf("books"), blocks.idsOfShownDatabases())
    }

    @Test
    fun theAiIndexSeesTheSharedTitleOfEveryDatabaseInTheNote() {
        val unknownView = view.copy(id = "other-view", databaseId = "films")
        val blocks = listOf(TextBlock(id = "text", text = "Hi"), view, unknownView)

        val withTitles = blocks.withDatabaseTitles(mapOf("books" to "Reading list"))

        assertEquals(listOf("", "Reading list", ""), withTitles.map { (it as? DatabaseBlock)?.title ?: "" })
        assertEquals(blocks[0], withTitles[0])
        assertEquals(unknownView, withTitles[2])
    }

    @Test
    fun unsavedEditsAreShownOnlyWhileTheyAreNewerThanWhatIsSaved() {
        val newerUnsaved = settings.copy(title = "Books", updatedAt = 200L)
        val olderUnsaved = settings.copy(title = "Old", updatedAt = 50L)

        assertEquals(newerUnsaved, newerSettings(settings, newerUnsaved))
        assertEquals(settings, newerSettings(settings, olderUnsaved))
        assertEquals(settings, newerSettings(settings, null))
        assertNull(newerSettings(null, newerUnsaved))
    }

    @Test
    fun syncingTwoCopiesOfAViewKeepsTheViewsMadeOnEachDevice() {
        val phone = view.editedAt(200L) { it.withViewAdded(DatabaseView(id = "gallery", name = "Gallery", type = DatabaseViewType.GALLERY)) }
        val laptop = view.editedAt(300L) { it.withViewChanged(DEFAULT_VIEW_ID) { defaultView -> defaultView.copy(hiddenColumnKeys = listOf("STATUS")) } }

        val merged = mergeDatabaseBlocks(phone, laptop)

        assertEquals(setOf(DEFAULT_VIEW_ID, "gallery"), merged.views.map { it.id }.toSet())
        assertEquals(listOf("STATUS"), merged.views.first { it.id == DEFAULT_VIEW_ID }.hiddenColumnKeys)
    }

    @Test
    fun typingStepsOnTheSameDatabaseAreCombinedIntoOne() {
        val first = DatabaseSettingsChange(before = settings, after = settings.copy(title = "B"))
        val second = DatabaseSettingsChange(before = settings.copy(title = "B"), after = settings.copy(title = "Bo"))

        val combined = listOf(first).combinedWith(listOf(second))

        assertEquals(listOf(DatabaseSettingsChange(before = settings, after = settings.copy(title = "Bo"))), combined)
    }
}
