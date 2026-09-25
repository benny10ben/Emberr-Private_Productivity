// The drawable pieces of a note - text lines, dividers and table records - and how blocks become them.
package com.emberr.presentation.widget.note

import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
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
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ThreeDotDividerBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.VoiceBlock
import com.emberr.domain.model.displayText
import com.emberr.domain.model.highlightColorNameOrNull
import com.emberr.domain.model.inlineSpansOrEmpty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WidgetTextStyleName { HEADING, SUBHEADING, BODY, QUOTE, CODE, SUBTLE }

@Serializable
sealed interface WidgetElement {
    val key: String

    @Serializable
    @SerialName("text")
    data class TextLine(
        override val key: String,
        val text: String,
        val style: WidgetTextStyleName,
        val indentationLevel: Int,
        val isStruckThrough: Boolean,
        val isHighlighted: Boolean = false,
        val highlightColorName: String? = null,
        val highlightedRanges: List<HighlightedRange> = emptyList()
    ) : WidgetElement

    @Serializable
    @SerialName("divider")
    data class DividerLine(override val key: String) : WidgetElement

    @Serializable
    @SerialName("record")
    data class Record(
        override val key: String,
        val fields: List<RecordField>,
        val indentationLevel: Int
    ) : WidgetElement
}

@Serializable
data class RecordField(
    val label: String,
    val value: String
)

@Serializable
data class HighlightedRange(
    val start: Int,
    val end: Int,
    val colorName: String? = null
)

private const val maximumRenderCost = 120
private const val maximumCharactersPerLine = 300
private const val maximumRecords = 8
private const val maximumFieldsPerRecord = 6
private const val maximumCharactersPerLabel = 40
private const val maximumCharactersPerValue = 120

fun buildElementsFromBlocks(
    blocks: List<NoteBlock>,
    linkedNoteTitles: Map<String, String>
): List<WidgetElement> {
    val collected = mutableListOf<WidgetElement>()
    var spentRenderCost = 0

    for (block in blocks) {
        if (block.isDeleted) continue

        for (element in convertBlockToElements(block, linkedNoteTitles)) {
            val elementCost = estimateRenderCost(element)
            if (spentRenderCost + elementCost > maximumRenderCost) return collected
            collected += element
            spentRenderCost += elementCost
        }
    }

    return collected
}

private fun estimateRenderCost(element: WidgetElement): Int = when (element) {
    is WidgetElement.TextLine -> 1 + element.highlightedRanges.size
    is WidgetElement.DividerLine -> 1
    is WidgetElement.Record -> element.fields.size * 2
}

private fun convertBlockToElements(
    block: NoteBlock,
    linkedNoteTitles: Map<String, String>
): List<WidgetElement> = when (block) {
    is HeadingBlock -> textLine(
        block = block,
        text = block.text,
        style = if (block.level <= 1) WidgetTextStyleName.HEADING else WidgetTextStyleName.SUBHEADING
    )

    is TextBlock -> textLine(block, block.text, WidgetTextStyleName.BODY)

    is QuoteBlock -> textLine(block, block.text, WidgetTextStyleName.QUOTE)

    is BulletedListBlock -> textLine(block, block.text, WidgetTextStyleName.BODY, prefix = "•  ")

    is NumberedListBlock -> textLine(block, block.text, WidgetTextStyleName.BODY, prefix = "${block.number}.  ")

    is ToggleBlock -> textLine(block, block.text, WidgetTextStyleName.BODY, prefix = "▸  ")

    is CheckboxBlock -> textLine(
        block = block,
        text = block.text,
        style = WidgetTextStyleName.BODY,
        prefix = if (block.isChecked) "☑  " else "☐  ",
        isStruckThrough = block.isChecked || block.isStrikeThrough
    )

    is CodeBlock -> block.code.lines().mapIndexed { index, codeLine ->
        WidgetElement.TextLine(
            key = "${block.id}#$index",
            text = codeLine.take(maximumCharactersPerLine),
            style = WidgetTextStyleName.CODE,
            indentationLevel = block.indentationLevel,
            isStruckThrough = false
        )
    }

    is BookmarkBlock -> textLine(
        block = block,
        text = block.title?.takeIf { it.isNotBlank() } ?: block.url,
        style = WidgetTextStyleName.SUBTLE
    )

    is LinkedNoteBlock -> textLine(
        block = block,
        text = linkedNoteTitles[block.linkedNoteId] ?: "Linked note",
        style = WidgetTextStyleName.SUBTLE
    )

    is ImageBlock -> textLine(block, "Image", WidgetTextStyleName.SUBTLE)

    is CanvasBlock -> textLine(block, "Canvas", WidgetTextStyleName.SUBTLE)

    is DocumentBlock -> textLine(block, block.fileName, WidgetTextStyleName.SUBTLE)

    is VoiceBlock -> textLine(
        block = block,
        text = "Voice note  ${formatSecondsAsClock(block.durationSeconds)}",
        style = WidgetTextStyleName.SUBTLE
    )

    is TableBlock -> convertTableToElements(block)

    is DatabaseBlock -> convertDatabaseToElements(block)

    is SolidDividerBlock, is ThreeDotDividerBlock ->
        listOf(WidgetElement.DividerLine(key = "${block.id}#0"))
}

