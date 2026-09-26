package com.emberr.domain.util.export

import com.emberr.domain.model.*
import com.emberr.domain.vault.NoteMarkdownWriter

object ExportEngine {

    fun generatePlainText(blocks: List<NoteBlock>, title: String? = null): String {
        val builder = StringBuilder()
        if (!title.isNullOrBlank()) {
            builder.appendLine(title)
            builder.appendLine()
        }
        buildPlainText(blocks, builder)
        return builder.toString().trim()
    }

    // Renders through the same engine the AI vault uses:
    // shared/src/commonMain/kotlin/com/emberr/domain/vault/NoteMarkdownWriter.kt
    fun generateMarkdown(
        blocks: List<NoteBlock>,
        title: String? = null,
        noteTitlesById: Map<String, String> = emptyMap(),
        categoryNamesById: Map<String, String> = emptyMap()
    ): String = NoteMarkdownWriter.writeSharedMarkdown(
        blocks = blocks,
        title = title,
        noteTitlesById = noteTitlesById,
        categoryNamesById = categoryNamesById
    )

    private fun buildPlainText(blocks: List<NoteBlock>, builder: StringBuilder) {
        blocks.filter { !it.isDeleted }.forEach { block ->
            val indent = "\t".repeat(block.indentationLevel)

            when (block) {
                is TextBlock -> {
                    if (block.text.isNotBlank()) builder.appendLine("$indent${block.text}")
                    else builder.appendLine()
                }
                is HeadingBlock -> builder.appendLine("$indent${block.text}")
                is CheckboxBlock -> builder.appendLine("$indent${if (block.isChecked) "[x]" else "[ ]"} ${block.text}")
                is BulletedListBlock -> builder.appendLine("$indent• ${block.text}")
                is NumberedListBlock -> builder.appendLine("$indent${block.number}. ${block.text}")
                is ToggleBlock -> builder.appendLine("$indent▶ ${block.text}")
                is QuoteBlock -> builder.appendLine("$indent\"${block.text}\"")
                is CodeBlock -> builder.appendLine("$indent${block.code}")
                is SolidDividerBlock, is ThreeDotDividerBlock -> builder.appendLine("$indent---")
                is BookmarkBlock -> builder.appendLine("$indent${block.title ?: block.url}\n$indent${block.url}")
                is ImageBlock -> builder.appendLine("${indent}[Image]")
                is DocumentBlock -> builder.appendLine("$indent[File: ${block.fileName}]")
                is TableBlock -> {
                    block.rows.forEach { row ->
                        builder.appendLine("$indent  ${row.joinToString(" | ")}")
                    }
                }
                is VoiceBlock -> { /* Ignored */ }
                is CanvasBlock -> builder.appendLine("${indent}[Canvas]")
                is LinkedNoteBlock -> { /* Ignored - only linkedNoteId is available here, no title to render */ }
            }
        }
    }
}
