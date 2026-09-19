package com.emberr.domain.model

import androidx.compose.runtime.Immutable
import com.emberr.data.local.room.entity.NoteMetadataEntity
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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

/** Paragraph alignment for the text-bearing block types - never applies to media/database blocks. */
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

enum class ViewType { TABLE, KANBAN, GALLERY }

/** Card density for a GALLERY view - purely a layout knob. */
enum class GalleryCardSize { SMALL, MEDIUM, LARGE }

@Immutable
@Serializable
data class DatabaseView(
    val id: String,
    val name: String,
    val type: ViewType,
    val activeSorts: List<SortConfig> = emptyList(),
    val activeFilters: List<FilterConfig> = emptyList(),
    val groupByColumnId: String? = null,
    val hiddenGroups: List<String> = emptyList(),
    val groupOrder: List<String> = emptyList(),
    val galleryCardSize: GalleryCardSize = GalleryCardSize.MEDIUM
)

@Immutable
@Serializable
@SerialName("database")
data class DatabaseBlock(
    override val id: String,
    val title: String = "",
    val columns: List<DatabaseColumn>,
    val rows: List<DatabaseRow>,
    val views: List<DatabaseView> = emptyList(),
    val activeViewId: String? = null,
    val cellStyles: Map<String, TableCellStyle> = emptyMap(),
    val cellSpans: Map<String, List<InlineSpan>> = emptyMap(),
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
data class DatabaseColumn(
    val id: String,
    val databaseId: String,
    val name: String,
    val type: ColumnType,
    val width: Int = 140,
    val formulaExpression: String? = null,
    val aggregationType: String? = null,
    val currencySymbol: String? = null,
    val isFormulaCurrency: Boolean = false,
    val isDeleted: Boolean = false,
    val isNameManuallySet: Boolean = false,
    val updatedAt: Long = 0L
)

@Immutable
@Serializable
data class DatabaseRow(
    val id: String,
    val databaseId: String,
    val cells: Map<String, CellData>,
    val isDeleted: Boolean = false,
    val updatedAt: Long = 0L
)

/**
 * Every value a database cell can hold. Each [ColumnType] maps to exactly one subclass, so a cell's Kotlin type always matches what the column expects.
 * Nested (not top-level) so `Number`/`Boolean`/`Date` don't shadow the `kotlin.*` types of the same name.
 */
@Immutable
@Serializable
sealed class CellData {
    @Immutable
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : CellData()

    @Immutable
    @Serializable
    @SerialName("number")
    data class Number(val value: Double?) : CellData()

    @Immutable
    @Serializable
    @SerialName("boolean")
    data class Boolean(val value: kotlin.Boolean) : CellData()

    @Immutable
    @Serializable
    @SerialName("date")
    data class Date(val timestamp: Long?) : CellData()

    @Immutable
    @Serializable
    @SerialName("tag_list")
    data class TagList(val tagIds: List<String>) : CellData()

    @Immutable
    @Serializable
    @SerialName("media_list")
    data class MediaList(val files: List<MediaItem>) : CellData()

    @Immutable
    @Serializable
    @SerialName("note_relation")
    data class NoteRelation(val noteIds: List<String>) : CellData()

    @Immutable
    @Serializable
    @SerialName("formula")
    data class Formula(val result: String) : CellData()
}

@Immutable
@Serializable
data class MediaItem(val fileName: String, val originalName: String)

/**
 * Canonical "cell as plain text" rendering, shared by export/PDF/search so they don't each
 * reimplement an 8-way `when` over [CellData].
 */
fun CellData?.displayText(): String = when (this) {
    null -> ""
    is CellData.Text -> value
    is CellData.Number -> value?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: ""
    is CellData.Boolean -> value.toString()
    is CellData.Date -> timestamp?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date.toString() } ?: ""
    is CellData.TagList -> tagIds.joinToString(",")
    is CellData.MediaList -> files.joinToString(",") { "${it.fileName}|${it.originalName}" }
    is CellData.NoteRelation -> noteIds.firstOrNull() ?: ""
    is CellData.Formula -> result
}

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
enum class ColumnType { TEXT, NUMBER, CHECKBOX, DATE, FORMULA, PHONE, EMAIL, TAGS, URL, FILES, PRIORITY, MONEY, AUDIO, NOTES, STATUS }

fun ColumnType.propertyLabel(): String = when (this) {
    ColumnType.TEXT -> "Text"
    ColumnType.NUMBER -> "Number"
    ColumnType.CHECKBOX -> "Checkbox"
    ColumnType.DATE -> "Date"
    ColumnType.FORMULA -> "Formula"
    ColumnType.PHONE -> "Phone"
    ColumnType.EMAIL -> "Email"
    ColumnType.TAGS -> "Tags"
    ColumnType.URL -> "URL"
    ColumnType.FILES -> "Files"
    ColumnType.PRIORITY -> "Priority"
    ColumnType.MONEY -> "Money"
    ColumnType.AUDIO -> "Audio"
    ColumnType.NOTES -> "Notes"
    ColumnType.STATUS -> "Status"
}

