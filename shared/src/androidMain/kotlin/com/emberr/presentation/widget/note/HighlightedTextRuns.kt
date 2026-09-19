package com.emberr.presentation.widget.note

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue

internal const val maximumChildrenPerGlanceContainer = 10
internal const val maximumWrappedRows = maximumChildrenPerGlanceContainer * maximumChildrenPerGlanceContainer

internal data class TextRun(
    val text: String,
    val isHighlighted: Boolean,
    val highlightColorName: String? = null
)

internal data class WidgetTextAppearance(
    val fontSizeInSp: Float,
    val fontWeight: Int,
    val isItalic: Boolean,
    val isMonospace: Boolean
)

internal fun appearanceFor(style: WidgetTextStyleName): WidgetTextAppearance = when (style) {
    WidgetTextStyleName.HEADING -> WidgetTextAppearance(21f, 700, isItalic = false, isMonospace = false)
    WidgetTextStyleName.SUBHEADING -> WidgetTextAppearance(18f, 500, isItalic = false, isMonospace = false)
    WidgetTextStyleName.BODY -> WidgetTextAppearance(16f, 400, isItalic = false, isMonospace = false)
    WidgetTextStyleName.QUOTE -> WidgetTextAppearance(16f, 400, isItalic = true, isMonospace = false)
    WidgetTextStyleName.CODE -> WidgetTextAppearance(14f, 400, isItalic = false, isMonospace = true)
    WidgetTextStyleName.SUBTLE -> WidgetTextAppearance(14f, 400, isItalic = false, isMonospace = false)
}

internal fun buildTextWidthMeasurer(context: Context, style: WidgetTextStyleName): (String) -> Float {
    val appearance = appearanceFor(style)
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    paint.typeface = Typeface.create(
        if (appearance.isMonospace) Typeface.MONOSPACE else Typeface.DEFAULT,
        appearance.fontWeight,
        appearance.isItalic
    )
    paint.textSize = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        appearance.fontSizeInSp,
        context.resources.displayMetrics
    )

    return { text -> paint.measureText(text) }
}

internal fun splitIntoHighlightRuns(line: WidgetElement.TextLine): List<TextRun> {
    val runs = mutableListOf<TextRun>()
    var cursor = 0

    for (range in line.highlightedRanges) {
        val highlightStart = range.start.coerceIn(cursor, line.text.length)
        val highlightEnd = range.end.coerceIn(highlightStart, line.text.length)

        if (highlightStart > cursor) {
            runs += TextRun(line.text.substring(cursor, highlightStart), isHighlighted = false)
        }
        if (highlightEnd > highlightStart) {
            runs += TextRun(
                text = line.text.substring(highlightStart, highlightEnd),
                isHighlighted = true,
                highlightColorName = range.colorName
            )
        }
        cursor = highlightEnd
    }

    if (cursor < line.text.length) {
        runs += TextRun(line.text.substring(cursor), isHighlighted = false)
    }
    return runs
}

internal fun wrapRunsIntoRows(
    runs: List<TextRun>,
    availableWidthInPixels: Float,
    highlightPaddingInPixels: Float,
    maximumRows: Int,
    measureTextWidth: (String) -> Float
): List<List<TextRun>> {
    if (availableWidthInPixels <= 0f) return finishRows(listOf(runs), remainingWordsExist = false)

    val rows = mutableListOf<List<TextRun>>()
    var currentRow = mutableListOf<TextRun>()
    var usedWidth = 0f

    for (word in splitRunsIntoWords(runs)) {
        if (currentRow.isNotEmpty()) {
            val widthIfKeptOnThisRow = measureTextWidth(word.text) +
                paddingWhenHighlightStarts(currentRow, word, highlightPaddingInPixels)

            if (usedWidth + widthIfKeptOnThisRow > availableWidthInPixels) {
                rows += currentRow
                if (rows.size == maximumRows) return finishRows(rows, remainingWordsExist = true)
                currentRow = mutableListOf()
                usedWidth = 0f
            }
        }

        val textToPlace = if (currentRow.isEmpty()) word.text.trimStart() else word.text
        if (textToPlace.isEmpty()) continue

        usedWidth += measureTextWidth(textToPlace) +
            paddingWhenHighlightStarts(currentRow, word, highlightPaddingInPixels)
        currentRow.add(TextRun(textToPlace, word.isHighlighted, word.highlightColorName))
    }

    if (currentRow.isNotEmpty()) rows += currentRow
    return finishRows(rows, remainingWordsExist = false)
}

