package com.emberr.domain.sample

import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TextBlock

object SampleNoteContent {

    fun buildBlocks(createdAt: Long): List<NoteBlock> {
        val spacers = BreathingRoom(idPrefix = "sample_note", createdAt = createdAt)

        val foldersSentence =
            "Your notes can live in folders/sub-folders. This one sits in Personal, and there are three more waiting whenever you need them."
        val safetyNetSentence =
            "Nothing you delete is really gone for thirty days, so go ahead and rename, drag and throw things out."

        return listOf(
            HeadingBlock(
                id = "sample_note_title",
                text = "Start here",
                level = 1,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_note_intro",
                text = foldersSentence,
                inlineSpans = listOfNotNull(
                    emphasisedWord(foldersSentence, "Personal", bold = true),
                    emphasisedWord(foldersSentence, "three more", italic = true)
                ),
                updatedAt = createdAt
            ),
            spacers.next(),
            SolidDividerBlock(id = "sample_note_divider_organising", updatedAt = createdAt),
            spacers.next(),

            HeadingBlock(
                id = "sample_note_organising_title",
                text = "Moving things around",
                level = 2,
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_organising_drag",
                text = "Drag a note onto a folder to file it there",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_organising_nest",
                text = "Folders can sit inside folders, as deep as you want",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_organising_favourite",
                text = "Add a note to Favorites and it rides at the top of the list",
                updatedAt = createdAt
            ),
            spacers.next(),

            HeadingBlock(
                id = "sample_note_inside_title",
                text = "Inside a note",
                level = 2,
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_inside_cover",
                text = "Give it a cover image and an emoji from the note's own menu",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_inside_word_count",
                text = "Word count is switched on for this one - the toggle is in that same menu",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_inside_slash",
                text = "Press / for any kind of block, and @ to link another note",
                updatedAt = createdAt
            ),
            spacers.next(),
            CheckboxBlock(
                id = "sample_note_try_it",
                text = "Try it: start a note in Work, then link back to this one with @",
                isChecked = false,
                updatedAt = createdAt
            ),
            spacers.next(),
            SolidDividerBlock(id = "sample_note_divider_safety", updatedAt = createdAt),
            spacers.next(),

            HeadingBlock(
                id = "sample_note_safety_title",
                text = "Room to make a mess",
                level = 2,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_note_safety_intro",
                text = safetyNetSentence,
                inlineSpans = listOfNotNull(
                    emphasisedWord(safetyNetSentence, "thirty days", bold = true),
                    emphasisedWord(safetyNetSentence, "really gone", strikeThrough = true)
                ),
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_safety_trash",
                text = "Trash keeps deleted notes, and you can put them back",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_safety_templates",
                text = "Templates give you a starting page instead of a blank one",
                updatedAt = createdAt
            ),
            spacers.next(),
            QuoteBlock(
                id = "sample_note_quote",
                text = "Blocks behave exactly the same here as they do on the daily page.",
                updatedAt = createdAt
            ),
            spacers.next(),
            TextBlock(
                id = "sample_note_outro",
                text = "Delete this note whenever you like. Personal is yours now.",
                updatedAt = createdAt
            )
        )
    }
}
