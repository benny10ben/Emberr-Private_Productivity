// Checks that every block type survives being written to a file and read back unchanged.

package com.emberr.domain.vault

import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.domain.model.BookmarkBlock
import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.DocumentBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.InlineSpan
import com.emberr.domain.model.LinkedNoteBlock
import com.emberr.domain.model.CanvasBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyType
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.RecurrenceFrequency
import com.emberr.domain.model.RecurrenceRule
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TableCellContentType
import com.emberr.domain.model.TableCellStyle
import com.emberr.domain.model.TextAlignment
import com.emberr.domain.model.TextBlock
import com.emberr.domain.model.ThreeDotDividerBlock
import com.emberr.domain.model.ToggleBlock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
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

    @Test
    fun aCanvasFenceTypedInTheVaultBecomesACanvasBlock() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = emptyList())
        ) + "```emberr-canvas\nnote: canvas-note-9\n```\n"

        val blocks = readBack(markdown, emptyList()).blocks

        assertEquals(listOf<NoteBlock>(CanvasBlock(id = "generated-0", canvasNoteId = "canvas-note-9", updatedAt = 9_999L)), blocks)
    }

    @Test
    fun aCanvasFenceWithoutANoteIdStaysACodeBlock() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = emptyList())
        ) + "```emberr-canvas\n```\n"

        val blocks = readBack(markdown, emptyList()).blocks

        assertEquals(listOf<NoteBlock>(CodeBlock(id = "generated-0", code = "", language = "emberr-canvas", updatedAt = 9_999L)), blocks)
    }

    @Test
    fun aPropertyFenceTypedInTheVaultBecomesAPropertyBlock() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = emptyList())
        ) + "```emberr-property\nproperty: Status\nvalue: In progress, Done\n```\n"

        val blocks = readBack(markdown, emptyList()).blocks

        assertEquals(
            listOf<NoteBlock>(
                PropertyBlock(
                    id = "generated-0",
                    propertyType = PropertyType.STATUS,
                    tags = listOf("In progress"),
                    updatedAt = 9_999L
                )
            ),
            blocks
        )
    }

    @Test
    fun aCustomPropertyFenceTypedInTheVaultBecomesACustomPropertyBlock() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = emptyList())
        ) + "```emberr-property\nproperty: custom\nid: client-id\nlabel: Client\ntype: tags\nvalue: Acme, Globex\n```\n"

        val blocks = readBack(markdown, emptyList()).blocks

        assertEquals(
            listOf<NoteBlock>(
                PropertyBlock(
                    id = "generated-0",
                    customPropertyId = "client-id",
                    customLabel = "Client",
                    customValueType = PropertyValueType.TAGS,
                    tags = listOf("Acme", "Globex"),
                    updatedAt = 9_999L
                )
            ),
            blocks
        )
    }

    @Test
    fun aCustomPropertyFenceWithoutAnIdStaysACodeBlock() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = emptyList())
        ) + "```emberr-property\nproperty: custom\nlabel: Client\n```\n"

        val block = readBack(markdown, emptyList()).blocks.single()

        assertEquals(CodeBlock(id = "generated-0", code = "property: custom\nlabel: Client", language = "emberr-property", updatedAt = 9_999L), block)
    }

    @Test
    fun tagsTypedInTheVaultAreTrimmedAndRepeatsAreDropped() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = emptyList())
        ) + "```emberr-property\nproperty: tags\nvalue: design ,  Urgent, DESIGN,,\n```\n"

        val block = readBack(markdown, emptyList()).blocks.single() as PropertyBlock

        assertEquals(listOf("design", "Urgent"), block.tags)
    }

    @Test
    fun aDateTheVaultCannotReadKeepsTheDateAlreadySaved() {
        val existing = PropertyBlock(
            id = "block-property-due",
            propertyType = PropertyType.DUE_DATE,
            date = LocalDate(2026, 9, 27),
            updatedAt = 126L
        )
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = listOf(existing))
        ).replace("2026-09-27", "next tuesday")

        assertEquals(listOf<NoteBlock>(existing), readBack(markdown, listOf(existing)).blocks)
    }

    @Test
    fun clearingAPropertyValueInTheVaultEmptiesTheBlock() {
        val existing = PropertyBlock(
            id = "block-property-tags",
            propertyType = PropertyType.TAGS,
            tags = listOf("design"),
            updatedAt = 127L
        )
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = listOf(existing))
        ).replace("value: design", "value:")

        assertEquals(
            listOf<NoteBlock>(existing.copy(tags = emptyList(), updatedAt = 9_999L)),
            readBack(markdown, listOf(existing)).blocks
        )
    }

    @Test
    fun aPropertyFenceWithAnUnknownPropertyStaysACodeBlock() {
        val markdown = NoteMarkdownWriter.writeNote(
            VaultNoteWriteRequest(metadata = noteMetadata(), blocks = emptyList())
        ) + "```emberr-property\nproperty: colour\n```\n"

        val blocks = readBack(markdown, emptyList()).blocks

        assertEquals(
            listOf<NoteBlock>(CodeBlock(id = "generated-0", code = "property: colour", language = "emberr-property", updatedAt = 9_999L)),
            blocks
        )
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
            CanvasBlock(
                id = "block-canvas",
                canvasNoteId = "canvas-note-1",
                isPinned = true,
                updatedAt = 118L
            ),
            PropertyBlock(
                id = "block-property-name",
                propertyType = PropertyType.NAME,
                text = "Ada \"the Countess\" Lovelace",
                updatedAt = 124L
            ),
            PropertyBlock(
                id = "block-property-link",
                propertyType = PropertyType.LINK,
                text = "https://example.com/a#b",
                isPinned = true,
                updatedAt = 125L
            ),
            PropertyBlock(
                id = "block-property-due",
                propertyType = PropertyType.DUE_DATE,
                date = LocalDate(2026, 9, 27),
                indentationLevel = 1,
                updatedAt = 126L
            ),
            PropertyBlock(
                id = "block-property-tags",
                propertyType = PropertyType.TAGS,
                tags = listOf("design", "urgent"),
                updatedAt = 127L
            ),
            PropertyBlock(id = "block-property-status", propertyType = PropertyType.STATUS, updatedAt = 128L),
            PropertyBlock(
                id = "block-property-custom-client",
                customPropertyId = "client-id",
                customLabel = "Client: \"Big\" accounts",
                customValueType = PropertyValueType.SINGLE_CHOICE,
                tags = listOf("Acme"),
                updatedAt = 129L
            ),
            PropertyBlock(
                id = "block-property-custom-kickoff",
                customPropertyId = "kickoff-id",
                customLabel = "Kickoff",
                customValueType = PropertyValueType.DATE,
                date = LocalDate(2026, 10, 1),
                updatedAt = 130L
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
