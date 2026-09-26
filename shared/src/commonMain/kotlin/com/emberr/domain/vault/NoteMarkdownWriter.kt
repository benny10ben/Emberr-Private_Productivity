// Turns a note's blocks into markdown, either for the vault mirror or for a plain shared export.

package com.emberr.domain.vault

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CanvasBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
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
import kotlinx.datetime.TimeZone
import com.emberr.domain.model.highlightColorNameOrNull
import com.emberr.domain.model.inlineSpansOrEmpty

enum class VaultMarkdownProfile { VAULT, SHARED }

data class VaultNoteWriteRequest(
    val metadata: NoteMetadataEntity,
    val blocks: List<NoteBlock>,
    val noteTitlesById: Map<String, String> = emptyMap(),
    val categoryNamesById: Map<String, String> = emptyMap(),
    val mediaPathPrefix: String = "../media/",
    val timeZone: TimeZone = TimeZone.currentSystemDefault()
)

private data class RenderOptions(
    val profile: VaultMarkdownProfile,
    val noteTitlesById: Map<String, String>,
    val categoryNamesById: Map<String, String>,
    val mediaPathPrefix: String,
    val timeZone: TimeZone
) {
    val isVault: Boolean get() = profile == VaultMarkdownProfile.VAULT
}

object NoteMarkdownWriter {

    fun writeNote(request: VaultNoteWriteRequest): String {
        val visibleBlocks = request.blocks.filter { !it.isDeleted }
        val tagsByBlockId = VaultBlockTags.buildTagsForBlocks(visibleBlocks)
        val options = RenderOptions(
            profile = VaultMarkdownProfile.VAULT,
            noteTitlesById = request.noteTitlesById,
            categoryNamesById = request.categoryNamesById,
            mediaPathPrefix = request.mediaPathPrefix,
            timeZone = request.timeZone
        )

        val body = joinRenderedBlocks(visibleBlocks) { block ->
            renderBlock(block, tagsByBlockId.getValue(block.id), options)
        }

        return buildString {
            append(renderFrontMatter(request.metadata))
            append('\n')
            if (body.isNotEmpty()) {
                append(body)
                append('\n')
            }
        }
    }

