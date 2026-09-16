package com.emberr.domain.repository

import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedNoteContentTest {

    private fun text(id: String, body: String, updatedAt: Long = 100L, isDeleted: Boolean = false) =
        TextBlock(id = id, text = body, updatedAt = updatedAt, isDeleted = isDeleted)

    private fun blockNamed(blocks: List<NoteBlock>, id: String) = blocks.first { it.id == id }

    @Test
    fun aBlockTheSaveLeftOutComesBackFlaggedDeletedInsteadOfGoingMissing() {
        val alreadyStored = listOf(text("a", "first"), text("b", "second"), text("c", "third"))
        val beingSaved = listOf(text("a", "first"), text("c", "third"))

        val result = SavedNoteContent.blocksTheNoteHoldsAfterSaving(beingSaved, alreadyStored, removedAt = 500L)

        assertEquals(listOf("a", "c", "b"), result.map { it.id })
        assertTrue(blockNamed(result, "b").isDeleted)
        assertEquals(500L, blockNamed(result, "b").updatedAt)
    }

    @Test
    fun everyBlockTheNoteHeldStillHasAnEntryWhenSeveralAreRemovedAtOnce() {
        val alreadyStored = listOf(text("a", "first"), text("b", "second"), text("c", "third"))
        val beingSaved = listOf(text("b", "second"))

        val result = SavedNoteContent.blocksTheNoteHoldsAfterSaving(beingSaved, alreadyStored, removedAt = 500L)

        assertEquals(setOf("a", "b", "c"), result.map { it.id }.toSet())
        assertEquals(setOf("a", "c"), result.filter { it.isDeleted }.map { it.id }.toSet())
    }

    @Test
    fun aBlockThatWasAlreadyDeletedKeepsTheTimeItWasDeletedAt() {
        val alreadyStored = listOf(text("a", "first"), text("b", "second", updatedAt = 200L, isDeleted = true))
        val beingSaved = listOf(text("a", "first"))

        val result = SavedNoteContent.blocksTheNoteHoldsAfterSaving(beingSaved, alreadyStored, removedAt = 500L)

        assertTrue(blockNamed(result, "b").isDeleted)
        assertEquals(200L, blockNamed(result, "b").updatedAt)
    }

    @Test
    fun nothingIsAddedWhenTheSaveStillCarriesEveryStoredBlock() {
        val alreadyStored = listOf(text("a", "first"), text("b", "second"))
        val beingSaved = listOf(text("a", "edited", updatedAt = 400L), text("b", "second"))

        val result = SavedNoteContent.blocksTheNoteHoldsAfterSaving(beingSaved, alreadyStored, removedAt = 500L)

        assertEquals(beingSaved, result)
    }

    @Test
    fun aBrandNewNoteWithNothingStoredYetKeepsExactlyWhatIsBeingSaved() {
        val beingSaved = listOf(text("a", "first"))

        val result = SavedNoteContent.blocksTheNoteHoldsAfterSaving(beingSaved, emptyList(), removedAt = 500L)

        assertEquals(beingSaved, result)
    }
}
