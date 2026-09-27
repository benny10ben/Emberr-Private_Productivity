package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseSettingTime
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.withPropertyTagReplaced
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseSettingTimesTest {

    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val tagsColumn = DatabaseColumnTarget.Property(PropertyType.TAGS)
    private val priorityProperty = DatabaseCustomProperty(id = "priority", name = "Priority", valueType = PropertyValueType.TAGS)
    private val ownerProperty = DatabaseCustomProperty(id = "owner", name = "Owner", valueType = PropertyValueType.TEXT)
    private val doneFilter = DatabaseFilter(id = "done", target = statusColumn, condition = DatabaseFilterCondition.IS, tagName = "Done")

    private val syncedTable = DatabaseBlock(
        id = "table",
        databaseId = "days",
        title = "Days",
        columns = listOf(statusColumn),
        columnWidths = mapOf(PropertyType.STATUS.name to 120),
        updatedAt = 100L
    )

    private fun DatabaseBlock.editedAt(time: Long, change: (DatabaseBlock) -> DatabaseBlock): DatabaseBlock =
        change(this).copy(updatedAt = time).withSettingTimesStamped(before = this, now = time)

    private fun DatabaseBlock.withPropertyColumn(property: DatabaseCustomProperty): DatabaseBlock =
        withDatabasePropertyCreated(property)

    @Test
    fun addingAColumnStampsOnlyThatColumn() {
        val edited = syncedTable.editedAt(200L) { it.withColumnAdded(tagsColumn) }

        assertEquals(mapOf("column:TAGS" to DatabaseSettingTime(200L)), edited.settingTimes)
    }

    @Test
    fun removingAColumnLeavesADeletedMarkerForItAndItsWidth() {
        val edited = syncedTable.editedAt(200L) { it.withColumnRemoved(statusColumn) }

        assertEquals(
            mapOf(
                "column:STATUS" to DatabaseSettingTime(200L, isDeleted = true),
                "width:STATUS" to DatabaseSettingTime(200L, isDeleted = true)
            ),
            edited.settingTimes
        )
    }

    @Test
    fun anEditThatChangesNothingKeepsTheOldTimes() {
        val stamped = syncedTable.copy(settingTimes = mapOf("title" to DatabaseSettingTime(90L)))

        val edited = stamped.editedAt(200L) { it.copy(title = "Days") }

        assertEquals(stamped.settingTimes, edited.settingTimes)
    }

    @Test
    fun restoringAnOlderCopyKeepsTheCurrentTimesOfSettingsItDidNotChange() {
        val oldCopy = syncedTable.copy(sort = DatabaseSort(statusColumn))
        val current = syncedTable.copy(title = "Week", settingTimes = mapOf("title" to DatabaseSettingTime(300L)))

        val restored = oldCopy.copy(title = "Week").withSettingTimesStamped(before = current, now = 400L)

        assertEquals(
            mapOf("title" to DatabaseSettingTime(300L), "sort" to DatabaseSettingTime(400L)),
            restored.settingTimes
        )
    }

    @Test
    fun renamingAFilterTagStampsThatFilter() {
        val withFilter = syncedTable.copy(filters = listOf(doneFilter))

        val renamed = withFilter.withPropertyTagReplaced(PropertyType.STATUS.name, "Done", "Finished", now = 200L) as DatabaseBlock

        assertEquals("Finished", renamed.filters.single().tagName)
        assertEquals(mapOf("filter:done" to DatabaseSettingTime(200L)), renamed.settingTimes)
    }

    @Test
    fun aColumnAddedOnOneDeviceAndAFilterAddedOnTheOtherAreBothKept() {
        val phone = syncedTable.editedAt(200L) { it.withColumnAdded(tagsColumn) }
        val laptop = syncedTable.editedAt(300L) { it.copy(filters = it.filters + doneFilter) }

        val merged = mergeDatabaseBlocks(phone, laptop)

        assertEquals(listOf(statusColumn, tagsColumn), merged.columns)
        assertEquals(listOf(doneFilter), merged.filters)
    }

    @Test
    fun aDifferentPropertyAddedOnEachDeviceIsKeptFromBoth() {
        val phone = syncedTable.editedAt(200L) { it.withPropertyColumn(priorityProperty) }
        val laptop = syncedTable.editedAt(300L) { it.withPropertyColumn(ownerProperty) }

        val merged = mergeDatabaseBlocks(phone, laptop)

        assertEquals(
            listOf(statusColumn, DatabaseColumnTarget.CustomProperty("owner"), DatabaseColumnTarget.CustomProperty("priority")),
            merged.columns
        )
        assertEquals(listOf(ownerProperty, priorityProperty), merged.customProperties)
    }

    @Test
    fun aColumnRemovedOnOneDeviceStaysRemovedWhenTheOtherDeviceEditedSomethingElseLater() {
        val phone = syncedTable.editedAt(200L) { it.withColumnRemoved(statusColumn) }
        val laptop = syncedTable.editedAt(300L) { it.copy(title = "Week plan") }

        val merged = mergeDatabaseBlocks(phone, laptop)

        assertEquals(emptyList(), merged.columns)
        assertEquals(emptyMap(), merged.columnWidths)
        assertEquals("Week plan", merged.title)
    }

    @Test
    fun whenBothDevicesChangeTheSameSettingTheLaterChangeWins() {
        val phone = syncedTable.editedAt(300L) { it.copy(title = "Phone title") }
        val laptop = syncedTable.editedAt(200L) { it.copy(title = "Laptop title") }.editedAt(250L) { it.withColumnAdded(tagsColumn) }

        val merged = mergeDatabaseBlocks(phone, laptop)

        assertEquals("Phone title", merged.title)
        assertEquals(listOf(statusColumn, tagsColumn), merged.columns)
    }

    @Test
    fun aClearedSortStaysClearedWhenTheOtherDeviceStillHasTheOldOne() {
        val sortedTable = syncedTable.copy(sort = DatabaseSort(statusColumn))
        val phone = sortedTable.editedAt(300L) { it.copy(sort = null) }
        val laptop = sortedTable.editedAt(200L) { it.withColumnAdded(tagsColumn) }

        val merged = mergeDatabaseBlocks(laptop, phone)

        assertNull(merged.sort)
        assertEquals(listOf(statusColumn, tagsColumn), merged.columns)
    }

    @Test
    fun theResultIsTheSameWhicheverDeviceMergesFirst() {
        val phone = syncedTable.editedAt(200L) { it.withPropertyColumn(priorityProperty) }.editedAt(210L) { it.copy(title = "Week") }
        val laptop = syncedTable.editedAt(300L) { it.withColumnRemoved(statusColumn) }.editedAt(310L) { it.copy(filters = listOf(doneFilter)) }

        assertEquals(mergeDatabaseBlocks(phone, laptop), mergeDatabaseBlocks(laptop, phone))
    }

    @Test
    fun tablesSavedWithoutAnyTimesFallBackToTheNewerTable() {
        val older = syncedTable.copy(title = "Old title", updatedAt = 100L)
        val newer = syncedTable.copy(title = "New title", columns = emptyList(), updatedAt = 200L)

        val merged = mergeDatabaseBlocks(older, newer)

        assertEquals("New title", merged.title)
        assertEquals(listOf(statusColumn), merged.columns)
    }
}
