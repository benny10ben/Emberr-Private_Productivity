package com.emberr.presentation.mobile.home

import com.emberr.data.local.room.entity.FolderEntity
import com.emberr.data.local.room.entity.NoteMetadataEntity

/*
 * Shared between the mobile home grid and the desktop sidebar tree.
 *
 * A HomeItem is one row/card - either a folder or a note. Both screens build their list from
 * these, so sorting lives here and only here: change homeItemComparator and both platforms
 * follow. Item keys (HomeItemKey) are the ids used for lazy list keys, drag payloads and the
 * reorder calls into HomeViewModel.
 *
 * Nothing here is Compose or platform specific - keep it that way.
 */

enum class DropInsertPosition { BEFORE, INTO, AFTER }

object HomeItemKey {
    const val FOLDER_PREFIX = "home_folder_"
    const val NOTE_PREFIX = "home_note_"

    fun forFolder(folderId: String) = "$FOLDER_PREFIX$folderId"
    fun forNote(noteId: String) = "$NOTE_PREFIX$noteId"

    fun isFolder(key: String) = key.startsWith(FOLDER_PREFIX)
    fun isNote(key: String) = key.startsWith(NOTE_PREFIX)

    fun folderIdOf(key: String) = key.removePrefix(FOLDER_PREFIX)
    fun noteIdOf(key: String) = key.removePrefix(NOTE_PREFIX)
}

sealed interface HomeItem {
    val key: String
    val level: Int

    data class Folder(val folder: FolderEntity, override val level: Int = 0) : HomeItem {
        override val key: String get() = HomeItemKey.forFolder(folder.folderId)
    }

    data class Note(val note: NoteMetadataEntity, override val level: Int = 0) : HomeItem {
        override val key: String get() = HomeItemKey.forNote(note.noteId)
    }
}

private val HomeItem.typeRank: Int
    get() = when (this) {
        is HomeItem.Folder -> 0
        is HomeItem.Note   -> 1
    }

private val HomeItem.sortName: String
    get() = when (this) {
        is HomeItem.Folder -> folder.name.lowercase()
        is HomeItem.Note   -> note.title.ifEmpty { "Untitled" }.lowercase()
    }

private val HomeItem.sortCreatedAt: Long
    get() = when (this) {
        is HomeItem.Folder -> folder.createdAt
        is HomeItem.Note   -> note.createdAt
    }

private val HomeItem.sortEditedAt: Long
    get() = when (this) {
        is HomeItem.Folder -> folder.lastEditedAt
        is HomeItem.Note   -> note.updatedAt
    }

private val HomeItem.manualOrder: Int
    get() = when (this) {
        is HomeItem.Folder -> if (folder.sortOrder == 0) Int.MAX_VALUE else folder.sortOrder
        is HomeItem.Note   -> if (note.sortOrder == 0)   Int.MAX_VALUE else note.sortOrder
    }

private fun homeItemNameComparator(descending: Boolean): Comparator<HomeItem> =
    if (descending) compareByDescending<HomeItem> { it.sortName }
    else compareBy<HomeItem> { it.sortName }

private fun homeItemTimestampComparator(
    descending: Boolean,
    selector: (HomeItem) -> Long
): Comparator<HomeItem> =
    if (descending) compareByDescending<HomeItem> { selector(it) }
    else compareBy<HomeItem> { selector(it) }

fun homeItemComparator(sortType: SortType, sortOrder: SortOrder): Comparator<HomeItem> {
    val descending = sortOrder == SortOrder.DESCENDING
    return when (sortType) {
        SortType.MANUAL       -> compareBy<HomeItem> { it.manualOrder }
            .then(homeItemTimestampComparator(true) { it.sortCreatedAt })

        SortType.TYPE         -> compareBy<HomeItem> { it.typeRank }.then(homeItemNameComparator(descending))
        SortType.NAME         -> homeItemNameComparator(descending)
        SortType.DATE_CREATED -> homeItemTimestampComparator(descending) { it.sortCreatedAt }.then(homeItemNameComparator(false))
        SortType.LAST_EDITED  -> homeItemTimestampComparator(descending) { it.sortEditedAt }.then(homeItemNameComparator(false))
    }
}

