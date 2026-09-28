package com.emberr.domain.database

import com.emberr.domain.model.DEFAULT_VIEW_ID
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCalculation
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.NOTES_COLUMN_KEY
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.labelOf
import com.emberr.domain.model.valueTypeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseColumnsTest {

    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val dueDateColumn = DatabaseColumnTarget.Property(PropertyType.DUE_DATE)
    private val clientColumn = DatabaseColumnTarget.CustomProperty("client-id")
    private val client = DatabaseCustomProperty(id = "client-id", name = "Client", valueType = PropertyValueType.SINGLE_CHOICE)
    private val budget = DatabaseCustomProperty(id = "budget-id", name = "Budget", valueType = PropertyValueType.TEXT)

    private val database = DatabaseBlock(
        id = "block-1",
        databaseId = "database-1",
        columns = listOf(statusColumn, dueDateColumn, clientColumn),
        customProperties = listOf(client, budget),
        columnWidths = mapOf(PropertyType.STATUS.name to 200, NOTES_COLUMN_KEY to 300, "client-id" to 180),
        filters = listOf(
            DatabaseFilter(id = "status-filter", target = statusColumn, condition = DatabaseFilterCondition.IS, tagName = "Done"),
            DatabaseFilter(id = "client-filter", target = clientColumn, condition = DatabaseFilterCondition.IS, tagName = "Acme"),
            DatabaseFilter(id = "title-filter", target = DatabaseColumnTarget.NotesTitle, condition = DatabaseFilterCondition.CONTAINS, text = "dune")
        ),
        sort = DatabaseSort(target = statusColumn)
    )

    @Test
    fun theColumnPickerOffersBuiltInsNotAddedYetAndThisDatabasesHiddenProperties() {
        assertEquals(PropertyType.entries - listOf(PropertyType.STATUS, PropertyType.DUE_DATE), database.builtInPropertiesNotYetAdded())
        assertEquals(listOf(budget), database.customPropertiesNotShown())
    }

    @Test
    fun addingAColumnPutsItAtTheEndOnlyOnce() {
        assertEquals(
            listOf(statusColumn, dueDateColumn, clientColumn, DatabaseColumnTarget.CustomProperty("budget-id")),
            database.withColumnAdded(DatabaseColumnTarget.CustomProperty("budget-id")).columns
        )
        assertSame(database, database.withColumnAdded(statusColumn))
        assertSame(database, database.withColumnAdded(DatabaseColumnTarget.NotesTitle))
        assertSame(database, database.withColumnAdded(DatabaseColumnTarget.CustomProperty("gone-id")))
    }

    @Test
    fun addingAColumnBeforeAnotherPutsItRightThere() {
        val budgetColumn = DatabaseColumnTarget.CustomProperty("budget-id")

        assertEquals(
            listOf(statusColumn, budgetColumn, dueDateColumn, clientColumn),
            database.withColumnAdded(budgetColumn, beforeColumn = dueDateColumn).columns
        )
        assertEquals(
            listOf(budgetColumn, statusColumn, dueDateColumn, clientColumn),
            database.withColumnAdded(budgetColumn, beforeColumn = statusColumn).columns
        )
    }

    @Test
    fun creatingAPropertyBeforeAColumnPutsItsColumnThere() {
        val kickoff = DatabaseCustomProperty(id = "kickoff-id", name = "Kickoff", valueType = PropertyValueType.DATE)

        val result = database.withDatabasePropertyCreated(kickoff, beforeColumn = clientColumn)

        assertEquals(listOf(statusColumn, dueDateColumn, DatabaseColumnTarget.CustomProperty("kickoff-id"), clientColumn), result.columns)
    }

    @Test
    fun movingAColumnBeforeAnotherSwapsNeighbours() {
        assertEquals(listOf(dueDateColumn, statusColumn, clientColumn), database.withColumnMovedBefore(dueDateColumn, statusColumn).columns)
        assertEquals(listOf(statusColumn, clientColumn, dueDateColumn), database.withColumnMovedBefore(clientColumn, dueDateColumn).columns)
        assertEquals(listOf(dueDateColumn, clientColumn, statusColumn), database.withColumnMovedBefore(statusColumn, beforeColumn = null).columns)
    }

    @Test
    fun theNotesColumnStartsFirstAndCanBeMovedAnywhere() {
        val notes = DatabaseColumnTarget.NotesTitle
        val notesBeforeClient = database.withColumnMovedBefore(notes, clientColumn)

        assertEquals(listOf(notes, statusColumn, dueDateColumn, clientColumn), database.columnsInTableOrder())
        assertEquals(listOf(statusColumn, dueDateColumn, notes, clientColumn), notesBeforeClient.columnsInTableOrder())
        assertEquals(listOf(statusColumn, dueDateColumn, clientColumn), notesBeforeClient.columns)
        assertEquals(listOf(statusColumn, dueDateColumn, clientColumn, notes), database.withColumnMovedBefore(notes, beforeColumn = null).columnsInTableOrder())
        assertEquals(database, notesBeforeClient.withColumnMovedBefore(notes, statusColumn))
    }

    @Test
    fun columnsCanBeMovedAndAddedAroundTheNotesColumn() {
        val notes = DatabaseColumnTarget.NotesTitle
        val tagsColumn = DatabaseColumnTarget.Property(PropertyType.TAGS)
        val notesInTheMiddle = database.withColumnMovedBefore(notes, dueDateColumn)

        assertEquals(
            listOf(clientColumn, statusColumn, notes, dueDateColumn),
            notesInTheMiddle.withColumnMovedBefore(clientColumn, statusColumn).columnsInTableOrder()
        )
        assertEquals(
            listOf(statusColumn, clientColumn, notes, dueDateColumn),
            notesInTheMiddle.withColumnMovedBefore(clientColumn, notes).columnsInTableOrder()
        )
        assertEquals(
            listOf(statusColumn, tagsColumn, notes, dueDateColumn, clientColumn),
            notesInTheMiddle.withColumnAdded(tagsColumn, beforeColumn = notes).columnsInTableOrder()
        )
    }

    @Test
    fun removingTheColumnInFrontOfNotesKeepsNotesWhereItWas() {
        val notesAfterDueDate = database.withColumnMovedBefore(DatabaseColumnTarget.NotesTitle, clientColumn)

        assertEquals(
            listOf(statusColumn, DatabaseColumnTarget.NotesTitle, clientColumn),
            notesAfterDueDate.withColumnRemoved(dueDateColumn).columnsInTableOrder()
        )
    }

    @Test
    fun hidingAColumnDoesNotMoveTheNotesColumn() {
        val notesAfterDueDate = database.withColumnMovedBefore(DatabaseColumnTarget.NotesTitle, clientColumn)

        val hidden = notesAfterDueDate.withColumnShown(DEFAULT_VIEW_ID, dueDateColumn, isShown = false)

        assertEquals(listOf(statusColumn, DatabaseColumnTarget.NotesTitle, clientColumn), hidden.visibleColumnsInTableOrder())
    }

    @Test
    fun removingAColumnAlsoDropsItsWidthFiltersAndSort() {
        val result = database.withColumnRemoved(statusColumn)

        assertEquals(listOf(dueDateColumn, clientColumn), result.columns)
        assertEquals(mapOf(NOTES_COLUMN_KEY to 300, "client-id" to 180), result.columnWidths)
        assertEquals(listOf("client-filter", "title-filter"), result.filters.map { it.id })
        assertEquals(null, result.sort)
    }

    @Test
    fun removingAColumnAlsoDropsItsCalculation() {
        val calculated = database.copy(
            calculations = mapOf(PropertyType.STATUS.name to DatabaseCalculation.COUNT_EMPTY, NOTES_COLUMN_KEY to DatabaseCalculation.COUNT_ALL)
        )

        val result = calculated.withColumnRemoved(statusColumn)

        assertEquals(mapOf(NOTES_COLUMN_KEY to DatabaseCalculation.COUNT_ALL), result.calculations)
    }

    @Test
    fun aColumnHiddenInOneViewStillShowsInTheOtherViews() {
        val gallery = DatabaseView(id = "gallery", name = "Gallery", type = DatabaseViewType.GALLERY)
        val hiddenInGallery = database.withViewAdded(gallery).withColumnShown("gallery", dueDateColumn, isShown = false)

        assertEquals(listOf(statusColumn, clientColumn), hiddenInGallery.visibleColumns())
        assertEquals(listOf(statusColumn, dueDateColumn, clientColumn), hiddenInGallery.copy(activeViewId = DEFAULT_VIEW_ID).visibleColumns())
        assertEquals(listOf(statusColumn, dueDateColumn, clientColumn), hiddenInGallery.columns)
    }

    @Test
    fun showingAHiddenColumnAgainPutsItBackInItsPlace() {
        val hidden = database.withColumnShown(DEFAULT_VIEW_ID, dueDateColumn, isShown = false)

        val shownAgain = hidden.withColumnShown(DEFAULT_VIEW_ID, dueDateColumn, isShown = true)

        assertEquals(listOf(statusColumn, dueDateColumn, clientColumn), shownAgain.visibleColumns())
    }

    @Test
    fun hidingAColumnTwiceMarksItOnce() {
        val hidden = database
            .withColumnShown(DEFAULT_VIEW_ID, dueDateColumn, isShown = false)
            .withColumnShown(DEFAULT_VIEW_ID, dueDateColumn, isShown = false)

        assertEquals(listOf(PropertyType.DUE_DATE.name), hidden.activeView().hiddenColumnKeys)
    }

    @Test
    fun removingAHiddenColumnForgetsThatItWasHiddenInEveryView() {
        val gallery = DatabaseView(id = "gallery", name = "Gallery", type = DatabaseViewType.GALLERY)
        val hiddenEverywhere = database.withViewAdded(gallery)
            .withColumnShown(DEFAULT_VIEW_ID, statusColumn, isShown = false)
            .withColumnShown("gallery", statusColumn, isShown = false)

        val result = hiddenEverywhere.withColumnRemoved(statusColumn)

        assertEquals(listOf(emptyList<String>(), emptyList()), result.views.map { it.hiddenColumnKeys })
    }

    @Test
    fun removingACustomColumnKeepsItsPropertyForLater() {
        val result = database.withColumnRemoved(clientColumn)

        assertEquals(listOf(statusColumn, dueDateColumn), result.columns)
        assertEquals(listOf(client, budget), result.customProperties)
        assertEquals(listOf(client, budget), result.customPropertiesNotShown())
    }

    @Test
    fun creatingAPropertyAddsItAndShowsItAsTheLastColumn() {
        val kickoff = DatabaseCustomProperty(id = "kickoff-id", name = "Kickoff", valueType = PropertyValueType.DATE)

        val result = database.withDatabasePropertyCreated(kickoff)

        assertEquals(listOf(client, budget, kickoff), result.customProperties)
        assertEquals(DatabaseColumnTarget.CustomProperty("kickoff-id"), result.columns.last())
        assertEquals("Kickoff", result.labelOf(DatabaseColumnTarget.CustomProperty("kickoff-id")))
        assertEquals(PropertyValueType.DATE, result.valueTypeOf(DatabaseColumnTarget.CustomProperty("kickoff-id")))
    }

    @Test
    fun renamingAPropertyChangesOnlyItsName() {
        val result = database.withDatabasePropertyRenamed("client-id", "Customer")

        assertEquals("Customer", result.labelOf(clientColumn))
        assertEquals(PropertyValueType.SINGLE_CHOICE, result.valueTypeOf(clientColumn))
        assertEquals(database.columns, result.columns)
    }

    @Test
    fun deletingAPropertyRemovesItsDefinitionColumnWidthAndFilters() {
        val result = database.withDatabasePropertyDeleted("client-id")

        assertEquals(listOf(budget), result.customProperties)
        assertEquals(listOf(statusColumn, dueDateColumn), result.columns)
        assertFalse("client-id" in result.columnWidths)
        assertEquals(listOf("status-filter", "title-filter"), result.filters.map { it.id })
    }

    @Test
    fun aNameIsTakenByABuiltInOrAnotherPropertyInThisDatabaseIgnoringCase() {
        assertTrue(database.isPropertyNameTaken(" status ", ignoringPropertyId = null))
        assertTrue(database.isPropertyNameTaken("CLIENT", ignoringPropertyId = null))
        assertFalse(database.isPropertyNameTaken("Client", ignoringPropertyId = "client-id"))
        assertFalse(database.isPropertyNameTaken("Kickoff", ignoringPropertyId = null))
    }
}
