package com.emberr.domain.model

import androidx.compose.runtime.Immutable
import com.emberr.data.local.room.entity.NoteMetadataEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The root container for a saved note.
 * It holds a list of blocks and a version number so I can handle migrations easily if the structure changes later.
 */

@Immutable
@Serializable
data class NoteContent(
    val version: Int = 1,
    val blocks: List<NoteBlock>
)

/**
 * A single cross-note search hit. [matchedText] is whichever snippet actually contains the
 * query - the note's own snippet/title for a metadata match, or the flattened text of the
 * first matching block for a content-only match - so the UI has one field to highlight.
 */
@Immutable
data class NoteSearchResult(
    val note: NoteMetadataEntity,
    val matchedText: String
)

/**
 * The base class for everything in the editor.
 * The editor is block-based, meaning every paragraph, image, or list item is its own distinct, serializable block.
 */

@Immutable
@Serializable
sealed class NoteBlock {
    abstract val id: String
    abstract val indentationLevel: Int
    abstract val isBold: Boolean
    abstract val isItalic: Boolean
    abstract val isStrikeThrough: Boolean
    abstract val isUnderlined: Boolean
    abstract val isHighlighted: Boolean
    abstract val isDeleted: Boolean
    abstract val isPinned: Boolean
    abstract val updatedAt: Long
}

/** Paragraph alignment for the text-bearing block types - never applies to media blocks. */
@Serializable
enum class TextAlignment { LEFT, RIGHT, CENTER, JUSTIFY }

/**
 * A character-range formatting run within a block's text, e.g. bolding just the word "urgent" in
 * "call the plumber, urgent" instead of the whole line. [start]/[end] are indices into that block's
 * own `text` (end-exclusive) - see NoteBlockItem's RichTextVisualTransformation for how these get
 * rendered, and BaseEditorViewModel.shiftSpansForEdit for how they stay valid as the text is edited.
 */
const val defaultHighlightColorName = "yellow"

@Immutable
@Serializable
data class InlineSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikeThrough: Boolean = false,
    val underline: Boolean = false,
    val highlight: Boolean = false,
    val highlightColorName: String? = null
)

