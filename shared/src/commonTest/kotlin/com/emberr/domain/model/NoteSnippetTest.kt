package com.emberr.domain.model

import com.emberr.domain.sample.SampleNoteContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteSnippetTest {

    @Test
    fun theSnippetJoinsTheTextOfWrittenBlocksAndSkipsEverythingElse() {
        val blocks = listOf(
            HeadingBlock(id = "heading", text = "Plan", level = 1),
            TextBlock(id = "empty", text = ""),
            SolidDividerBlock(id = "divider"),
            TextBlock(id = "deleted", text = "Gone", isDeleted = true),
            CheckboxBlock(id = "task", text = "Pack bags", isChecked = false)
        )

        assertEquals("Plan Pack bags", generateSnippet(blocks))
    }

    @Test
    fun theSnippetStopsAtOneHundredTwentyCharacters() {
        val blocks = listOf(TextBlock(id = "long", text = "a".repeat(200)))

        assertEquals(120, generateSnippet(blocks).length)
    }

    @Test
    fun theStartHereNoteHasASnippetForItsFavoriteCard() {
        val snippet = generateSnippet(SampleNoteContent.buildBlocks(createdAt = 1_000L))

        assertTrue(snippet.startsWith("Start here This note sits in Favorites"))
    }
}
