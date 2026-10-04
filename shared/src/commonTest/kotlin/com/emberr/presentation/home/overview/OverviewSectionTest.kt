package com.emberr.presentation.home.overview

import kotlin.test.Test
import kotlin.test.assertEquals

class OverviewSectionTest {

    @Test
    fun allSectionsAreVisibleWhenNothingIsHidden() {
        assertEquals(
            listOf(OverviewSection.TASKS, OverviewSection.BOOKMARKS, OverviewSection.IMAGES, OverviewSection.DOCUMENTS),
            visibleOverviewSections(emptySet())
        )
    }

    @Test
    fun hiddenSectionsAreLeftOutAndTheRestKeepTheirOrder() {
        assertEquals(
            listOf(OverviewSection.BOOKMARKS, OverviewSection.DOCUMENTS),
            visibleOverviewSections(setOf("tasks", "images"))
        )
    }

    @Test
    fun noSectionsAreVisibleWhenEverythingIsHidden() {
        assertEquals(
            emptyList(),
            visibleOverviewSections(OverviewSection.entries.map { it.storageKey }.toSet())
        )
    }

    @Test
    fun unknownStoredKeysAreIgnored() {
        assertEquals(OverviewSection.entries.toList(), visibleOverviewSections(setOf("calendar", "")))
    }
}
