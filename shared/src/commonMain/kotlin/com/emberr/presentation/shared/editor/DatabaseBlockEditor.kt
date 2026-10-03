package com.emberr.presentation.shared.editor

import androidx.compose.runtime.Stable
import com.emberr.domain.database.DatabaseBoardGroupValue
import com.emberr.domain.database.DatabaseCellPreset
import com.emberr.domain.database.DatabaseRow
import com.emberr.domain.database.DatabaseRowChange
import com.emberr.domain.database.DatabaseStyleTarget
import com.emberr.domain.database.HistoryDirection
import com.emberr.domain.database.DatabaseSettingsChange
import com.emberr.domain.database.idsOfShownDatabases
import com.emberr.domain.database.newerSettings
import com.emberr.domain.database.withEditsFrom
import com.emberr.domain.database.newDatabaseFilter
import com.emberr.domain.database.manualRowOrderWithCopiesPlaced
import com.emberr.domain.database.manualRowOrderWithRowPlaced
import com.emberr.domain.database.withColumnAdded
import com.emberr.domain.database.withColumnMovedBefore
import com.emberr.domain.database.withColumnRemoved
import com.emberr.domain.database.movedBetweenGroups
import com.emberr.domain.database.newViewName
import com.emberr.domain.database.withColumnShown
import com.emberr.domain.database.withStyle
import com.emberr.domain.database.withViewAdded
import com.emberr.domain.database.withViewChanged
import com.emberr.domain.database.withViewDeleted
import com.emberr.domain.database.withViewGroupedBy
import com.emberr.domain.database.withRowStylesCopied
import com.emberr.domain.database.withDatabasePropertyCreated
import com.emberr.domain.database.withDatabasePropertyDeleted
import com.emberr.domain.database.withDatabasePropertyRenamed
import com.emberr.domain.database.withFormulaSet
import com.emberr.domain.database.withNumberFormat
import com.emberr.domain.database.RepeatingTemplateSchedule
import com.emberr.domain.database.localNow
import com.emberr.domain.database.repeatedRowsDueAt
import com.emberr.domain.database.withTemplateRepeat
import com.emberr.domain.database.isPropertyNameTaken
import com.emberr.data.local.room.entity.NoteMetadataEntity
import com.emberr.data.local.room.entity.PropertyTagEntity
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseCalculation
import com.emberr.domain.model.DatabaseCardSize
import com.emberr.domain.model.DatabaseCellStyle
import com.emberr.domain.model.DatabaseColorRule
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseNumberFormat
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.DatabaseTemplateRepeat
import com.emberr.domain.model.DatabaseView
import com.emberr.domain.model.DatabaseViewType
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyDateRange
import com.emberr.domain.model.withDateRange
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.valueTypeOf
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.sync.SyncEventBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface DatabaseBlockHost {
    val historyGeneration: Int
    val blocks: StateFlow<List<NoteBlock>>
    fun findDatabaseBlock(blockId: String): DatabaseBlock?
    fun changeDatabaseBlock(blockId: String, change: (DatabaseBlock) -> DatabaseBlock)
    fun showDatabaseView(blockId: String, viewId: String)
    fun changeDatabaseBlockTogetherWithRows(
        blockId: String,
        rowChanges: List<DatabaseRowChange>,
        historyGeneration: Int,
        change: (DatabaseBlock) -> DatabaseBlock
    )
    fun recordDatabaseRowStep(rowChanges: List<DatabaseRowChange>, typingKey: String?, historyGeneration: Int)
    fun endDatabaseTypingStep()
}

sealed interface DatabaseSettingsState {
    data object Loading : DatabaseSettingsState
    data object Missing : DatabaseSettingsState
    data class Found(val settings: DatabaseBlock) : DatabaseSettingsState
}

