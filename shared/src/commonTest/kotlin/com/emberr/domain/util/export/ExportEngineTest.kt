package com.emberr.domain.util.export

import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ThreeDotDividerBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.VoiceBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportEngineTest {

    private fun plainTextOf(vararg blocks: NoteBlock, title: String? = null) =
        ExportEngine.generatePlainText(blocks.toList(), title)

    private fun markdownOf(vararg blocks: NoteBlock, title: String? = null) =
        ExportEngine.generateMarkdown(blocks.toList(), title)

    @Test
    fun anEmptyNoteExportsAsAnEmptyString() {
        assertEquals("", ExportEngine.generatePlainText(emptyList()))
        assertEquals("", ExportEngine.generateMarkdown(emptyList()))
    }

    @Test
    fun theTitleLeadsThePlainTextExport() {
        assertEquals(
            "My note\n\nhello",
            plainTextOf(TextBlock(id = "text-1", text = "hello"), title = "My note")
        )
    }

    @Test
    fun theTitleBecomesATopLevelMarkdownHeading() {
        assertEquals(
            "# My note\n\nhello",
            markdownOf(TextBlock(id = "text-1", text = "hello"), title = "My note")
        )
    }

    @Test
    fun aMissingOrBlankTitleIsLeftOutEntirely() {
        assertEquals("hello", plainTextOf(TextBlock(id = "text-1", text = "hello"), title = null))
        assertEquals("hello", plainTextOf(TextBlock(id = "text-1", text = "hello"), title = "   "))
        assertEquals("hello", markdownOf(TextBlock(id = "text-1", text = "hello"), title = "   "))
    }

    @Test
    fun deletedBlocksAreLeftOutOfBothExports() {
        val blocks = arrayOf(
            TextBlock(id = "text-1", text = "kept"),
            TextBlock(id = "text-2", text = "removed", isDeleted = true)
        )

        assertEquals("kept", plainTextOf(*blocks))
        assertEquals("kept", markdownOf(*blocks))
    }

    @Test
    fun headingsBecomeHashesMatchingTheirLevel() {
        assertEquals("# Title", markdownOf(HeadingBlock(id = "heading-1", text = "Title", level = 1)))
        assertEquals("### Title", markdownOf(HeadingBlock(id = "heading-1", text = "Title", level = 3)))
        assertEquals("Title", plainTextOf(HeadingBlock(id = "heading-1", text = "Title", level = 3)))
    }

    @Test
    fun checkboxesShowWhetherTheyAreTicked() {
        val ticked = CheckboxBlock(id = "checkbox-1", text = "Done", isChecked = true)
        val unticked = CheckboxBlock(id = "checkbox-2", text = "Todo", isChecked = false)

        assertEquals("[x] Done", plainTextOf(ticked))
        assertEquals("[ ] Todo", plainTextOf(unticked))
        assertEquals("- [x] Done", markdownOf(ticked))
        assertEquals("- [ ] Todo", markdownOf(unticked))
    }

    @Test
    fun listsKeepTheirBulletsAndNumbers() {
        assertEquals("• First", plainTextOf(BulletedListBlock(id = "bullet-1", text = "First")))
        assertEquals("- First", markdownOf(BulletedListBlock(id = "bullet-1", text = "First")))
        assertEquals("3. Step", plainTextOf(NumberedListBlock(id = "number-1", text = "Step", number = 3)))
        assertEquals("3. Step", markdownOf(NumberedListBlock(id = "number-1", text = "Step", number = 3)))
    }

    @Test
    fun quotesAreWrappedForReadingAndMarkedUpForMarkdown() {
        assertEquals("\"Quoted\"", plainTextOf(QuoteBlock(id = "quote-1", text = "Quoted")))
        assertEquals("> Quoted", markdownOf(QuoteBlock(id = "quote-1", text = "Quoted")))
    }

    @Test
    fun togglesBecomeAMarkedBulletInMarkdown() {
        assertEquals("- \u25B8 Details", markdownOf(ToggleBlock(id = "toggle-1", text = "Details")))
        assertEquals("▶ Details", plainTextOf(ToggleBlock(id = "toggle-1", text = "Details")))
    }

    @Test
    fun codeIsFencedInMarkdownAndLeftBareInPlainText() {
        val code = CodeBlock(id = "code-1", code = "val answer = 42")

        assertEquals("```\nval answer = 42\n```", markdownOf(code))
        assertEquals("val answer = 42", plainTextOf(code))
    }

    @Test
    fun theTwoDividerStylesStayTellableApartInMarkdown() {
        assertEquals("---", markdownOf(SolidDividerBlock(id = "divider-1")))
        assertEquals("* * *", markdownOf(ThreeDotDividerBlock(id = "divider-2")))
        assertEquals("---", plainTextOf(SolidDividerBlock(id = "divider-1")))
    }

    @Test
    fun bookmarksBecomeALinkAndFallBackToTheAddressWhenThereIsNoTitle() {
        val titled = BookmarkBlock(id = "bookmark-1", url = "https://example.com", title = "An article")
        val untitled = BookmarkBlock(id = "bookmark-2", url = "https://example.com", title = null)

        assertEquals("[An article](https://example.com)", markdownOf(titled))
        assertEquals("[https://example.com](https://example.com)", markdownOf(untitled))
        assertEquals("An article\nhttps://example.com", plainTextOf(titled))
        assertEquals("https://example.com\nhttps://example.com", plainTextOf(untitled))
    }

    @Test
    fun imagesAndDocumentsArePlaceholdersInPlainTextAndLinksInMarkdown() {
        val image = ImageBlock(id = "image-1", localFilePath = "photo.png")
        val document = DocumentBlock(id = "document-1", localFilePath = "stored.pdf", fileName = "invoice.pdf")

        assertEquals("[Image]", plainTextOf(image))
        assertEquals("![](photo.png)", markdownOf(image))
        assertEquals("[File: invoice.pdf]", plainTextOf(document))
        assertEquals("[invoice.pdf](stored.pdf)", markdownOf(document))
    }

    @Test
    fun aVoiceNoteHasNoPlainTextFormButStillAppearsInMarkdown() {
        val blocks = arrayOf(
            TextBlock(id = "text-1", text = "kept"),
            VoiceBlock(id = "voice-1", localFilePath = "memo.m4a")
        )

        assertEquals("kept", plainTextOf(*blocks))
        assertEquals(
            "kept\n\n```emberr-voice\nfile: memo.m4a\nseconds: 0\n```",
            markdownOf(*blocks)
        )
    }

    @Test
    fun aLinkedNoteWithNoKnownTitleIsSkippedInMarkdown() {
        val blocks = arrayOf(
            TextBlock(id = "text-1", text = "kept"),
            LinkedNoteBlock(id = "linked-1", linkedNoteId = "note-2")
        )

        assertEquals("kept", plainTextOf(*blocks))
        assertEquals("kept", markdownOf(*blocks))
    }

    @Test
    fun textStylesAreNestedBoldThenItalicThenStrikethrough() {
        val styled = TextBlock(
            id = "text-1",
            text = "urgent",
            isBold = true,
            isItalic = true,
            isStrikeThrough = true
        )

        assertEquals("***~~urgent~~***", markdownOf(styled))
    }

    @Test
    fun formattingAppliedToPartOfALineSurvivesTheExport() {
        val styled = TextBlock(
            id = "text-1",
            text = "call the plumber, urgent",
            inlineSpans = listOf(InlineSpan(start = 18, end = 24, bold = true))
        )

        assertEquals("call the plumber, **urgent**", markdownOf(styled))
    }

    @Test
    fun eachTextStyleCanBeUsedOnItsOwn() {
        assertEquals("**urgent**", markdownOf(TextBlock(id = "t", text = "urgent", isBold = true)))
        assertEquals("*urgent*", markdownOf(TextBlock(id = "t", text = "urgent", isItalic = true)))
        assertEquals("~~urgent~~", markdownOf(TextBlock(id = "t", text = "urgent", isStrikeThrough = true)))
    }

    @Test
    fun aBoldHeadingKeepsItsMarkersSoTheFlagIsNotLostOnImport() {
        assertEquals("# **Title**", markdownOf(HeadingBlock(id = "heading-1", text = "Title", isBold = true)))
    }

    @Test
    fun plainTextExportIndentsWithTabs() {
        assertEquals(
            "Parent\n\t• Child",
            plainTextOf(
                TextBlock(id = "text-1", text = "Parent"),
                BulletedListBlock(id = "bullet-1", text = "Child", indentationLevel = 1)
            )
        )
    }

    @Test
    fun markdownExportIndentsWithTwoSpacesPerLevel() {
        assertEquals(
            "Parent\n\n  - Child\n    - Grandchild",
            markdownOf(
                TextBlock(id = "text-1", text = "Parent"),
                BulletedListBlock(id = "bullet-1", text = "Child", indentationLevel = 1),
                BulletedListBlock(id = "bullet-2", text = "Grandchild", indentationLevel = 2)
            )
        )
    }

    @Test
    fun aTableBecomesAMarkdownTableWithASeparatorAfterTheFirstRow() {
        assertEquals(
            "| Header A | Header B |\n| --- | --- |\n| Cell A | Cell B |",
            markdownOf(
                TableBlock(
                    id = "table-1",
                    rows = listOf(listOf("Header A", "Header B"), listOf("Cell A", "Cell B"))
                )
            )
        )
    }

    @Test
    fun aTableIsListedRowByRowInPlainText() {
        assertEquals(
            "Intro\n  Header A | Header B\n  Cell A | Cell B",
            plainTextOf(
                TextBlock(id = "text-1", text = "Intro"),
                TableBlock(
                    id = "table-1",
                    rows = listOf(listOf("Header A", "Header B"), listOf("Cell A", "Cell B"))
                )
            )
        )
    }
}
