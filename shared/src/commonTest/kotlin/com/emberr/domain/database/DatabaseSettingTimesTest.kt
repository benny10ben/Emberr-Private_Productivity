package com.emberr.domain.database

import com.emberr.domain.model.DEFAULT_VIEW_ID
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCalculation
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseSettingTime
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
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
    fun aDefaultTemplateChosenOnOneDeviceSurvivesAnOtherEditOnTheOtherDevice() {
        val phone = syncedTable.editedAt(300L) { it.copy(defaultTemplateId = "task-template") }
        val laptop = syncedTable.editedAt(400L) { it.copy(title = "Week") }

        val merged = mergeDatabaseBlocks(laptop, phone)

        assertEquals("task-template", merged.defaultTemplateId)
        assertEquals("Week", merged.title)
    }

    @Test
    fun aClearedDefaultTemplateStaysClearedWhenTheOtherDeviceStillHasTheOldOne() {
        val tableWithDefault = syncedTable.copy(defaultTemplateId = "task-template")
        val phone = tableWithDefault.editedAt(300L) { it.copy(defaultTemplateId = null) }
        val laptop = tableWithDefault.editedAt(400L) { it.withColumnAdded(tagsColumn) }

        val merged = mergeDatabaseBlocks(laptop, phone)

        assertNull(merged.defaultTemplateId)
        assertEquals(listOf(statusColumn, tagsColumn), merged.columns)
    }

    @Test
    fun calculationsChosenOnTwoDevicesForDifferentColumnsAreBothKept() {
        val phone = syncedTable.editedAt(300L) { it.copy(calculations = mapOf("STATUS" to DatabaseCalculation.COUNT_EMPTY)) }
        val laptop = syncedTable.editedAt(400L) { it.copy(calculations = mapOf("notes" to DatabaseCalculation.COUNT_ALL)) }

        val merged = mergeDatabaseBlocks(laptop, phone)

        assertEquals(mapOf("notes" to DatabaseCalculation.COUNT_ALL, "STATUS" to DatabaseCalculation.COUNT_EMPTY), merged.calculations)
    }

    @Test
    fun aColumnHiddenInAViewOnOneDeviceStaysHiddenAfterAnOtherEditOnTheOtherDevice() {
        val phone = syncedTable.editedAt(300L) { it.withColumnShown(DEFAULT_VIEW_ID, statusColumn, isShown = false) }
        val laptop = syncedTable.editedAt(400L) { it.copy(title = "Week") }

        val merged = mergeDatabaseBlocks(laptop, phone)

        assertEquals(listOf("STATUS"), merged.activeView().hiddenColumnKeys)
        assertEquals("Week", merged.title)
    }

    @Test
    fun turningTheRowCountOffWinsOverTheOlderDeviceThatStillHasItOn() {
        val countedTable = syncedTable.copy(showsRowCount = true)
        val phone = countedTable.editedAt(300L) { it.copy(showsRowCount = false) }
        val laptop = countedTable.editedAt(200L) { it.copy(title = "Week") }

        assertEquals(false, mergeDatabaseBlocks(laptop, phone).showsRowCount)
        assertEquals(true, mergeDatabaseBlocks(laptop, countedTable.editedAt(100L) { it }).showsRowCount)
    }

    @Test
    fun viewsAddedOnTwoDevicesAreBothKept() {
        val phoneGallery = DatabaseView(id = "phone-gallery", name = "Gallery", type = DatabaseViewType.GALLERY)
        val laptopTable = DatabaseView(id = "laptop-table", name = "Table 2", type = DatabaseViewType.TABLE)
        val phone = syncedTable.editedAt(300L) { it.withViewAdded(phoneGallery) }
        val laptop = syncedTable.editedAt(400L) { it.withViewAdded(laptopTable) }

        val merged = mergeDatabaseBlocks(laptop, phone)

        assertEquals(setOf(DEFAULT_VIEW_ID, "laptop-table", "phone-gallery"), merged.allViews().map { it.id }.toSet())
        assertEquals("laptop-table", merged.activeViewId)
    }

    @Test
    fun aViewDeletedOnOneDeviceStaysDeleted() {
        val gallery = DatabaseView(id = "gallery", name = "Gallery", type = DatabaseViewType.GALLERY)
        val withGallery = syncedTable.withViewAdded(gallery)
        val phone = withGallery.editedAt(300L) { it.withViewDeleted("gallery") }
        val laptop = withGallery.editedAt(200L) { it.copy(title = "Week") }

        val merged = mergeDatabaseBlocks(laptop, phone)

        assertEquals(listOf(DEFAULT_VIEW_ID), merged.allViews().map { it.id })
        assertEquals("Week", merged.title)
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
