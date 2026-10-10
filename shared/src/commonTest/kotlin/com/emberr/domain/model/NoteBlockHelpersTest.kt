package com.emberr.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteBlockHelpersTest {

    private val blockTypesThatCarryAlignment = setOf(
        "TextBlock",
        "HeadingBlock",
        "QuoteBlock",
        "CheckboxBlock",
        "BulletedListBlock",
        "NumberedListBlock",
        "ToggleBlock",
        "CalloutBlock",
        "CodeBlock"
    )

    private val blockTypesThatCarryInlineSpans = setOf(
        "TextBlock",
        "HeadingBlock",
        "QuoteBlock",
        "CheckboxBlock",
        "BulletedListBlock",
        "NumberedListBlock",
        "ToggleBlock",
        "CalloutBlock"
    )

    @Test
    fun onlyTextBearingBlocksReportAnAlignment() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            val className = block::class.simpleName

            if (className in blockTypesThatCarryAlignment) {
                assertTrue(block.textAlignmentOrNull() != null, "$className should report an alignment")
            } else {
                assertNull(block.textAlignmentOrNull(), "$className should not report an alignment")
            }
        }
    }

    @Test
    fun settingAnAlignmentOnlyChangesBlocksThatCanCarryOne() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            val realigned = block.withTextAlignment(TextAlignment.JUSTIFY, now = 777L)

            if (block::class.simpleName in blockTypesThatCarryAlignment) {
                assertEquals(TextAlignment.JUSTIFY, realigned.textAlignmentOrNull())
                assertEquals(777L, realigned.updatedAt)
            } else {
                assertEquals(block, realigned)
            }
        }
    }

    @Test
    fun aCodeBlockCarriesAnAlignmentButNeverInlineSpans() {
        val codeBlock = CodeBlock(id = "code-1", code = "val answer = 42")

        assertEquals(TextAlignment.LEFT, codeBlock.textAlignmentOrNull())
        assertTrue(codeBlock.inlineSpansOrEmpty().isEmpty())
        assertEquals(codeBlock, codeBlock.withInlineSpans(listOf(TestNoteBlocks.everyInlineStyle), now = 777L))
    }

    @Test
    fun onlyBlocksThatCanCarryInlineSpansReportThem() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            val className = block::class.simpleName

            if (className in blockTypesThatCarryInlineSpans) {
                assertEquals(listOf(TestNoteBlocks.everyInlineStyle), block.inlineSpansOrEmpty(), className)
            } else {
                assertTrue(block.inlineSpansOrEmpty().isEmpty(), "$className should report no inline spans")
            }
        }
    }

    @Test
    fun settingInlineSpansOnlyChangesBlocksThatCanCarryThem() {
        val newSpans = listOf(InlineSpan(start = 1, end = 2, bold = true))

        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            val restyled = block.withInlineSpans(newSpans, now = 777L)

            if (block::class.simpleName in blockTypesThatCarryInlineSpans) {
                assertEquals(newSpans, restyled.inlineSpansOrEmpty())
                assertEquals(777L, restyled.updatedAt)
            } else {
                assertEquals(block, restyled)
            }
        }
    }

    @Test
    fun everyBlockTypeCanBePinnedAndUnpinned() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            val pinned = block.withPin(pinned = true, now = 500L)
            val unpinned = block.withPin(pinned = false, now = 600L)

            assertTrue(pinned.isPinned, "${block::class.simpleName} could not be pinned")
            assertEquals(500L, pinned.updatedAt)
            assertTrue(!unpinned.isPinned, "${block::class.simpleName} could not be unpinned")
            assertEquals(600L, unpinned.updatedAt)
        }
    }

    @Test
    fun everyBlockTypeCanHaveItsTimestampStamped() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            assertEquals(4_242L, block.withUpdatedAt(4_242L).updatedAt, block::class.simpleName)
        }
    }

    @Test
    fun stampingATimestampChangesNothingElse() {
        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            val stampedTwice = block.withUpdatedAt(4_242L).withUpdatedAt(block.updatedAt)

            assertEquals(block, stampedTwice, block::class.simpleName)
        }
    }

    @Test
    fun everyBlockTypeCanBeMarkedDeletedAndGetsAFreshTimestamp() {
        val before = System.currentTimeMillis()

        TestNoteBlocks.oneOfEveryBlockType().forEach { block ->
            val deleted = block.markDeleted()

            assertTrue(deleted.isDeleted, "${block::class.simpleName} could not be marked deleted")
            assertTrue(
                deleted.updatedAt >= before,
                "${block::class.simpleName} kept a stale timestamp after deletion"
            )
        }
    }

    @Test
    fun markingABlockDeletedKeepsItsContentSoItStaysReadable() {
        val checkbox = CheckboxBlock(
            id = "checkbox-1",
            text = "Call the plumber",
            isChecked = true,
            reminderTimestamp = 1_700_000_000_000L
        )

        val deleted = checkbox.markDeleted() as CheckboxBlock

        assertEquals(checkbox.id, deleted.id)
        assertEquals(checkbox.text, deleted.text)
        assertEquals(checkbox.isChecked, deleted.isChecked)
        assertEquals(checkbox.reminderTimestamp, deleted.reminderTimestamp)
    }
}