/**
 * Canonical Kanban status values. A STATUS cell is stored as [CellData.Text] holding one of these
 * (or blank, meaning "No Status") - kept as a fixed set so Kanban bucketing never has to deal with
 * arbitrary free-form values.
 */
val DEFAULT_STATUS_OPTIONS = listOf("Not Started", "In Progress", "Done")

@Immutable
@Serializable
data class SortConfig(val columnId: String, val isAscending: Boolean)

@Immutable
@Serializable
data class FilterConfig(val columnId: String, val operator: String, val value: String)

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
    is DatabaseBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is TableBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
    is VoiceBlock -> copy(isDeleted = true, updatedAt = System.currentTimeMillis())
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
    is DatabaseBlock -> copy(isPinned = pinned, updatedAt = now)
    is TableBlock -> copy(isPinned = pinned, updatedAt = now)
    is VoiceBlock -> copy(isPinned = pinned, updatedAt = now)
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
    is DatabaseBlock -> copy(updatedAt = now)
    is TableBlock -> copy(updatedAt = now)
    is VoiceBlock -> copy(updatedAt = now)
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
    is DatabaseBlock -> copy(isDeleted = deleted, updatedAt = now)
    is TableBlock -> copy(isDeleted = deleted, updatedAt = now)
    is VoiceBlock -> copy(isDeleted = deleted, updatedAt = now)
    is QuoteBlock -> copy(isDeleted = deleted, updatedAt = now)
    is SolidDividerBlock -> copy(isDeleted = deleted, updatedAt = now)
    is ThreeDotDividerBlock -> copy(isDeleted = deleted, updatedAt = now)
}

// Rebuilds an entire note's content with fresh ids on every block - used when a note is
// created from a template so the copy never collides with the template's own rows in Room
// (block ids and DatabaseBlock schema ids are primary/foreign keys there).
fun NoteContent.deepCopyWithNewIds(): NoteContent = copy(blocks = blocks.map { it.deepCopyWithNewIds() })

// Gives a single block a new id. Most block types are flat (id swap only), but DatabaseBlock owns
// nested ids of its own and needs its own recursive/remapping logic - see the private helper below.
fun NoteBlock.deepCopyWithNewIds(): NoteBlock {
    val newId = UUID.randomUUID().toString()
    return when (this) {
        is DatabaseBlock -> deepCopyDatabase(newId)
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
        is SolidDividerBlock -> copy(id = newId)
        is ThreeDotDividerBlock -> copy(id = newId)
    }
}

// DatabaseBlock.id doubles as the databaseId every DatabaseColumn/DatabaseRow points back to
// (see BaseEditorViewModel.buildDatabaseBlock for the same convention when instantiating a saved
// DatabaseTemplateEntity). A full copy carries real rows/views too, unlike that schema-only path,
// so it additionally has to:
//  1. remap DatabaseRow.cells (a Map<columnId, CellData>) to the new column ids, and
//  2. remap every column-id reference inside DatabaseView (groupByColumnId, activeSorts,
//     activeFilters) - dropping any that pointed at a column that no longer exists.
// DatabaseView.hiddenGroups/groupOrder are NOT column ids (they're bucket *values*, e.g. Kanban
// status strings - see KanbanView.kt), so those carry over unchanged.
private fun DatabaseBlock.deepCopyDatabase(newId: String): DatabaseBlock {
    val oldToNewColumnId = columns.associate { it.id to UUID.randomUUID().toString() }
    val oldToNewViewId = views.associate { it.id to UUID.randomUUID().toString() }

    val newColumns = columns.map { column ->
        column.copy(id = oldToNewColumnId.getValue(column.id), databaseId = newId)
    }

    val newRows = rows.map { row ->
        row.copy(
            id = UUID.randomUUID().toString(),
            databaseId = newId,
            // Fall back to the old column id for any orphaned cell rather than dropping data -
            // that cell was already orphaned before the copy, so this doesn't make it worse.
            cells = row.cells.mapKeys { (oldColumnId, _) -> oldToNewColumnId[oldColumnId] ?: oldColumnId }
        )
    }

    val newViews = views.map { view ->
        view.copy(
            id = oldToNewViewId.getValue(view.id),
            groupByColumnId = view.groupByColumnId?.let { oldToNewColumnId[it] },
            activeSorts = view.activeSorts.mapNotNull { sort ->
                oldToNewColumnId[sort.columnId]?.let { sort.copy(columnId = it) }
            },
            activeFilters = view.activeFilters.mapNotNull { filter ->
                oldToNewColumnId[filter.columnId]?.let { filter.copy(columnId = it) }
            }
        )
    }

    return copy(
        id = newId,
        columns = newColumns,
        rows = newRows,
        views = newViews,
        activeViewId = activeViewId?.let { oldToNewViewId[it] }
    )
}