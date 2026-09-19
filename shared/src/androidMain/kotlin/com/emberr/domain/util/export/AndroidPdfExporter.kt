package com.emberr.domain.util.export

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import com.emberr.ui.theme.HighlightColor
import com.emberr.domain.model.*
import com.emberr.domain.util.media.MediaStorageHelper
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withSave

private fun pdfHighlightColorFor(colorName: String?): Int =
    HighlightColor.named(colorName).lightBackground.toArgb()

fun generateAndSaveAndroidPdf(
    context: Context,
    uri: Uri,
    title: String,
    blocks: List<NoteBlock>,
    mediaStorageHelper: MediaStorageHelper
) {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val textPaint = android.text.TextPaint().apply {
            textSize = 12f
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }

        val titlePaint = android.text.TextPaint().apply {
            textSize = 24f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
            isAntiAlias = true
        }

        var currentY = 70f
        val startX = 50f
        val endX = pageWidth - 50f
        val maxContentWidth = (endX - startX).toInt()
        val maxY = pageHeight - 50f

        fun checkPagination(neededHeight: Float) {
            if (currentY + neededHeight > maxY) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                currentY = 70f
            }
        }

        val safeTitle = title.ifBlank { "Untitled Note" }
        val titleLayout = android.text.StaticLayout.Builder.obtain(safeTitle, 0, safeTitle.length, titlePaint, maxContentWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .build()

        checkPagination(titleLayout.height.toFloat())
        canvas.withTranslation(startX, currentY) {
            titleLayout.draw(this)
        }
        currentY += titleLayout.height + 30f

        for (block in blocks) {
            if (block.isDeleted) continue

            val indent = block.indentationLevel * 20f
            val availableWidth = maxContentWidth - indent.toInt()
            if (availableWidth <= 0) continue

            when (block) {
                is TextBlock, is HeadingBlock, is BulletedListBlock, is NumberedListBlock, is CheckboxBlock, is QuoteBlock -> {
                    val isBold = when (block) { is TextBlock -> block.isBold; is CheckboxBlock -> block.isBold; is BulletedListBlock -> block.isBold; is NumberedListBlock -> block.isBold; is QuoteBlock -> block.isBold; else -> false }
                    val isItalic = when (block) { is TextBlock -> block.isItalic; is CheckboxBlock -> block.isItalic; is BulletedListBlock -> block.isItalic; is NumberedListBlock -> block.isItalic; is QuoteBlock -> block.isItalic; else -> false }
                    val isStrike = when (block) { is TextBlock -> block.isStrikeThrough; is CheckboxBlock -> block.isStrikeThrough; is BulletedListBlock -> block.isStrikeThrough; is NumberedListBlock -> block.isStrikeThrough; is QuoteBlock -> block.isStrikeThrough; else -> false }
                    val isUnder = when (block) { is TextBlock -> block.isUnderlined; is CheckboxBlock -> block.isUnderlined; is BulletedListBlock -> block.isUnderlined; is NumberedListBlock -> block.isUnderlined; is QuoteBlock -> block.isUnderlined; else -> false }
                    val isHighlighted = when (block) { is TextBlock -> block.isHighlighted; is CheckboxBlock -> block.isHighlighted; is BulletedListBlock -> block.isHighlighted; is NumberedListBlock -> block.isHighlighted; is QuoteBlock -> block.isHighlighted; else -> false }

                    textPaint.isFakeBoldText = isBold || block is HeadingBlock
                    textPaint.textSkewX = if (isItalic) -0.25f else 0f
                    textPaint.isStrikeThruText = isStrike
                    textPaint.isUnderlineText = isUnder
                    textPaint.bgColor =
                        if (isHighlighted) pdfHighlightColorFor(block.highlightColorNameOrNull()) else 0
                    textPaint.textSize = if (block is HeadingBlock) (if (block.level == 1) 18f else 14f) else 12f

                    val textStr = when (block) {
                        is TextBlock -> block.text
                        is HeadingBlock -> block.text
                        is BulletedListBlock -> "•  ${block.text}"
                        is NumberedListBlock -> "${block.number}.  ${block.text}"
                        is CheckboxBlock -> "${if (block.isChecked) "[x]" else "[ ]"}  ${block.text}"
                        is QuoteBlock -> block.text
                    }

                    if (textStr.isBlank() && block is TextBlock) {
                        currentY += (12f * 1.4f) + 12f
                        continue
                    }

                    val drawX = startX + indent + if (block is QuoteBlock) 15f else 0f
                    val actualAvailableWidth = availableWidth - if (block is QuoteBlock) 15 else 0

                    val layout = android.text.StaticLayout.Builder.obtain(textStr, 0, textStr.length, textPaint, actualAvailableWidth)
                        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                        .build()

                    val blockHeight = layout.height.toFloat()
                    val totalHeightNeeded = if (block is QuoteBlock) blockHeight + 10f else blockHeight

                    checkPagination(totalHeightNeeded)

                    canvas.withSave {
                        translate(drawX, currentY)
                        layout.draw(this)
                    }

                    if (block is QuoteBlock) {
                        val linePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            strokeWidth = 3f
                        }
                        canvas.drawLine(startX + indent, currentY, startX + indent, currentY + blockHeight, linePaint)
                    }

                    currentY += totalHeightNeeded + if (block is HeadingBlock) 2f else 12f
                }

                is CodeBlock -> {
                    textPaint.typeface = android.graphics.Typeface.MONOSPACE
                    textPaint.textSize = 10f
                    textPaint.isFakeBoldText = false

                    val padding = 10f
                    val bgPaint = android.graphics.Paint().apply { color = "#F5F5F5".toColorInt() }

                    for (p in block.code.split("\n")) {
                        val pText = p.ifEmpty { " " }
                        val layout = android.text.StaticLayout.Builder.obtain(pText, 0, pText.length, textPaint, availableWidth - (padding * 2).toInt())
                            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                            .build()

                        for (i in 0 until layout.lineCount) {
                            val lineText = pText.substring(layout.getLineStart(i), layout.getLineEnd(i)).replace("\n", "")
                            val singleLineLayout = android.text.StaticLayout.Builder.obtain(lineText, 0, lineText.length, textPaint, availableWidth - (padding * 2).toInt())
                                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                                .build()

                            val lineHeight = singleLineLayout.height.toFloat()
                            checkPagination(lineHeight)

                            canvas.drawRect(startX + indent, currentY, startX + indent + availableWidth, currentY + lineHeight, bgPaint)
                            canvas.withTranslation(startX + indent + padding, currentY) {
                                singleLineLayout.draw(this)
                            }

                            currentY += lineHeight
                        }
                    }

                    textPaint.typeface = android.graphics.Typeface.DEFAULT
                    currentY += 12f
                }

                is SolidDividerBlock -> {
                    checkPagination(20f)
                    val linePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.LTGRAY
                        strokeWidth = 1f
                    }
                    currentY += 10f
                    canvas.drawLine(startX + indent, currentY, startX + availableWidth, currentY, linePaint)
                    currentY += 10f
                }

                is DatabaseBlock -> {
                    textPaint.textSize = 10f
                    textPaint.isFakeBoldText = true

                    val validCols = block.columns.filter { !it.isDeleted }
                    if (validCols.isEmpty()) continue

                    val colWidth = availableWidth / validCols.size
                    val rowHeight = 20f

                    checkPagination(rowHeight)

                    var currentX = startX + indent
                    val borderPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.LTGRAY
                        style = android.graphics.Paint.Style.STROKE
                    }

                    for (col in validCols) {
                        val truncated = android.text.TextUtils.ellipsize(col.name, textPaint, colWidth.toFloat() - 10f, android.text.TextUtils.TruncateAt.END).toString()
                        canvas.drawText(truncated, currentX + 5f, currentY + 14f, textPaint)
                        canvas.drawRect(currentX, currentY, currentX + colWidth, currentY + rowHeight, borderPaint)
                        currentX += colWidth
                    }
                    currentY += rowHeight
                    textPaint.isFakeBoldText = false

                    for (row in block.rows.filter { !it.isDeleted }) {
                        checkPagination(rowHeight)
                        currentX = startX + indent
                        for (col in validCols) {
                            val cellText = row.cells[col.id].displayText().replace("\n", " ")
                            val truncated = android.text.TextUtils.ellipsize(cellText, textPaint, colWidth.toFloat() - 10f, android.text.TextUtils.TruncateAt.END).toString()
                            canvas.drawText(truncated, currentX + 5f, currentY + 14f, textPaint)
                            canvas.drawRect(currentX, currentY, currentX + colWidth, currentY + rowHeight, borderPaint)
                            currentX += colWidth
                        }
                        currentY += rowHeight
                    }
                    currentY += 15f
                }

                is ImageBlock -> {
                    val filePath = block.localFilePath ?: continue
                    val imgFile = java.io.File(mediaStorageHelper.getAbsoluteMediaPath(filePath))

                    if (imgFile.exists()) {
                        try {
                            val bitmap = android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath) ?: continue
                            val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
                            var drawWidth = availableWidth.toFloat()
                            var drawHeight = drawWidth * aspectRatio

                            if (drawHeight > 400f) {
                                drawHeight = 400f
                                drawWidth = drawHeight / aspectRatio
                            }

                            checkPagination(drawHeight + 20f)

                            canvas.drawBitmap(bitmap, null, android.graphics.RectF(
                                startX + indent, currentY,
                                startX + indent + drawWidth, currentY + drawHeight
                            ), null)

                            currentY += drawHeight + 15f
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                is TableBlock -> {
                    val colCount = block.rows.firstOrNull()?.size ?: 0
                    if (colCount == 0) continue

                    val colWidth = availableWidth / colCount
                    val rowHeight = 20f
                    textPaint.textSize = 10f
                    val borderPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.LTGRAY
                        style = android.graphics.Paint.Style.STROKE
                    }

                    for (row in block.rows) {
                        checkPagination(rowHeight)
                        var currentX = startX + indent
                        for (cellText in row) {
                            val truncated = android.text.TextUtils.ellipsize(cellText, textPaint, colWidth.toFloat() - 10f, android.text.TextUtils.TruncateAt.END).toString()
                            canvas.drawText(truncated, currentX + 5f, currentY + 14f, textPaint)
                            canvas.drawRect(currentX, currentY, currentX + colWidth, currentY + rowHeight, borderPaint)
                            currentX += colWidth
                        }
                        currentY += rowHeight
                    }
                    currentY += 15f
                }

                else -> {}
            }
        }

        pdfDocument.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { pdfDocument.writeTo(it) }
        pdfDocument.close()
        Toast.makeText(context, "PDF saved successfully", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
    }
}