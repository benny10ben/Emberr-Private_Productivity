// Checks that a block's formatting spans come out as the right markdown markers.

package com.emberr.domain.vault

import com.emberr.domain.model.InlineSpan
import kotlin.test.Test
import kotlin.test.assertEquals

class InlineMarkdownFormatterTest {

    @Test
    fun plainTextIsReturnedUnchanged() {
        assertEquals("call the plumber", InlineMarkdownFormatter.toMarkdown("call the plumber"))
    }

    @Test
    fun boldSpanBecomesDoubleAsterisks() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "call the plumber, urgent",
            spans = listOf(InlineSpan(start = 18, end = 24, bold = true))
        )
        assertEquals("call the plumber, **urgent**", result)
    }

    @Test
    fun markersHugTheTextInsteadOfSurroundingSpaces() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "one two three",
            spans = listOf(InlineSpan(start = 3, end = 8, bold = true))
        )
        assertEquals("one **two** three", result)
    }

    @Test
    fun highlightSpanBecomesDoubleEquals() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "call the plumber, urgent",
            spans = listOf(InlineSpan(start = 18, end = 24, highlight = true))
        )
        assertEquals("call the plumber, ==urgent==", result)
    }

    @Test
    fun wholeBlockHighlightWrapsEveryCharacter() {
        val result = InlineMarkdownFormatter.toMarkdown(text = "abc", isWholeBlockHighlighted = true)
        assertEquals("==abc==", result)
    }

    @Test
    fun colouredHighlightSpanBecomesAMarkTag() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "call the plumber, urgent",
            spans = listOf(
                InlineSpan(start = 18, end = 24, highlight = true, highlightColorName = "red")
            )
        )
        assertEquals("call the plumber, <mark class=\"red\">urgent</mark>", result)
    }

    @Test
    fun yellowHighlightStillUsesDoubleEquals() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "abc",
            spans = listOf(
                InlineSpan(start = 0, end = 3, highlight = true, highlightColorName = "yellow")
            )
        )
        assertEquals("==abc==", result)
    }

    @Test
    fun wholeBlockColouredHighlightWrapsEveryCharacter() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "abc",
            isWholeBlockHighlighted = true,
            wholeBlockHighlightColorName = "blue"
        )
        assertEquals("<mark class=\"blue\">abc</mark>", result)
    }

    @Test
    fun literalDoubledEqualsIsEscaped() {
        assertEquals("2 \\=\\= 3", InlineMarkdownFormatter.toMarkdown("2 == 3"))
    }

    @Test
    fun aSingleEqualsSignIsLeftAlone() {
        assertEquals("2 = 3", InlineMarkdownFormatter.toMarkdown("2 = 3"))
    }

    @Test
    fun combinedStylesOpenAndCloseInMirroredOrder() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "abc",
            spans = listOf(
                InlineSpan(
                    start = 0,
                    end = 3,
                    bold = true,
                    italic = true,
                    strikeThrough = true,
                    underline = true,
                    highlight = true
                )
            )
        )
        assertEquals("***~~<u>==abc==</u>~~***", result)
    }

    @Test
    fun wholeBlockStyleAppliesToEveryCharacter() {
        val result = InlineMarkdownFormatter.toMarkdown(text = "abc", isWholeBlockBold = true)
        assertEquals("**abc**", result)
    }

    @Test
    fun markersNeverCrossALineBreak() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "first\nsecond",
            spans = listOf(InlineSpan(start = 0, end = 12, bold = true))
        )
        assertEquals("**first**\n**second**", result)
    }

    @Test
    fun literalFormattingCharactersAreEscaped() {
        assertEquals("2 \\* 3 \\~ 4", InlineMarkdownFormatter.toMarkdown("2 * 3 ~ 4"))
    }

    @Test
    fun spansOutsideTheTextRangeAreIgnoredSafely() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "abc",
            spans = listOf(InlineSpan(start = 10, end = 40, bold = true))
        )
        assertEquals("abc", result)
    }

    @Test
    fun overlappingSpansMergeTheirStyles() {
        val result = InlineMarkdownFormatter.toMarkdown(
            text = "abcd",
            spans = listOf(
                InlineSpan(start = 0, end = 2, bold = true),
                InlineSpan(start = 2, end = 4, italic = true)
            )
        )
        assertEquals("**ab***cd*", result)
    }

    @Test
    fun emptyTextProducesEmptyOutput() {
        assertEquals("", InlineMarkdownFormatter.toMarkdown(""))
    }
}
