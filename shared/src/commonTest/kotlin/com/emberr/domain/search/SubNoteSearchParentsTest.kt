package com.emberr.domain.search

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.NoteSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

class SubNoteSearchParentsTest {

    private fun note(
        noteId: String,
        title: String = noteId,
        isSubNote: Boolean = false,
        databaseId: String? = null,
        updatedAt: Long = 1L,
        isDaily: Boolean = false,
        dateString: String? = null
    ) = NoteMetadataEntity(
        noteId = noteId,
        title = title,
        folderId = null,
        isDaily = isDaily,
        dateString = dateString,
        createdAt = 1L,
        updatedAt = updatedAt,
        filePath = "",
        isSubNote = isSubNote,
        databaseId = databaseId
    )

    private fun result(note: NoteMetadataEntity) = NoteSearchResult(note = note, matchedText = "match")

    private val weekPlan = note("week-plan", title = "Week plan")

    @Test
    fun aNormalNoteIsLeftAsItIs() {
        val plainNote = result(note("plain"))

        assertEquals(listOf(plainNote), listOf(plainNote).withSubNoteParents(emptyMap(), emptyMap()))
    }

    @Test
    fun aRowIsLabelledWithTheNoteThatShowsItsTable() {
        val mondayRow = result(note("monday", isSubNote = true, databaseId = "days"))

        val labelled = listOf(mondayRow).withSubNoteParents(mapOf("days" to weekPlan), emptyMap())

        assertEquals(listOf(mondayRow.copy(parentTitle = "Week plan")), labelled)
    }

    @Test
    fun aRowWhoseTableIsNotShownAnywhereIsLeftOut() {
        val hiddenRow = result(note("monday", isSubNote = true, databaseId = "days"))

        assertEquals(emptyList(), listOf(hiddenRow).withSubNoteParents(emptyMap(), emptyMap()))
    }

    @Test
    fun aLinkedSubNoteIsLabelledWithTheNoteThatLinksToIt() {
        val linkedPage = result(note("linked-page", isSubNote = true))

        val labelled = listOf(linkedPage).withSubNoteParents(emptyMap(), mapOf("linked-page" to weekPlan))

        assertEquals(listOf(linkedPage.copy(parentTitle = "Week plan")), labelled)
    }

    @Test
    fun aLinkedSubNoteThatNothingLinksToStaysWithoutALabel() {
        val linkedPage = result(note("linked-page", isSubNote = true))

        assertEquals(listOf(linkedPage), listOf(linkedPage).withSubNoteParents(emptyMap(), emptyMap()))
    }

    @Test
    fun parentLabelsUseUntitledForBlankTitlesAndTheDateForDailyNotes() {
        val firstRow = result(note("row-1", isSubNote = true, databaseId = "untitled-table"))
        val secondRow = result(note("row-2", isSubNote = true, databaseId = "daily-table"))
        val untitledParent = note("untitled", title = "")
        val dailyParent = note("daily", title = "Daily: 2026-09-27", isDaily = true, dateString = "2026-09-27")

        val labelled = listOf(firstRow, secondRow).withSubNoteParents(
            parentsByDatabaseId = mapOf("untitled-table" to untitledParent, "daily-table" to dailyParent),
            parentsByLinkedNoteId = emptyMap()
        )

        assertEquals(listOf("Untitled", "2026-09-27"), labelled.map { it.parentTitle })
    }

    @Test
    fun parentTitlesAreGivenForRowsAndLinkedSubNotesButNotForNormalNotes() {
        val row = note("monday", isSubNote = true, databaseId = "days")
        val linkedPage = note("linked-page", isSubNote = true)
        val plainNote = note("plain")
        val rowWithoutParent = note("lost-row", isSubNote = true, databaseId = "nowhere")

        val parentTitles = listOf(row, linkedPage, plainNote, rowWithoutParent).subNoteParentTitles(
            parentsByDatabaseId = mapOf("days" to weekPlan),
            parentsByLinkedNoteId = mapOf("linked-page" to note("reading", title = "Reading list"))
        )

        assertEquals(mapOf("monday" to "Week plan", "linked-page" to "Reading list"), parentTitles)
    }

    @Test
    fun theMostRecentlyEditedParentWinsWhenThereAreSeveral() {
        val olderParent = note("older", updatedAt = 10L)
        val newerParent = note("newer", updatedAt = 20L)

        val parents = listOf("days" to olderParent, "days" to newerParent, "films" to olderParent).latestParentByChildId()

        assertEquals(mapOf("days" to newerParent, "films" to olderParent), parents)
    }
}