@Stable
class DatabaseBlockEditor(
    private val repository: NoteRepository,
    private val appScope: CoroutineScope,
    private val host: DatabaseBlockHost
) {
    private data class PendingTextEdit(
        val blockId: String,
        val rowNoteId: String,
        val column: DatabaseColumnTarget?,
        val text: String
    ) {
        val typingKey: String
            get() = if (column == null) "database-title:$rowNoteId" else "database-cell:$rowNoteId:${column.columnKey}"
    }

    private val writeOrder = Mutex()
    private var writesInFlight = 0
    private val pendingTextEdits = LinkedHashMap<String, PendingTextEdit>()
    private var pendingTextEditsJob: Job? = null
    private val rowNoteIdsToIndex = mutableSetOf<String>()
    private val databaseIdsWithRenamesToIndex = mutableSetOf<String>()
    private val savedSettings = MutableStateFlow<Map<String, DatabaseBlock?>>(emptyMap())
    private val unsavedSettings = MutableStateFlow<Map<String, DatabaseBlock>>(emptyMap())
    private val databaseIdsOfSettingsToWrite = mutableSetOf<String>()
    private var settingsWritesJob: Job? = null

    private val _rowToFocus = MutableStateFlow<String?>(null)
    val rowToFocus: StateFlow<String?> = _rowToFocus.asStateFlow()

    private val _historyStepsApplied = MutableStateFlow(0)
    val historyStepsApplied: StateFlow<Int> = _historyStepsApplied.asStateFlow()

    fun rowsOf(databaseId: String): Flow<List<DatabaseRow>> = repository.observeDatabaseRows(databaseId)

    val databaseTemplates: Flow<List<NoteMetadataEntity>> = repository.getAllDatabaseTemplates()

    fun settingsOf(databaseId: String): DatabaseBlock? =
        newerSettings(savedSettings.value[databaseId], unsavedSettings.value[databaseId])

    fun settingsShownFor(databaseId: String): Flow<DatabaseSettingsState> =
        combine(savedSettings, unsavedSettings) { saved, unsaved ->
            when {
                databaseId !in saved -> DatabaseSettingsState.Loading
                else -> newerSettings(saved[databaseId], unsaved[databaseId])
                    ?.let { DatabaseSettingsState.Found(it) }
                    ?: DatabaseSettingsState.Missing
            }
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun keepDatabaseSettingsUpdated() {
        host.blocks
            .map { it.idsOfShownDatabases() }
            .distinctUntilChanged()
            .flatMapLatest { databaseIds ->
                if (databaseIds.isEmpty()) {
                    flowOf(emptyMap<String, DatabaseBlock?>())
                } else {
                    combine(databaseIds.map { databaseId -> repository.observeDatabaseSettings(databaseId).map { databaseId to it } }) {
                        it.toMap()
                    }
                }
            }
            .collect { savedSettings.value = it }
    }

    fun saveSettings(settings: DatabaseBlock, waitsForMoreTyping: Boolean) {
        val databaseId = settings.databaseId
        unsavedSettings.update { it + (databaseId to settings) }
        databaseIdsOfSettingsToWrite += databaseId
        settingsWritesJob?.cancel()
        settingsWritesJob = null
        if (!waitsForMoreTyping) {
            writeSettingsNow()
            return
        }
        settingsWritesJob = appScope.launch(Dispatchers.Main.immediate) {
            delay(TEXT_SAVE_DELAY_MILLIS.milliseconds)
            settingsWritesJob = null
            writeSettingsNow()
        }
    }

    fun setTitle(blockId: String, title: String) {
        host.changeDatabaseBlock(blockId) { it.copy(title = title) }
    }

    fun setColumnWidth(blockId: String, columnKey: String, width: Int) {
        host.changeDatabaseBlock(blockId) { it.copy(columnWidths = it.columnWidths + (columnKey to width)) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addFilter(blockId: String, viewId: String, target: DatabaseColumnTarget) {
        val valueType = host.findDatabaseBlock(blockId)?.valueTypeOf(target) ?: return
        val filter = newDatabaseFilter(id = Uuid.random().toString(), target = target, valueType = valueType)
        changeView(blockId, viewId) { it.copy(filters = it.filters + filter) }
    }

    fun changeFilter(blockId: String, viewId: String, filterId: String, change: (DatabaseFilter) -> DatabaseFilter) {
        changeView(blockId, viewId) { view ->
            view.copy(filters = view.filters.map { if (it.id == filterId) change(it) else it })
        }
    }

    fun removeFilter(blockId: String, viewId: String, filterId: String) {
        changeView(blockId, viewId) { view -> view.copy(filters = view.filters.filterNot { it.id == filterId }) }
    }

    fun setSorts(blockId: String, viewId: String, sorts: List<DatabaseSort>) {
        changeView(blockId, viewId) { it.copy(sorts = sorts) }
    }

    fun showView(blockId: String, viewId: String) {
        host.showDatabaseView(blockId, viewId)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addView(blockId: String, type: DatabaseViewType) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        val view = DatabaseView(id = Uuid.random().toString(), name = databaseBlock.newViewName(type), type = type)
        host.changeDatabaseBlock(blockId) { it.withViewAdded(view) }
    }

    fun renameView(blockId: String, viewId: String, name: String) {
        host.changeDatabaseBlock(blockId) { databaseBlock -> databaseBlock.withViewChanged(viewId) { it.copy(name = name) } }
    }

    fun deleteView(blockId: String, viewId: String) {
        host.changeDatabaseBlock(blockId) { it.withViewDeleted(viewId) }
    }

    fun changeView(blockId: String, viewId: String, change: (DatabaseView) -> DatabaseView) {
        host.changeDatabaseBlock(blockId) { it.withViewChanged(viewId, change) }
    }

    fun setViewShowsIcon(blockId: String, viewId: String, showsIcon: Boolean) {
        host.changeDatabaseBlock(blockId) { databaseBlock -> databaseBlock.withViewChanged(viewId) { it.copy(showsIcon = showsIcon) } }
    }

    fun setViewShowsCoverImage(blockId: String, viewId: String, showsCoverImage: Boolean) {
        host.changeDatabaseBlock(blockId) { databaseBlock ->
            databaseBlock.withViewChanged(viewId) { it.copy(showsCoverImage = showsCoverImage) }
        }
    }

    fun setViewCardSize(blockId: String, viewId: String, cardSize: DatabaseCardSize) {
        host.changeDatabaseBlock(blockId) { databaseBlock -> databaseBlock.withViewChanged(viewId) { it.copy(cardSize = cardSize) } }
    }

    fun setColumnShown(blockId: String, viewId: String, column: DatabaseColumnTarget, isShown: Boolean) {
        host.changeDatabaseBlock(blockId) { it.withColumnShown(viewId, column, isShown) }
    }

    fun setShowsRowCount(blockId: String, showsRowCount: Boolean) {
        host.changeDatabaseBlock(blockId) { it.copy(showsRowCount = showsRowCount) }
    }

    fun setCalculation(blockId: String, column: DatabaseColumnTarget, calculation: DatabaseCalculation?) {
        host.changeDatabaseBlock(blockId) { databaseBlock ->
            val calculations = if (calculation == null) {
                databaseBlock.calculations - column.columnKey
            } else {
                databaseBlock.calculations + (column.columnKey to calculation)
            }
            databaseBlock.copy(calculations = calculations)
        }
    }

    fun setFormula(blockId: String, column: DatabaseColumnTarget, formula: String) {
        host.changeDatabaseBlock(blockId) { it.withFormulaSet(column, formula) }
    }

    fun setLocked(blockId: String, isLocked: Boolean) {
        host.changeDatabaseBlock(blockId) { it.copy(isLocked = isLocked) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addColorRule(blockId: String, column: DatabaseColumnTarget) {
        val valueType = host.findDatabaseBlock(blockId)?.valueTypeOf(column) ?: return
        val rule = DatabaseColorRule(
            id = Uuid.random().toString(),
            condition = newDatabaseFilter(id = Uuid.random().toString(), target = column, valueType = valueType),
            backgroundColorName = NEW_COLOR_RULE_BACKGROUND
        )
        host.changeDatabaseBlock(blockId) { it.copy(colorRules = it.colorRules + rule) }
    }

    fun changeColorRule(blockId: String, ruleId: String, change: (DatabaseColorRule) -> DatabaseColorRule) {
        host.changeDatabaseBlock(blockId) { databaseBlock ->
            databaseBlock.copy(colorRules = databaseBlock.colorRules.map { if (it.id == ruleId) change(it) else it })
        }
    }

    fun removeColorRule(blockId: String, ruleId: String) {
        host.changeDatabaseBlock(blockId) { databaseBlock -> databaseBlock.copy(colorRules = databaseBlock.colorRules.filterNot { it.id == ruleId }) }
    }

    fun setTemplateRepeat(blockId: String, templateNoteId: String, repeat: DatabaseTemplateRepeat?) {
        host.changeDatabaseBlock(blockId) { it.withTemplateRepeat(templateNoteId, repeat) }
        writeInOrder {
            val databaseBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            databaseBlock.repeatedRowsDueAt(localNow())
                .filter { it.templateNoteId == templateNoteId }
                .forEach { rowToCreate ->
                    if (repository.createRepeatedDatabaseRow(databaseBlock, rowToCreate)) rememberRowWasWritten(rowToCreate.rowNoteId)
                }
            RepeatingTemplateSchedule.notifyRepeatChanged()
        }
    }

    fun setNumberFormat(blockId: String, column: DatabaseColumnTarget, format: DatabaseNumberFormat) {
        host.changeDatabaseBlock(blockId) { it.withNumberFormat(column, format) }
    }

    fun savedTagsOf(tagPoolKey: String): Flow<List<PropertyTagEntity>> = repository.getPropertyTags(tagPoolKey)

    fun addRow(blockId: String) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        addRowFromTemplate(blockId, databaseBlock.defaultTemplateId)
    }

    fun addRowFromTemplate(blockId: String, templateNoteId: String?, cellPreset: DatabaseCellPreset? = null) {
        if (host.findDatabaseBlock(blockId) == null) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val databaseBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val change = repository.createDatabaseRow(databaseBlock, templateNoteId, cellPreset)
            rememberRowWasWritten(change.rowNoteId)
            host.recordDatabaseRowStep(listOf(change), typingKey = null, historyGeneration = historyGeneration)
            _rowToFocus.value = change.rowNoteId
        }
    }

    fun insertRow(blockId: String, viewId: String, shownRowIds: List<String>, nextToRowId: String, isAfter: Boolean) {
        if (host.findDatabaseBlock(blockId) == null) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val databaseBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val change = repository.createDatabaseRow(databaseBlock, databaseBlock.defaultTemplateId, cellPreset = null)
            rememberRowWasWritten(change.rowNoteId)
            host.changeDatabaseBlockTogetherWithRows(blockId, listOf(change), historyGeneration) { databaseBlockNow ->
                databaseBlockNow.withViewChanged(viewId) { view ->
                    view.copy(manualRowOrder = manualRowOrderWithRowPlaced(shownRowIds, view.manualRowOrder, change.rowNoteId, nextToRowId, isAfter))
                }
            }
            _rowToFocus.value = change.rowNoteId
        }
    }

    fun moveRow(blockId: String, viewId: String, shownRowIds: List<String>, rowNoteId: String, nextToRowId: String, isAfter: Boolean) {
        changeView(blockId, viewId) { view ->
            view.copy(manualRowOrder = manualRowOrderWithRowPlaced(shownRowIds, view.manualRowOrder, rowNoteId, nextToRowId, isAfter))
        }
    }

    fun addRowInGroup(blockId: String, column: DatabaseColumnTarget, groupValue: DatabaseBoardGroupValue) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        val preset = DatabaseCellPreset(column) { it.movedBetweenGroups(from = DatabaseBoardGroupValue.NoValue, to = groupValue) }
        addRowFromTemplate(blockId, databaseBlock.defaultTemplateId, preset)
    }

    fun moveCard(
        blockId: String,
        viewId: String,
        rowNoteId: String,
        column: DatabaseColumnTarget,
        from: DatabaseBoardGroupValue,
        to: DatabaseBoardGroupValue,
        newManualRowOrder: List<String>?
    ) {
        if (from == to) {
            if (newManualRowOrder != null) changeView(blockId, viewId) { it.copy(manualRowOrder = newManualRowOrder) }
            return
        }
        if (newManualRowOrder == null) {
            writeCellNow(blockId, rowNoteId, column) { it.movedBetweenGroups(from, to) }
            return
        }
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val databaseBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val cellChange = repository.updateDatabaseCell(databaseBlock, rowNoteId, column) { it.movedBetweenGroups(from, to) }
            if (cellChange != null) rememberRowWasWritten(rowNoteId)
            host.changeDatabaseBlockTogetherWithRows(blockId, listOfNotNull(cellChange), historyGeneration) { databaseBlockNow ->
                databaseBlockNow.withViewChanged(viewId) { it.copy(manualRowOrder = newManualRowOrder) }
            }
        }
    }

    fun addGroupOption(tagPoolKey: String, name: String) {
        writeInOrder {
            repository.createPropertyTag(tagPoolKey, name)
        }
    }

    fun reorderGroupOptions(tagPoolKey: String, orderedNames: List<String>) {
        writeInOrder {
            repository.reorderPropertyTags(tagPoolKey, orderedNames)
        }
    }

    fun setGroupOptionColor(tagPoolKey: String, name: String, colorName: String?) {
        writeInOrder {
            repository.setPropertyTagColor(tagPoolKey, name, colorName)
        }
    }

    fun setDefaultTemplate(blockId: String, templateNoteId: String?) {
        host.changeDatabaseBlock(blockId) { it.copy(defaultTemplateId = templateNoteId) }
    }

    fun createTemplate(blockId: String, onCreated: (String) -> Unit) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        writeInOrder {
            onCreated(repository.createDatabaseTemplate(databaseBlock))
        }
    }

    fun deleteTemplate(templateNoteId: String) {
        writeInOrder {
            repository.deleteTemplate(templateNoteId)
        }
    }

    fun clearRowToFocus() {
        _rowToFocus.value = null
    }

    fun renameRow(blockId: String, rowNoteId: String, title: String) {
        queueTextEdit(PendingTextEdit(blockId, rowNoteId, column = null, text = title))
    }

    fun updateCellText(blockId: String, rowNoteId: String, column: DatabaseColumnTarget, text: String) {
        queueTextEdit(PendingTextEdit(blockId, rowNoteId, column, text))
    }

    fun updateCellDate(blockId: String, rowNoteId: String, column: DatabaseColumnTarget, range: PropertyDateRange) {
        writeCellNow(blockId, rowNoteId, column) { it.withDateRange(range) }
    }

    fun updateCellTags(blockId: String, rowNoteId: String, column: DatabaseColumnTarget, tags: List<String>) {
        writeCellNow(blockId, rowNoteId, column) { it.copy(tags = tags) }
    }

    fun updateCellChecked(blockId: String, rowNoteId: String, column: DatabaseColumnTarget, isChecked: Boolean) {
        writeCellNow(blockId, rowNoteId, column) { it.copy(isChecked = isChecked) }
    }

    fun finishTyping() {
        writeTextEditsNow()
        writeInOrder { host.endDatabaseTypingStep() }
    }

    fun deleteRow(rowNoteId: String) {
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val change = repository.trashDatabaseRow(rowNoteId) ?: return@writeInOrder
            rememberRowWasWritten(rowNoteId)
            host.recordDatabaseRowStep(listOf(change), typingKey = null, historyGeneration = historyGeneration)
        }
    }

    fun duplicateRow(blockId: String, viewId: String, shownRowIds: List<String>, rowNoteId: String) {
        duplicateRows(blockId, viewId, shownRowIds, listOf(rowNoteId))
    }

    fun duplicateRows(blockId: String, viewId: String, shownRowIds: List<String>, rowNoteIds: List<String>) {
        if (host.findDatabaseBlock(blockId) == null || rowNoteIds.isEmpty()) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val sourceAndCopyRowIds = rowNoteIds.mapNotNull { sourceRowId ->
                repository.duplicateDatabaseRow(sourceRowId)?.let { change -> sourceRowId to change }
            }
            if (sourceAndCopyRowIds.isEmpty()) return@writeInOrder
            val changes = sourceAndCopyRowIds.map { (_, change) -> change }
            changes.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.changeDatabaseBlockTogetherWithRows(blockId, changes, historyGeneration) { databaseBlockNow ->
                val withStyles = sourceAndCopyRowIds.fold(databaseBlockNow) { databaseBlock, (sourceRowId, change) ->
                    databaseBlock.withRowStylesCopied(sourceRowId, change.rowNoteId)
                }
                withStyles.withViewChanged(viewId) { view ->
                    if (view.sorts.isNotEmpty()) {
                        view
                    } else {
                        val copyIds = sourceAndCopyRowIds.map { (sourceRowId, change) -> sourceRowId to change.rowNoteId }
                        view.copy(manualRowOrder = manualRowOrderWithCopiesPlaced(shownRowIds, view.manualRowOrder, copyIds))
                    }
                }
            }
        }
    }

    fun deleteRows(rowNoteIds: List<String>) {
        if (rowNoteIds.isEmpty()) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val changes = rowNoteIds.mapNotNull { repository.trashDatabaseRow(it) }
            changes.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.recordDatabaseRowStep(changes, typingKey = null, historyGeneration = historyGeneration)
        }
    }

    fun updateCells(blockId: String, rowNoteIds: List<String>, column: DatabaseColumnTarget, change: (PropertyBlock) -> PropertyBlock) {
        if (rowNoteIds.isEmpty()) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val databaseBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val cellChanges = rowNoteIds.mapNotNull { rowNoteId -> repository.updateDatabaseCell(databaseBlock, rowNoteId, column, change) }
            cellChanges.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.recordDatabaseRowStep(cellChanges, typingKey = null, historyGeneration = historyGeneration)
        }
    }

    fun openRow(rowNoteId: String, open: (String) -> Unit) {
        writeTextEditsNow()
        appScope.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
            writeOrder.withLock { }
            open(rowNoteId)
        }
    }

    fun addColumn(
        blockId: String,
        column: DatabaseColumnTarget,
        viewIdToGroupByIt: String? = null,
        beforeColumn: DatabaseColumnTarget? = null
    ) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        if (databaseBlock.withColumnAdded(column) === databaseBlock) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val latestBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val rowChanges = repository.addDatabaseColumnToRows(latestBlock, column)
            rowChanges.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.changeDatabaseBlockTogetherWithRows(blockId, rowChanges, historyGeneration) {
                it.withColumnAdded(column, beforeColumn).groupedByIfAsked(viewIdToGroupByIt, column)
            }
        }
    }

    fun setStyle(
        blockId: String,
        target: DatabaseStyleTarget,
        rowNoteId: String,
        column: DatabaseColumnTarget,
        style: DatabaseCellStyle
    ) {
        host.changeDatabaseBlock(blockId) { it.withStyle(target, rowNoteId, column, style) }
    }

    fun moveColumnBefore(blockId: String, column: DatabaseColumnTarget, beforeColumn: DatabaseColumnTarget?) {
        host.changeDatabaseBlock(blockId) { it.withColumnMovedBefore(column, beforeColumn) }
    }

    private fun DatabaseBlock.groupedByIfAsked(viewId: String?, column: DatabaseColumnTarget): DatabaseBlock =
        if (viewId == null) this else withViewGroupedBy(viewId, column.columnKey)

    fun removeColumn(blockId: String, column: DatabaseColumnTarget) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val rowChanges = repository.removeDatabaseColumnFromRows(databaseBlock.databaseId, column)
            rowChanges.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.changeDatabaseBlockTogetherWithRows(blockId, rowChanges, historyGeneration) {
                it.withColumnRemoved(column)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createProperty(
        blockId: String,
        name: String,
        valueType: PropertyValueType,
        viewIdToGroupByIt: String? = null,
        beforeColumn: DatabaseColumnTarget? = null
    ) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        val cleanedName = name.trim()
        if (cleanedName.isEmpty() || databaseBlock.isPropertyNameTaken(cleanedName, ignoringPropertyId = null)) return
        val property = DatabaseCustomProperty(id = Uuid.random().toString(), name = cleanedName, valueType = valueType)
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val latestBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val blockWithProperty = latestBlock.withDatabasePropertyCreated(property, beforeColumn)
            val rowChanges = repository.addDatabaseColumnToRows(blockWithProperty, DatabaseColumnTarget.CustomProperty(property.id))
            rowChanges.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.changeDatabaseBlockTogetherWithRows(blockId, rowChanges, historyGeneration) {
                it.withDatabasePropertyCreated(property, beforeColumn)
                    .groupedByIfAsked(viewIdToGroupByIt, DatabaseColumnTarget.CustomProperty(property.id))
            }
        }
    }

    fun renameProperty(blockId: String, propertyId: String, newName: String) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        val cleanedName = newName.trim()
        if (cleanedName.isEmpty() || databaseBlock.isPropertyNameTaken(cleanedName, ignoringPropertyId = propertyId)) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val rowChanges = repository.renameDatabasePropertyInRows(databaseBlock.databaseId, propertyId, cleanedName)
            rowChanges.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.changeDatabaseBlockTogetherWithRows(blockId, rowChanges, historyGeneration) {
                it.withDatabasePropertyRenamed(propertyId, cleanedName)
            }
        }
    }

    fun deleteProperty(blockId: String, propertyId: String) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val column = DatabaseColumnTarget.CustomProperty(propertyId)
            val rowChanges = repository.removeDatabaseColumnFromRows(databaseBlock.databaseId, column)
            rowChanges.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.changeDatabaseBlockTogetherWithRows(blockId, rowChanges, historyGeneration) {
                it.withDatabasePropertyDeleted(propertyId)
            }
            repository.deleteSavedTagsOf(propertyId)
        }
    }

    fun hasUnfinishedWrites(): Boolean =
        pendingTextEdits.isNotEmpty() || databaseIdsOfSettingsToWrite.isNotEmpty() || writesInFlight > 0

    suspend fun finishWrites() {
        writeTextEditsNow()
        writeSettingsNow()
        writeOrder.withLock { }
    }

    fun applyHistoryStep(rowChanges: List<DatabaseRowChange>, settingsChanges: List<DatabaseSettingsChange>, direction: HistoryDirection) {
        if (rowChanges.isEmpty() && settingsChanges.isEmpty()) return
        writeInOrder {
            if (rowChanges.isNotEmpty()) {
                repository.applyDatabaseRowChanges(rowChanges, direction)
                rowChanges.map { it.rowNoteId }.distinct().forEach { rememberRowWasWritten(it) }
            }
            val now = System.currentTimeMillis()
            val settingsChangesInApplyOrder = if (direction == HistoryDirection.UNDO) settingsChanges.asReversed() else settingsChanges
            settingsChangesInApplyOrder.forEach { change ->
                val wasWritten = repository.changeDatabaseSettings(change.databaseId) { saved -> change.settingsToWrite(direction, saved, now) }
                if (wasWritten && change.before.title != change.after.title) databaseIdsWithRenamesToIndex += change.databaseId
            }
            _historyStepsApplied.value++
        }
    }

    suspend fun indexChangedRowsForAiNow() {
        withContext(Dispatchers.Main.immediate) {
            finishWrites()
            writeOrder.withLock { indexChangedRows() }
        }
    }

    fun finishWhenEditorCloses() {
        writeTextEditsNow()
        writeSettingsNow()
        writeInOrder { indexChangedRows() }
    }

    private fun queueTextEdit(edit: PendingTextEdit) {
        pendingTextEdits[edit.typingKey] = edit
        pendingTextEditsJob?.cancel()
        pendingTextEditsJob = appScope.launch(Dispatchers.Main.immediate) {
            delay(TEXT_SAVE_DELAY_MILLIS.milliseconds)
            pendingTextEditsJob = null
            writeTextEditsNow()
        }
    }

    private fun writeTextEditsNow() {
        pendingTextEditsJob?.cancel()
        pendingTextEditsJob = null
        if (pendingTextEdits.isEmpty()) return
        val edits = pendingTextEdits.values.toList()
        pendingTextEdits.clear()
        writeInOrder { historyGeneration ->
            edits.forEach { edit ->
                val change = if (edit.column == null) {
                    repository.renameDatabaseRow(edit.rowNoteId, edit.text)
                } else {
                    host.findDatabaseBlock(edit.blockId)?.let { databaseBlock ->
                        repository.updateDatabaseCell(databaseBlock, edit.rowNoteId, edit.column) { it.copy(text = edit.text) }
                    }
                }
                if (change != null) {
                    rememberRowWasWritten(edit.rowNoteId)
                    host.recordDatabaseRowStep(listOf(change), typingKey = edit.typingKey, historyGeneration = historyGeneration)
                }
            }
        }
    }

    private fun writeSettingsNow() {
        settingsWritesJob?.cancel()
        settingsWritesJob = null
        if (databaseIdsOfSettingsToWrite.isEmpty()) return
        val databaseIds = databaseIdsOfSettingsToWrite.toList()
        databaseIdsOfSettingsToWrite.clear()
        writeInOrder {
            databaseIds.forEach { databaseId ->
                val settings = unsavedSettings.value[databaseId] ?: return@forEach
                val now = System.currentTimeMillis()
                var titleBeforeWrite: String? = null
                val wasWritten = repository.changeDatabaseSettings(databaseId) { saved ->
                    titleBeforeWrite = saved.title
                    saved.withEditsFrom(settings, now)
                }
                if (!wasWritten) unsavedSettings.update { if (it[databaseId] === settings) it - databaseId else it }
                if (wasWritten && titleBeforeWrite != settings.title) databaseIdsWithRenamesToIndex += databaseId
            }
        }
    }

    private fun writeCellNow(
        blockId: String,
        rowNoteId: String,
        column: DatabaseColumnTarget,
        change: (PropertyBlock) -> PropertyBlock
    ) {
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val databaseBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val cellChange = repository.updateDatabaseCell(databaseBlock, rowNoteId, column, change) ?: return@writeInOrder
            rememberRowWasWritten(rowNoteId)
            host.recordDatabaseRowStep(listOf(cellChange), typingKey = null, historyGeneration = historyGeneration)
        }
    }

    private fun writeInOrder(write: suspend (historyGeneration: Int) -> Unit) {
        val historyGeneration = host.historyGeneration
        writesInFlight++
        appScope.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
            try {
                writeOrder.withLock {
                    ActiveEditorRegistry.flushAllPending()
                    write(historyGeneration)
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                cause.printStackTrace()
            } finally {
                writesInFlight--
            }
        }
    }

    private suspend fun rememberRowWasWritten(rowNoteId: String) {
        rowNoteIdsToIndex += rowNoteId
        SyncEventBus.emitSyncCompleted(rowNoteId)
    }

    private suspend fun indexChangedRows() {
        val renamedDatabaseIds = databaseIdsWithRenamesToIndex.toList()
        databaseIdsWithRenamesToIndex.clear()
        renamedDatabaseIds.forEach { databaseId -> repository.indexNotesShowingDatabase(databaseId) }
        val rowNoteIds = rowNoteIdsToIndex.toList()
        rowNoteIdsToIndex.clear()
        rowNoteIds.forEach { rowNoteId ->
            val rowNote = repository.getNoteById(rowNoteId) ?: return@forEach
            val content = repository.getNoteContent(rowNoteId) ?: return@forEach
            repository.indexNote(rowNote, content)
        }
    }

    private companion object {
        const val TEXT_SAVE_DELAY_MILLIS = 600L
        const val NEW_COLOR_RULE_BACKGROUND = "red"
    }
}
