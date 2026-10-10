package com.emberr.domain.util.export

import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CalloutBlock
import com.emberr.domain.model.CanvasBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ThreeDotDividerBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.UnknownBlock
import com.emberr.domain.model.VoiceBlock
import com.emberr.domain.model.highlightColorNameOrNull
import com.emberr.domain.model.inlineSpansOrEmpty
import com.emberr.domain.model.valueAsText
import kotlinx.datetime.TimeZone

private const val VOICE_FENCE_NAME = "emberr-voice"
private const val TOGGLE_MARKER = "- ▸ "
private const val CHECKED_MARKER = "- [x] "
private const val UNCHECKED_MARKER = "- [ ] "
private const val BULLET_MARKER = "- "
private const val SOLID_DIVIDER_LINE = "---"
private const val DOT_DIVIDER_LINE = "* * *"
private const val DEFAULT_CODE_LANGUAGE = "plaintext"
private const val MAX_HEADING_LEVEL = 6
private const val MAX_INDENT_LEVELS = 10
private const val SPACES_PER_INDENT_LEVEL = 2

private data class RenderOptions(
    val noteTitlesById: Map<String, String>,
    val categoryNamesById: Map<String, String>,
    val timeZone: TimeZone
)

object NoteMarkdownWriter {

    fun writeMarkdown(
        blocks: List<NoteBlock>,
        title: String? = null,
        noteTitlesById: Map<String, String> = emptyMap(),
        categoryNamesById: Map<String, String> = emptyMap()
    ): String {
        val visibleBlocks = blocks.filter { !it.isDeleted }
        val options = RenderOptions(
            noteTitlesById = noteTitlesById,
            categoryNamesById = categoryNamesById,
            timeZone = TimeZone.currentSystemDefault()
        )

        val body = renderBlocks(visibleBlocks, options, indentationToIgnore = 0)

        return buildString {
            if (!title.isNullOrBlank()) {
                append("# ")
                append(flattenLineBreaks(title).trim())
                append("\n\n")
            }
            append(body)
        }.trim()
    }

    private fun renderBlocks(blocks: List<NoteBlock>, options: RenderOptions, indentationToIgnore: Int): String {
        val outerBlocks = mutableListOf<NoteBlock>()
        val calloutBodies = mutableMapOf<String, List<NoteBlock>>()
        var index = 0
        while (index < blocks.size) {
            val block = blocks[index]
            outerBlocks.add(block)
            index++
            if (block is CalloutBlock) {
                val body = blocks.drop(index).takeWhile { it.indentationLevel > block.indentationLevel }
                calloutBodies[block.id] = body
                index += body.size
            }
        }

        return joinRenderedBlocks(outerBlocks) { block ->
            val rendered = renderBlock(block, options, indentationToIgnore)
            if (block is CalloutBlock) {
                quoteCallout(block, rendered, calloutBodies.getValue(block.id), options, indentationToIgnore)
            } else {
                rendered
            }
        }
    }

    private fun quoteCallout(
        callout: CalloutBlock,
        renderedHeader: String,
        body: List<NoteBlock>,
        options: RenderOptions,
        indentationToIgnore: Int
    ): String {
        val renderedBody = renderBlocks(body, options, indentationToIgnore = callout.indentationLevel + 1)
        val calloutText = if (renderedBody.isEmpty()) renderedHeader else "$renderedHeader\n$renderedBody"
        val indent = listIndentFor(callout, indentationToIgnore)
        return calloutText
            .split('\n')
            .joinToString("\n") { line -> "$indent> $line".trimEnd() }
    }

    private fun joinRenderedBlocks(
        blocks: List<NoteBlock>,
        render: (NoteBlock) -> String
    ): String {
        val output = StringBuilder()
        var previousBlock: NoteBlock? = null

        for (block in blocks) {
            val rendered = render(block)
            if (rendered.isEmpty()) continue

            if (output.isNotEmpty()) {
                val staysTight = previousBlock != null &&
                    isListStyleBlock(previousBlock) &&
                    isListStyleBlock(block)
                output.append(if (staysTight) "\n" else "\n\n")
            }
            output.append(rendered)
            previousBlock = block
        }
        return output.toString()
    }

    private fun isListStyleBlock(block: NoteBlock): Boolean = when (block) {
        is CheckboxBlock, is BulletedListBlock, is NumberedListBlock, is ToggleBlock -> true
        else -> false
    }

