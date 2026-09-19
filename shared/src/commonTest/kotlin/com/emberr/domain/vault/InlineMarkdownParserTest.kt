// Checks that markdown markers come back as the right formatting spans.

package com.emberr.domain.vault

import com.emberr.domain.model.InlineSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineMarkdownParserTest {

    @Test
    fun plainTextHasNoFormatting() {
        val parsed = InlineMarkdownParser.parse("call the plumber")

        assertEquals("call the plumber", parsed.text)
        assertTrue(parsed.spans.isEmpty())
        assertTrue(!parsed.isWholeTextBold)
    }

    @Test
    fun boldMarkersBecomeASpan() {
        val parsed = InlineMarkdownParser.parse("call the plumber, **urgent**")

        assertEquals("call the plumber, urgent", parsed.text)
        assertEquals(listOf(InlineSpan(start = 18, end = 24, bold = true)), parsed.spans)
    }

    @Test
    fun formattingCoveringEveryCharacterBecomesABlockLevelFlag() {
        val parsed = InlineMarkdownParser.parse("**everything**")

        assertEquals("everything", parsed.text)
        assertTrue(parsed.isWholeTextBold)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun escapedMarkersStayLiteral() {
        val parsed = InlineMarkdownParser.parse("2 \\* 3 \\~ 4")

        assertEquals("2 * 3 ~ 4", parsed.text)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun underlineTagsBecomeAnUnderlineSpan() {
        val parsed = InlineMarkdownParser.parse("plain <u>marked</u> plain")

        assertEquals("plain marked plain", parsed.text)
        assertEquals(listOf(InlineSpan(start = 6, end = 12, underline = true)), parsed.spans)
    }

    @Test
    fun highlightMarkersBecomeAHighlightSpan() {
        val parsed = InlineMarkdownParser.parse("plain ==marked== plain")

        assertEquals("plain marked plain", parsed.text)
        assertEquals(listOf(InlineSpan(start = 6, end = 12, highlight = true)), parsed.spans)
    }

    @Test
    fun escapedDoubledEqualsStaysLiteral() {
        val parsed = InlineMarkdownParser.parse("2 \\=\\= 3")

        assertEquals("2 == 3", parsed.text)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun combinedMarkersProduceOneSpanWithEveryFlag() {
        val parsed = InlineMarkdownParser.parse("***~~<u>==abc==</u>~~***")

        assertEquals("abc", parsed.text)
        assertTrue(parsed.isWholeTextBold)
        assertTrue(parsed.isWholeTextItalic)
        assertTrue(parsed.isWholeTextStrikeThrough)
        assertTrue(parsed.isWholeTextUnderlined)
        assertTrue(parsed.isWholeTextHighlighted)
    }

    @Test
    fun aSpanSurvivesALineBreakAsOneSpan() {
        val parsed = InlineMarkdownParser.parse("**first**\n**second**")

        assertEquals("first\nsecond", parsed.text)
        assertTrue(parsed.isWholeTextBold)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun partialFormattingAcrossTwoLinesStaysASingleSpan() {
        val parsed = InlineMarkdownParser.parse("**one**\n**two** three")

        assertEquals("one\ntwo three", parsed.text)
        assertEquals(listOf(InlineSpan(start = 0, end = 7, bold = true)), parsed.spans)
    }

    @Test
    fun aBlockWideStyleStaysBlockWideWhenASpanSitsInsideIt() {
        val parsed = InlineMarkdownParser.parse("*<u>one two</u>* ***<u>three</u>*** *<u>four</u>*")

        assertEquals("one two three four", parsed.text)
        assertTrue(parsed.isWholeTextItalic, "italic should still cover the whole block")
        assertTrue(parsed.isWholeTextUnderlined, "underline should still cover the whole block")
        assertTrue(!parsed.isWholeTextBold)
        assertEquals(listOf(InlineSpan(start = 8, end = 13, bold = true)), parsed.spans)
    }

    @Test
    fun aRunOfSpacesBetweenStyledTextIsBridgedToo() {
        val parsed = InlineMarkdownParser.parse("**one**   **two**")

        assertEquals("one   two", parsed.text)
        assertTrue(parsed.isWholeTextBold)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun spacesAreNotBridgedWhenOnlyOneSideIsStyled() {
        val parsed = InlineMarkdownParser.parse("**one** two")

        assertEquals("one two", parsed.text)
        assertTrue(!parsed.isWholeTextBold)
        assertEquals(listOf(InlineSpan(start = 0, end = 3, bold = true)), parsed.spans)
    }

    @Test
    fun everyFormatterOutputComesBackUnchanged() {
        val samples = listOf(
            "plain text",
            "with **bold** inside",
            "with *italic* inside",
            "with ~~strike~~ inside",
            "with <u>underline</u> inside",
            "2 \\* 3",
            "**ab***cd*",
            "one **two** three",
            "plain ==marked== plain",
            "==**both**==",
            "2 \\=\\= 3"
        )

        for (sample in samples) {
            val parsed = InlineMarkdownParser.parse(sample)
            val rewritten = InlineMarkdownFormatter.toMarkdown(
                text = parsed.text,
                spans = parsed.spans,
                isWholeBlockBold = parsed.isWholeTextBold,
                isWholeBlockItalic = parsed.isWholeTextItalic,
                isWholeBlockStrikeThrough = parsed.isWholeTextStrikeThrough,
                isWholeBlockUnderlined = parsed.isWholeTextUnderlined,
                isWholeBlockHighlighted = parsed.isWholeTextHighlighted
            )
            assertEquals(parsed.text, InlineMarkdownParser.parse(rewritten).text, "text drifted for: $sample")
            assertEquals(parsed.spans, InlineMarkdownParser.parse(rewritten).spans, "spans drifted for: $sample")
        }
    }

    @Test
    fun markTagBecomesAColouredHighlightSpan() {
        val parsed = InlineMarkdownParser.parse("call the plumber, <mark class=\"red\">urgent</mark>")

        assertEquals("call the plumber, urgent", parsed.text)
        assertEquals(
            listOf(InlineSpan(start = 18, end = 24, highlight = true, highlightColorName = "red")),
            parsed.spans
        )
    }

    @Test
    fun twoHighlightColoursStayInSeparateSpans() {
        val parsed = InlineMarkdownParser.parse("<mark class=\"red\">ab</mark><mark class=\"blue\">cd</mark>")

        assertEquals("abcd", parsed.text)
        assertEquals(
            listOf(
                InlineSpan(start = 0, end = 2, highlight = true, highlightColorName = "red"),
                InlineSpan(start = 2, end = 4, highlight = true, highlightColorName = "blue")
            ),
            parsed.spans
        )
        assertTrue(!parsed.isWholeTextHighlighted)
    }

    @Test
    fun oneColourCoveringEverythingBecomesABlockLevelColour() {
        val parsed = InlineMarkdownParser.parse("<mark class=\"green\">all of it</mark>")

        assertEquals("all of it", parsed.text)
        assertTrue(parsed.isWholeTextHighlighted)
        assertEquals("green", parsed.wholeTextHighlightColorName)
        assertTrue(parsed.spans.isEmpty())
    }

    @Test
    fun colouredHighlightsSurviveARoundTrip() {
        val samples = listOf(
            "plain <mark class=\"red\">marked</mark> plain",
            "<mark class=\"blue\">**both**</mark>",
            "<mark class=\"red\">ab</mark> <mark class=\"blue\">cd</mark>"
        )

        for (sample in samples) {
            val parsed = InlineMarkdownParser.parse(sample)
            val rewritten = InlineMarkdownFormatter.toMarkdown(
                text = parsed.text,
                spans = parsed.spans,
                isWholeBlockBold = parsed.isWholeTextBold,
                isWholeBlockItalic = parsed.isWholeTextItalic,
                isWholeBlockStrikeThrough = parsed.isWholeTextStrikeThrough,
                isWholeBlockUnderlined = parsed.isWholeTextUnderlined,
                isWholeBlockHighlighted = parsed.isWholeTextHighlighted,
                wholeBlockHighlightColorName = parsed.wholeTextHighlightColorName
            )
            assertEquals(parsed.text, InlineMarkdownParser.parse(rewritten).text, "text drifted for: $sample")
            assertEquals(parsed.spans, InlineMarkdownParser.parse(rewritten).spans, "spans drifted for: $sample")
        }
    }

    @Test
    fun emptyInputProducesEmptyResult() {
        assertEquals(ParsedInlineText.empty, InlineMarkdownParser.parse(""))
    }
}
