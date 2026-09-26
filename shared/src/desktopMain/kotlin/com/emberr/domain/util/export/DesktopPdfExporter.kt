package com.emberr.domain.util.export

import com.emberr.domain.model.BulletedListBlock
import com.emberr.domain.model.CheckboxBlock
import com.emberr.domain.model.CodeBlock
import com.emberr.domain.model.HeadingBlock
import com.emberr.domain.model.ImageBlock
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NumberedListBlock
import com.emberr.domain.model.QuoteBlock
import com.emberr.domain.model.SolidDividerBlock
import com.emberr.domain.model.TableBlock
import com.emberr.domain.model.TextBlock
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Color
import java.io.File
import kotlin.text.ifEmpty

@Throws(Exception::class)
fun generateDesktopPdf(file: File, title: String, blocks: List<NoteBlock>) {
    val document = PDDocument()
    document.use { document ->
        var page = PDPage()
        document.addPage(page)

        var contentStream = PDPageContentStream(document, page)

        val titleFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val bodyFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val boldFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val italicFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        val boldItalicFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE)
        val codeFont = PDType1Font(Standard14Fonts.FontName.COURIER)

        val margin = 72f
        val pageWidth = page.mediaBox.width
        val pageHeight = page.mediaBox.height

        var currentY = pageHeight - margin

        fun checkPagination(neededHeight: Float) {
            if (currentY - neededHeight < margin) {
                contentStream.close()
                page = PDPage()
                document.addPage(page)
                contentStream = PDPageContentStream(document, page)
                currentY = pageHeight - margin
            }
        }

        fun wrapText(text: String, font: PDType1Font, fontSize: Float, maxWidth: Float): List<String> {
            val lines = mutableListOf<String>()
            var currentLine = ""

            val safeText = text.replace(Regex("[^\\x20-\\x7E\\u00A0-\\u00FF\\u2022\\u201C\\u201D\\u2018\\u2019\\u2013\\u2014]"), "")
            val words = safeText.split(" ")

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                try {
                    val width = (font.getStringWidth(testLine) / 1000f) * fontSize
                    if (width > maxWidth && currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                } catch (_: IllegalArgumentException) {
                    val stripped = word.replace(Regex("[^\\x20-\\x7E]"), "")
                    val testLineStripped = if (currentLine.isEmpty()) stripped else "$currentLine $stripped"
                    currentLine = testLineStripped
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)

            return lines.ifEmpty { listOf("") }
        }

        val safeTitle = title.ifBlank { "Untitled Note" }
        val titleLines = wrapText(safeTitle, titleFont, 24f, pageWidth - (margin * 2))

        for (line in titleLines) {
            checkPagination(28f)
            if (line.isNotBlank()) {
                contentStream.beginText()
                contentStream.setFont(titleFont, 24f)
                contentStream.newLineAtOffset(margin, currentY)
                contentStream.showText(line)
                contentStream.endText()
            }
            currentY -= 28f
        }
        currentY -= 12f

        for (block in blocks) {
            if (block.isDeleted) continue
            val indent = block.indentationLevel * 20f
            val startX = margin + indent
            val maxWidth = pageWidth - margin - startX

            when (block) {
                is TextBlock, is HeadingBlock, is BulletedListBlock, is NumberedListBlock, is CheckboxBlock, is QuoteBlock -> {
                    val isHeading = block is HeadingBlock
                    val fontSize = if (isHeading && block.level == 1) 18f
                    else if (isHeading) 14f else 12f

                    val useBold = isHeading || when(block) { is TextBlock -> block.isBold; is CheckboxBlock -> block.isBold; is BulletedListBlock -> block.isBold; is NumberedListBlock -> block.isBold; is QuoteBlock -> block.isBold }
                    val useItalic = when(block) { is TextBlock -> block.isItalic; is CheckboxBlock -> block.isItalic; is BulletedListBlock -> block.isItalic; is NumberedListBlock -> block.isItalic; is QuoteBlock -> block.isItalic; else -> false }

                    val currentFont = when {
                        useBold && useItalic -> boldItalicFont
                        useBold -> boldFont
                        useItalic -> italicFont
                        else -> bodyFont
                    }

                    val textStr = when (block) {
                        is TextBlock -> block.text
                        is HeadingBlock -> block.text
                        is BulletedListBlock -> "-  ${block.text}"
                        is NumberedListBlock -> "${block.number}.  ${block.text}"
                        is CheckboxBlock -> "${if (block.isChecked) "[x]" else "[ ]"}  ${block.text}"
                        is QuoteBlock -> "\"${block.text}\""
                    }

                    val leading = fontSize * 1.4f

                    if (block is TextBlock && textStr.isBlank()) {
                        checkPagination(leading)
                        currentY -= (leading + 8f)
                        continue
                    }

                    val paragraphs = textStr.split("\n", "\r\n")

                    for (p in paragraphs) {
                        val lines = wrapText(p, currentFont, fontSize, maxWidth - (if (block is QuoteBlock) 15f else 0f))
                        for (line in lines) {
                            checkPagination(leading)

                            if (line.isNotBlank()) {
                                contentStream.beginText()
                                contentStream.setFont(currentFont, fontSize)
                                contentStream.newLineAtOffset(startX + (if (block is QuoteBlock) 15f else 0f), currentY)
                                contentStream.showText(line)
                                contentStream.endText()
                            }

                            currentY -= leading
                        }
                    }

                    currentY -= if (isHeading) 2f else 8f
                }
                is SolidDividerBlock -> {
                    checkPagination(20f)
                    currentY -= 4f
                    contentStream.moveTo(startX, currentY)
                    contentStream.lineTo(pageWidth - margin, currentY)
                    contentStream.setStrokingColor(Color.LIGHT_GRAY)
                    contentStream.stroke()
                    currentY -= 12f
                }

                is CodeBlock -> {
                    val fontSize = 10f
                    val leading = fontSize * 1.4f
                    val padding = 10f

                    val paragraphs = block.code.split("\n", "\r\n")

                    for (p in paragraphs) {
                        val pText = p.ifEmpty { " " }
                        val lines = wrapText(pText, codeFont, fontSize, maxWidth - (padding * 2))

                        for (line in lines) {
                            checkPagination(leading)

                            contentStream.setNonStrokingColor(Color(245, 245, 245))
                            contentStream.addRect(startX, currentY - 4f, maxWidth, leading)
                            contentStream.fill()

                            contentStream.setNonStrokingColor(Color.BLACK)
                            if (line.isNotBlank()) {
                                contentStream.beginText()
                                contentStream.setFont(codeFont, fontSize)
                                contentStream.newLineAtOffset(startX + padding, currentY)
                                contentStream.showText(line)
                                contentStream.endText()
                            }

                            currentY -= leading
                        }
                    }
                    currentY -= 8f // Gap after the code block ends
                }

                is ImageBlock -> {
                    val filePath = block.localFilePath ?: continue
                    val cleanPath = filePath.removePrefix("file://")

                    // Safely locate the file using existing Desktop storage logic
                    val imgFile = if (cleanPath.contains("/") || cleanPath.contains("\\")) {
                        File(cleanPath)
                    } else {
                        File(System.getProperty("user.home"), ".emberr/media/$cleanPath")
                    }

                    if (imgFile.exists()) {
                        try {
                            // Load image into PDFBox
                            val pdImage = PDImageXObject.createFromFile(imgFile.absolutePath, document)

                            // Calculate aspect ratio
                            val aspectRatio = pdImage.height.toFloat() / pdImage.width.toFloat()
                            var drawWidth = maxWidth
                            var drawHeight = drawWidth * aspectRatio

                            if (drawHeight > 400f) {
                                drawHeight = 400f
                                drawWidth = drawHeight / aspectRatio
                            }

                            checkPagination(drawHeight + 20f)

                            // PDFBox draws from the bottom-left corner of the image, so we subtract height first
                            currentY -= drawHeight

                            contentStream.drawImage(pdImage, startX, currentY, drawWidth, drawHeight)

                            currentY -= 15f // Add spacing after image
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                is TableBlock -> {
                    val colCount = block.rows.firstOrNull()?.size ?: 0
                    if (colCount == 0) continue

                    val rowHeight = 22f
                    val cellPadding = 4f
                    val tableFontSize = 10f
                    val colWidth = maxWidth / colCount

                    contentStream.setStrokingColor(Color.LIGHT_GRAY)
                    contentStream.setLineWidth(0.5f)

                    fun truncateForCell(text: String, maxW: Float): String {
                        val safeText = text.replace(Regex("[^\\x20-\\x7E\\u00A0-\\u00FF]"), "").trim()
                        if (safeText.isEmpty()) return ""
                        try {
                            if ((bodyFont.getStringWidth(safeText) / 1000f) * tableFontSize <= maxW) return safeText
                            var truncated = safeText
                            while (truncated.isNotEmpty() && (bodyFont.getStringWidth("$truncated...") / 1000f) * tableFontSize > maxW) {
                                truncated = truncated.dropLast(1)
                            }
                            return if (truncated.isEmpty()) "" else "$truncated..."
                        } catch (_: Exception) {
                            return ""
                        }
                    }

                    for (row in block.rows) {
                        checkPagination(rowHeight)
                        var currentX = startX

                        for (cellText in row) {
                            contentStream.addRect(currentX, currentY - rowHeight, colWidth, rowHeight)
                            contentStream.stroke()

                            val text = truncateForCell(cellText.replace(Regex("[\\n\\r\\t]"), " "), colWidth - (cellPadding * 2))
                            if (text.isNotEmpty()) {
                                contentStream.beginText()
                                contentStream.setFont(bodyFont, tableFontSize)
                                contentStream.newLineAtOffset(currentX + cellPadding, currentY - rowHeight + 7f)
                                contentStream.showText(text)
                                contentStream.endText()
                            }
                            currentX += colWidth
                        }
                        currentY -= rowHeight
                    }

                    currentY -= 16f
                }
                else -> { /* Ignore complex blocks */ }
            }
        }

        contentStream.close()
        document.save(file)
    }
}