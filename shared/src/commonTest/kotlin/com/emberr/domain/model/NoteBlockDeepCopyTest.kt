package com.emberr.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NoteBlockDeepCopyTest {

    @Test
    fun everyBlockTypeGetsAFreshIdAndKeepsEverythingElse() {
        TestNoteBlocks.oneOfEveryBlockType()
            .forEach { original ->
                val copy = original.deepCopyWithNewIds()

                assertNotEquals(original.id, copy.id, "id was reused for ${original::class.simpleName}")
                assertEquals(
                    original,
                    copy.withSameIdAs(original),
                    "copying changed more than the id for ${original::class.simpleName}"
                )
            }
    }

    @Test
    fun copyingAWholeNoteGivesEveryBlockAFreshId() {
        val original = NoteContent(blocks = TestNoteBlocks.oneOfEveryBlockType())

        val copy = original.deepCopyWithNewIds()

        val originalIds = original.blocks.map { it.id }.toSet()
        val copiedIds = copy.blocks.map { it.id }.toSet()

        assertEquals(original.blocks.size, copy.blocks.size)
        assertEquals(original.blocks.size, copiedIds.size)
        assertTrue(originalIds.intersect(copiedIds).isEmpty())
    }

    private fun NoteBlock.withSameIdAs(other: NoteBlock): NoteBlock = when (this) {
        is TextBlock -> copy(id = other.id)
        is HeadingBlock -> copy(id = other.id)
        is QuoteBlock -> copy(id = other.id)
        is CheckboxBlock -> copy(id = other.id)
        is BulletedListBlock -> copy(id = other.id)
        is NumberedListBlock -> copy(id = other.id)
        is ToggleBlock -> copy(id = other.id)
        is CodeBlock -> copy(id = other.id)
        is BookmarkBlock -> copy(id = other.id)
        is LinkedNoteBlock -> copy(id = other.id)
        is ImageBlock -> copy(id = other.id)
        is DocumentBlock -> copy(id = other.id)
        is TableBlock -> copy(id = other.id)
        is VoiceBlock -> copy(id = other.id)
        is CanvasBlock -> copy(id = other.id)
        is SolidDividerBlock -> copy(id = other.id)
        is ThreeDotDividerBlock -> copy(id = other.id)
    }
}
