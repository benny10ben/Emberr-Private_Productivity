package com.emberr.domain.ai

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.canvas.CanvasContent
import com.emberr.domain.model.*
import com.emberr.database.EmberrDatabase
import java.text.SimpleDateFormat
import java.util.*

class NoteIndexer(
    private val database: EmberrDatabase,
    private val aiEngine: LocalAiEngine,
    private val settingsManager: SettingsManager
) {
    private data class PendingIndex(
        val blockId: String,
        val chunkText: String,
        val embeddingString: String
    )

    private val embeddingChunkCharacterLimit = 1800

    private fun splitIntoEmbeddingSafeChunks(text: String): List<String> {
        if (text.length <= embeddingChunkCharacterLimit) return listOf(text)

        val chunks = mutableListOf<String>()
        var startIndex = 0
        while (startIndex < text.length) {
            var endIndex = (startIndex + embeddingChunkCharacterLimit).coerceAtMost(text.length)
            if (endIndex < text.length) {
                val paragraphBreak = text.lastIndexOf('\n', endIndex)
                val wordBreak = text.lastIndexOf(' ', endIndex)
                val breakPoint = maxOf(paragraphBreak, wordBreak)
                if (breakPoint > startIndex) endIndex = breakPoint
            }
            val chunk = text.substring(startIndex, endIndex).trim()
            if (chunk.isNotBlank()) chunks.add(chunk)
            startIndex = endIndex
        }
        return chunks
    }

    private val dateOnlyFormatter = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    private val dateTimeFormatter = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
    private val isoInputFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun isoToHuman(isoDate: String?): String? {
        if (isoDate == null) return null
        return try {
            val parsed = isoInputFormatter.parse(isoDate) ?: return isoDate
            dateOnlyFormatter.format(parsed)
        } catch (_: Exception) {
            isoDate
        }
    }

    private fun timestampToDateTime(millis: Long): String =
        dateTimeFormatter.format(Date(millis))

    private fun timestampToDate(millis: Long): String =
        dateOnlyFormatter.format(Date(millis))

    suspend fun indexNote(metadata: NoteMetadataEntity, content: NoteContent) {
        if (settingsManager.isAiFeaturesDisabled()) return
        if (aiEngine.unsupportedHardwareReason != null) return
        if (metadata.noteId == "global_pinned") return

        val baseContext = buildContextString(metadata)

        val blockIds = mutableListOf<String>()
        val chunkTexts = mutableListOf<String>()

        for (block in content.blocks) {
            if (block.isDeleted) continue

            val blockText = extractTextFromBlock(
                block = block,
                allBlocks = content.blocks,
                isDaily = metadata.isDaily,
                noteDate = metadata.dateString
            )

            if (!blockText.isNullOrBlank()) {
                val subChunks = splitIntoEmbeddingSafeChunks(blockText)
                subChunks.forEachIndexed { index, subChunk ->
                    val chunkBlockId = if (subChunks.size == 1) block.id else "${block.id}#$index"
                    blockIds.add(chunkBlockId)
                    chunkTexts.add("$baseContext\n$subChunk")
                }
            }
        }

        replaceIndexForNote(metadata, blockIds, chunkTexts)
    }

    suspend fun indexCanvas(metadata: NoteMetadataEntity, canvas: CanvasContent, ownerNoteTitle: String?) {
        if (settingsManager.isAiFeaturesDisabled()) return
        if (aiEngine.unsupportedHardwareReason != null) return

        val context = buildString {
            appendLine("[Source: Canvas]")
            if (metadata.title.isNotBlank()) appendLine("Title: ${metadata.title}")
            if (!ownerNoteTitle.isNullOrBlank()) appendLine("Inside note: $ownerNoteTitle")
        }
        val canvasText = canvasTextForIndexing(canvas)
        val chunkBodies = if (canvasText.isBlank()) emptyList() else splitIntoEmbeddingSafeChunks(canvasText)
        val blockIds = chunkBodies.indices.map { index -> "canvas:${metadata.noteId}#$index" }
        val chunkTexts = chunkBodies.map { body -> "$context\n$body" }
        replaceIndexForNote(metadata, blockIds, chunkTexts)
    }

    private suspend fun replaceIndexForNote(metadata: NoteMetadataEntity, blockIds: List<String>, chunkTexts: List<String>) {
        if (chunkTexts.isEmpty()) {
            database.transaction {
                database.vectorStoreQueries.deleteBlocksForNote(metadata.noteId)
            }
            return
        }

        val vectors = aiEngine.generateEmbeddings(chunkTexts)

        val pendingIndexes = chunkTexts.indices.map { i ->
            PendingIndex(
                blockId = blockIds[i],
                chunkText = chunkTexts[i],
                embeddingString = vectors[i].joinToString(prefix = "[", postfix = "]")
            )
        }

        database.transaction {
            database.vectorStoreQueries.deleteBlocksForNote(metadata.noteId)
            for (pending in pendingIndexes) {
                database.vectorStoreQueries.insertMetadata(
                    block_id = pending.blockId,
                    note_id = metadata.noteId,
                    space_id = metadata.spaceId,
                    chunk_text = pending.chunkText,
                    embedding = pending.embeddingString
                )
            }
        }
    }

    private fun buildContextString(metadata: NoteMetadataEntity): String {
        return buildString {
            if (metadata.isDaily) {
                appendLine("[Source: Daily Note]")
                // Convert "2026-06-12" → "June 12, 2026"
                appendLine("Date: ${isoToHuman(metadata.dateString) ?: metadata.dateString}")
            } else {
                appendLine("[Source: Note]")
                appendLine("Title: ${metadata.title}")
                if (metadata.snippet.isNotBlank()) {
                    appendLine("Description: ${metadata.snippet}")
                }
            }
        }
    }

    /**
     * Returns null for block types with no useful text (images, documents, voice).
     *
     * Checkbox deadline logic:
     * Any note  + reminder set  → full date+time from the timestamp (user set it explicitly)
     * Daily     + no reminder   → date-only from the note's own date (implied deadline)
     * Note  + no reminder  → "None"
     */
    private fun extractTextFromBlock(
        block: NoteBlock,
        allBlocks: List<NoteBlock>,
        isDaily: Boolean,
        noteDate: String?
    ): String? = when (block) {

        is TextBlock -> block.text.trim().ifBlank { null }

        is HeadingBlock -> when (block.level) {
            1 -> "Main Topic: ${block.text}"
            2 -> "Section: ${block.text}"
            else -> "Sub-section: ${block.text}"
        }

        is QuoteBlock -> "Quote: \"${block.text}\""

        is BulletedListBlock -> "• ${block.text}"
        is NumberedListBlock -> "${block.number}. ${block.text}"

        is ToggleBlock -> buildString {
            append("Toggle: ${block.text}")
            val children = allBlocks.filter { child ->
                !child.isDeleted &&
                        child.id != block.id &&
                        child.indentationLevel > block.indentationLevel
            }
            if (children.isNotEmpty()) {
                appendLine()
                children.forEach { child ->
                    val childText = extractTextFromBlock(child, allBlocks, isDaily, noteDate)
                    if (!childText.isNullOrBlank()) appendLine("  $childText")
                }
            }
        }

        is CodeBlock -> buildString {
            append("Code")
            if (block.language.isNotBlank() && block.language != "plaintext") {
                append(" (${block.language})")
            }
            append(":\n${block.code}")
        }

        is CheckboxBlock -> buildString {
            val status = if (block.isChecked) "Completed Task" else "Pending Task"
            append("$status: ${block.text}")

            val deadlineStr: String = when {
                block.reminderTimestamp != null -> {
                    "Deadline: ${timestampToDateTime(block.reminderTimestamp)}"
                }
                isDaily && noteDate != null -> {
                    "Deadline: ${isoToHuman(noteDate) ?: noteDate}"
                }
                else -> "Deadline: None"
            }
            append(" | $deadlineStr")

            if (block.isChecked && block.completedAt != null) {
                append(" | Completed on: ${timestampToDate(block.completedAt)}")
            }
        }

        is BookmarkBlock -> buildString {
            append("Bookmark: ${block.title ?: block.url}")
            if (!block.description.isNullOrBlank()) append("\nSummary: ${block.description}")
            append("\nURL: ${block.url}")
        }

        is DatabaseBlock -> buildString {
            // Title
            if (block.title.isNotBlank()) appendLine("Database / Table: ${block.title}")

            val activeCols = block.columns.filter { !it.isDeleted }
            val activeRows = block.rows.filter { !it.isDeleted }

            if (activeCols.isEmpty() || activeRows.isEmpty()) return@buildString

            // Column schema — type matters for AI reasoning
            val schemaLine = activeCols.joinToString(", ") { col ->
                val typeLabel = when (col.type) {
                    ColumnType.TEXT     -> "text"
                    ColumnType.NUMBER   -> "number"
                    ColumnType.CHECKBOX -> "checkbox (true/false)"
                    ColumnType.DATE     -> "date"
                    ColumnType.FORMULA  -> "formula (calculated)"
                    ColumnType.PHONE    -> "phone"
                    ColumnType.EMAIL    -> "email"
                    ColumnType.TAGS     -> "tags"
                    ColumnType.URL      -> "url"
                    ColumnType.FILES    -> "files"
                    ColumnType.PRIORITY -> "priority (Low/Medium/High/Urgent)"
                    ColumnType.MONEY    -> "money/currency"
                    ColumnType.AUDIO    -> "audio"
                    ColumnType.NOTES    -> "sub-note reference"
                    ColumnType.STATUS   -> "status (Not Started/In Progress/Done)"
                }
                "${col.name} ($typeLabel)"
            }
            appendLine("Columns: $schemaLine")

            val numericCols = activeCols.filter {
                it.type == ColumnType.NUMBER || it.type == ColumnType.MONEY
            }
            numericCols.forEach { col ->
                val values = activeRows.mapNotNull { row -> (row.cells[col.id] as? CellData.Number)?.value }
                if (values.isNotEmpty()) {
                    val sum = values.sum()
                    val avg = sum / values.size
                    val fmt: (Double) -> String = { v ->
                        if (v == v.toLong().toDouble()) v.toLong().toString()
                        else String.format(Locale.US, "%.2f", v)
                    }
                    appendLine("${col.name} totals: sum=${fmt(sum)}, avg=${fmt(avg)}, min=${fmt(values.min())}, max=${fmt(values.max())}")
                }
            }

            // Checkbox summary
            val checkboxCols = activeCols.filter { it.type == ColumnType.CHECKBOX }
            checkboxCols.forEach { col ->
                val checked = activeRows.count { (it.cells[col.id] as? CellData.Boolean)?.value == true }
                val total = activeRows.size
                appendLine("${col.name}: $checked of $total checked")
            }

            // Priority summary
            val priorityCols = activeCols.filter { it.type == ColumnType.PRIORITY }
            priorityCols.forEach { col ->
                val counts = activeRows.groupBy { (it.cells[col.id] as? CellData.Text)?.value?.ifBlank { "None" } ?: "None" }
                    .mapValues { it.value.size }
                val summary = listOf("Urgent", "High", "Medium", "Low")
                    .filter { (counts[it] ?: 0) > 0 }
                    .joinToString(", ") { "$it: ${counts[it]}" }
                if (summary.isNotBlank()) appendLine("${col.name} breakdown: $summary")
            }

            appendLine("Rows (${activeRows.size} total):")

            // Each row as readable key-value pairs
            activeRows.forEach { row ->
                val rowText = activeCols.mapNotNull { col ->
                    val raw = row.cells[col.id].displayText().trim()
                    if (raw.isBlank()) return@mapNotNull null
                    when (col.type) {
                        ColumnType.CHECKBOX -> "${col.name}: ${if (raw == "true") "Yes (checked)" else "No (unchecked)"}"
                        ColumnType.PRIORITY -> "${col.name}: $raw priority"
                        ColumnType.FORMULA  -> "${col.name}: $raw (calculated)"
                        ColumnType.NOTES    -> "${col.name}: [Sub-note attached]"
                        ColumnType.TAGS     -> null
                        else -> "${col.name}: $raw"
                    }
                }.joinToString(" | ")
                if (rowText.isNotBlank()) appendLine("  - $rowText")
            }
        }

        is TableBlock -> buildString {
            appendLine("Table:")
            block.rows.forEach { row ->
                val rowText = row.filter { it.isNotBlank() }.joinToString(" | ")
                if (rowText.isNotBlank()) appendLine("  - $rowText")
            }
        }.trim().ifBlank { null }

        is ImageBlock    -> null
        is DocumentBlock -> null
        is VoiceBlock    -> null
        is SolidDividerBlock    -> null
        is ThreeDotDividerBlock    -> null
        is LinkedNoteBlock -> null
        is CanvasBlock -> null
    }

    fun deleteNoteFromIndex(noteId: String) {
        database.vectorStoreQueries.deleteBlocksForNote(noteId)
    }
}