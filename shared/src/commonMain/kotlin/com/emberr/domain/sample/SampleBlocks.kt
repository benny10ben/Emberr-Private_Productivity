package com.emberr.domain.sample

import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.TextBlock

internal fun emphasisedWord(
    sentence: String,
    word: String,
    bold: Boolean = false,
    italic: Boolean = false,
    strikeThrough: Boolean = false
): InlineSpan? {
    val start = sentence.indexOf(word)
    if (start < 0) return null
    return InlineSpan(
        start = start,
        end = start + word.length,
        bold = bold,
        italic = italic,
        strikeThrough = strikeThrough
    )
}

internal class BreathingRoom(private val idPrefix: String, private val createdAt: Long) {
    private var spacerCount = 0

    fun next(): NoteBlock {
        spacerCount += 1
        return TextBlock(id = "${idPrefix}_space_$spacerCount", text = "", updatedAt = createdAt)
    }
}
