package com.emberr.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PropertyBlockTest {

    @Test
    fun aTagNameLosesCommasAndExtraSpaces() {
        assertEquals("in progress", cleanPropertyTagName("  in,progress  "))
        assertEquals("very urgent", cleanPropertyTagName("very    urgent"))
        assertEquals("", cleanPropertyTagName(" , "))
    }

    @Test
    fun theValueTextMatchesWhatThePropertyHolds() {
        assertEquals(
            "+1 555 0100",
            PropertyBlock(id = "p1", propertyType = PropertyType.PHONE, text = "+1 555 0100").valueAsText()
        )
        assertEquals(
            "2026-09-27",
            PropertyBlock(id = "p2", propertyType = PropertyType.DATE, date = LocalDate(2026, 9, 27)).valueAsText()
        )
        assertEquals(
            "design, urgent",
            PropertyBlock(id = "p3", propertyType = PropertyType.TAGS, tags = listOf("design", "urgent")).valueAsText()
        )
        assertEquals("", PropertyBlock(id = "p4", propertyType = PropertyType.STATUS).valueAsText())
    }

    @Test
    fun aDatePropertyIgnoresLeftoverTextAndTags() {
        val block = PropertyBlock(
            id = "p1",
            propertyType = PropertyType.DUE_DATE,
            text = "leftover",
            tags = listOf("leftover")
        )

        assertEquals("", block.valueAsText())
    }

    private val tagsBlock = PropertyBlock(
        id = "p1",
        propertyType = PropertyType.TAGS,
        tags = listOf("design", "urgnet"),
        updatedAt = 100L
    )

    @Test
    fun renamingATagReplacesItWhateverItsCaseAndStampsTheBlock() {
        val renamed = tagsBlock.withPropertyTagReplaced("TAGS", "URGNET", "urgent", now = 500L)

        assertEquals(tagsBlock.copy(tags = listOf("design", "urgent"), updatedAt = 500L), renamed)
    }

    @Test
    fun renamingATagOntoOneTheBlockAlreadyHasKeepsOnlyOne() {
        val renamed = tagsBlock.withPropertyTagReplaced("TAGS", "urgnet", "Design", now = 500L)

        assertEquals(listOf("design"), (renamed as PropertyBlock).tags)
    }

    @Test
    fun deletingATagRemovesItFromTheBlock() {
        val withoutTag = tagsBlock.withPropertyTagReplaced("TAGS", "urgnet", null, now = 500L)

        assertEquals(tagsBlock.copy(tags = listOf("design"), updatedAt = 500L), withoutTag)
    }

    @Test
    fun aBlockThatDoesNotUseTheTagIsLeftExactlyAsItWas() {
        val status = PropertyBlock(id = "p2", propertyType = PropertyType.STATUS, tags = listOf("urgnet"), updatedAt = 100L)
        val deletedTags = tagsBlock.copy(isDeleted = true)
        val text = TextBlock(id = "t1", text = "urgnet", updatedAt = 100L)

        assertSame(status, status.withPropertyTagReplaced("TAGS", "urgnet", "urgent", now = 500L))
        assertSame(deletedTags, deletedTags.withPropertyTagReplaced("TAGS", "urgnet", "urgent", now = 500L))
        assertSame(text, text.withPropertyTagReplaced("TAGS", "urgnet", "urgent", now = 500L))
        assertSame(tagsBlock, tagsBlock.withPropertyTagReplaced("TAGS", "missing", "urgent", now = 500L))
    }

    @Test
    fun applyingTheSameRenameTwiceChangesNothingTheSecondTime() {
        val renamed = tagsBlock.withPropertyTagReplaced("TAGS", "urgnet", "urgent", now = 500L)

        assertSame(renamed, renamed.withPropertyTagReplaced("TAGS", "urgnet", "urgent", now = 900L))
    }

    private val clientBlock = PropertyBlock(
        id = "c1",
        customPropertyId = "client-id",
        customLabel = "Client",
        customValueType = PropertyValueType.SINGLE_CHOICE,
        tags = listOf("Acme"),
        updatedAt = 100L
    )

    @Test
    fun aCustomPropertyTakesItsLabelTypeAndTagListFromItsOwnFields() {
        assertEquals("Client", clientBlock.label)
        assertEquals(PropertyValueType.SINGLE_CHOICE, clientBlock.valueType)
        assertEquals("client-id", clientBlock.tagPoolKey)
        assertEquals("Acme", clientBlock.valueAsText())
    }

    @Test
    fun aBuiltInPropertyIgnoresTheCustomFields() {
        val status = PropertyBlock(id = "s1", propertyType = PropertyType.STATUS, customLabel = "leftover")

        assertEquals("Status", status.label)
        assertEquals(PropertyValueType.SINGLE_CHOICE, status.valueType)
        assertEquals("STATUS", status.tagPoolKey)
    }

    @Test
    fun aCustomPropertysTagsAreKeptApartFromTheBuiltInOnes() {
        val builtInTags = PropertyBlock(id = "t1", propertyType = PropertyType.TAGS, tags = listOf("Acme"), updatedAt = 100L)

        assertSame(builtInTags, builtInTags.withPropertyTagReplaced("client-id", "Acme", null, now = 500L))
        assertEquals(emptyList(), (clientBlock.withPropertyTagReplaced("client-id", "Acme", null, now = 500L) as PropertyBlock).tags)
    }

    @Test
    fun renamingACustomPropertyChangesOnlyItsOwnBlocks() {
        val otherCustom = clientBlock.copy(id = "c2", customPropertyId = "other-id")

        assertEquals(
            clientBlock.copy(customLabel = "Customer", updatedAt = 500L),
            clientBlock.withCustomPropertyRenamed("client-id", "Customer", now = 500L)
        )
        assertSame(otherCustom, otherCustom.withCustomPropertyRenamed("client-id", "Customer", now = 500L))
        assertSame(clientBlock, clientBlock.withCustomPropertyRenamed("client-id", "Client", now = 500L))
    }

    @Test
    fun removingACustomPropertyTurnsItsBlocksIntoTombstones() {
        assertEquals(
            clientBlock.copy(isDeleted = true, updatedAt = 500L),
            clientBlock.withCustomPropertyRemoved("client-id", now = 500L)
        )
        assertSame(tagsBlock, tagsBlock.withCustomPropertyRemoved("client-id", now = 500L))
    }
}
