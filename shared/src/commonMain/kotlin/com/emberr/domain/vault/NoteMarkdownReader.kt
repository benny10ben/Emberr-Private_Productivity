// Turns a markdown file back into blocks, merged onto the blocks the database already holds.

package com.emberr.domain.vault

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
import com.emberr.domain.model.withUpdatedAt
import com.emberr.domain.model.RecurrenceRule
import kotlinx.datetime.TimeZone

private const val CHECKBOX_MARKER_WIDTH = 6
private const val TOGGLE_MARKER_WIDTH = 4
private const val BULLET_MARKER_WIDTH = 2
private const val NUMBER_SUFFIX_WIDTH = 2

private val imageLinkRegex = Regex("""^!\[([^\]]*)]\((.*)\)$""")
private val wikiLinkRegex = Regex("""^\[\[(.+)]]$""")
private val inlineLinkRegex = Regex("""^\[((?:\\.|[^\\\[\]])*)]\((.*)\)$""")

class VaultNoteReadRequest(
    val markdown: String,
    val existingBlocks: List<NoteBlock>,
    val timestamp: Long,
    val generateBlockId: () -> String,
    val noteIdsByLowercaseTitle: Map<String, String> = emptyMap(),
    val categoryIdsByLowercaseName: Map<String, String> = emptyMap(),
    val timeZone: TimeZone = TimeZone.currentSystemDefault()
)

data class VaultNoteReadResult(
    val frontMatter: VaultFrontMatter,
    val blocks: List<NoteBlock>,
    val problems: List<String>
)

object NoteMarkdownReader {

    fun readNote(request: VaultNoteReadRequest): VaultNoteReadResult {
        val (frontMatter, body) = VaultMarkdownScanner.splitFrontMatter(request.markdown)
        val chunks = VaultMarkdownScanner.scanBody(body)

        val liveExistingBlocks = request.existingBlocks.filter { !it.isDeleted }
        val tagsByBlockId = VaultBlockTags.buildTagsForBlocks(liveExistingBlocks)
        val existingBlocksByTag = liveExistingBlocks.associateBy { tagsByBlockId.getValue(it.id) }

        val claimedTags = mutableSetOf<String>()
        val problems = mutableListOf<String>()
        val blocks = mutableListOf<NoteBlock>()

        for (chunk in chunks) {
            val chunkTag = chunk.tag
            val existingBlock =
                if (chunkTag != null && claimedTags.add(chunkTag)) existingBlocksByTag[chunkTag] else null

            val block = buildBlock(chunk, existingBlock, request, problems)
            if (block != null) blocks.add(block)
        }

        return VaultNoteReadResult(frontMatter, blocks, problems)
    }

