package com.emberr.presentation.calendar

import androidx.compose.ui.graphics.Color

// Uses the "#RRGGBB" colorHex convention (no alpha) so the same
// storage format can be reused if/when this moves into Room alongside events.
data class CalendarCategory(
    val id: String,
    val name: String,
    val colorHex: String
)

// Eight pastel swatches offered when creating or editing a category.
val CategoryPastelPalette = listOf(
    "#FFADAD", // pastel red
    "#FFD6A5", // pastel orange
    "#FDFFB6", // pastel yellow
    "#CAFFBF", // pastel green
    "#9BF6FF", // pastel cyan
    "#A0C4FF", // pastel blue
    "#BDB2FF", // pastel purple
    "#FFC6FF"  // pastel pink
)

fun defaultCalendarCategories(): List<CalendarCategory> = listOf(
    CalendarCategory(id = "personal", name = "Personal", colorHex = CategoryPastelPalette[5])
)

fun String.toCategoryColor(): Color = try {
    Color(this.removePrefix("#").toLong(16) or 0xFF000000)
} catch (_: Exception) {
    Color.Gray
}
