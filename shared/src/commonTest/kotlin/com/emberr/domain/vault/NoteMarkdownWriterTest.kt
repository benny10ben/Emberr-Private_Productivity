// Checks the markdown the writer produces for each kind of block.

package com.emberr.domain.vault

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.CellData
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseRow
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteMarkdownWriterTest {

    @Test
    fun writesFrontMatterThenTaggedBlocks() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = noteMetadata(),
                blocks = listOf(
                    HeadingBlock(id = "heading-aaaaaaaa", text = "Q3 Budget", level = 1),
                    TextBlock(id = "text-bbbbbbbb", text = "Some notes."),
                    CheckboxBlock(id = "check-cccccccc", text = "Email finance")
                )
            )
        )

        val expected = "---\n" +
            "id: note-1\n" +
            "title: Q3 Budget\n" +
            "created: 1000\n" +
            "updated: 2000\n" +
            "---\n" +
            "\n" +
            "# Q3 Budget ^em-headinga\n" +
            "\n" +
            "Some notes. ^em-textbbbb\n" +
            "\n" +
            "- [ ] Email finance ^em-checkccc\n"

        assertEquals(expected, markdown)
    }

    @Test
    fun dailyNoteRecordsItsDateInFrontMatter() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = noteMetadata(isDaily = true, dateString = "2026-09-11"),
                blocks = emptyList()
            )
        )

        assertTrue(markdown.contains("daily: true"))
        assertTrue(markdown.contains("date: 2026-09-11"))
    }

    @Test
    fun deletedBlocksAreNotExported() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = noteMetadata(),
                blocks = listOf(
                    TextBlock(id = "kept-block", text = "keep me"),
                    TextBlock(id = "gone-block", text = "delete me", isDeleted = true)
                )
            )
        )

        assertTrue(markdown.contains("keep me"))
        assertTrue(!markdown.contains("delete me"))
    }

    @Test
    fun codeBlockTagSitsAfterTheClosingFence() {
        val rendered = renderSingleBlock(
            CodeBlock(
                id = "code-1",
                code = "fun hello() {\n    println(\"hi\")\n}",
                language = "kotlin"
            )
        )

        val expected = "```kotlin\n" +
            "fun hello() {\n" +
            "    println(\"hi\")\n" +
            "}\n" +
            "```\n" +
            "^em-code1"

        assertEquals(expected, rendered)
    }

    @Test
    fun multiLineTextKeepsOneTagAtTheEnd() {
        val rendered = renderSingleBlock(TextBlock(id = "para-1", text = "first\nsecond\nthird"))

        assertEquals("first\nsecond\nthird ^em-para1", rendered)
    }

    @Test
    fun listItemContinuationLinesAlignUnderTheMarker() {
        val rendered = renderSingleBlock(
            CheckboxBlock(id = "task-1", text = "first\nsecond", isChecked = true)
        )

        assertEquals("- [x] first\n      second ^em-task1", rendered)
    }

    @Test
    fun indentationLevelBecomesTwoSpacesPerLevel() {
        val rendered = renderSingleBlock(
            CheckboxBlock(id = "task-2", text = "nested", indentationLevel = 2)
        )

        assertEquals("    - [ ] nested ^em-task2", rendered)
    }

    @Test
    fun tableBecomesAMarkdownTableWithAHeaderSeparator() {
        val rendered = renderSingleBlock(
            TableBlock(
                id = "table-1",
                rows = listOf(listOf("Item", "Cost"), listOf("Server", "240"))
            )
        )

        val expected = "| Item | Cost |\n" +
            "| --- | --- |\n" +
            "| Server | 240 |\n" +
            "^em-table1"

        assertEquals(expected, rendered)
    }

    @Test
    fun dividerPutsItsTagOnTheNextLine() {
        assertEquals("---\n^em-div1", renderSingleBlock(SolidDividerBlock(id = "div-1")))
    }

    @Test
    fun databaseBecomesAConfigFencePlusATableWithRowIds() {
        val databaseId = "db-1"
        val rendered = renderSingleBlock(
            DatabaseBlock(
                id = databaseId,
                title = "Q3 Budget",
                columns = listOf(
                    DatabaseColumn(id = "col-item", databaseId = databaseId, name = "Item", type = ColumnType.TEXT),
                    DatabaseColumn(id = "col-cost", databaseId = databaseId, name = "Cost", type = ColumnType.MONEY)
                ),
                rows = listOf(
                    DatabaseRow(
                        id = "row-01",
                        databaseId = databaseId,
                        cells = mapOf(
                            "col-item" to CellData.Text("Server"),
                            "col-cost" to CellData.Number(240.0)
                        )
                    )
                )
            )
        )

        val expected = "```emberr-database\n" +
            "title: Q3 Budget\n" +
            "view: table\n" +
            "columns:\n" +
            "  Item: text\n" +
            "  Cost: money\n" +
            "```\n" +
            "\n" +
            "| id | Item | Cost |\n" +
            "| --- | --- | --- |\n" +
            "| row01 | Server | 240 |\n" +
            "^em-db1"

        assertEquals(expected, rendered)
    }

    @Test
    fun everyBlockGetsItsOwnUniqueTag() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = noteMetadata(),
                blocks = listOf(
                    TextBlock(id = "duplicate-prefix-one", text = "one"),
                    TextBlock(id = "duplicate-prefix-two", text = "two")
                )
            )
        )

        val tags = Regex("\\^em-([a-z0-9]+)").findAll(markdown).map { it.groupValues[1] }.toList()

        assertEquals(2, tags.size)
        assertEquals(2, tags.distinct().size)
    }

    @Test
    fun titlesWithColonsAreQuotedSoTheFrontMatterStaysValid() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = noteMetadata(title = "Plan: Q3"),
                blocks = emptyList()
            )
        )

        assertTrue(markdown.contains("title: \"Plan: Q3\""))
    }

    private fun renderSingleBlock(block: NoteBlock): String =
        NoteMarkdownWriter
            .writeNote(VaultNoteWriteRequest(metadata = noteMetadata(), blocks = listOf(block)))
            .substringAfter("---\n\n")
            .trimEnd('\n')

    private fun noteMetadata(
        noteId: String = "note-1",
        title: String = "Q3 Budget",
        isDaily: Boolean = false,
        dateString: String? = null
    ) = NoteMetadataEntity(
        noteId = noteId,
        title = title,
        folderId = null,
        isDaily = isDaily,
        dateString = dateString,
        createdAt = 1000L,
        updatedAt = 2000L,
        filePath = ""
    )
}