fun List<HomeItem>.movedTo(draggedKey: String, targetKey: String): List<HomeItem> {
    val from = indexOfFirst { it.key == draggedKey }
    val to = indexOfFirst { it.key == targetKey }
    if (from == -1 || to == -1 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

data class TreeSelectionMenu(
    val showRename: Boolean,
    val showFavorite: Boolean,
    val favoriteLabel: String,
    val makeFavorite: Boolean,
    val deleteLabel: String
)

val SINGLE_ITEM_TREE_MENU = TreeSelectionMenu(
    showRename = true,
    showFavorite = false,
    favoriteLabel = "Add to Favorites",
    makeFavorite = true,
    deleteLabel = "Delete"
)

fun treeSelectionMenu(
    selectedNoteIds: Set<String>,
    selectedFolderIds: Set<String>,
    isNoteFavorite: (String) -> Boolean
): TreeSelectionMenu {
    val selectedCount = selectedNoteIds.size + selectedFolderIds.size
    val holdsOnlyNotes = selectedNoteIds.isNotEmpty() && selectedFolderIds.isEmpty()
    val everySelectedNoteIsFavorite = holdsOnlyNotes && selectedNoteIds.all(isNoteFavorite)

    return TreeSelectionMenu(
        showRename = selectedCount == 1,
        showFavorite = holdsOnlyNotes,
        favoriteLabel = if (everySelectedNoteIsFavorite) "Remove from Favorites" else "Add to Favorites",
        makeFavorite = !everySelectedNoteIsFavorite,
        deleteLabel = if (selectedCount > 1) "Delete $selectedCount items" else "Delete"
    )
}

data class TreeGuideLines(
    val ancestorVerticalLines: List<Boolean>,
    val isLastChildOfParent: Boolean
)

val ROOT_TREE_GUIDE_LINES = TreeGuideLines(ancestorVerticalLines = emptyList(), isLastChildOfParent = true)

fun List<HomeItem>.buildTreeGuideLines(): List<TreeGuideLines> {
    if (isEmpty()) return emptyList()

    val deepestLevel = maxOf { it.level }
    val hasRowBelowAtLevel = BooleanArray(deepestLevel + 2)
    val guidesFromBottom = ArrayList<TreeGuideLines>(size)

    for (index in indices.reversed()) {
        val level = this[index].level
        val ancestorVerticalLines =
            if (level < 2) emptyList()
            else (0 until level - 1).map { depth -> hasRowBelowAtLevel[depth + 1] }

        guidesFromBottom += TreeGuideLines(
            ancestorVerticalLines = ancestorVerticalLines,
            isLastChildOfParent = !hasRowBelowAtLevel[level]
        )

        hasRowBelowAtLevel[level] = true
        for (deeperLevel in level + 1..deepestLevel) hasRowBelowAtLevel[deeperLevel] = false
    }

    guidesFromBottom.reverse()
    return guidesFromBottom
}

fun List<HomeItem>.rowsBetween(firstKey: String, secondKey: String): List<HomeItem> {
    val firstIndex = indexOfFirst { it.key == firstKey }
    val secondIndex = indexOfFirst { it.key == secondKey }
    if (firstIndex == -1 || secondIndex == -1) return emptyList()
    return subList(minOf(firstIndex, secondIndex), maxOf(firstIndex, secondIndex) + 1).toList()
}

fun List<HomeItem>.subtreeKeys(rootKey: String?): Set<String> {
    if (rootKey == null) return emptySet()
    val rootIndex = indexOfFirst { it.key == rootKey }
    if (rootIndex == -1) return setOf(rootKey)

    val rootLevel = this[rootIndex].level
    val keys = mutableSetOf(rootKey)
    for (index in rootIndex + 1 until size) {
        if (this[index].level <= rootLevel) break
        keys += this[index].key
    }
    return keys
}

fun sortedHomeItems(
    folders: List<FolderEntity>,
    notes: List<NoteMetadataEntity>,
    sortType: SortType,
    sortOrder: SortOrder,
    level: Int = 0
): List<HomeItem> =
    (folders.map { HomeItem.Folder(it, level) } + notes.map { HomeItem.Note(it, level) })
        .sortedWith(homeItemComparator(sortType, sortOrder))

fun flattenFolderTree(
    parentId: String?,
    level: Int,
    foldersByParent: Map<String?, List<FolderEntity>>,
    notesByFolder: Map<String?, List<NoteMetadataEntity>>,
    expandedFolderIds: Set<String>,
    sortType: SortType = SortType.LAST_EDITED,
    sortOrder: SortOrder = SortOrder.DESCENDING
): List<HomeItem> {
    val out = mutableListOf<HomeItem>()

    val combined = sortedHomeItems(
        folders = foldersByParent[parentId].orEmpty(),
        notes = notesByFolder[parentId].orEmpty(),
        sortType = sortType,
        sortOrder = sortOrder,
        level = level
    )

    combined.forEach { row ->
        out += row
        if (row is HomeItem.Folder && row.folder.folderId in expandedFolderIds) {
            out += flattenFolderTree(
                row.folder.folderId, level + 1,
                foldersByParent, notesByFolder, expandedFolderIds, sortType, sortOrder
            )
        }
    }
    return out
}
