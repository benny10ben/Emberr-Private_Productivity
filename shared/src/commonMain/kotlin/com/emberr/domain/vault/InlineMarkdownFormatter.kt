// Turns a block's text and its formatting spans into markdown, writing **bold** style markers.

package com.emberr.domain.vault

import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.defaultHighlightColorName

private const val BOLD_MARKER = "**"
private const val ITALIC_MARKER = "*"
private const val STRIKE_THROUGH_MARKER = "~~"
private const val UNDERLINE_OPENING_TAG = "<u>"
private const val UNDERLINE_CLOSING_TAG = "</u>"
private const val HIGHLIGHT_MARKER = "=="
private const val COLORED_HIGHLIGHT_OPENING_PREFIX = "<mark class=\""
private const val COLORED_HIGHLIGHT_OPENING_SUFFIX = "\">"
private const val COLORED_HIGHLIGHT_CLOSING_TAG = "</mark>"

private data class CharacterStyle(
    val isBold: Boolean,
    val isItalic: Boolean,
    val isStrikeThrough: Boolean,
    val isUnderlined: Boolean,
    val isHighlighted: Boolean,
    val highlightColorName: String?
) {
    val hasNoFormatting: Boolean
        get() = !isBold && !isItalic && !isStrikeThrough && !isUnderlined && !isHighlighted
}

object InlineMarkdownFormatter {

    private val charactersNeedingEscape = setOf('\\', '*', '~', '<')

    fun toMarkdown(
        text: String,
        spans: List<InlineSpan> = emptyList(),
        isWholeBlockBold: Boolean = false,
        isWholeBlockItalic: Boolean = false,
        isWholeBlockStrikeThrough: Boolean = false,
        isWholeBlockUnderlined: Boolean = false,
        isWholeBlockHighlighted: Boolean = false,
        wholeBlockHighlightColorName: String? = null
    ): String {
        if (text.isEmpty()) return ""

        val stylePerCharacter = buildStylePerCharacter(
            textLength = text.length,
            spans = spans,
            isWholeBlockBold = isWholeBlockBold,
            isWholeBlockItalic = isWholeBlockItalic,
            isWholeBlockStrikeThrough = isWholeBlockStrikeThrough,
            isWholeBlockUnderlined = isWholeBlockUnderlined,
            isWholeBlockHighlighted = isWholeBlockHighlighted,
            wholeBlockHighlightColorName = wholeBlockHighlightColorName
        )
        return renderStyledRuns(text, stylePerCharacter)
    }

    fun escapeMarkdown(text: String): String = buildString(text.length) {
        for (index in text.indices) {
            val character = text[index]
            if (character in charactersNeedingEscape || isPartOfDoubledEquals(text, index)) append('\\')
            append(character)
        }
    }

    private fun isPartOfDoubledEquals(text: String, index: Int): Boolean =
        text[index] == '=' && (text.getOrNull(index - 1) == '=' || text.getOrNull(index + 1) == '=')

    private fun buildStylePerCharacter(
        textLength: Int,
        spans: List<InlineSpan>,
        isWholeBlockBold: Boolean,
        isWholeBlockItalic: Boolean,
        isWholeBlockStrikeThrough: Boolean,
        isWholeBlockUnderlined: Boolean,
        isWholeBlockHighlighted: Boolean,
        wholeBlockHighlightColorName: String?
    ): Array<CharacterStyle> {
        val blockWideStyle = CharacterStyle(
            isBold = isWholeBlockBold,
            isItalic = isWholeBlockItalic,
            isStrikeThrough = isWholeBlockStrikeThrough,
            isUnderlined = isWholeBlockUnderlined,
            isHighlighted = isWholeBlockHighlighted,
            highlightColorName = wholeBlockHighlightColorName
        )
        val stylePerCharacter = Array(textLength) { blockWideStyle }

        for (span in spans) {
            val start = span.start.coerceIn(0, textLength)
            val end = span.end.coerceIn(start, textLength)
            for (index in start until end) {
                val existing = stylePerCharacter[index]
                stylePerCharacter[index] = existing.copy(
                    isBold = existing.isBold || span.bold,
                    isItalic = existing.isItalic || span.italic,
                    isStrikeThrough = existing.isStrikeThrough || span.strikeThrough,
                    isUnderlined = existing.isUnderlined || span.underline,
                    isHighlighted = existing.isHighlighted || span.highlight,
                    highlightColorName =
                        if (span.highlight) span.highlightColorName else existing.highlightColorName
                )
            }
        }
        return stylePerCharacter
    }

    private fun renderStyledRuns(text: String, stylePerCharacter: Array<CharacterStyle>): String {
        val output = StringBuilder(text.length + 16)
        var runStart = 0

        while (runStart < text.length) {
            if (text[runStart] == '\n') {
                output.append('\n')
                runStart++
                continue
            }

            var runEnd = runStart
            while (
                runEnd < text.length &&
                text[runEnd] != '\n' &&
                stylePerCharacter[runEnd] == stylePerCharacter[runStart]
            ) {
                runEnd++
            }

            appendStyledSegment(output, text.substring(runStart, runEnd), stylePerCharacter[runStart])
            runStart = runEnd
        }
        return output.toString()
    }

    private fun usesDefaultHighlightColor(highlightColorName: String?): Boolean =
        highlightColorName == null || highlightColorName == defaultHighlightColorName

    private fun highlightOpeningMarkerFor(highlightColorName: String?): String =
        if (usesDefaultHighlightColor(highlightColorName)) HIGHLIGHT_MARKER
        else COLORED_HIGHLIGHT_OPENING_PREFIX + highlightColorName + COLORED_HIGHLIGHT_OPENING_SUFFIX

    private fun highlightClosingMarkerFor(highlightColorName: String?): String =
        if (usesDefaultHighlightColor(highlightColorName)) HIGHLIGHT_MARKER
        else COLORED_HIGHLIGHT_CLOSING_TAG

    private fun appendStyledSegment(output: StringBuilder, segment: String, style: CharacterStyle) {
        if (style.hasNoFormatting) {
            output.append(escapeMarkdown(segment))
            return
        }

        val firstVisibleIndex = segment.indexOfFirst { !it.isWhitespace() }
        if (firstVisibleIndex < 0) {
            output.append(escapeMarkdown(segment))
            return
        }
        val afterLastVisibleIndex = segment.indexOfLast { !it.isWhitespace() } + 1

        output.append(segment.substring(0, firstVisibleIndex))
        if (style.isBold) output.append(BOLD_MARKER)
        if (style.isItalic) output.append(ITALIC_MARKER)
        if (style.isStrikeThrough) output.append(STRIKE_THROUGH_MARKER)
        if (style.isUnderlined) output.append(UNDERLINE_OPENING_TAG)
        if (style.isHighlighted) output.append(highlightOpeningMarkerFor(style.highlightColorName))
        output.append(escapeMarkdown(segment.substring(firstVisibleIndex, afterLastVisibleIndex)))
        if (style.isHighlighted) output.append(highlightClosingMarkerFor(style.highlightColorName))
        if (style.isUnderlined) output.append(UNDERLINE_CLOSING_TAG)
        if (style.isStrikeThrough) output.append(STRIKE_THROUGH_MARKER)
        if (style.isItalic) output.append(ITALIC_MARKER)
        if (style.isBold) output.append(BOLD_MARKER)
        output.append(segment.substring(afterLastVisibleIndex))
    }
}
