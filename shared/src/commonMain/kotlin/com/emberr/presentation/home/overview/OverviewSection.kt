package com.emberr.presentation.home.overview

import emberr.shared.generated.resources.Res
import emberr.shared.generated.resources.bookmark
import emberr.shared.generated.resources.check_square
import emberr.shared.generated.resources.images
import emberr.shared.generated.resources.notes2
import org.jetbrains.compose.resources.DrawableResource

enum class OverviewSection(val storageKey: String, val title: String, val icon: DrawableResource) {
    TASKS("tasks", "Tasks", Res.drawable.check_square),
    BOOKMARKS("bookmarks", "Bookmarks", Res.drawable.bookmark),
    IMAGES("images", "Images", Res.drawable.images),
    DOCUMENTS("documents", "Documents", Res.drawable.notes2)
}

fun visibleOverviewSections(hiddenStorageKeys: Set<String>): List<OverviewSection> =
    OverviewSection.entries.filterNot { section -> section.storageKey in hiddenStorageKeys }