    fun writeSharedMarkdown(
        blocks: List<NoteBlock>,
        title: String? = null,
        noteTitlesById: Map<String, String> = emptyMap(),
        categoryNamesById: Map<String, String> = emptyMap()
    ): String {
        val visibleBlocks = blocks.filter { !it.isDeleted }
        val options = RenderOptions(
            profile = VaultMarkdownProfile.SHARED,
            noteTitlesById = noteTitlesById,
            categoryNamesById = categoryNamesById,
            mediaPathPrefix = "",
            timeZone = TimeZone.currentSystemDefault()
        )

        val body = joinRenderedBlocks(visibleBlocks) { block -> renderBlock(block, null, options) }

        return buildString {
            if (!title.isNullOrBlank()) {
                append("# ")
                append(flattenLineBreaks(title).trim())
                append("\n\n")
            }
            append(body)
        }.trim()
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

    private fun renderFrontMatter(metadata: NoteMetadataEntity): String = buildString {
        appendLine("---")
        appendLine("id: ${metadata.noteId}")
        appendLine("title: ${yamlScalar(metadata.title)}")
        appendLine("created: ${metadata.createdAt}")
        appendLine("updated: ${metadata.updatedAt}")

        val icon = metadata.icon
        if (!icon.isNullOrBlank()) appendLine("icon: ${yamlScalar(icon)}")
        if (metadata.isFavorite) appendLine("favorite: true")
        if (metadata.isDaily) {
            appendLine("daily: true")
            val dateString = metadata.dateString
            if (!dateString.isNullOrBlank()) appendLine("date: ${yamlScalar(dateString)}")
        }
        appendLine("---")
    }

    private fun renderBlock(block: NoteBlock, tag: String?, options: RenderOptions): String {
        val indent = listIndentFor(block)
        return when (block) {
            is TextBlock -> withInlineTag(formatText(block, block.text), tag)
            is HeadingBlock -> renderHeading(block, tag)
            is QuoteBlock -> renderQuote(block, tag)
            is CheckboxBlock -> renderListItem(
                block = block,
                text = block.text,
                marker = if (block.isChecked) VaultFormat.CHECKED_MARKER else VaultFormat.UNCHECKED_MARKER,
                indent = indent,
                tag = tag,
                trailingGroup = renderTaskAttributes(block, options)
            )
            is BulletedListBlock -> renderListItem(block, block.text, VaultFormat.BULLET_MARKER, indent, tag)
            is NumberedListBlock -> renderListItem(block, block.text, "${block.number}. ", indent, tag)
            is ToggleBlock -> renderListItem(block, block.text, VaultFormat.TOGGLE_MARKER, indent, tag)
            is CodeBlock -> renderCode(block, tag)
            is BookmarkBlock -> renderBookmark(block, tag)
            is LinkedNoteBlock -> renderLinkedNote(block, tag, options)
            is ImageBlock -> renderImage(block, tag, options)
            is DocumentBlock -> renderDocument(block, tag, options)
            is VoiceBlock -> renderVoice(block, tag, options)
            is CanvasBlock -> renderCanvas(block, tag, options)
            is TableBlock -> renderTable(block, tag)
            is SolidDividerBlock -> withTagOnItsOwnLine(VaultFormat.SOLID_DIVIDER_LINE, tag)
            is ThreeDotDividerBlock -> withTagOnItsOwnLine(VaultFormat.DOT_DIVIDER_LINE, tag)
        }
    }

    private fun renderHeading(block: HeadingBlock, tag: String?): String {
        val level = block.level.coerceIn(1, VaultFormat.MAX_HEADING_LEVEL)
        val formattedText = formatText(block, flattenLineBreaks(block.text))
        return withInlineTag(("#".repeat(level) + " " + formattedText).trimEnd(), tag)
    }

    private fun renderQuote(block: QuoteBlock, tag: String?): String {
        val formattedText = formatText(block, block.text)
        val quotedLines = formattedText
            .split('\n')
            .joinToString("\n") { line -> "> $line".trimEnd() }
        return withInlineTag(quotedLines, tag)
    }

    private fun renderListItem(
        block: NoteBlock,
        text: String,
        marker: String,
        indent: String,
        tag: String?,
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

        val withGroup = if (trailingGroup == null) renderedLines else "$renderedLines $trailingGroup"
        return withInlineTag(withGroup, tag)
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

    private fun renderCode(block: CodeBlock, tag: String?): String {
        val fence = "`".repeat(longestBacktickRun(block.code).coerceAtLeast(2) + 1)
        val language = if (block.language.isBlank() || block.language == VaultFormat.DEFAULT_CODE_LANGUAGE) {
            ""
        } else {
            block.language
        }

        val fencedCode = buildString {
            append(fence)
            append(language)
            append('\n')
            if (block.code.isNotEmpty()) {
                append(block.code)
                append('\n')
            }
            append(fence)
        }
        return withTagOnItsOwnLine(fencedCode, tag)
    }

    private fun renderBookmark(block: BookmarkBlock, tag: String?): String {
        val label = block.title?.takeIf { it.isNotBlank() } ?: block.url.ifBlank { "Bookmark" }
        return withInlineTag("[${escapeLinkLabel(label)}](${escapeLinkTarget(block.url)})", tag)
    }

    private fun renderLinkedNote(block: LinkedNoteBlock, tag: String?, options: RenderOptions): String {
        val title = options.noteTitlesById[block.linkedNoteId]?.takeIf { it.isNotBlank() }
        if (title == null && !options.isVault) return ""

        val safeTitle = flattenLineBreaks(title ?: "Untitled")
            .replace("[", "")
            .replace("]", "")
            .trim()
        return withInlineTag("[[${safeTitle.ifEmpty { "Untitled" }}]]", tag)
    }

    private fun renderImage(block: ImageBlock, tag: String?, options: RenderOptions): String {
        val target = mediaLinkTargetFor(block.localFilePath, options.mediaPathPrefix)
        return withInlineTag("![]($target)", tag)
    }

    private fun renderDocument(block: DocumentBlock, tag: String?, options: RenderOptions): String {
        val target = mediaLinkTargetFor(block.localFilePath, options.mediaPathPrefix)
        val label = escapeLinkLabel(block.fileName.ifBlank { "Document" })
        return withInlineTag("[$label]($target)", tag)
    }

    private fun renderVoice(block: VoiceBlock, tag: String?, options: RenderOptions): String {
        val fileName = mediaFileNameOf(block.localFilePath)
        val fence = buildString {
            appendLine("```${VaultFormat.VOICE_FENCE_NAME}")
            if (fileName != null) appendLine("file: ${options.mediaPathPrefix}$fileName")
            appendLine("seconds: ${block.durationSeconds}")
            append("```")
        }
        return withTagOnItsOwnLine(fence, tag)
    }

    private fun renderCanvas(block: CanvasBlock, tag: String?, options: RenderOptions): String {
        if (!options.isVault) return ""
        val fence = buildString {
            appendLine("```${VaultFormat.CANVAS_FENCE_NAME}")
            appendLine("note: ${block.canvasNoteId}")
            append("```")
        }
        return withTagOnItsOwnLine(fence, tag)
    }

    private fun renderTable(block: TableBlock, tag: String?): String {
        if (block.rows.isEmpty()) return if (tag == null) "" else VaultBlockTags.renderTag(tag)

        val columnCount = block.rows.maxOf { it.size }.coerceAtLeast(1)
        val lines = mutableListOf<String>()

        block.rows.forEachIndexed { rowIndex, cells ->
            lines.add(renderTableRow(cells, columnCount))
            if (rowIndex == 0) lines.add(renderTableSeparator(columnCount))
        }
        return withTagOnItsOwnLine(lines.joinToString("\n"), tag)
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

    private fun withInlineTag(content: String, tag: String?): String = when {
        tag == null -> content
        content.isEmpty() -> VaultBlockTags.renderTag(tag)
        else -> "$content ${VaultBlockTags.renderTag(tag)}"
    }

    private fun withTagOnItsOwnLine(content: String, tag: String?): String =
        if (tag == null) content else "$content\n${VaultBlockTags.renderTag(tag)}"

    private fun listIndentFor(block: NoteBlock): String =
        " ".repeat(VaultFormat.SPACES_PER_INDENT_LEVEL)
            .repeat(block.indentationLevel.coerceIn(0, VaultFormat.MAX_INDENT_LEVELS))

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

    private fun mediaLinkTargetFor(localFilePath: String?, mediaPathPrefix: String): String {
        val fileName = mediaFileNameOf(localFilePath) ?: return ""
        return escapeLinkTarget(mediaPathPrefix + fileName)
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

    private fun yamlScalar(rawText: String): String {
        val singleLine = flattenLineBreaks(rawText)
        val needsQuotes = singleLine.isEmpty() ||
            singleLine != singleLine.trim() ||
            singleLine.any { it == ':' || it == '#' || it == '"' || it == '\'' || it == '\\' }
        if (!needsQuotes) return singleLine

        val escaped = singleLine.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }
}