private data class WordToPlace(
    val text: String,
    val isHighlighted: Boolean,
    val highlightColorName: String?
)

private val afterAnyWhitespace = Regex("(?<=\\s)")

private fun splitRunsIntoWords(runs: List<TextRun>): List<WordToPlace> =
    runs.flatMap { run ->
        run.text.split(afterAnyWhitespace).map { word ->
            WordToPlace(word, run.isHighlighted, run.highlightColorName)
        }
    }

private fun paddingWhenHighlightStarts(
    rowSoFar: List<TextRun>,
    word: WordToPlace,
    highlightPaddingInPixels: Float
): Float {
    if (!word.isHighlighted) return 0f

    val previousRun = rowSoFar.lastOrNull()
    val continuesTheSameHighlight = previousRun != null &&
            previousRun.isHighlighted &&
            previousRun.highlightColorName == word.highlightColorName
    return if (continuesTheSameHighlight) 0f else highlightPaddingInPixels
}

private fun finishRows(rows: List<List<TextRun>>, remainingWordsExist: Boolean): List<List<TextRun>> {
    val tidiedRows = rows
        .map { row -> limitRunsInRow(dropTrailingSpace(joinNeighbouringRuns(row))) }
        .filter { row -> row.isNotEmpty() }
    if (!remainingWordsExist || tidiedRows.isEmpty()) return tidiedRows

    return tidiedRows.dropLast(1) + listOf(appendEllipsis(tidiedRows.last()))
}

private fun dropTrailingSpace(runs: List<TextRun>): List<TextRun> {
    val lastRun = runs.lastOrNull() ?: return runs
    val textWithoutTrailingSpace = lastRun.text.trimEnd()

    return if (textWithoutTrailingSpace.isEmpty()) dropTrailingSpace(runs.dropLast(1))
    else runs.dropLast(1) + lastRun.copy(text = textWithoutTrailingSpace)
}

private fun appendEllipsis(row: List<TextRun>): List<TextRun> {
    val lastRun = row.lastOrNull() ?: return row
    return row.dropLast(1) + lastRun.copy(text = lastRun.text.trimEnd() + "…")
}

private fun joinNeighbouringRuns(runs: List<TextRun>): List<TextRun> {
    val joined = mutableListOf<TextRun>()
    for (run in runs) {
        val previous = joined.lastOrNull()
        if (
            previous != null &&
            previous.isHighlighted == run.isHighlighted &&
            previous.highlightColorName == run.highlightColorName
        ) {
            joined[joined.lastIndex] = previous.copy(text = previous.text + run.text)
        } else {
            joined += run
        }
    }
    return joined
}

private fun limitRunsInRow(runs: List<TextRun>): List<TextRun> {
    if (runs.size <= maximumChildrenPerGlanceContainer) return runs

    val keptRuns = runs.take(maximumChildrenPerGlanceContainer - 1)
    val overflowRuns = runs.drop(maximumChildrenPerGlanceContainer - 1)
    val mergedOverflow = TextRun(
        text = overflowRuns.joinToString(separator = "") { run -> run.text },
        isHighlighted = overflowRuns.first().isHighlighted,
        highlightColorName = overflowRuns.first().highlightColorName
    )
    return keptRuns + mergedOverflow
}
