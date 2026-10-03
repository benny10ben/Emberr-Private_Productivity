package com.emberr.domain.database

import com.emberr.domain.model.DEFAULT_VIEW_ID
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseFilterCondition
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseExportTest {

    private val statusColumn = DatabaseColumnTarget.Property(PropertyType.STATUS)
    private val dueDateColumn = DatabaseColumnTarget.Property(PropertyType.DUE_DATE)

    private fun row(noteId: String, title: String, status: String? = null, dueDate: LocalDate? = null) = DatabaseRow(
        noteId = noteId,
        title = title,
        createdAt = 0L,
        cellsByColumn = listOfNotNull(
            status?.let { statusColumn to PropertyBlock(id = "status-$noteId", propertyType = PropertyType.STATUS, tags = listOf(it)) },
            dueDate?.let { dueDateColumn to PropertyBlock(id = "due_date-$noteId", propertyType = PropertyType.DUE_DATE, date = it) }
        ).toMap()
    )

    private val rows = listOf(
        row("dune", title = "Dune", status = "Reading", dueDate = LocalDate(2026, 10, 1)),
        row("emma", title = "Emma", status = "Dropped"),
        row("blank", title = "", dueDate = LocalDate(2026, 9, 1))
    )

    @Test
    fun aDatabaseExportsAsItsTitleAndATableOfTheRowsItShows() {
        val database = DatabaseBlock(
            id = "d1",
            databaseId = "books",
            title = "Reading list",
            columns = listOf(statusColumn, dueDateColumn),
            views = listOf(
                DatabaseView(
                    id = DEFAULT_VIEW_ID,
                    name = "Table",
                    type = DatabaseViewType.TABLE,
                    filters = listOf(
                        DatabaseFilter(id = "f1", target = statusColumn, condition = DatabaseFilterCondition.IS_NOT, tagName = "Dropped")
                    ),
                    sorts = listOf(DatabaseSort(target = dueDateColumn))
                )
            ),
            indentationLevel = 1,
            updatedAt = 100L
        )

        assertEquals(
            listOf<NoteBlock>(
                TextBlock(id = "d1-title", text = "Reading list", isBold = true, indentationLevel = 1, updatedAt = 100L),
                TableBlock(
                    id = "d1",
                    rows = listOf(
                        listOf("Notes", "Status", "Due Date"),
                        listOf("Untitled", "", "2026-09-01"),
                        listOf("Dune", "Reading", "2026-10-01")
                    ),
                    indentationLevel = 1,
                    updatedAt = 100L
                )
            ),
            database.asExportBlocks(rows)
        )
    }

    @Test
    fun aDatabaseWithoutATitleExportsOnlyItsTable() {
        val database = DatabaseBlock(id = "d1", databaseId = "books")

        assertEquals(
            listOf<NoteBlock>(
                TableBlock(id = "d1", rows = listOf(listOf("Notes"), listOf("Untitled"), listOf("Dune"), listOf("Emma")))
            ),
            database.asExportBlocks(rows)
        )
    }
}
