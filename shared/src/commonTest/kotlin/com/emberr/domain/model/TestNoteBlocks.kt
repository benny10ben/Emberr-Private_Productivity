package com.emberr.domain.model

import kotlinx.datetime.DayOfWeek

object TestNoteBlocks {

    val everyInlineStyle = InlineSpan(
        start = 0,
        end = 3,
        bold = true,
        italic = true,
        strikeThrough = true,
        underline = true,
        highlight = true
    )

    fun textColumn(id: String, databaseId: String, name: String) = DatabaseColumn(
        id = id,
        databaseId = databaseId,
        name = name,
        type = ColumnType.TEXT,
        width = 220,
        formulaExpression = "prop(\"$name\") * 2",
        aggregationType = "sum",
        currencySymbol = "$",
        isFormulaCurrency = true,
        isDeleted = false,
        isNameManuallySet = true,
        updatedAt = 900L
    )

    fun populatedDatabaseBlock(): DatabaseBlock {
        val databaseId = "database-1"
        val nameColumn = textColumn("column-name", databaseId, "Name")
        val amountColumn = textColumn("column-amount", databaseId, "Amount")

        val row = DatabaseRow(
            id = "row-1",
            databaseId = databaseId,
            cells = mapOf(
                nameColumn.id to CellData.Text("Buy milk"),
                amountColumn.id to CellData.Number(12.5),
                "column-done" to CellData.Boolean(true),
                "column-due" to CellData.Date(1_700_000_000_000L),
                "column-tags" to CellData.TagList(listOf("tag-a", "tag-b")),
                "column-files" to CellData.MediaList(
                    listOf(MediaItem("stored-1.png", "holiday.png"))
                ),
                "column-notes" to CellData.NoteRelation(listOf("note-a", "note-b")),
                "column-total" to CellData.Formula("25.00")
            ),
            isDeleted = false,
            updatedAt = 950L
        )

        val view = DatabaseView(
            id = "view-1",
            name = "Board",
            type = ViewType.KANBAN,
            activeSorts = listOf(SortConfig(nameColumn.id, isAscending = false)),
            activeFilters = listOf(FilterConfig(amountColumn.id, "gt", "10")),
            groupByColumnId = nameColumn.id,
            hiddenGroups = listOf("Done"),
            groupOrder = listOf("Not Started", "In Progress", "Done"),
            galleryCardSize = GalleryCardSize.LARGE
        )

        return DatabaseBlock(
            id = databaseId,
            title = "Shopping",
            columns = listOf(nameColumn, amountColumn),
            rows = listOf(row),
            views = listOf(view),
            activeViewId = view.id,
            indentationLevel = 1,
            isBold = true,
            isItalic = true,
            isStrikeThrough = true,
            isUnderlined = true,
            isDeleted = true,
            isPinned = true,
            updatedAt = 1_000L
        )
    }

