package com.emberr.domain.database

import com.emberr.domain.model.PropertyBlock

enum class HistoryDirection { UNDO, REDO }

sealed class DatabaseRowChange {
    abstract val rowNoteId: String

    data class Cell(
        override val rowNoteId: String,
        val before: PropertyBlock?,
        val after: PropertyBlock
    ) : DatabaseRowChange() {

        private val stateBefore: PropertyBlock get() = before ?: after.copy(isDeleted = true)

        fun cellToWrite(direction: HistoryDirection, currentCell: PropertyBlock?, now: Long): PropertyBlock? {
            val expectedNow = if (direction == HistoryDirection.UNDO) after else stateBefore
            val target = if (direction == HistoryDirection.UNDO) stateBefore else after
            if (!isSameCellState(currentCell, expectedNow)) return null
            return target.copy(updatedAt = now)
        }
    }

    data class Title(
        override val rowNoteId: String,
        val before: String,
        val after: String
    ) : DatabaseRowChange() {

        fun titleToWrite(direction: HistoryDirection, currentTitle: String): String? {
            val expectedNow = if (direction == HistoryDirection.UNDO) after else before
            val target = if (direction == HistoryDirection.UNDO) before else after
            return if (currentTitle == expectedNow) target else null
        }
    }

    data class RowPresence(
        override val rowNoteId: String,
        val wasInTable: Boolean,
        val isInTable: Boolean
    ) : DatabaseRowChange() {

        fun inTableStateToWrite(direction: HistoryDirection, isCurrentlyInTable: Boolean): Boolean? {
            val expectedNow = if (direction == HistoryDirection.UNDO) isInTable else wasInTable
            val target = if (direction == HistoryDirection.UNDO) wasInTable else isInTable
            return if (isCurrentlyInTable == expectedNow) target else null
        }
    }
}

private fun isSameCellState(currentCell: PropertyBlock?, expectedCell: PropertyBlock): Boolean {
    if (currentCell == null || currentCell.isDeleted) return expectedCell.isDeleted
    if (expectedCell.isDeleted) return false
    return currentCell.copy(updatedAt = 0L) == expectedCell.copy(updatedAt = 0L)
}

fun List<DatabaseRowChange>.mergedWith(laterChanges: List<DatabaseRowChange>): List<DatabaseRowChange> {
    val merged = toMutableList()
    laterChanges.forEach { laterChange ->
        val earlierIndex = merged.indexOfFirst { it.changesSameThingAs(laterChange) }
        if (earlierIndex == -1) {
            merged += laterChange
        } else {
            merged[earlierIndex] = merged[earlierIndex].followedBy(laterChange)
        }
    }
    return merged
}

private fun DatabaseRowChange.changesSameThingAs(other: DatabaseRowChange): Boolean = when (this) {
    is DatabaseRowChange.Cell -> other is DatabaseRowChange.Cell && other.rowNoteId == rowNoteId && other.after.id == after.id
    is DatabaseRowChange.Title -> other is DatabaseRowChange.Title && other.rowNoteId == rowNoteId
    is DatabaseRowChange.RowPresence -> other is DatabaseRowChange.RowPresence && other.rowNoteId == rowNoteId
}

private fun DatabaseRowChange.followedBy(laterChange: DatabaseRowChange): DatabaseRowChange = when {
    this is DatabaseRowChange.Cell && laterChange is DatabaseRowChange.Cell -> copy(after = laterChange.after)
    this is DatabaseRowChange.Title && laterChange is DatabaseRowChange.Title -> copy(after = laterChange.after)
    this is DatabaseRowChange.RowPresence && laterChange is DatabaseRowChange.RowPresence -> copy(isInTable = laterChange.isInTable)
    else -> laterChange
}
