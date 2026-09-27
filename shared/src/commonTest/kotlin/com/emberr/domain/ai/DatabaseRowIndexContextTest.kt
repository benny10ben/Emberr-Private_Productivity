package com.emberr.domain.ai

import com.emberr.data.local.room.entity.NoteMetadataEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseRowIndexContextTest {

    private fun rowNote(title: String, snippet: String = "") = NoteMetadataEntity(
        noteId = "monday",
        title = title,
        folderId = null,
        isDaily = false,
        dateString = null,
        createdAt = 1L,
        updatedAt = 1L,
        filePath = "",
        snippet = snippet,
        isSubNote = true,
        databaseId = "days"
    )

    @Test
    fun aRowSaysWhichDatabaseAndNoteItBelongsTo() {
        val context = databaseRowIndexContext(
            rowNote("Monday", snippet = "Gym at 7"),
            DatabaseRowLocation(databaseTitle = "Days", hostNoteTitle = "Week plan")
        )

        assertEquals(
            "[Source: Database row]\nTitle: Monday\nDatabase: Days\nInside note: Week plan\nDescription: Gym at 7\n",
            context
        )
    }

    @Test
    fun blankTitlesAreLeftOutAndAnUntitledRowIsCalledUntitled() {
        val context = databaseRowIndexContext(
            rowNote(""),
            DatabaseRowLocation(databaseTitle = "", hostNoteTitle = "")
        )

        assertEquals("[Source: Database row]\nTitle: Untitled\n", context)
    }

    @Test
    fun aRowWhoseTableIsNotFoundStillSaysItIsARow() {
        val context = databaseRowIndexContext(rowNote("Monday"), location = null)

        assertEquals("[Source: Database row]\nTitle: Monday\n", context)
    }
}
