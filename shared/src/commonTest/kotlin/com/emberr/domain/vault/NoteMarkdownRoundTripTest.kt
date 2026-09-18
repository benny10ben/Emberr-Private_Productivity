// Checks that every block type survives being written to a file and read back unchanged.

package com.emberr.domain.vault

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CellData
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseRow
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.GalleryCardSize
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.MediaItem
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.RecurrenceFrequency
import com.emberr.domain.model.RecurrenceRule
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.SortConfig
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TableCellContentType
import com.emberr.domain.model.TableCellStyle
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ThreeDotDividerBlock
import com.emberr.domain.model.ToggleBlock
import com.emberr.domain.model.ViewType
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import com.emberr.domain.model.VoiceBlock
import kotlin.test.Test
import kotlin.test.assertEquals

private const val LINKED_NOTE_ID = "note-2"
private const val LINKED_NOTE_TITLE = "Other Note"
private const val CATEGORY_ID = "category-work"
private const val CATEGORY_NAME = "Work"

class NoteMarkdownRoundTripTest {

    @Test
    fun everyBlockTypeSurvivesAWriteThenRead() {
        val originalBlocks = allBlockTypes()

        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = noteMetadata(),
                blocks = originalBlocks,
                noteTitlesById = mapOf(LINKED_NOTE_ID to LINKED_NOTE_TITLE),
                categoryNamesById = mapOf(CATEGORY_ID to CATEGORY_NAME),
                timeZone = TimeZone.UTC
            )
        )

        val result = readBack(markdown, originalBlocks)

        assertEquals(emptyList(), result.problems)
        assertEquals(originalBlocks.size, result.blocks.size, "block count changed\n$markdown")
        originalBlocks.forEachIndexed { index, original ->
            assertEquals(original, result.blocks[index], "block $index changed\n$markdown")
        }
    }

    @Test
    fun everyBlockTypeSurvivesTwoFullRoundTrips() {
        val originalBlocks = allBlockTypes()

        val firstMarkdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = noteMetadata(),
                blocks = originalBlocks,
                noteTitlesById = mapOf(LINKED_NOTE_ID to LINKED_NOTE_TITLE),
                categoryNamesById = mapOf(CATEGORY_ID to CATEGORY_NAME),
                timeZone = TimeZone.UTC
            )
        )
        val firstRead = readBack(firstMarkdown, originalBlocks)

        val secondMarkdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(
                metadata = noteMetadata(),
                blocks = firstRead.blocks,
                noteTitlesById = mapOf(LINKED_NOTE_ID to LINKED_NOTE_TITLE),
                categoryNamesById = mapOf(CATEGORY_ID to CATEGORY_NAME),
                timeZone = TimeZone.UTC
            )
        )

        assertEquals(firstMarkdown, secondMarkdown)
    }

    @Test
    fun frontMatterIsReadBackFromTheFile() {
        val metadata = noteMetadata(title = "Plan: Q3", isFavorite = true)
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = metadata, blocks = emptyList())
        )

        val frontMatter = readBack(markdown, emptyList()).frontMatter

        assertEquals(metadata.noteId, frontMatter.noteId)
        assertEquals("Plan: Q3", frontMatter.title)
        assertEquals(metadata.createdAt, frontMatter.createdAt)
        assertEquals(metadata.updatedAt, frontMatter.updatedAt)
        assertEquals(true, frontMatter.isFavorite)
    }

    private fun readBack(markdown: String, existingBlocks: List<NoteBlock>): VaultNoteReadResult {
        var generatedIdCount = 0
        return NoteMarkdownReader.readNote(
            VaultNoteReadRequest(
                markdown = markdown,
                existingBlocks = existingBlocks,
                timestamp = 9_999L,
                generateBlockId = { "generated-${generatedIdCount++}" },
                noteIdsByLowercaseTitle = mapOf(LINKED_NOTE_TITLE.lowercase() to LINKED_NOTE_ID),
                categoryIdsByLowercaseName = mapOf(CATEGORY_NAME.lowercase() to CATEGORY_ID),
                timeZone = TimeZone.UTC
            )
        )
    }

    private fun allBlockTypes(): List<NoteBlock> {
        val databaseId = "block-database"
        val viewId = "view-1"

        return listOf(
            HeadingBlock(
                id = "block-heading",
                text = "Q3 Budget",
                level = 2,
                textAlignment = TextAlignment.CENTER,
                isPinned = true,
                updatedAt = 100L
            ),
            TextBlock(
                id = "block-text-spans",
                text = "Some notes here",
                inlineSpans = listOf(InlineSpan(start = 5, end = 10, bold = true)),
                updatedAt = 101L
            ),
            TextBlock(
                id = "block-text-multiline",
                text = "multi\nline\ntext",
                indentationLevel = 2,
                textAlignment = TextAlignment.JUSTIFY,
                isPinned = true,
                updatedAt = 102L
            ),
            TextBlock(
                id = "block-text-mixed-styles",
                text = "italic all over with bold inside",
                inlineSpans = listOf(InlineSpan(start = 21, end = 25, bold = true)),
                isItalic = true,
                isUnderlined = true,
                updatedAt = 123L
            ),
            TextBlock(id = "block-text-bold", text = "all bold", isBold = true, updatedAt = 103L),
            TextBlock(id = "block-text-empty", text = "", updatedAt = 104L),
            CheckboxBlock(
                id = "block-checkbox-done",
                text = "Email finance",
                isChecked = true,
                completedAt = 105_500L,
                updatedAt = 105L
            ),
            CheckboxBlock(id = "block-checkbox-nested", text = "Nested", indentationLevel = 1, updatedAt = 106L),
            CheckboxBlock(
                id = "block-checkbox-due",
                text = "Email finance",
                reminderTimestamp = 1_789_221_300_000L,
                durationMinutes = 45,
                categoryId = CATEGORY_ID,
                recurrenceRule = RecurrenceRule(
                    frequency = RecurrenceFrequency.WEEKLY,
                    interval = 2,
                    daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                    untilDateString = "2027-01-31"
                ),
                url = "https://example.com/q3",
                description = "Bring the numbers; see {appendix}\nand the forecast",
                updatedAt = 122L
            ),
            BulletedListBlock(
                id = "block-bullet",
                text = "a point",
                textAlignment = TextAlignment.RIGHT,
                isPinned = true,
                updatedAt = 107L
            ),
            NumberedListBlock(id = "block-number", text = "first", number = 3, updatedAt = 108L),
            ToggleBlock(
                id = "block-toggle",
                text = "Details",
                isExpanded = false,
                textAlignment = TextAlignment.CENTER,
                isPinned = true,
                updatedAt = 109L
            ),
            QuoteBlock(id = "block-quote", text = "quoted\nlines", updatedAt = 110L),
            CodeBlock(
                id = "block-code",
                code = "val x = 1",
                language = "kotlin",
                textAlignment = TextAlignment.RIGHT,
                isPinned = true,
                updatedAt = 111L
            ),
            TableBlock(
                id = "block-table",
                rows = listOf(listOf("A", "B"), listOf("1", "2")),
                cellStyles = mapOf(
                    "0:0" to TableCellStyle(
                        backgroundColorHex = "#FFEEEE",
                        textColorHex = "#333333",
                        isBold = true,
                        isCode = true,
                        contentType = TableCellContentType.EMAIL,
                        alignment = TextAlignment.CENTER
                    )
                ),
                cellSpans = mapOf("1:0" to listOf(InlineSpan(start = 0, end = 1, underline = true))),
                rowStyles = mapOf("0" to TableCellStyle(isItalic = true)),
                columnStyles = mapOf("1" to TableCellStyle(isStrikeThrough = true)),
                columnWidths = mapOf("0" to 220, "1" to 90),
                isPinned = true,
                updatedAt = 112L
            ),
            DatabaseBlock(
                id = databaseId,
                title = "Q3 Budget",
                columns = listOf(
                    DatabaseColumn(
                        id = "column-item",
                        databaseId = databaseId,
                        name = "Item",
                        type = ColumnType.TEXT,
                        width = 220,
                        isNameManuallySet = true,
                        updatedAt = 90L
                    ),
                    DatabaseColumn(
                        id = "column-cost",
                        databaseId = databaseId,
                        name = "Cost",
                        type = ColumnType.MONEY,
                        width = 90,
                        currencySymbol = "$",
                        aggregationType = "sum",
                        updatedAt = 91L
                    ),
                    DatabaseColumn(
                        id = "column-double",
                        databaseId = databaseId,
                        name = "Double",
                        type = ColumnType.FORMULA,
                        formulaExpression = "prop(\"Cost\") * 2",
                        isFormulaCurrency = true,
                        updatedAt = 93L
                    ),
                    DatabaseColumn(
                        id = "column-status",
                        databaseId = databaseId,
                        name = "Status",
                        type = ColumnType.STATUS,
                        updatedAt = 94L
                    ),
                    DatabaseColumn(
                        id = "column-due",
                        databaseId = databaseId,
                        name = "Due",
                        type = ColumnType.DATE,
                        updatedAt = 95L
                    ),
                    DatabaseColumn(
                        id = "column-tags",
                        databaseId = databaseId,
                        name = "Tags",
                        type = ColumnType.TAGS,
                        updatedAt = 96L
                    ),
                    DatabaseColumn(
                        id = "column-files",
                        databaseId = databaseId,
                        name = "Files",
                        type = ColumnType.FILES,
                        updatedAt = 97L
                    ),
                    DatabaseColumn(
                        id = "column-related",
                        databaseId = databaseId,
                        name = "Related",
                        type = ColumnType.NOTES,
                        updatedAt = 98L
                    ),
                    DatabaseColumn(
                        id = "column-shipped",
                        databaseId = databaseId,
                        name = "Shipped",
                        type = ColumnType.CHECKBOX,
                        updatedAt = 99L
                    ),
                    DatabaseColumn(
                        id = "column-gone",
                        databaseId = databaseId,
                        name = "Removed",
                        type = ColumnType.TEXT,
                        isDeleted = true,
                        updatedAt = 89L
                    )
                ),
                rows = listOf(
                    DatabaseRow(
                        id = "row-server",
                        databaseId = databaseId,
                        cells = mapOf(
                            "column-item" to CellData.Text("Server"),
                            "column-cost" to CellData.Number(240.0),
                            "column-double" to CellData.Formula("480"),
                            "column-status" to CellData.Text("In Progress"),
                            "column-due" to CellData.Date(1_789_221_300_000L),
                            "column-tags" to CellData.TagList(listOf("tag-a", "tag-b")),
                            "column-files" to CellData.MediaList(
                                listOf(MediaItem(fileName = "media_9.pdf", originalName = "quote.pdf"))
                            ),
                            "column-related" to CellData.NoteRelation(listOf(LINKED_NOTE_ID, "note-3")),
                            "column-shipped" to CellData.Boolean(true)
                        ),
                        updatedAt = 92L
                    ),
                    DatabaseRow(
                        id = "row-blank",
                        databaseId = databaseId,
                        cells = mapOf("column-item" to CellData.Text("")),
                        updatedAt = 94L
                    ),
                    DatabaseRow(
                        id = "row-gone",
                        databaseId = databaseId,
                        cells = mapOf("column-item" to CellData.Text("Deleted")),
                        isDeleted = true,
                        updatedAt = 88L
                    )
                ),
                views = listOf(
                    DatabaseView(
                        id = viewId,
                        name = "Board",
                        type = ViewType.KANBAN,
                        activeSorts = listOf(SortConfig(columnId = "column-cost", isAscending = false)),
                        activeFilters = listOf(
                            FilterConfig(columnId = "column-item", operator = "contains", value = "Ser")
                        ),
                        groupByColumnId = "column-status",
                        hiddenGroups = listOf("Done"),
                        groupOrder = listOf("Not Started", "In Progress", "Done"),
                        galleryCardSize = GalleryCardSize.LARGE
                    )
                ),
                activeViewId = viewId,
                cellStyles = mapOf(
                    "row-server:column-item" to TableCellStyle(backgroundColorHex = "#EEFFEE", isBold = true)
                ),
                cellSpans = mapOf(
                    "row-server:column-item" to listOf(InlineSpan(start = 0, end = 3, italic = true))
                ),
                isPinned = true,
                updatedAt = 113L
            ),
            BookmarkBlock(
                id = "block-bookmark",
                url = "https://example.com",
                title = "Example",
                description = "kept in the database",
                previewImageUrl = "https://example.com/preview.png",
                isPinned = true,
                updatedAt = 114L
            ),
            ImageBlock(
                id = "block-image",
                localFilePath = "media_1.png",
                indentationLevel = 1,
                isPinned = true,
                updatedAt = 115L
            ),
            DocumentBlock(
                id = "block-document",
                localFilePath = "media_2.pdf",
                fileName = "spec.pdf",
                mimeType = "application/pdf",
                fileSizeString = "1.2 MB",
                isPinned = true,
                updatedAt = 116L
            ),
            VoiceBlock(
                id = "block-voice",
                localFilePath = "media_3.m4a",
                durationSeconds = 34,
                isPinned = true,
                updatedAt = 117L
            ),
            LinkedNoteBlock(
                id = "block-linked",
                linkedNoteId = LINKED_NOTE_ID,
                showIcon = false,
                showCoverImage = true,
                isPinned = true,
                updatedAt = 119L
            ),
            SolidDividerBlock(
                id = "block-divider-solid",
                indentationLevel = 1,
                isPinned = true,
                updatedAt = 120L
            ),
            ThreeDotDividerBlock(id = "block-divider-dots", isPinned = true, updatedAt = 121L)
        )
    }

    private fun noteMetadata(
        title: String = "Q3 Budget",
        isFavorite: Boolean = false
    ) = NoteMetadataEntity(
        noteId = "note-1",
        title = title,
        folderId = null,
        isDaily = false,
        dateString = null,
        createdAt = 1000L,
        updatedAt = 2000L,
        filePath = "",
        isFavorite = isFavorite
    )
}
