package com.emberr.domain.sample

import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CanvasBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ThreeDotDividerBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.VoiceBlock

data class SampleDatabaseRow(val title: String, val status: String)

object SampleNoteContent {

    private const val PROJECT_PAGE_URL = "https://github.com/emberr-app/Emberr"
    const val DATABASE_ID = "sample_note_database"
    const val CANVAS_NOTE_ID = "sample_note_canvas"
    const val DATABASE_TITLE = "Weekend plans"
    val databaseStatusOptions = listOf("To do", "Doing", "Done")
    val databaseRows = listOf(
        SampleDatabaseRow(title = "Try a new recipe", status = "To do"),
        SampleDatabaseRow(title = "Call an old friend", status = "Done")
    )

    fun buildBlocks(createdAt: Long): List<NoteBlock> {
        val spacers = BreathingRoom(idPrefix = "sample_note", createdAt = createdAt)

        val foldersSentence =
            "This note sits in Favorites, so it stays at the top. Your other notes can live in folders/sub-folders, and a few are already waiting below."
        val dailySentence =
            "The Daily screen gives every day a page of its own. Use it for today's tasks and quick thoughts, and tap the dates at the top to move between days."
        val notesSentence =
            "Notes and folders live apart from your daily pages: on the Home tab on your phone, and in the sidebar on desktop. They are not tied to a day, so use them for anything you want to keep and come back to."
        val blockKitSentence =
            "Any line can become any of these. Make a word bold, lean on italic, or cross it out once it stops being true."
        val canvasSentence =
            "A canvas, for thinking in cards and arrows. Open Canvas tour in Favorites to see one."
        val safetyNetSentence =
            "Nothing you delete is really gone for thirty days, so go ahead and rename, drag and throw things out."

        return listOf(
            HeadingBlock(
                id = "sample_note_title",
                text = "Hi, Welcome to Emberr (Beta)",
                level = 1,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_note_intro",
                text = foldersSentence,
                inlineSpans = listOfNotNull(
                    emphasisedWord(foldersSentence, "Favorites", bold = true),
                    emphasisedWord(foldersSentence, "already waiting", italic = true)
                ),
                updatedAt = createdAt
            ),
            spacers.next(),
            SolidDividerBlock(id = "sample_note_divider_daily", updatedAt = createdAt),
            spacers.next(),

            HeadingBlock(
                id = "sample_note_daily_title",
                text = "The Daily screen",
                level = 2,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_note_daily_intro",
                text = dailySentence,
                inlineSpans = listOfNotNull(
                    emphasisedWord(dailySentence, "Daily screen", bold = true)
                ),
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_daily_done",
                text = "Done tasks stay on the day you did them",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_daily_unfinished",
                text = "Unfinished tasks follow you to tomorrow",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_daily_reminder",
                text = "Give a task a time and Emberr will remind you",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_daily_pin",
                text = "Long press a block and pin it, and it shows up at the top of every day",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_daily_timeline",
                text = "The timeline button at the top replays every day you have written on, like a chat",
                updatedAt = createdAt
            ),
            spacers.next(),
            SolidDividerBlock(id = "sample_note_divider_notes", updatedAt = createdAt),
            spacers.next(),

            HeadingBlock(
                id = "sample_note_notes_title",
                text = "Your notes",
                level = 2,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_note_notes_intro",
                text = notesSentence,
                inlineSpans = listOfNotNull(
                    emphasisedWord(notesSentence, "Home tab", bold = true),
                    emphasisedWord(notesSentence, "sidebar", bold = true)
                ),
                updatedAt = createdAt
            ),
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
                text = "Try it: start a note in Reading List, then link back to this one with @",
                isChecked = false,
                updatedAt = createdAt
            ),
            spacers.next(),
            SolidDividerBlock(id = "sample_note_divider_blocks", updatedAt = createdAt),
            spacers.next(),

            HeadingBlock(
                id = "sample_note_blocks_title",
                text = "Press / to add a block",
                level = 2,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_note_blocks_intro",
                text = blockKitSentence,
                inlineSpans = listOfNotNull(
                    emphasisedWord(blockKitSentence, "bold", bold = true),
                    emphasisedWord(blockKitSentence, "italic", italic = true),
                    emphasisedWord(blockKitSentence, "cross it out", strikeThrough = true)
                ),
                updatedAt = createdAt
            ),
            spacers.next(),
            BulletedListBlock(
                id = "sample_note_bullet_one",
                text = "A bullet, for a quick list",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_note_bullet_two",
                text = "Indent it to tuck it under the one above",
                updatedAt = createdAt
            ),
            spacers.next(),
            NumberedListBlock(
                id = "sample_note_number_one",
                text = "Numbers, when the order matters",
                number = 1,
                updatedAt = createdAt
            ),
            NumberedListBlock(
                id = "sample_note_number_two",
                text = "They renumber themselves",
                number = 2,
                updatedAt = createdAt
            ),
            spacers.next(),
            ToggleBlock(
                id = "sample_note_toggle",
                text = "A toggle, to fold a section out of the way",
                isExpanded = true,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_note_toggle_child",
                text = "",
                indentationLevel = 1,
                updatedAt = createdAt
            ),
            spacers.next(),
            QuoteBlock(
                id = "sample_note_block_quote",
                text = "A quote, for a line worth keeping.",
                updatedAt = createdAt
            ),
            spacers.next(),
            CodeBlock(
                id = "sample_note_code",
                code = "val today = \"a code block, for the exact characters\"",
                language = "kotlin",
                updatedAt = createdAt
            ),
            spacers.next(),
            ThreeDotDividerBlock(id = "sample_note_divider_dots", updatedAt = createdAt),
            spacers.next(),

            TextBlock(
                id = "sample_note_table_intro",
                text = "A simple table, when you just need a grid:",
                updatedAt = createdAt
            ),
            TableBlock(
                id = "sample_note_table",
                rows = listOf(
                    listOf("Day", "Felt like"),
                    listOf("Today", "A good start")
                ),
                updatedAt = createdAt
            ),
            spacers.next(),

            TextBlock(
                id = "sample_note_database_intro",
                text = "A database, when every row deserves its own page. Add columns, then sort, filter or switch it to a board or gallery:",
                updatedAt = createdAt
            ),
            DatabaseBlock(id = "sample_note_database_view", databaseId = DATABASE_ID, updatedAt = createdAt),
            spacers.next(),

            TextBlock(
                id = "sample_note_canvas_intro",
                text = canvasSentence,
                inlineSpans = listOfNotNull(
                    emphasisedWord(canvasSentence, "Canvas tour", bold = true)
                ),
                updatedAt = createdAt
            ),
            CanvasBlock(id = "sample_note_canvas_view", canvasNoteId = CANVAS_NOTE_ID, updatedAt = createdAt),
            spacers.next(),

            TextBlock(
                id = "sample_note_media_intro",
                text = "Photos, files and voice notes live inline too. Tap one to fill it in:",
                updatedAt = createdAt
            ),
            ImageBlock(id = "sample_note_image", localFilePath = null, updatedAt = createdAt),
            DocumentBlock(id = "sample_note_document", localFilePath = null, updatedAt = createdAt),
            VoiceBlock(id = "sample_note_voice", localFilePath = null, updatedAt = createdAt),
            spacers.next(),
            BookmarkBlock(
                id = "sample_note_bookmark",
                url = PROJECT_PAGE_URL,
                title = "Emberr on GitHub",
                description = "A saved link keeps its title and note, so you know why you kept it.",
                previewImageUrl = null,
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
