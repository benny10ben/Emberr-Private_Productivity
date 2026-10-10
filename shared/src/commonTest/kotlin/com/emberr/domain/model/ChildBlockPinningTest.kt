package com.emberr.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ChildBlockPinningTest {

    private val now = 9_000L

    @Test
    fun aChildIsPinnedWhenItsCalloutIsPinned() {
        val blocks = listOf(
            CalloutBlock(id = "callout", isPinned = true, updatedAt = 1L),
            TextBlock(id = "child", indentationLevel = 1, updatedAt = 1L),
            TextBlock(id = "outside", updatedAt = 1L)
        )

        val result = blocks.withChildrenPinnedLikeTheirToggleOrCallout(now)

        assertEquals(listOf(true, true, false), result.map { it.isPinned })
        assertEquals(listOf(1L, now, 1L), result.map { it.updatedAt })
    }

    @Test
    fun aChildIsPinnedWhenItsToggleIsPinned() {
        val blocks = listOf(
            ToggleBlock(id = "toggle", isPinned = true),
            CheckboxBlock(id = "child", indentationLevel = 1),
            TextBlock(id = "outside")
        )

        val result = blocks.withChildrenPinnedLikeTheirToggleOrCallout(now)

        assertEquals(listOf(true, true, false), result.map { it.isPinned })
    }

    @Test
    fun childrenAreUnpinnedWhenTheirToggleOrCalloutIsUnpinned() {
        val blocks = listOf(
            CalloutBlock(id = "callout"),
            BulletedListBlock(id = "child", indentationLevel = 1, isPinned = true),
            BulletedListBlock(id = "deeper-child", indentationLevel = 2, isPinned = true),
            ToggleBlock(id = "toggle"),
            TextBlock(id = "toggle-child", indentationLevel = 1, isPinned = true)
        )

        val result = blocks.withChildrenPinnedLikeTheirToggleOrCallout(now)

        assertEquals(listOf(false, false, false, false, false), result.map { it.isPinned })
    }

    @Test
    fun theOutermostToggleOrCalloutDecidesForEverythingNestedInsideIt() {
        val blocks = listOf(
            CalloutBlock(id = "outer", isPinned = true),
            ToggleBlock(id = "inner", indentationLevel = 1),
            TextBlock(id = "inner-child", indentationLevel = 2),
            TextBlock(id = "outside")
        )

        val result = blocks.withChildrenPinnedLikeTheirToggleOrCallout(now)

        assertEquals(listOf(true, true, true, false), result.map { it.isPinned })
    }

    @Test
    fun aDeletedBlockIsLeftAloneAndDoesNotEndTheChildren() {
        val blocks = listOf(
            ToggleBlock(id = "toggle", isPinned = true),
            TextBlock(id = "deleted", isDeleted = true, updatedAt = 1L),
            TextBlock(id = "child", indentationLevel = 1)
        )

        val result = blocks.withChildrenPinnedLikeTheirToggleOrCallout(now)

        assertEquals(blocks[1], result[1])
        assertEquals(true, result[2].isPinned)
    }

    @Test
    fun blocksThatAlreadyMatchKeepTheirTimestamp() {
        val blocks = listOf(
            CalloutBlock(id = "callout", isPinned = true, updatedAt = 1L),
            TextBlock(id = "child", indentationLevel = 1, isPinned = true, updatedAt = 1L)
        )

        assertEquals(blocks, blocks.withChildrenPinnedLikeTheirToggleOrCallout(now))
    }

    @Test
    fun aNoteWithoutTogglesOrCalloutsIsReturnedAsItIs() {
        val blocks = listOf(
            TextBlock(id = "text", isPinned = true),
            TextBlock(id = "indented", indentationLevel = 1)
        )

        assertSame(blocks, blocks.withChildrenPinnedLikeTheirToggleOrCallout(now))
    }

    @Test
    fun onlyBlocksInsideAToggleOrCalloutAreReportedAsChildren() {
        val blocks = listOf(
            ToggleBlock(id = "toggle"),
            TextBlock(id = "toggle-child", indentationLevel = 1),
            CalloutBlock(id = "callout"),
            TextBlock(id = "callout-child", indentationLevel = 1),
            TextBlock(id = "outside"),
            TextBlock(id = "indented-but-no-parent", indentationLevel = 1)
        )

        assertEquals(setOf("toggle-child", "callout-child"), blocks.idsOfBlocksInsideAToggleOrCallout())
    }
}