    private fun renderBlock(block: NoteBlock, options: RenderOptions, indentationToIgnore: Int): String {
        val indent = listIndentFor(block, indentationToIgnore)
        return when (block) {
            is TextBlock -> formatText(block, block.text)
            is HeadingBlock -> renderHeading(block)
            is QuoteBlock -> renderQuote(block)
            is CheckboxBlock -> renderListItem(
                block = block,
                text = block.text,
                marker = if (block.isChecked) CHECKED_MARKER else UNCHECKED_MARKER,
                indent = indent,
                trailingGroup = renderTaskAttributes(block, options)
            )
            is BulletedListBlock -> renderListItem(block, block.text, BULLET_MARKER, indent)
            is NumberedListBlock -> renderListItem(block, block.text, "${block.number}. ", indent)
            is ToggleBlock -> renderListItem(block, block.text, TOGGLE_MARKER, indent)
            is CalloutBlock -> renderCalloutHeader(block)
            is CodeBlock -> renderCode(block)
            is BookmarkBlock -> renderBookmark(block)
            is LinkedNoteBlock -> renderLinkedNote(block, options)
            is ImageBlock -> "![](${mediaLinkTargetFor(block.localFilePath)})"
            is DocumentBlock -> renderDocument(block)
            is VoiceBlock -> renderVoice(block)
            is CanvasBlock -> ""
            is PropertyBlock -> renderProperty(block)
            is DatabaseBlock -> ""
            is TableBlock -> renderTable(block)
            is SolidDividerBlock -> SOLID_DIVIDER_LINE
            is ThreeDotDividerBlock -> DOT_DIVIDER_LINE
            is UnknownBlock -> ""
        }
    }

    private fun renderHeading(block: HeadingBlock): String {
        val level = block.level.coerceIn(1, MAX_HEADING_LEVEL)
        val formattedText = formatText(block, flattenLineBreaks(block.text))
        return ("#".repeat(level) + " " + formattedText).trimEnd()
    }

    private fun renderQuote(block: QuoteBlock): String =
        formatText(block, block.text)
            .split('\n')
            .joinToString("\n") { line -> "> $line".trimEnd() }

    private fun renderCalloutHeader(block: CalloutBlock): String {
        val foldMarker = when {
            !block.isFoldable -> ""
            block.isExpanded -> "+"
            else -> "-"
        }
        val formattedTitle = formatText(block, flattenLineBreaks(block.text))
        return "[!${block.calloutTypeName.lowercase()}]$foldMarker $formattedTitle".trimEnd()
    }

    private fun renderListItem(
        block: NoteBlock,
        text: String,
        marker: String,
        indent: String,
        trailingGroup: String? = null
    ): String {
        val formattedText = formatText(block, text)
        val continuationIndent = indent + " ".repeat(marker.length)

        val renderedLines = formattedText
            .split('\n')
            .mapIndexed { lineIndex, line ->
                if (lineIndex == 0) "$indent$marker$line".trimEnd()
                else "$continuationIndent$line".trimEnd()
            }
            .joinToString("\n")

        return if (trailingGroup == null) renderedLines else "$renderedLines $trailingGroup"
    }

    private fun renderTaskAttributes(block: CheckboxBlock, options: RenderOptions): String? =
        TaskAttributesFormat.render(
            TaskAttributes(
                dueText = block.reminderTimestamp?.let { TaskDueTimeFormat.render(it, options.timeZone) },
                durationMinutes = block.durationMinutes.takeIf { it != DEFAULT_TASK_DURATION_MINUTES },
                categoryName = block.categoryId?.let { options.categoryNamesById[it] },
                repeatText = block.recurrenceRule?.let { TaskRepeatFormat.render(it) },
                link = block.url?.takeIf { it.isNotBlank() },
                details = block.description?.takeIf { it.isNotBlank() }
            )
        )

    private fun renderCode(block: CodeBlock): String {
        val fence = "`".repeat(longestBacktickRun(block.code).coerceAtLeast(2) + 1)
        val language = if (block.language.isBlank() || block.language == DEFAULT_CODE_LANGUAGE) {
            ""
        } else {
            block.language
        }

        return buildString {
            append(fence)
            append(language)
            append('\n')
            if (block.code.isNotEmpty()) {
                append(block.code)
                append('\n')
            }
            append(fence)
        }
    }

    private fun renderBookmark(block: BookmarkBlock): String {
        val label = block.title?.takeIf { it.isNotBlank() } ?: block.url.ifBlank { "Bookmark" }
        return "[${escapeLinkLabel(label)}](${escapeLinkTarget(block.url)})"
    }