private fun textLine(
    block: NoteBlock,
    text: String,
    style: WidgetTextStyleName,
    prefix: String = "",
    isStruckThrough: Boolean = block.isStrikeThrough
): List<WidgetElement> {
    val trimmedLine = (prefix + text).trim()
    if (trimmedLine.isBlank()) return emptyList()

    val renderedText = trimmedLine.take(maximumCharactersPerLine)
    return listOf(
        WidgetElement.TextLine(
            key = "${block.id}#0",
            text = renderedText,
            style = style,
            indentationLevel = block.indentationLevel,
            isStruckThrough = isStruckThrough,
            isHighlighted = block.isHighlighted,
            highlightColorName = block.highlightColorNameOrNull(),
            highlightedRanges = highlightedRangesIn(block, prefix, text, renderedText)
        )
    )
}

private fun highlightedRangesIn(
    block: NoteBlock,
    prefix: String,
    text: String,
    renderedText: String
): List<HighlightedRange> {
    if (block.isHighlighted) return emptyList()

    val highlightedSpans = block.inlineSpansOrEmpty().filter { span -> span.highlight }
    if (highlightedSpans.isEmpty()) return emptyList()

    val untrimmedLine = prefix + text
    val charactersDroppedFromStart = untrimmedLine.length - untrimmedLine.trimStart().length
    val shiftFromBlockTextToLine = prefix.length - charactersDroppedFromStart

    val rangesInLine = highlightedSpans
        .map { span ->
            HighlightedRange(
                start = (span.start + shiftFromBlockTextToLine).coerceIn(0, renderedText.length),
                end = (span.end + shiftFromBlockTextToLine).coerceIn(0, renderedText.length),
                colorName = span.highlightColorName
            )
        }
        .filter { range -> range.end > range.start }
        .sortedBy { range -> range.start }

    return mergeTouchingRanges(rangesInLine)
}

private fun mergeTouchingRanges(sortedRanges: List<HighlightedRange>): List<HighlightedRange> {
    val merged = mutableListOf<HighlightedRange>()
    for (range in sortedRanges) {
        val previous = merged.lastOrNull()
        if (previous != null && range.start <= previous.end && previous.colorName == range.colorName) {
            merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, range.end))
        } else {
            merged += range
        }
    }
    return merged
}

private fun convertTableToElements(block: TableBlock): List<WidgetElement> {
    val records = buildRecords(
        key = block.id,
        allRows = block.rows,
        indentationLevel = block.indentationLevel
    )
    if (records.isEmpty()) return emptyList()

    val title = WidgetElement.TextLine(
        key = "${block.id}#title",
        text = "Table",
        style = WidgetTextStyleName.SUBHEADING,
        indentationLevel = block.indentationLevel,
        isStruckThrough = false
    )

    return listOf(title) + records
}

private fun convertDatabaseToElements(block: DatabaseBlock): List<WidgetElement> {
    val visibleColumns = block.columns.filterNot { it.isDeleted }
    if (visibleColumns.isEmpty()) return emptyList()

    val elements = mutableListOf<WidgetElement>()

    block.title.trim().takeIf { it.isNotBlank() }?.let { title ->
        elements += WidgetElement.TextLine(
            key = "${block.id}#title",
            text = title.take(maximumCharactersPerLine),
            style = WidgetTextStyleName.SUBHEADING,
            indentationLevel = block.indentationLevel,
            isStruckThrough = false
        )
    }

    val headerRow = visibleColumns.map { it.name }
    val bodyRows = block.rows
        .filterNot { it.isDeleted }
        .map { row -> visibleColumns.map { column -> row.cells[column.id].displayText() } }

    elements += buildRecords(
        key = block.id,
        allRows = listOf(headerRow) + bodyRows,
        indentationLevel = block.indentationLevel
    )

    return elements
}

private fun buildRecords(
    key: String,
    allRows: List<List<String>>,
    indentationLevel: Int
): List<WidgetElement> {
    val nonEmptyRows = allRows.filter { row -> row.any { cell -> cell.isNotBlank() } }
    if (nonEmptyRows.size < 2) return emptyList()

    val labels = nonEmptyRows.first().map { label -> label.trim().take(maximumCharactersPerLabel) }

    return nonEmptyRows.drop(1).take(maximumRecords).mapIndexedNotNull { rowIndex, row ->
        val fields = row.take(maximumFieldsPerRecord).mapIndexedNotNull { cellIndex, cell ->
            val value = cell.trim().take(maximumCharactersPerValue)
            if (value.isBlank()) null else RecordField(
                label = labels.getOrNull(cellIndex).orEmpty().ifBlank { "—" },
                value = value
            )
        }

        if (fields.isEmpty()) null else WidgetElement.Record(
            key = "$key#record$rowIndex",
            fields = fields,
            indentationLevel = indentationLevel
        )
    }
}

private fun formatSecondsAsClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
