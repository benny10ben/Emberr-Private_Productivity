package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseSort
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
    fun removingAColumnAlsoDropsItsWidthFiltersAndSort() {
        val result = database.withColumnRemoved(statusColumn)

        assertEquals(listOf(dueDateColumn, clientColumn), result.columns)
        assertEquals(mapOf(NOTES_COLUMN_KEY to 300, "client-id" to 180), result.columnWidths)
        assertEquals(listOf("client-filter", "title-filter"), result.filters.map { it.id })
        assertEquals(null, result.sort)
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