    private fun renderLinkedNote(block: LinkedNoteBlock, options: RenderOptions): String {
        val title = options.noteTitlesById[block.linkedNoteId]?.takeIf { it.isNotBlank() } ?: return ""

        val safeTitle = flattenLineBreaks(title)
            .replace("[", "")
            .replace("]", "")
            .trim()
        return "[[${safeTitle.ifEmpty { "Untitled" }}]]"
    }

    private fun renderDocument(block: DocumentBlock): String {
        val label = escapeLinkLabel(block.fileName.ifBlank { "Document" })
        return "[$label](${mediaLinkTargetFor(block.localFilePath)})"
    }

    private fun renderVoice(block: VoiceBlock): String {
        val fileName = mediaFileNameOf(block.localFilePath)
        return buildString {
            appendLine("```$VOICE_FENCE_NAME")
            if (fileName != null) appendLine("file: $fileName")
            appendLine("seconds: ${block.durationSeconds}")
            append("```")
        }
    }

    private fun renderProperty(block: PropertyBlock): String {
        val value = flattenLineBreaks(block.valueAsText())
        return if (value.isBlank()) "" else "**${flattenLineBreaks(block.label)}:** $value"
    }

    private fun renderTable(block: TableBlock): String {
        if (block.rows.isEmpty()) return ""

        val columnCount = block.rows.maxOf { it.size }.coerceAtLeast(1)
        val lines = mutableListOf<String>()

        block.rows.forEachIndexed { rowIndex, cells ->
            lines.add(renderTableRow(cells, columnCount))
            if (rowIndex == 0) lines.add(renderTableSeparator(columnCount))
        }
        return lines.joinToString("\n")
    }

    private fun renderTableRow(cells: List<String>, columnCount: Int): String =
        (0 until columnCount).joinToString(" | ", prefix = "| ", postfix = " |") { columnIndex ->
            escapeTableCell(cells.getOrNull(columnIndex).orEmpty())
        }

    private fun renderTableSeparator(columnCount: Int): String =
        (0 until columnCount).joinToString(" | ", prefix = "| ", postfix = " |") { "---" }

    private fun escapeTableCell(value: String): String = value
        .replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\r", "")
        .replace("\n", "<br>")

    private fun formatText(block: NoteBlock, text: String): String =
        InlineMarkdownFormatter.toMarkdown(
            text = text,
            spans = block.inlineSpansOrEmpty(),
            isWholeBlockBold = block.isBold,
            isWholeBlockItalic = block.isItalic,
            isWholeBlockStrikeThrough = block.isStrikeThrough,
            isWholeBlockUnderlined = block.isUnderlined,
            isWholeBlockHighlighted = block.isHighlighted,
            wholeBlockHighlightColorName = block.highlightColorNameOrNull()
        )

    private fun listIndentFor(block: NoteBlock, indentationToIgnore: Int): String =
        " ".repeat(SPACES_PER_INDENT_LEVEL)
            .repeat((block.indentationLevel - indentationToIgnore).coerceIn(0, MAX_INDENT_LEVELS))

    private fun flattenLineBreaks(text: String): String = buildString(text.length) {
        for (character in text) {
            if (character == '\n' || character == '\r') append(' ') else append(character)
        }
    }

    private fun longestBacktickRun(text: String): Int {
        var longestRun = 0
        var currentRun = 0
        for (character in text) {
            if (character == '`') {
                currentRun++
                if (currentRun > longestRun) longestRun = currentRun
            } else {
                currentRun = 0
            }
        }
        return longestRun
    }

    private fun mediaFileNameOf(localFilePath: String?): String? = localFilePath
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.takeIf { it.isNotBlank() }

    private fun mediaLinkTargetFor(localFilePath: String?): String {
        val fileName = mediaFileNameOf(localFilePath) ?: return ""
        return escapeLinkTarget(fileName)
    }

    private fun escapeLinkLabel(label: String): String = flattenLineBreaks(label)
        .replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")

    private fun escapeLinkTarget(target: String): String {
        val singleLine = flattenLineBreaks(target).trim()
        if (singleLine.isEmpty()) return ""
        val needsAngleBrackets = singleLine.any { it == ' ' || it == '(' || it == ')' }
        return if (needsAngleBrackets) "<${singleLine.replace(">", "%3E")}>" else singleLine
    }
}
