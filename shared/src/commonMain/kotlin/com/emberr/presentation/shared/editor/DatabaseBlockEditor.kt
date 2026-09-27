package com.emberr.presentation.shared.editor

import androidx.compose.runtime.Stable
import com.emberr.domain.database.DatabaseRow
import com.emberr.domain.database.DatabaseRowChange
import com.emberr.domain.database.HistoryDirection
import com.emberr.domain.database.newDatabaseFilter
import com.emberr.domain.database.withColumnAdded
import com.emberr.domain.database.withColumnRemoved
import com.emberr.domain.database.withDatabasePropertyCreated
import com.emberr.domain.database.withDatabasePropertyDeleted
import com.emberr.domain.database.withDatabasePropertyRenamed
import com.emberr.domain.database.isPropertyNameTaken
import com.emberr.data.local.room.entity.PropertyTagEntity
import com.emberr.domain.model.DatabaseBlock
import com.emberr.domain.model.DatabaseColumnTarget
import com.emberr.domain.model.DatabaseCustomProperty
import com.emberr.domain.model.DatabaseFilter
import com.emberr.domain.model.DatabaseSort
import com.emberr.domain.model.PropertyBlock
import com.emberr.domain.model.PropertyValueType
import com.emberr.domain.model.columnKey
import com.emberr.domain.model.valueTypeOf
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.util.sync.SyncEventBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface DatabaseBlockHost {
    val historyGeneration: Int
    fun findDatabaseBlock(blockId: String): DatabaseBlock?
    fun changeDatabaseBlock(blockId: String, change: (DatabaseBlock) -> DatabaseBlock)
    fun changeDatabaseBlockTogetherWithRows(
        blockId: String,
        rowChanges: List<DatabaseRowChange>,
        historyGeneration: Int,
        change: (DatabaseBlock) -> DatabaseBlock
    )
    fun recordDatabaseRowStep(rowChanges: List<DatabaseRowChange>, typingKey: String?, historyGeneration: Int)
    fun endDatabaseTypingStep()
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

    private val _rowToFocus = MutableStateFlow<String?>(null)
    val rowToFocus: StateFlow<String?> = _rowToFocus.asStateFlow()

    private val _historyStepsApplied = MutableStateFlow(0)
    val historyStepsApplied: StateFlow<Int> = _historyStepsApplied.asStateFlow()

    fun rowsOf(databaseId: String): Flow<List<DatabaseRow>> = repository.observeDatabaseRows(databaseId)

    fun setTitle(blockId: String, title: String) {
        host.changeDatabaseBlock(blockId) { it.copy(title = title) }
    }

    fun setColumnWidth(blockId: String, columnKey: String, width: Int) {
        host.changeDatabaseBlock(blockId) { it.copy(columnWidths = it.columnWidths + (columnKey to width)) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun addFilter(blockId: String, target: DatabaseColumnTarget) {
        val valueType = host.findDatabaseBlock(blockId)?.valueTypeOf(target) ?: return
        val filter = newDatabaseFilter(id = Uuid.random().toString(), target = target, valueType = valueType)
        host.changeDatabaseBlock(blockId) { it.copy(filters = it.filters + filter) }
    }

    fun changeFilter(blockId: String, filterId: String, change: (DatabaseFilter) -> DatabaseFilter) {
        host.changeDatabaseBlock(blockId) { databaseBlock ->
            databaseBlock.copy(filters = databaseBlock.filters.map { if (it.id == filterId) change(it) else it })
        }
    }

    fun removeFilter(blockId: String, filterId: String) {
        host.changeDatabaseBlock(blockId) { databaseBlock ->
            databaseBlock.copy(filters = databaseBlock.filters.filterNot { it.id == filterId })
        }
    }

    fun setSort(blockId: String, sort: DatabaseSort?) {
        host.changeDatabaseBlock(blockId) { it.copy(sort = sort) }
    }

    fun savedTagsOf(tagPoolKey: String): Flow<List<PropertyTagEntity>> = repository.getPropertyTags(tagPoolKey)

    fun addRow(blockId: String) {
        if (host.findDatabaseBlock(blockId) == null) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val databaseBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val change = repository.createDatabaseRow(databaseBlock)
            rememberRowWasWritten(change.rowNoteId)
            host.recordDatabaseRowStep(listOf(change), typingKey = null, historyGeneration = historyGeneration)
            _rowToFocus.value = change.rowNoteId
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

    fun updateCellDate(blockId: String, rowNoteId: String, column: DatabaseColumnTarget, date: LocalDate?) {
        writeCellNow(blockId, rowNoteId, column) { it.copy(date = date) }
    }

    fun updateCellTags(blockId: String, rowNoteId: String, column: DatabaseColumnTarget, tags: List<String>) {
        writeCellNow(blockId, rowNoteId, column) { it.copy(tags = tags) }
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

    fun openRow(rowNoteId: String, open: (String) -> Unit) {
        writeTextEditsNow()
        appScope.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
            writeOrder.withLock { }
            open(rowNoteId)
        }
    }

    fun addColumn(blockId: String, column: DatabaseColumnTarget) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        if (databaseBlock.withColumnAdded(column) === databaseBlock) return
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val latestBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val rowChanges = repository.addDatabaseColumnToRows(latestBlock, column)
            rowChanges.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.changeDatabaseBlockTogetherWithRows(blockId, rowChanges, historyGeneration) {
                it.withColumnAdded(column)
            }
        }
    }

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
    fun createProperty(blockId: String, name: String, valueType: PropertyValueType) {
        val databaseBlock = host.findDatabaseBlock(blockId) ?: return
        val cleanedName = name.trim()
        if (cleanedName.isEmpty() || databaseBlock.isPropertyNameTaken(cleanedName, ignoringPropertyId = null)) return
        val property = DatabaseCustomProperty(id = Uuid.random().toString(), name = cleanedName, valueType = valueType)
        writeTextEditsNow()
        writeInOrder { historyGeneration ->
            val latestBlock = host.findDatabaseBlock(blockId) ?: return@writeInOrder
            val blockWithProperty = latestBlock.withDatabasePropertyCreated(property)
            val rowChanges = repository.addDatabaseColumnToRows(blockWithProperty, DatabaseColumnTarget.CustomProperty(property.id))
            rowChanges.forEach { rememberRowWasWritten(it.rowNoteId) }
            host.changeDatabaseBlockTogetherWithRows(blockId, rowChanges, historyGeneration) {
                it.withDatabasePropertyCreated(property)
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

    fun hasUnfinishedWrites(): Boolean = pendingTextEdits.isNotEmpty() || writesInFlight > 0

    suspend fun finishWrites() {
        writeTextEditsNow()
        writeOrder.withLock { }
    }

    fun applyHistoryStep(rowChanges: List<DatabaseRowChange>, direction: HistoryDirection) {
        if (rowChanges.isEmpty()) return
        writeInOrder {
            repository.applyDatabaseRowChanges(rowChanges, direction)
            rowChanges.map { it.rowNoteId }.distinct().forEach { rememberRowWasWritten(it) }
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
    }
}
