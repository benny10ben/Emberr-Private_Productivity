package com.emberr.domain.model

fun generateSnippet(blocks: List<NoteBlock>): String {
    return blocks.asSequence()
        .mapNotNull { extractTextFromBlock(it) }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
        .take(120)
}

fun extractTextFromBlock(block: NoteBlock): String? {
    if (block.isDeleted) return null
    return when (block) {
        is TextBlock -> block.text
        is HeadingBlock -> block.text
        is QuoteBlock -> block.text
        is CheckboxBlock -> block.text
        is BulletedListBlock -> block.text
        is NumberedListBlock -> block.text
        is ToggleBlock -> block.text
        else -> null
    }
}
