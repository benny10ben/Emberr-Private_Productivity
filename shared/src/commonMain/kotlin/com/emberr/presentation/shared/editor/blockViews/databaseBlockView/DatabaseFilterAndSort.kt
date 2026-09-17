package com.emberr.presentation.shared.editor.blockViews.databaseBlockView

import androidx.compose.ui.graphics.Color
import com.emberr.domain.model.ColumnType
import com.emberr.domain.model.DatabaseColumn
import com.emberr.domain.model.DatabaseRow
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.FilterConfig
import com.emberr.domain.model.displayText

private val PRIORITY_ACCENT_COLORS = mapOf(
    "Low" to Color(0xFF7FB3D5),
    "Medium" to Color(0xFFF2C14E),
    "High" to Color(0xFFE8873A),
    "Urgent" to Color(0xFFE0574F)
)

internal val PRIORITY_LEVELS = listOf("Low", "Medium", "High", "Urgent")

internal fun priorityAccentColor(priority: String): Color? = PRIORITY_ACCENT_COLORS[priority]

private val PRIORITY_SORT_WEIGHTS = mapOf("Low" to 1, "Medium" to 2, "High" to 3, "Urgent" to 4)

private fun DatabaseRow.matches(filter: FilterConfig): Boolean {
    val cellVal = cells[filter.columnId].displayText()
    return when (filter.operator) {
        "contains" -> cellVal.contains(filter.value, ignoreCase = true)
        "not_contains" -> !cellVal.contains(filter.value, ignoreCase = true)
        "equals" -> cellVal.equals(filter.value, ignoreCase = true)
        "not_equals" -> !cellVal.equals(filter.value, ignoreCase = true)
        "starts_with" -> cellVal.startsWith(filter.value, ignoreCase = true)
        "ends_with" -> cellVal.endsWith(filter.value, ignoreCase = true)

        "empty" -> cellVal.isBlank() || cellVal == "—"
        "not_empty" -> cellVal.isNotBlank() && cellVal != "—"
        "checked" -> cellVal == "true"
        "unchecked" -> cellVal != "true"

        "gt" -> (cellVal.toDoubleOrNull() ?: 0.0) > (filter.value.toDoubleOrNull() ?: 0.0)
        "gte" -> (cellVal.toDoubleOrNull() ?: 0.0) >= (filter.value.toDoubleOrNull() ?: 0.0)
        "lt" -> (cellVal.toDoubleOrNull() ?: 0.0) < (filter.value.toDoubleOrNull() ?: 0.0)
        "lte" -> (cellVal.toDoubleOrNull() ?: 0.0) <= (filter.value.toDoubleOrNull() ?: 0.0)

        "between" -> {
            val parts = filter.value.split("|")
            if (parts.size == 2 && cellVal.isNotBlank()) {
                val lo = parts[0].toDoubleOrNull()
                val hi = parts[1].toDoubleOrNull()
                val numeric = cellVal.toDoubleOrNull()
                if (lo != null && hi != null && numeric != null) numeric in lo..hi
                else cellVal >= parts[0] && cellVal <= parts[1]
            } else true
        }

        "priority" -> cellVal.equals(filter.value, ignoreCase = true)
        "before" -> cellVal.isNotBlank() && cellVal < filter.value
        "after" -> cellVal.isNotBlank() && cellVal > filter.value
        else -> true
    }
}

private fun compareByColumnType(type: ColumnType, first: String, second: String): Int = when (type) {
    ColumnType.NUMBER, ColumnType.MONEY -> {
        val left = first.toDoubleOrNull() ?: Double.MAX_VALUE
        val right = second.toDoubleOrNull() ?: Double.MAX_VALUE
        left.compareTo(right)
    }
    ColumnType.CHECKBOX -> (first == "true").compareTo(second == "true")
    ColumnType.PRIORITY -> (PRIORITY_SORT_WEIGHTS[first] ?: 0).compareTo(PRIORITY_SORT_WEIGHTS[second] ?: 0)
    else -> first.lowercase().compareTo(second.lowercase())
}

internal fun applyFiltersAndSorts(
    rows: List<DatabaseRow>,
    columns: List<DatabaseColumn>,
    view: DatabaseView
): List<DatabaseRow> {
    var result = rows.filter { !it.isDeleted }

    view.activeFilters.forEach { filter ->
        result = result.filter { it.matches(filter) }
    }

    if (view.activeSorts.isEmpty()) return result

    return result.sortedWith { first, second ->
        var comparison = 0
        for (rule in view.activeSorts) {
            val column = columns.find { it.id == rule.columnId } ?: continue
            comparison = compareByColumnType(
                column.type,
                first.cells[rule.columnId].displayText(),
                second.cells[rule.columnId].displayText()
            )
            if (!rule.isAscending) comparison = -comparison
            if (comparison != 0) break
        }
        comparison
    }
}

internal fun filterChipLabel(filter: FilterConfig, columnName: String): String = when (filter.operator) {
    "not_empty" -> "$columnName is not empty"
    "empty" -> "$columnName is empty"
    "checked" -> "$columnName is checked"
    "unchecked" -> "$columnName is unchecked"
    "priority" -> "$columnName = ${filter.value}"
    "gt" -> "$columnName > ${filter.value}"
    "lt" -> "$columnName < ${filter.value}"
    "gte" -> "$columnName ≥ ${filter.value}"
    "lte" -> "$columnName ≤ ${filter.value}"
    "between" -> filter.value.split("|").let { if (it.size == 2) "$columnName: ${it[0]} – ${it[1]}" else "$columnName between" }
    "before" -> "$columnName before ${filter.value}"
    "after" -> "$columnName after ${filter.value}"
    "starts_with" -> "$columnName starts with \"${filter.value}\""
    "ends_with" -> "$columnName ends with \"${filter.value}\""
    "not_contains" -> "$columnName does not contain \"${filter.value}\""
    "not_equals" -> "$columnName is not \"${filter.value}\""
    else -> "$columnName ${filter.operator} \"${filter.value}\""
}

internal fun filterConditionsFor(type: ColumnType?): List<Pair<String, String>> = when (type) {
    ColumnType.CHECKBOX -> listOf(
        "unchecked" to "Hide Checked rows",
        "checked" to "Hide Unchecked rows"
    )

    ColumnType.NUMBER, ColumnType.MONEY -> listOf(
        "equals" to "Equals",
        "not_equals" to "Does not equal",
        "gt" to "Greater than (>)",
        "gte" to "Greater than or equal (≥)",
        "lt" to "Less than (<)",
        "lte" to "Less than or equal (≤)",
        "between" to "Between (range)",
        "not_empty" to "Is not empty",
        "empty" to "Is empty"
    )

    ColumnType.DATE -> listOf(
        "equals" to "On exactly date",
        "before" to "Is before date",
        "after" to "Is after date",
        "between" to "Between two dates",
        "not_empty" to "Is scheduled (Not empty)",
        "empty" to "Is unscheduled (Empty)"
    )

    else -> listOf(
        "contains" to "Contains text",
        "not_contains" to "Does not contain",
        "equals" to "Is exactly",
        "not_equals" to "Is not",
        "starts_with" to "Starts with",
        "ends_with" to "Ends with",
        "not_empty" to "Is not empty",
        "empty" to "Is empty",
        "priority" to "Priority status is"
    )
}
