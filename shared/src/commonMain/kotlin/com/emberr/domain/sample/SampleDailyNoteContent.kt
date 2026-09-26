package com.emberr.domain.sample

import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
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

object SampleDailyNoteContent {

    private const val PROJECT_PAGE_URL = "https://github.com/benny10ben/Emberr-Privacy-Notes-Tasks-Calendar"

    fun buildBlocks(createdAt: Long): List<NoteBlock> {
        val spacers = BreathingRoom(idPrefix = "sample_daily", createdAt = createdAt)

        val introSentence =
            "This is today. Every day gets a page of its own, and the dates above move you around."
        val blockKitSentence =
            "Any line can become any of these. Make a word bold, lean on italic, or cross it out once it stops being true."

        return listOf(
            HeadingBlock(
                id = "sample_daily_title",
                text = "Hey, welcome to Emberr",
                level = 1,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_daily_intro",
                text = introSentence,
                inlineSpans = listOfNotNull(
                    emphasisedWord(introSentence, "today", bold = true)
                ),
                updatedAt = createdAt
            ),
            spacers.next(),
            SolidDividerBlock(id = "sample_daily_divider_tasks", updatedAt = createdAt),
            spacers.next(),

            HeadingBlock(
                id = "sample_daily_tasks_title",
                text = "Today's list",
                level = 2,
                updatedAt = createdAt
            ),
            CheckboxBlock(
                id = "sample_daily_task_done",
                text = "Done things stay on the day you did them",
                isChecked = true,
                completedAt = createdAt,
                updatedAt = createdAt
            ),
            CheckboxBlock(
                id = "sample_daily_task_open",
                text = "Leave this one - unfinished tasks follow you to tomorrow",
                isChecked = false,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_daily_reminder_hint",
                text = "Give a task a time and Emberr will remind you.",
                updatedAt = createdAt
            ),
            spacers.next(),
            SolidDividerBlock(id = "sample_daily_divider_blocks", updatedAt = createdAt),
            spacers.next(),

            HeadingBlock(
                id = "sample_daily_blocks_title",
                text = "Press / to add a block",
                level = 2,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_daily_blocks_intro",
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
                id = "sample_daily_bullet_one",
                text = "A bullet, for a quick list",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_daily_bullet_two",
                text = "Indent it to tuck it under the one above",
                updatedAt = createdAt
            ),
            spacers.next(),
            NumberedListBlock(
                id = "sample_daily_number_one",
                text = "Numbers, when the order matters",
                number = 1,
                updatedAt = createdAt
            ),
            NumberedListBlock(
                id = "sample_daily_number_two",
                text = "They renumber themselves",
                number = 2,
                updatedAt = createdAt
            ),
            spacers.next(),
            ToggleBlock(
                id = "sample_daily_toggle",
                text = "A toggle, to fold a section out of the way",
                isExpanded = true,
                updatedAt = createdAt
            ),
            TextBlock(
                id = "sample_daily_toggle_child",
                text = "",
                indentationLevel = 1,
                updatedAt = createdAt
            ),
            spacers.next(),
            QuoteBlock(
                id = "sample_daily_quote",
                text = "A quote, for a line worth keeping.",
                updatedAt = createdAt
            ),
            spacers.next(),
            CodeBlock(
                id = "sample_daily_code",
                code = "val today = \"a code block, for the exact characters\"",
                language = "kotlin",
                updatedAt = createdAt
            ),
            spacers.next(),
            ThreeDotDividerBlock(id = "sample_daily_divider_dots", updatedAt = createdAt),
            spacers.next(),

            TextBlock(
                id = "sample_daily_table_intro",
                text = "A simple table, when you just need a grid:",
                updatedAt = createdAt
            ),
            TableBlock(
                id = "sample_daily_table",
                rows = listOf(
                    listOf("Day", "Felt like"),
                    listOf("Today", "A good start")
                ),
                updatedAt = createdAt
            ),
            spacers.next(),

            TextBlock(
                id = "sample_daily_media_intro",
                text = "Photos, files and voice notes live inline too. Tap one to fill it in:",
                updatedAt = createdAt
            ),
            ImageBlock(id = "sample_daily_image", localFilePath = null, updatedAt = createdAt),
            DocumentBlock(id = "sample_daily_document", localFilePath = null, updatedAt = createdAt),
            VoiceBlock(id = "sample_daily_voice", localFilePath = null, updatedAt = createdAt),
            spacers.next(),
            BookmarkBlock(
                id = "sample_daily_bookmark",
                url = PROJECT_PAGE_URL,
                title = "Emberr on GitHub",
                description = "A saved link keeps its title and note, so you know why you kept it.",
                previewImageUrl = null,
                updatedAt = createdAt
            ),
            spacers.next(),
            SolidDividerBlock(id = "sample_daily_divider_outro", updatedAt = createdAt),
            spacers.next(),

            HeadingBlock(
                id = "sample_daily_outro_title",
                text = "Three things worth knowing",
                level = 2,
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_daily_outro_pin",
                text = "Long press a block and pin a block and it shows up at the top of every day",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_daily_outro_timeline",
                text = "The timeline button at the top replays every day you have written on like a chatbox",
                updatedAt = createdAt
            ),
            BulletedListBlock(
                id = "sample_daily_outro_mention",
                text = "Type @ to link one of your other notes",
                updatedAt = createdAt
            ),
            spacers.next(),
            TextBlock(
                id = "sample_daily_outro",
                text = "That is everything. Long prees -> select all blocks -> delete the blocks whenever you like, it is only blocks.",
                updatedAt = createdAt
            )
        )
    }
}
