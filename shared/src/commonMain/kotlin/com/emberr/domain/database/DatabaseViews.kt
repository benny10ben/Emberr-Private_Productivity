package com.emberr.domain.database

import com.emberr.domain.model.DEFAULT_VIEW_ID
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType

private val defaultTableView = DatabaseView(id = DEFAULT_VIEW_ID, name = DatabaseViewType.TABLE.label, type = DatabaseViewType.TABLE)

fun DatabaseBlock.allViews(): List<DatabaseView> = views.ifEmpty { listOf(defaultTableView) }

fun DatabaseBlock.activeView(): DatabaseView {
    val allViews = allViews()
    return allViews.firstOrNull { it.id == activeViewId } ?: allViews.first()
}

fun DatabaseBlock.newViewName(type: DatabaseViewType): String {
    val takenNames = allViews().map { it.name.lowercase() }.toSet()
    if (type.label.lowercase() !in takenNames) return type.label
    return generateSequence(2) { it + 1 }.map { "${type.label} $it" }.first { it.lowercase() !in takenNames }
}

fun DatabaseBlock.withViewAdded(view: DatabaseView): DatabaseBlock =
    copy(views = allViews() + view, activeViewId = view.id)

fun DatabaseBlock.withViewChanged(viewId: String, change: (DatabaseView) -> DatabaseView): DatabaseBlock =
    copy(views = allViews().map { if (it.id == viewId) change(it) else it })

fun DatabaseBlock.withViewDeleted(viewId: String): DatabaseBlock {
    val remainingViews = allViews().filterNot { it.id == viewId }
    if (remainingViews.isEmpty()) return this
    return copy(views = remainingViews, activeViewId = activeViewId.takeUnless { it == viewId })
}

fun DatabaseBlock.withActiveView(viewId: String, now: Long): DatabaseBlock =
    if (activeView().id == viewId) this else copy(activeViewId = viewId).withSettingTimesStamped(before = this, now)

fun List<DatabaseRow>.loadedRows(loadedCount: Int, alwaysShownRowIds: Collection<String> = emptySet()): List<DatabaseRow> =
    filterIndexed { position, row -> position < loadedCount || row.noteId in alwaysShownRowIds }
