package com.emberr.domain.sync

import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.UnknownBlockSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanSyncSchemaTest {

    private val formatChangedMessage =
        "The format that devices send each other changed. If older versions of Emberr can still read it " +
                "(a new block type, or a new field with a default value), update the reviewed list in this test. " +
                "If they cannot (a field was renamed, removed, or changed type), also raise LAN_SYNC_SCHEMA_VERSION."

    private val reviewedBlockFields = mapOf(
        "text" to "id, text, indentationLevel, textAlignment, inlineSpans, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, highlightColorName, isDeleted, isPinned, updatedAt",
        "heading" to "id, text, level, indentationLevel, textAlignment, inlineSpans, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, highlightColorName, isDeleted, isPinned, updatedAt",
        "quote" to "id, text, indentationLevel, textAlignment, inlineSpans, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, highlightColorName, isDeleted, isPinned, updatedAt",
        "checkbox" to "id, text, isChecked, indentationLevel, textAlignment, inlineSpans, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, highlightColorName, reminderTimestamp, completedAt, categoryId, durationMinutes, url, description, recurrenceRule, isDeleted, isPinned, updatedAt",
        "bullet" to "id, text, indentationLevel, textAlignment, inlineSpans, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, highlightColorName, isDeleted, isPinned, updatedAt",
        "number" to "id, text, number, indentationLevel, textAlignment, inlineSpans, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, highlightColorName, isDeleted, isPinned, updatedAt",
        "toggle" to "id, text, isExpanded, indentationLevel, textAlignment, inlineSpans, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, highlightColorName, isDeleted, isPinned, updatedAt",
        "code" to "id, code, language, indentationLevel, textAlignment, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "bookmark" to "id, url, title, description, previewImageUrl, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "linked_note" to "id, linkedNoteId, showIcon, showCoverImage, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "image" to "id, localFilePath, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "document" to "id, localFilePath, fileName, mimeType, fileSizeString, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "table" to "id, rows, cellStyles, cellSpans, rowStyles, columnStyles, columnWidths, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "voice" to "id, localFilePath, durationSeconds, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "canvas" to "id, canvasNoteId, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "property" to "id, propertyType, customPropertyId, customLabel, customValueType, text, date, endDate, time, endTime, tags, isChecked, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "database" to "id, databaseId, isLinkedDatabase, title, columns, notesColumnAfterKey, customProperties, columnWidths, defaultTemplateId, calculations, formulas, numberFormats, colorRules, repeatingTemplates, isLocked, showsRowCount, views, activeViewId, cellStyles, rowStyles, columnStyles, settingTimes, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "solid_divider" to "id, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt",
        "dot_divider" to "id, indentationLevel, isBold, isItalic, isStrikeThrough, isUnderlined, isHighlighted, isDeleted, isPinned, updatedAt"
    )

    private val reviewedSyncEnvelopeFields =
        "entityId, entityType, updatedAt, isDeleted, metadataJson, contentJson, embeddedBlocksJson, canvasJson"

    @Test
    fun theBlockFormatSentBetweenDevicesHasNotChangedWithoutAReview() {
        val blockTypes = NoteBlock.serializer().descriptor.getElementDescriptor(1)
        val currentBlockFields = (0 until blockTypes.elementsCount)
            .filter { index -> blockTypes.getElementName(index) != UnknownBlockSerializer.descriptor.serialName }
            .associate { index ->
                blockTypes.getElementName(index) to blockTypes.getElementDescriptor(index).elementNames.joinToString(", ")
            }

        assertEquals(reviewedBlockFields, currentBlockFields, formatChangedMessage)
    }

    @Test
    fun theKindsOfDataSentBetweenDevicesHaveNotChangedWithoutAReview() {
        val reviewedKinds =
            "NOTE, SPACE, DAILY_NOTE, FOLDER, CATEGORY, PROPERTY_TAG, CUSTOM_PROPERTY, EVENT_EXCEPTION, " +
                    "NOTE_TOMBSTONE, CHAT_SESSION, EXTERNAL_API_CONFIG, BOOKMARK_CATEGORY_ORDER, FAVORITE_NOTE_ORDER"

        assertEquals(
            reviewedKinds,
            SyncType.entries.joinToString(", ") { it.name },
            "A kind of synced data was added, renamed, or removed. Older versions of Emberr cannot read it, " +
                    "so raise LAN_SYNC_SCHEMA_VERSION and then update the reviewed list in this test."
        )
    }

    @Test
    fun theSyncEnvelopeFormatHasNotChangedWithoutAReview() {
        val currentFields = SyncEnvelope.serializer().descriptor.elementNames.joinToString(", ")

        assertEquals(reviewedSyncEnvelopeFields, currentFields, formatChangedMessage)
    }

    @Test
    fun ourOwnSchemaVersionIsAlwaysSupported() {
        assertTrue(isSupportedLanSyncSchemaVersion(LAN_SYNC_SCHEMA_VERSION))
    }

    @Test
    fun theOldestSchemaVersionWeStillSpeakIsSupported() {
        assertTrue(isSupportedLanSyncSchemaVersion(OLDEST_SUPPORTED_LAN_SYNC_SCHEMA_VERSION))
    }

    @Test
    fun aPeerOlderThanOurOldestSupportedVersionIsRefused() {
        assertFalse(isSupportedLanSyncSchemaVersion(OLDEST_SUPPORTED_LAN_SYNC_SCHEMA_VERSION - 1))
        assertFalse(isSupportedLanSyncSchemaVersion(0))
        assertFalse(isSupportedLanSyncSchemaVersion(-1))
    }

    @Test
    fun aPeerNewerThanUsIsRefusedBecauseWeCannotReadItYet() {
        assertFalse(isSupportedLanSyncSchemaVersion(LAN_SYNC_SCHEMA_VERSION + 1))
        assertFalse(isSupportedLanSyncSchemaVersion(99))
    }

    @Test
    fun theSupportedRangeNeverRunsBackwards() {
        assertTrue(OLDEST_SUPPORTED_LAN_SYNC_SCHEMA_VERSION <= LAN_SYNC_SCHEMA_VERSION)
    }
}