@Immutable
@Serializable
@SerialName("text")
data class TextBlock(
    override val id: String,
    val text: String = "",
    override val indentationLevel: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val inlineSpans: List<InlineSpan> = emptyList(),
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    val highlightColorName: String? = null,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("heading")
data class HeadingBlock(
    override val id: String,
    val text: String = "",
    val level: Int = 1,
    override val indentationLevel: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val inlineSpans: List<InlineSpan> = emptyList(),
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    val highlightColorName: String? = null,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("quote")
data class QuoteBlock(
    override val id: String,
    val text: String = "",
    override val indentationLevel: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val inlineSpans: List<InlineSpan> = emptyList(),
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    val highlightColorName: String? = null,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("checkbox")
data class CheckboxBlock(
    override val id: String,
    val text: String = "",
    val isChecked: Boolean = false,
    override val indentationLevel: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val inlineSpans: List<InlineSpan> = emptyList(),
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    val highlightColorName: String? = null,
    val reminderTimestamp: Long? = null,
    val completedAt: Long? = null,
    val categoryId: String? = null,
    val durationMinutes: Int = 30,
    val url: String? = null,
    val description: String? = null,
    val recurrenceRule: RecurrenceRule? = null,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("bullet")
data class BulletedListBlock(
    override val id: String,
    val text: String = "",
    override val indentationLevel: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val inlineSpans: List<InlineSpan> = emptyList(),
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    val highlightColorName: String? = null,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("number")
data class NumberedListBlock(
    override val id: String,
    val text: String = "",
    val number: Int = 1,
    override val indentationLevel: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val inlineSpans: List<InlineSpan> = emptyList(),
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    val highlightColorName: String? = null,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("toggle")
data class ToggleBlock(
    override val id: String,
    val text: String = "",
    val isExpanded: Boolean = true,
    override val indentationLevel: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    val inlineSpans: List<InlineSpan> = emptyList(),
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    val highlightColorName: String? = null,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("code")
data class CodeBlock(
    override val id: String,
    val code: String = "",
    val language: String = "plaintext",
    override val indentationLevel: Int = 0,
    val textAlignment: TextAlignment = TextAlignment.LEFT,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("bookmark")
data class BookmarkBlock(
    override val id: String,
    val url: String = "",
    val title: String? = null,
    val description: String? = null,
    val previewImageUrl: String? = null,
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("linked_note")
data class LinkedNoteBlock(
    override val id: String,
    val linkedNoteId: String,
    val showIcon: Boolean = true,
    val showCoverImage: Boolean = false,
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("image")
data class ImageBlock(
    override val id: String,
    val localFilePath: String? = null,
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("document")
data class DocumentBlock(
    override val id: String,
    val localFilePath: String? = null,
    val fileName: String = "Unknown Document",
    val mimeType: String = "application/octet-stream",
    val fileSizeString: String = "",
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

enum class TableCellContentType { NONE, LINK, PHONE, EMAIL }

/**
 * Free-form styling for a table cell/row/column. Kept as one flat object (rather than per-field
 * overrides) since [TableBlock] resolves style by precedence - cell wins over row wins over
 * column - so a "reset" is just deleting the map entry instead of unwinding partial field merges.
 */
@Immutable
@Serializable
data class TableCellStyle(
    val backgroundColorHex: String? = null,
    val textColorHex: String? = null,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderlined: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val isCode: Boolean = false,
    val contentType: TableCellContentType = TableCellContentType.NONE,
    val alignment: TextAlignment? = null
)

@Immutable
@Serializable
@SerialName("table")
data class TableBlock(
    override val id: String,
    val rows: List<List<String>> = listOf(listOf("", ""), listOf("", "")),
    val cellStyles: Map<String, TableCellStyle> = emptyMap(),
    val cellSpans: Map<String, List<InlineSpan>> = emptyMap(),
    val rowStyles: Map<String, TableCellStyle> = emptyMap(),
    val columnStyles: Map<String, TableCellStyle> = emptyMap(),
    val columnWidths: Map<String, Int> = emptyMap(),
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("voice")
data class VoiceBlock(
    override val id: String,
    val localFilePath: String? = null,
    val durationSeconds: Int = 0,
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("canvas")
data class CanvasBlock(
    override val id: String,
    val canvasNoteId: String,
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("solid_divider")
data class SolidDividerBlock(
    override val id: String,
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

@Immutable
@Serializable
@SerialName("dot_divider")
data class ThreeDotDividerBlock(
    override val id: String,
    override val indentationLevel: Int = 0,
    override val isBold: Boolean = false,
    override val isItalic: Boolean = false,
    override val isStrikeThrough: Boolean = false,
    override val isUnderlined: Boolean = false,
    override val isHighlighted: Boolean = false,
    override val isDeleted: Boolean = false,
    override val isPinned: Boolean = false,
    override val updatedAt: Long = 0L
) : NoteBlock()

fun NoteBlock.markDeleted(): NoteBlock = when (this) {
    is TextBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is HeadingBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is CheckboxBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is BulletedListBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is NumberedListBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is ToggleBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is CodeBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is BookmarkBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is LinkedNoteBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is ImageBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is DocumentBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is TableBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is VoiceBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is CanvasBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is QuoteBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is SolidDividerBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is ThreeDotDividerBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
}

fun NoteBlock.textAlignmentOrNull(): TextAlignment? = when (this) {
    is TextBlock -> textAlignment
    is HeadingBlock -> textAlignment
    is QuoteBlock -> textAlignment
    is CheckboxBlock -> textAlignment
    is BulletedListBlock -> textAlignment
    is NumberedListBlock -> textAlignment
    is ToggleBlock -> textAlignment
    is CodeBlock -> textAlignment
    else -> null
}

fun NoteBlock.withTextAlignment(alignment: TextAlignment, now: Long): NoteBlock = when (this) {
    is TextBlock -> copy(textAlignment = alignment, updatedAt = now)
    is HeadingBlock -> copy(textAlignment = alignment, updatedAt = now)
    is QuoteBlock -> copy(textAlignment = alignment, updatedAt = now)
    is CheckboxBlock -> copy(textAlignment = alignment, updatedAt = now)
    is BulletedListBlock -> copy(textAlignment = alignment, updatedAt = now)
    is NumberedListBlock -> copy(textAlignment = alignment, updatedAt = now)
    is ToggleBlock -> copy(textAlignment = alignment, updatedAt = now)
    is CodeBlock -> copy(textAlignment = alignment, updatedAt = now)
    else -> this
}

fun NoteBlock.inlineSpansOrEmpty(): List<InlineSpan> = when (this) {
    is TextBlock -> inlineSpans
    is HeadingBlock -> inlineSpans
    is QuoteBlock -> inlineSpans
    is CheckboxBlock -> inlineSpans
    is BulletedListBlock -> inlineSpans
    is NumberedListBlock -> inlineSpans
    is ToggleBlock -> inlineSpans
    else -> emptyList()
}

fun NoteBlock.highlightColorNameOrNull(): String? = when (this) {
    is TextBlock -> highlightColorName
    is HeadingBlock -> highlightColorName
    is QuoteBlock -> highlightColorName
    is CheckboxBlock -> highlightColorName
    is BulletedListBlock -> highlightColorName
    is NumberedListBlock -> highlightColorName
    is ToggleBlock -> highlightColorName
    else -> null
}

fun NoteBlock.withHighlight(isHighlighted: Boolean, colorName: String?, now: Long): NoteBlock = when (this) {
    is TextBlock -> copy(isHighlighted = isHighlighted, highlightColorName = colorName, updatedAt = now)
    is HeadingBlock -> copy(isHighlighted = isHighlighted, highlightColorName = colorName, updatedAt = now)
    is QuoteBlock -> copy(isHighlighted = isHighlighted, highlightColorName = colorName, updatedAt = now)
    is CheckboxBlock -> copy(isHighlighted = isHighlighted, highlightColorName = colorName, updatedAt = now)
    is BulletedListBlock -> copy(isHighlighted = isHighlighted, highlightColorName = colorName, updatedAt = now)
    is NumberedListBlock -> copy(isHighlighted = isHighlighted, highlightColorName = colorName, updatedAt = now)
    is ToggleBlock -> copy(isHighlighted = isHighlighted, highlightColorName = colorName, updatedAt = now)
    else -> this
}

fun NoteBlock.withInlineSpans(spans: List<InlineSpan>, now: Long): NoteBlock = when (this) {
    is TextBlock -> copy(inlineSpans = spans, updatedAt = now)
    is HeadingBlock -> copy(inlineSpans = spans, updatedAt = now)
    is QuoteBlock -> copy(inlineSpans = spans, updatedAt = now)
    is CheckboxBlock -> copy(inlineSpans = spans, updatedAt = now)
    is BulletedListBlock -> copy(inlineSpans = spans, updatedAt = now)
    is NumberedListBlock -> copy(inlineSpans = spans, updatedAt = now)
    is ToggleBlock -> copy(inlineSpans = spans, updatedAt = now)
    else -> this
}

fun NoteBlock.withPin(pinned: Boolean, now: Long): NoteBlock = when (this) {
    is TextBlock -> copy(isPinned = pinned, updatedAt = now)
    is HeadingBlock -> copy(isPinned = pinned, updatedAt = now)
    is CheckboxBlock -> copy(isPinned = pinned, updatedAt = now)
    is BulletedListBlock -> copy(isPinned = pinned, updatedAt = now)
    is NumberedListBlock -> copy(isPinned = pinned, updatedAt = now)
    is ToggleBlock -> copy(isPinned = pinned, updatedAt = now)
    is CodeBlock -> copy(isPinned = pinned, updatedAt = now)
    is BookmarkBlock -> copy(isPinned = pinned, updatedAt = now)
    is LinkedNoteBlock -> copy(isPinned = pinned, updatedAt = now)
    is ImageBlock -> copy(isPinned = pinned, updatedAt = now)
    is DocumentBlock -> copy(isPinned = pinned, updatedAt = now)
    is TableBlock -> copy(isPinned = pinned, updatedAt = now)
    is VoiceBlock -> copy(isPinned = pinned, updatedAt = now)
    is CanvasBlock -> copy(isPinned = pinned, updatedAt = now)
    is QuoteBlock -> copy(isPinned = pinned, updatedAt = now)
    is SolidDividerBlock -> copy(isPinned = pinned, updatedAt = now)
    is ThreeDotDividerBlock -> copy(isPinned = pinned, updatedAt = now)
}

fun NoteBlock.withUpdatedAt(now: Long): NoteBlock = when (this) {
    is TextBlock -> copy(updatedAt = now)
    is HeadingBlock -> copy(updatedAt = now)
    is CheckboxBlock -> copy(updatedAt = now)
    is BulletedListBlock -> copy(updatedAt = now)
    is NumberedListBlock -> copy(updatedAt = now)
    is ToggleBlock -> copy(updatedAt = now)
    is CodeBlock -> copy(updatedAt = now)
    is BookmarkBlock -> copy(updatedAt = now)
    is LinkedNoteBlock -> copy(updatedAt = now)
    is ImageBlock -> copy(updatedAt = now)
    is DocumentBlock -> copy(updatedAt = now)
    is TableBlock -> copy(updatedAt = now)
    is VoiceBlock -> copy(updatedAt = now)
    is CanvasBlock -> copy(updatedAt = now)
    is QuoteBlock -> copy(updatedAt = now)
    is SolidDividerBlock -> copy(updatedAt = now)
    is ThreeDotDividerBlock -> copy(updatedAt = now)
}

fun NoteBlock.withDeleted(deleted: Boolean, now: Long): NoteBlock = when (this) {
    is TextBlock -> copy(isDeleted = deleted, updatedAt = now)
    is HeadingBlock -> copy(isDeleted = deleted, updatedAt = now)
    is CheckboxBlock -> copy(isDeleted = deleted, updatedAt = now)
    is BulletedListBlock -> copy(isDeleted = deleted, updatedAt = now)
    is NumberedListBlock -> copy(isDeleted = deleted, updatedAt = now)
    is ToggleBlock -> copy(isDeleted = deleted, updatedAt = now)
    is CodeBlock -> copy(isDeleted = deleted, updatedAt = now)
    is BookmarkBlock -> copy(isDeleted = deleted, updatedAt = now)
    is LinkedNoteBlock -> copy(isDeleted = deleted, updatedAt = now)
    is ImageBlock -> copy(isDeleted = deleted, updatedAt = now)
    is DocumentBlock -> copy(isDeleted = deleted, updatedAt = now)
    is TableBlock -> copy(isDeleted = deleted, updatedAt = now)
    is VoiceBlock -> copy(isDeleted = deleted, updatedAt = now)
    is CanvasBlock -> copy(isDeleted = deleted, updatedAt = now)
    is QuoteBlock -> copy(isDeleted = deleted, updatedAt = now)
    is SolidDividerBlock -> copy(isDeleted = deleted, updatedAt = now)
    is ThreeDotDividerBlock -> copy(isDeleted = deleted, updatedAt = now)
}

// Rebuilds an entire note's content with fresh ids on every block - used when a note is
// created from a template so the copy never collides with the template's own rows in Room
// (block ids are primary keys there).
fun NoteContent.deepCopyWithNewIds(): NoteContent = copy(blocks = blocks.map { it.deepCopyWithNewIds() })

// Gives a single block a new id.
fun NoteBlock.deepCopyWithNewIds(): NoteBlock {
    val newId = UUID.randomUUID().toString()
    return when (this) {
        is TextBlock -> copy(id = newId)
        is HeadingBlock -> copy(id = newId)
        is QuoteBlock -> copy(id = newId)
        is CheckboxBlock -> copy(id = newId)
        is BulletedListBlock -> copy(id = newId)
        is NumberedListBlock -> copy(id = newId)
        is ToggleBlock -> copy(id = newId)
        is CodeBlock -> copy(id = newId)
        is BookmarkBlock -> copy(id = newId)
        is LinkedNoteBlock -> copy(id = newId)
        is ImageBlock -> copy(id = newId)
        is DocumentBlock -> copy(id = newId)
        is TableBlock -> copy(id = newId)
        is VoiceBlock -> copy(id = newId)
        is CanvasBlock -> copy(id = newId)
        is SolidDividerBlock -> copy(id = newId)
        is ThreeDotDividerBlock -> copy(id = newId)
    }
}