    private fun buildBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest,
        problems: MutableList<String>
    ): NoteBlock? = when (chunk.kind) {
        VaultChunkKind.FENCE -> buildFenceBlock(chunk, existing, request)
        VaultChunkKind.TABLE -> buildTableBlock(chunk, existing, request)
        VaultChunkKind.QUOTE -> buildQuoteBlock(chunk, existing, request)
        VaultChunkKind.DIVIDER -> buildDividerBlock(chunk, existing, request)
        VaultChunkKind.HEADING -> buildHeadingBlock(chunk, existing, request)
        VaultChunkKind.LIST -> buildListBlock(chunk, existing, request)
        VaultChunkKind.PARAGRAPH -> buildParagraphBlock(chunk, existing, request, problems)
        VaultChunkKind.EMPTY -> buildEmptyTextBlock(existing, request)
    }

    private fun buildEmptyTextBlock(existing: NoteBlock?, request: VaultNoteReadRequest): NoteBlock {
        val base = existing as? TextBlock ?: TextBlock(id = idFor(existing, request))
        return settle(
            base.copy(
                text = "",
                inlineSpans = emptyList(),
                isBold = false,
                isItalic = false,
                isStrikeThrough = false,
                isUnderlined = false,
                isHighlighted = false
            ),
            existing,
            request.timestamp
        )
    }

    private fun buildHeadingBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val line = chunk.lines.firstOrNull().orEmpty()
        val match = VaultMarkdownScanner.headingRegex.matchEntire(line)
        val level = match?.groupValues?.get(1)?.length ?: 1
        val parsed = InlineMarkdownParser.parse(match?.groupValues?.get(2).orEmpty())

        val base = existing as? HeadingBlock ?: HeadingBlock(id = idFor(existing, request))
        return settle(
            base.copy(
                text = parsed.text,
                level = level.coerceIn(1, VaultFormat.MAX_HEADING_LEVEL),
                inlineSpans = parsed.spans,
                isBold = parsed.isWholeTextBold,
                isItalic = parsed.isWholeTextItalic,
                isStrikeThrough = parsed.isWholeTextStrikeThrough,
                isUnderlined = parsed.isWholeTextUnderlined,
                isHighlighted = parsed.isWholeTextHighlighted,
                highlightColorName = parsed.wholeTextHighlightColorName
            ),
            existing,
            request.timestamp
        )
    }

    private fun buildQuoteBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val rawText = chunk.lines.joinToString("\n") { line ->
            val withoutMarker = line.trimStart().removePrefix(">")
            if (withoutMarker.startsWith(" ")) withoutMarker.drop(1) else withoutMarker
        }
        val parsed = InlineMarkdownParser.parse(rawText)

        val base = existing as? QuoteBlock ?: QuoteBlock(id = idFor(existing, request))
        return settle(
            base.copy(
                text = parsed.text,
                inlineSpans = parsed.spans,
                isBold = parsed.isWholeTextBold,
                isItalic = parsed.isWholeTextItalic,
                isStrikeThrough = parsed.isWholeTextStrikeThrough,
                isUnderlined = parsed.isWholeTextUnderlined,
                isHighlighted = parsed.isWholeTextHighlighted,
                highlightColorName = parsed.wholeTextHighlightColorName
            ),
            existing,
            request.timestamp
        )
    }

    private fun buildDividerBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val line = chunk.lines.firstOrNull()?.trim().orEmpty()
        val isDotDivider = line.contains('*')
        val blockId = idFor(existing, request)

        val rebuilt = if (isDotDivider) {
            (existing as? ThreeDotDividerBlock)?.copy() ?: ThreeDotDividerBlock(id = blockId)
        } else {
            (existing as? SolidDividerBlock)?.copy() ?: SolidDividerBlock(id = blockId)
        }
        return settle(rebuilt, existing, request.timestamp)
    }

    private fun buildListBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val firstLine = chunk.lines.firstOrNull().orEmpty()

        VaultMarkdownScanner.checkboxRegex.matchEntire(firstLine)?.let { match ->
            val indent = match.groupValues[1]
            val joinedText = joinListLines(match.groupValues[3], chunk.lines, indent.length + CHECKBOX_MARKER_WIDTH)
            val split = TaskAttributesFormat.extractFrom(joinedText)
            val parsed = InlineMarkdownParser.parse(split.text)

            val base = existing as? CheckboxBlock ?: CheckboxBlock(id = idFor(existing, request))
            val isChecked = match.groupValues[2].equals("x", ignoreCase = true)

            return settle(
                base.copy(
                    text = parsed.text,
                    isChecked = isChecked,
                    completedAt = resolveCompletedAt(isChecked, base, request),
                    reminderTimestamp = resolveReminder(split.attributes, base, request),
                    durationMinutes = split.attributes.durationMinutes ?: DEFAULT_TASK_DURATION_MINUTES,
                    categoryId = resolveCategoryId(split.attributes, base, request),
                    recurrenceRule = resolveRecurrence(split.attributes, base),
                    url = split.attributes.link,
                    description = split.attributes.details,
                    indentationLevel = indentLevelOf(indent),
                    inlineSpans = parsed.spans,
                    isBold = parsed.isWholeTextBold,
                    isItalic = parsed.isWholeTextItalic,
                    isStrikeThrough = parsed.isWholeTextStrikeThrough,
                    isUnderlined = parsed.isWholeTextUnderlined,
                    isHighlighted = parsed.isWholeTextHighlighted,
                    highlightColorName = parsed.wholeTextHighlightColorName
                ),
                existing,
                request.timestamp
            )
        }

        VaultMarkdownScanner.toggleRegex.matchEntire(firstLine)?.let { match ->
            val indent = match.groupValues[1]
            val parsed = parseListText(match.groupValues[2], chunk.lines, indent.length + TOGGLE_MARKER_WIDTH)
            val base = existing as? ToggleBlock ?: ToggleBlock(id = idFor(existing, request))
            return settle(
                base.copy(
                    text = parsed.text,
                    indentationLevel = indentLevelOf(indent),
                    inlineSpans = parsed.spans,
                    isBold = parsed.isWholeTextBold,
                    isItalic = parsed.isWholeTextItalic,
                    isStrikeThrough = parsed.isWholeTextStrikeThrough,
                    isUnderlined = parsed.isWholeTextUnderlined,
                    isHighlighted = parsed.isWholeTextHighlighted,
                    highlightColorName = parsed.wholeTextHighlightColorName
                ),
                existing,
                request.timestamp
            )
        }

        VaultMarkdownScanner.numberedRegex.matchEntire(firstLine)?.let { match ->
            val indent = match.groupValues[1]
            val numberText = match.groupValues[2]
            val markerWidth = indent.length + numberText.length + NUMBER_SUFFIX_WIDTH
            val parsed = parseListText(match.groupValues[3], chunk.lines, markerWidth)
            val base = existing as? NumberedListBlock ?: NumberedListBlock(id = idFor(existing, request))
            return settle(
                base.copy(
                    text = parsed.text,
                    number = numberText.toIntOrNull() ?: 1,
                    indentationLevel = indentLevelOf(indent),
                    inlineSpans = parsed.spans,
                    isBold = parsed.isWholeTextBold,
                    isItalic = parsed.isWholeTextItalic,
                    isStrikeThrough = parsed.isWholeTextStrikeThrough,
                    isUnderlined = parsed.isWholeTextUnderlined,
                    isHighlighted = parsed.isWholeTextHighlighted,
                    highlightColorName = parsed.wholeTextHighlightColorName
                ),
                existing,
                request.timestamp
            )
        }

        VaultMarkdownScanner.bulletRegex.matchEntire(firstLine)?.let { match ->
            val indent = match.groupValues[1]
            val parsed = parseListText(match.groupValues[2], chunk.lines, indent.length + BULLET_MARKER_WIDTH)
            val base = existing as? BulletedListBlock ?: BulletedListBlock(id = idFor(existing, request))
            return settle(
                base.copy(
                    text = parsed.text,
                    indentationLevel = indentLevelOf(indent),
                    inlineSpans = parsed.spans,
                    isBold = parsed.isWholeTextBold,
                    isItalic = parsed.isWholeTextItalic,
                    isStrikeThrough = parsed.isWholeTextStrikeThrough,
                    isUnderlined = parsed.isWholeTextUnderlined,
                    isHighlighted = parsed.isWholeTextHighlighted,
                    highlightColorName = parsed.wholeTextHighlightColorName
                ),
                existing,
                request.timestamp
            )
        }

        return buildPlainTextBlock(chunk.lines.joinToString("\n"), existing, request)
    }

    private fun buildParagraphBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest,
        problems: MutableList<String>
    ): NoteBlock? {
        val rawText = chunk.lines.joinToString("\n")
        val singleLine = rawText.trim()

        imageLinkRegex.matchEntire(singleLine)?.let { match ->
            return buildImageBlock(match.groupValues[2], existing, request)
        }
        wikiLinkRegex.matchEntire(singleLine)?.let { match ->
            return buildLinkedNoteBlock(match.groupValues[1], existing, request, problems)
        }
        inlineLinkRegex.matchEntire(singleLine)?.let { match ->
            return buildBookmarkOrDocumentBlock(match.groupValues[1], match.groupValues[2], existing, request)
        }

        return buildPlainTextBlock(rawText, existing, request)
    }

    private fun buildPlainTextBlock(
        rawText: String,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val parsed = InlineMarkdownParser.parse(rawText)
        val base = existing as? TextBlock ?: TextBlock(id = idFor(existing, request))
        return settle(
            base.copy(
                text = parsed.text,
                inlineSpans = parsed.spans,
                isBold = parsed.isWholeTextBold,
                isItalic = parsed.isWholeTextItalic,
                isStrikeThrough = parsed.isWholeTextStrikeThrough,
                isUnderlined = parsed.isWholeTextUnderlined,
                isHighlighted = parsed.isWholeTextHighlighted,
                highlightColorName = parsed.wholeTextHighlightColorName
            ),
            existing,
            request.timestamp
        )
    }

    private fun buildImageBlock(
        linkTarget: String,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val fileName = mediaFileNameOf(linkTarget)
        val base = existing as? ImageBlock ?: ImageBlock(id = idFor(existing, request))
        return settle(
            base.copy(localFilePath = fileName ?: base.localFilePath),
            existing,
            request.timestamp
        )
    }

    private fun buildLinkedNoteBlock(
        linkedTitle: String,
        existing: NoteBlock?,
        request: VaultNoteReadRequest,
        problems: MutableList<String>
    ): NoteBlock? {
        val title = linkedTitle.trim()
        val resolvedNoteId = request.noteIdsByLowercaseTitle[title.lowercase()]
            ?: (existing as? LinkedNoteBlock)?.linkedNoteId

        if (resolvedNoteId == null) {
            problems.add("No note named \"$title\" exists, so its [[link]] was dropped")
            return null
        }

        val base = existing as? LinkedNoteBlock
            ?: LinkedNoteBlock(id = idFor(existing, request), linkedNoteId = resolvedNoteId)
        return settle(base.copy(linkedNoteId = resolvedNoteId), existing, request.timestamp)
    }

    private fun buildBookmarkOrDocumentBlock(
        linkLabel: String,
        linkTarget: String,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val label = unescapeLinkLabel(linkLabel)
        val target = unwrapLinkTarget(linkTarget)
        val isWebAddress = target.startsWith("http://", ignoreCase = true) ||
            target.startsWith("https://", ignoreCase = true)

        if (isWebAddress) {
            val base = existing as? BookmarkBlock ?: BookmarkBlock(id = idFor(existing, request))
            val title = if (label == target || label.isBlank()) null else label
            return settle(base.copy(url = target, title = title), existing, request.timestamp)
        }

        val base = existing as? DocumentBlock ?: DocumentBlock(id = idFor(existing, request))
        val fileName = mediaFileNameOf(linkTarget)
        return settle(
            base.copy(
                fileName = label.ifBlank { base.fileName },
                localFilePath = fileName ?: base.localFilePath
            ),
            existing,
            request.timestamp
        )
    }

    private fun buildFenceBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock = when (chunk.fenceInfo) {
        VaultFormat.VOICE_FENCE_NAME -> buildVoiceBlock(chunk, existing, request)
        VaultFormat.CANVAS_FENCE_NAME -> buildCanvasBlock(chunk, existing, request)
        else -> buildCodeBlock(chunk, existing, request)
    }

    private fun buildCodeBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val base = existing as? CodeBlock ?: CodeBlock(id = idFor(existing, request))
        val language = chunk.fenceInfo?.takeIf { it.isNotBlank() } ?: VaultFormat.DEFAULT_CODE_LANGUAGE
        return settle(
            base.copy(code = chunk.lines.joinToString("\n"), language = language),
            existing,
            request.timestamp
        )
    }

    private fun buildVoiceBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val fields = VaultMarkdownScanner.parseKeyValueLines(chunk.lines)
        val base = existing as? VoiceBlock ?: VoiceBlock(id = idFor(existing, request))
        return settle(
            base.copy(
                localFilePath = mediaFileNameOf(fields["file"].orEmpty()) ?: base.localFilePath,
                durationSeconds = fields["seconds"]?.toIntOrNull() ?: base.durationSeconds
            ),
            existing,
            request.timestamp
        )
    }

    private fun buildCanvasBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val fields = VaultMarkdownScanner.parseKeyValueLines(chunk.lines)
        val canvasNoteId = fields["note"]?.takeIf { it.isNotBlank() }
            ?: (existing as? CanvasBlock)?.canvasNoteId
            ?: return buildCodeBlock(chunk, existing, request)
        val base = existing as? CanvasBlock ?: CanvasBlock(id = idFor(existing, request), canvasNoteId = canvasNoteId)
        return settle(base.copy(canvasNoteId = canvasNoteId), existing, request.timestamp)
    }

    private fun buildTableBlock(
        chunk: VaultMarkdownChunk,
        existing: NoteBlock?,
        request: VaultNoteReadRequest
    ): NoteBlock {
        val rows = chunk.lines
            .filterNot { VaultMarkdownScanner.isSeparatorTableRow(it) }
            .map { line -> VaultMarkdownScanner.splitTableCells(line).map { unescapeTableCell(it.trim()) } }

        val base = existing as? TableBlock ?: TableBlock(id = idFor(existing, request))
        return settle(base.copy(rows = rows), existing, request.timestamp)
    }

    private fun settle(rebuilt: NoteBlock, existing: NoteBlock?, timestamp: Long): NoteBlock {
        if (existing == null) return rebuilt.withUpdatedAt(timestamp)
        val comparable = rebuilt.withUpdatedAt(existing.updatedAt)
        return if (comparable == existing) existing else rebuilt.withUpdatedAt(timestamp)
    }

    private fun parseListText(
        firstLineText: String,
        allLines: List<String>,
        markerWidth: Int
    ): ParsedInlineText = InlineMarkdownParser.parse(joinListLines(firstLineText, allLines, markerWidth))

    private fun joinListLines(firstLineText: String, allLines: List<String>, markerWidth: Int): String {
        if (allLines.size <= 1) return firstLineText

        val continuationLines = allLines.drop(1).map { line -> dropLeadingSpaces(line, markerWidth) }
        return (listOf(firstLineText) + continuationLines).joinToString("\n")
    }

    private fun resolveCompletedAt(
        isChecked: Boolean,
        base: CheckboxBlock,
        request: VaultNoteReadRequest
    ): Long? = when {
        !isChecked -> null
        base.completedAt != null -> base.completedAt
        else -> request.timestamp
    }

    // A due time is written to the minute, so comparing as text first keeps the exact value.
    private fun resolveReminder(
        attributes: TaskAttributes,
        base: CheckboxBlock,
        request: VaultNoteReadRequest
    ): Long? {
        val dueText = attributes.dueText ?: return null
        val existingReminder = base.reminderTimestamp

        if (existingReminder != null &&
            TaskDueTimeFormat.render(existingReminder, request.timeZone) == dueText
        ) {
            return existingReminder
        }
        return TaskDueTimeFormat.parse(dueText, request.timeZone) ?: existingReminder
    }

    private fun resolveCategoryId(
        attributes: TaskAttributes,
        base: CheckboxBlock,
        request: VaultNoteReadRequest
    ): String? {
        val categoryName = attributes.categoryName ?: return null
        return request.categoryIdsByLowercaseName[categoryName.lowercase()] ?: base.categoryId
    }

    private fun resolveRecurrence(attributes: TaskAttributes, base: CheckboxBlock): RecurrenceRule? {
        val repeatText = attributes.repeatText ?: return null
        return TaskRepeatFormat.parse(repeatText) ?: base.recurrenceRule
    }

    private fun dropLeadingSpaces(line: String, maximumToDrop: Int): String {
        var dropped = 0
        var index = 0
        while (index < line.length && dropped < maximumToDrop && line[index] == ' ') {
            index++
            dropped++
        }
        return line.substring(index)
    }

    private fun indentLevelOf(indent: String): Int =
        (indent.length / VaultFormat.SPACES_PER_INDENT_LEVEL).coerceIn(0, VaultFormat.MAX_INDENT_LEVELS)

    private fun idFor(existing: NoteBlock?, request: VaultNoteReadRequest): String =
        existing?.id ?: request.generateBlockId()

    private fun mediaFileNameOf(linkTarget: String): String? = unwrapLinkTarget(linkTarget)
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .takeIf { it.isNotBlank() }

    private fun unwrapLinkTarget(linkTarget: String): String {
        val trimmed = linkTarget.trim()
        return if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            trimmed.substring(1, trimmed.length - 1).replace("%3E", ">")
        } else {
            trimmed
        }
    }

    private fun unescapeLinkLabel(label: String): String {
        val output = StringBuilder(label.length)
        var index = 0
        while (index < label.length) {
            val character = label[index]
            if (character == '\\' && index + 1 < label.length) {
                output.append(label[index + 1])
                index += 2
            } else {
                output.append(character)
                index++
            }
        }
        return output.toString()
    }

    private fun unescapeTableCell(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                output.append(value[index + 1])
                index += 2
            } else {
                output.append(character)
                index++
            }
        }
        return output.toString().replace("<br>", "\n")
    }
}
