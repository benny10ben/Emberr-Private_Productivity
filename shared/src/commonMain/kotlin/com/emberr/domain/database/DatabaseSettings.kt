package com.emberr.domain.database

import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.NoteBlock

fun databaseNoteId(databaseId: String): String = "database-$databaseId"

fun databaseSettingsBlockId(databaseId: String): String = "database-settings-$databaseId"

val DatabaseBlock.holdsSharedSettings: Boolean
    get() = id == databaseSettingsBlockId(databaseId)

data class LinkableDatabase(val databaseId: String, val title: String, val noteTitle: String)

data class DatabaseViewChange(val viewBlock: DatabaseBlock, val settings: DatabaseBlock)

data class DatabaseSettingsChange(val before: DatabaseBlock, val after: DatabaseBlock) {

    val databaseId: String
        get() = after.databaseId

    val changesOnlyTheTitle: Boolean
        get() = before.copy(title = after.title, updatedAt = after.updatedAt, settingTimes = after.settingTimes) == after

    fun settingsToWrite(direction: HistoryDirection, savedSettings: DatabaseBlock, now: Long): DatabaseBlock? {
        val expectedNow = if (direction == HistoryDirection.UNDO) after else before
        val target = if (direction == HistoryDirection.UNDO) before else after
        if (savedSettings.isDeleted || !savedSettings.hasSameSharedSettingsAs(expectedNow)) return null
        return savedSettings.withSharedSettingsFrom(target).copy(updatedAt = now).withSettingTimesStamped(before = savedSettings, now)
    }
}

fun DatabaseBlock.withSharedSettingsFrom(settings: DatabaseBlock): DatabaseBlock = copy(
    title = settings.title,
    columns = settings.columns,
    notesColumnAfterKey = settings.notesColumnAfterKey,
    customProperties = settings.customProperties,
    formulas = settings.formulas,
    numberFormats = settings.numberFormats,
    defaultTemplateId = settings.defaultTemplateId,
    repeatingTemplates = settings.repeatingTemplates,
    isLocked = settings.isLocked
)

fun DatabaseBlock.withoutSharedSettings(): DatabaseBlock = withSharedSettingsFrom(DatabaseBlock(id = id, databaseId = databaseId))

fun DatabaseBlock.keepsSharedSettingsInside(): Boolean = withoutSharedSettings() != this

private fun DatabaseBlock.hasSameSharedSettingsAs(other: DatabaseBlock): Boolean = withSharedSettingsFrom(other) == this

fun newDatabaseSettings(databaseId: String, now: Long): DatabaseBlock =
    DatabaseBlock(id = databaseSettingsBlockId(databaseId), databaseId = databaseId, updatedAt = now)

fun DatabaseBlock.sharedSettingsMovedOut(): DatabaseBlock =
    DatabaseBlock(
        id = databaseSettingsBlockId(databaseId),
        databaseId = databaseId,
        settingTimes = settingTimes,
        updatedAt = updatedAt
    ).withSharedSettingsFrom(this)

fun DatabaseBlock.sharedSettingsCopiedTo(copyDatabaseId: String, now: Long): DatabaseBlock =
    newDatabaseSettings(copyDatabaseId, now).withSharedSettingsFrom(this)

fun DatabaseBlock.changedThroughView(settings: DatabaseBlock, change: (DatabaseBlock) -> DatabaseBlock): DatabaseViewChange {
    val shownAfter = change(withSharedSettingsFrom(settings))
    return DatabaseViewChange(viewBlock = shownAfter.withoutSharedSettings(), settings = settings.withSharedSettingsFrom(shownAfter))
}

fun DatabaseBlock.withEditsFrom(editedCopy: DatabaseBlock, now: Long): DatabaseBlock? {
    if (isDeleted) return null
    val merged = mergeDatabaseBlocks(this, editedCopy).copy(
        id = id,
        databaseId = databaseId,
        isLinkedDatabase = isLinkedDatabase,
        indentationLevel = indentationLevel,
        isPinned = isPinned,
        isDeleted = isDeleted,
        updatedAt = updatedAt
    )
    return if (merged == this) this else merged.copy(updatedAt = now)
}

fun List<NoteBlock>.idsOfShownDatabases(): Set<String> =
    filterIsInstance<DatabaseBlock>().filter { !it.isDeleted && !it.holdsSharedSettings }.mapTo(HashSet()) { it.databaseId }

fun List<NoteBlock>.withDatabaseTitles(titlesByDatabaseId: Map<String, String>): List<NoteBlock> =
    map { block ->
        if (block !is DatabaseBlock || block.holdsSharedSettings) return@map block
        titlesByDatabaseId[block.databaseId]?.let { block.copy(title = it) } ?: block
    }

fun newerSettings(saved: DatabaseBlock?, unsaved: DatabaseBlock?): DatabaseBlock? {
    if (saved == null) return null
    val unsavedIsNewer = unsaved != null && unsaved.id == saved.id && unsaved.updatedAt > saved.updatedAt
    return if (unsavedIsNewer) unsaved else saved
}

fun List<DatabaseSettingsChange>.combinedWith(laterChanges: List<DatabaseSettingsChange>): List<DatabaseSettingsChange> {
    val combined = toMutableList()
    laterChanges.forEach { laterChange ->
        val earlierIndex = combined.indexOfFirst { it.databaseId == laterChange.databaseId }
        if (earlierIndex == -1) combined += laterChange else combined[earlierIndex] = combined[earlierIndex].copy(after = laterChange.after)
    }
    return combined
}