    fun oneOfEveryBlockType(): List<NoteBlock> = listOf(
        TextBlock(
            id = "text-1",
            text = "hello",
            indentationLevel = 2,
            textAlignment = TextAlignment.CENTER,
            inlineSpans = listOf(everyInlineStyle),
            isBold = true,
            isItalic = true,
            isStrikeThrough = true,
            isUnderlined = true,
            isDeleted = true,
            isPinned = true,
            updatedAt = 101L
        ),
        HeadingBlock(
            id = "heading-1",
            text = "Title",
            level = 3,
            indentationLevel = 1,
            textAlignment = TextAlignment.RIGHT,
            inlineSpans = listOf(everyInlineStyle),
            isBold = true,
            isPinned = true,
            updatedAt = 102L
        ),
        QuoteBlock(
            id = "quote-1",
            text = "Quoted",
            indentationLevel = 1,
            textAlignment = TextAlignment.JUSTIFY,
            inlineSpans = listOf(everyInlineStyle),
            isItalic = true,
            updatedAt = 103L
        ),
        CheckboxBlock(
            id = "checkbox-1",
            text = "Call the plumber",
            isChecked = true,
            indentationLevel = 2,
            textAlignment = TextAlignment.CENTER,
            inlineSpans = listOf(everyInlineStyle),
            isBold = true,
            reminderTimestamp = 1_700_000_000_000L,
            completedAt = 1_700_000_500_000L,
            categoryId = "category-1",
            durationMinutes = 45,
            url = "https://example.com",
            description = "Bring the receipt",
            recurrenceRule = RecurrenceRule(
                frequency = RecurrenceFrequency.WEEKLY,
                interval = 2,
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                untilDateString = "2026-12-31"
            ),
            isDeleted = true,
            isPinned = true,
            updatedAt = 104L
        ),
        BulletedListBlock(
            id = "bullet-1",
            text = "First",
            indentationLevel = 3,
            inlineSpans = listOf(everyInlineStyle),
            updatedAt = 105L
        ),
        NumberedListBlock(
            id = "number-1",
            text = "Step",
            number = 7,
            indentationLevel = 1,
            inlineSpans = listOf(everyInlineStyle),
            updatedAt = 106L
        ),
        ToggleBlock(
            id = "toggle-1",
            text = "Details",
            isExpanded = false,
            indentationLevel = 1,
            inlineSpans = listOf(everyInlineStyle),
            updatedAt = 107L
        ),
        CodeBlock(
            id = "code-1",
            code = "val answer = 42",
            language = "kotlin",
            indentationLevel = 1,
            textAlignment = TextAlignment.LEFT,
            isBold = true,
            updatedAt = 108L
        ),
        BookmarkBlock(
            id = "bookmark-1",
            url = "https://example.com/article",
            title = "An article",
            description = "Worth reading",
            previewImageUrl = "https://example.com/preview.png",
            indentationLevel = 1,
            isPinned = true,
            updatedAt = 109L
        ),
        LinkedNoteBlock(
            id = "linked-1",
            linkedNoteId = "note-42",
            showIcon = false,
            showCoverImage = true,
            indentationLevel = 1,
            updatedAt = 110L
        ),
        ImageBlock(
            id = "image-1",
            localFilePath = "stored-image.png",
            indentationLevel = 1,
            isPinned = true,
            updatedAt = 111L
        ),
        DocumentBlock(
            id = "document-1",
            localFilePath = "stored-document.pdf",
            fileName = "invoice.pdf",
            mimeType = "application/pdf",
            fileSizeString = "1.2 MB",
            indentationLevel = 1,
            updatedAt = 112L
        ),
        populatedDatabaseBlock(),
        TableBlock(
            id = "table-1",
            rows = listOf(listOf("Header A", "Header B"), listOf("Cell A", "Cell B")),
            cellStyles = mapOf(
                "0:0" to TableCellStyle(
                    backgroundColorHex = "#FFEEEEEE",
                    textColorHex = "#FF111111",
                    isBold = true,
                    isItalic = true,
                    isUnderlined = true,
                    isStrikeThrough = true,
                    isCode = true,
                    contentType = TableCellContentType.EMAIL,
                    alignment = TextAlignment.CENTER
                )
            ),
            rowStyles = mapOf("0" to TableCellStyle(isBold = true)),
            columnStyles = mapOf("1" to TableCellStyle(contentType = TableCellContentType.LINK)),
            columnWidths = mapOf("0" to 180, "1" to 240),
            indentationLevel = 1,
            updatedAt = 113L
        ),
        VoiceBlock(
            id = "voice-1",
            localFilePath = "stored-voice.m4a",
            durationSeconds = 42,
            indentationLevel = 1,
            updatedAt = 114L
        ),
        SolidDividerBlock(
            id = "solid-divider-1",
            indentationLevel = 1,
            isPinned = true,
            updatedAt = 116L
        ),
        ThreeDotDividerBlock(
            id = "dot-divider-1",
            indentationLevel = 1,
            updatedAt = 117L
        )
    )
}
