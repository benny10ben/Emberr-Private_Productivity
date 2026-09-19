// Reads those markdown markers back into the character positions a block stores its formatting as.

package com.emberr.domain.vault

import com.emberr.domain.model.InlineSpan

private const val BOLD_BIT = 1
private const val ITALIC_BIT = 2
private const val STRIKE_THROUGH_BIT = 4
private const val UNDERLINE_BIT = 8
private const val HIGHLIGHT_BIT = 16

data class ParsedInlineText(
    val text: String,
    val spans: List<InlineSpan>,
    val isWholeTextBold: Boolean,
    val isWholeTextItalic: Boolean,
    val isWholeTextStrikeThrough: Boolean,
    val isWholeTextUnderlined: Boolean,
    val isWholeTextHighlighted: Boolean
) {
    companion object {
        val empty = ParsedInlineText("", emptyList(), false, false, false, false, false)
    }
}

object InlineMarkdownParser {

    fun parse(markdown: String): ParsedInlineText {
        if (markdown.isEmpty()) return ParsedInlineText.empty

        val plainText = StringBuilder(markdown.length)
        val stylePerCharacter = mutableListOf<Int>()
        var activeStyle = 0
        var index = 0

        while (index < markdown.length) {
            val character = markdown[index]
            when {
                character == '\\' && index + 1 < markdown.length -> {
                    plainText.append(markdown[index + 1])
                    stylePerCharacter.add(activeStyle)
                    index += 2
                }
                markdown.startsWith("**", index) -> {
                    activeStyle = activeStyle xor BOLD_BIT
                    index += 2
                }
                markdown.startsWith("~~", index) -> {
                    activeStyle = activeStyle xor STRIKE_THROUGH_BIT
                    index += 2
                }
                markdown.startsWith("==", index) -> {
                    activeStyle = activeStyle xor HIGHLIGHT_BIT
                    index += 2
                }
                markdown.startsWith("</u>", index) -> {
                    activeStyle = activeStyle and UNDERLINE_BIT.inv()
                    index += 4
                }
                markdown.startsWith("<u>", index) -> {
                    activeStyle = activeStyle or UNDERLINE_BIT
                    index += 3
                }
                character == '*' -> {
                    activeStyle = activeStyle xor ITALIC_BIT
                    index += 1
                }
                character == '\n' -> {
                    activeStyle = 0
                    plainText.append('\n')
                    stylePerCharacter.add(0)
                    index += 1
                }
                else -> {
                    plainText.append(character)
                    stylePerCharacter.add(activeStyle)
                    index += 1
                }
            }
        }

        val text = plainText.toString()
        if (text.isEmpty()) return ParsedInlineText.empty

        bridgeStylesAcrossWhitespace(text, stylePerCharacter)

        val isWholeTextBold = styleCoversEveryCharacter(text, stylePerCharacter, BOLD_BIT)
        val isWholeTextItalic = styleCoversEveryCharacter(text, stylePerCharacter, ITALIC_BIT)
        val isWholeTextStrikeThrough = styleCoversEveryCharacter(text, stylePerCharacter, STRIKE_THROUGH_BIT)
        val isWholeTextUnderlined = styleCoversEveryCharacter(text, stylePerCharacter, UNDERLINE_BIT)
        val isWholeTextHighlighted = styleCoversEveryCharacter(text, stylePerCharacter, HIGHLIGHT_BIT)

        var bitsMovedToWholeText = 0
        if (isWholeTextBold) bitsMovedToWholeText = bitsMovedToWholeText or BOLD_BIT
        if (isWholeTextItalic) bitsMovedToWholeText = bitsMovedToWholeText or ITALIC_BIT
        if (isWholeTextStrikeThrough) bitsMovedToWholeText = bitsMovedToWholeText or STRIKE_THROUGH_BIT
        if (isWholeTextUnderlined) bitsMovedToWholeText = bitsMovedToWholeText or UNDERLINE_BIT
        if (isWholeTextHighlighted) bitsMovedToWholeText = bitsMovedToWholeText or HIGHLIGHT_BIT

        val remainingStyles = stylePerCharacter.map { it and bitsMovedToWholeText.inv() }

        return ParsedInlineText(
            text = text,
            spans = buildSpans(remainingStyles),
            isWholeTextBold = isWholeTextBold,
            isWholeTextItalic = isWholeTextItalic,
            isWholeTextStrikeThrough = isWholeTextStrikeThrough,
            isWholeTextUnderlined = isWholeTextUnderlined,
            isWholeTextHighlighted = isWholeTextHighlighted
        )
    }

    // Whitespace between two styled runs arrives unstyled, so it takes back whatever styles both
    // neighbours share.
    private fun bridgeStylesAcrossWhitespace(text: String, stylePerCharacter: MutableList<Int>) {
        var index = 0

        while (index < text.length) {
            if (!isUnstyledWhitespace(text, stylePerCharacter, index)) {
                index++
                continue
            }

            var runEnd = index
            while (runEnd < text.length && isUnstyledWhitespace(text, stylePerCharacter, runEnd)) runEnd++

            val styleBefore = if (index > 0) stylePerCharacter[index - 1] else 0
            val styleAfter = if (runEnd < stylePerCharacter.size) stylePerCharacter[runEnd] else 0
            val sharedStyle = styleBefore and styleAfter

            if (sharedStyle != 0) {
                for (fillIndex in index until runEnd) stylePerCharacter[fillIndex] = sharedStyle
            }
            index = runEnd
        }
    }

    private fun isUnstyledWhitespace(
        text: String,
        stylePerCharacter: List<Int>,
        index: Int
    ): Boolean = text[index].isWhitespace() && stylePerCharacter[index] == 0

    private fun styleCoversEveryCharacter(text: String, stylePerCharacter: List<Int>, bit: Int): Boolean {
        var sawVisibleCharacter = false
        for (index in text.indices) {
            if (text[index] == '\n') continue
            sawVisibleCharacter = true
            if (stylePerCharacter[index] and bit == 0) return false
        }
        return sawVisibleCharacter
    }

    private fun buildSpans(stylePerCharacter: List<Int>): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        var index = 0

        while (index < stylePerCharacter.size) {
            val style = stylePerCharacter[index]
            if (style == 0) {
                index++
                continue
            }
            var runEnd = index
            while (runEnd < stylePerCharacter.size && stylePerCharacter[runEnd] == style) runEnd++

            spans.add(
                InlineSpan(
                    start = index,
                    end = runEnd,
                    bold = style and BOLD_BIT != 0,
                    italic = style and ITALIC_BIT != 0,
                    strikeThrough = style and STRIKE_THROUGH_BIT != 0,
                    underline = style and UNDERLINE_BIT != 0,
                    highlight = style and HIGHLIGHT_BIT != 0
                )
            )
            index = runEnd
        }
        return spans
    }
}
