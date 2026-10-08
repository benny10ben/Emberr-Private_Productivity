package com.emberr.domain.sample

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.CanvasBlock
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.template.PREDEFINED_TEMPLATES
import com.emberr.domain.template.noteIdInSpace
import com.emberr.domain.template.templatesNeverSeededInSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StarterContentTest {

    private fun note(updatedAt: Long) = NoteMetadataEntity(
        noteId = "sample_note_welcome",
        title = "Start here",
        folderId = null,
        isDaily = false,
        dateString = null,
        createdAt = 5_000L,
        updatedAt = updatedAt,
        filePath = ""
    )

    @Test
    fun aStarterNoteNobodyEditedIsUntouched() {
        assertTrue(note(updatedAt = STARTER_CONTENT_UPDATED_AT).isUntouchedStarterContent())
    }

    @Test
    fun anEditedStarterNoteIsNoLongerUntouched() {
        assertFalse(note(updatedAt = 9_000L).isUntouchedStarterContent())
    }

    @Test
    fun anUntouchedStarterNoteShowsWhenItWasCreatedInsteadOf1970() {
        assertEquals(5_000L, note(updatedAt = STARTER_CONTENT_UPDATED_AT).lastEditedAtForDisplay())
    }

    @Test
    fun anEditedNoteShowsWhenItWasLastEdited() {
        assertEquals(9_000L, note(updatedAt = 9_000L).lastEditedAtForDisplay())
    }

    @Test
    fun everyStartHereBlockHasItsOwnId() {
        val blocks = SampleNoteContent.buildBlocks(STARTER_CONTENT_UPDATED_AT)

        assertEquals(blocks.size, blocks.map { it.id }.toSet().size)
    }

    @Test
    fun startHereShowsTheSampleDatabase() {
        val blocks = SampleNoteContent.buildBlocks(STARTER_CONTENT_UPDATED_AT)

        assertEquals(SampleNoteContent.DATABASE_ID, blocks.filterIsInstance<DatabaseBlock>().single().databaseId)
    }

    @Test
    fun startHereShowsTheCanvasTour() {
        val blocks = SampleNoteContent.buildBlocks(STARTER_CONTENT_UPDATED_AT)

        assertEquals(SampleNoteContent.CANVAS_NOTE_ID, blocks.filterIsInstance<CanvasBlock>().single().canvasNoteId)
    }

    @Test
    fun aFreshSpaceGetsEveryDefaultTemplate() {
        assertEquals(PREDEFINED_TEMPLATES, templatesNeverSeededInSpace("space-1", seededTemplateNoteIds = emptySet()))
    }

    @Test
    fun aTemplateThatWasSeededOnceIsNeverSeededAgain() {
        val alreadySeeded = PREDEFINED_TEMPLATES.map { it.noteIdInSpace("space-1") }.toSet()

        assertTrue(templatesNeverSeededInSpace("space-1", alreadySeeded).isEmpty())
    }

    @Test
    fun seedingOneSpaceDoesNotStopAnotherSpaceGettingTheTemplates() {
        val seededInFirstSpace = PREDEFINED_TEMPLATES.map { it.noteIdInSpace("space-1") }.toSet()

        assertEquals(PREDEFINED_TEMPLATES, templatesNeverSeededInSpace("space-2", seededInFirstSpace))
    }
}
