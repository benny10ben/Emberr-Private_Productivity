package com.emberr.domain.backup.manual

import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.AppDatabase
import com.emberr.data.local.room.dao.CalendarTaskDao
import com.emberr.data.local.room.inOneTransaction
import com.emberr.database.EmberrDatabase
import com.emberr.domain.ai.ReindexAllNotesUseCase
import com.emberr.domain.backup.BackupFormat
import com.emberr.domain.backup.automatic.BackupRescheduler
import com.emberr.domain.media.LocalMediaGcTrigger
import com.emberr.domain.model.NoteBlock
import com.emberr.domain.model.NoteBlockSerializer
import com.emberr.domain.reminders.ReminderRescheduler
import com.emberr.domain.reminders.ReminderScheduler
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.space.SpaceRepository
import com.emberr.domain.sync.AutoSyncTrigger
import com.emberr.domain.util.sync.SyncCoordinator
import com.emberr.domain.util.sync.SyncEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

class BackupRestorer(
    private val appDatabase: AppDatabase,
    private val noteRepository: NoteRepository,
    private val spaceRepository: SpaceRepository,
    private val settingsManager: SettingsManager,
    private val calendarTaskDao: CalendarTaskDao,
    private val reminderScheduler: ReminderScheduler,
    private val reminderRescheduler: ReminderRescheduler,
    private val vectorDatabase: EmberrDatabase,
    private val reindexAllNotesUseCase: ReindexAllNotesUseCase,
    private val backupRescheduler: BackupRescheduler
) {

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val blockJson = Json { ignoreUnknownKeys = true }

    suspend fun replaceAllDataWith(backup: EmberrBackupData, settingsText: String?) = withContext(Dispatchers.IO) {
        val reminderBlockIdsBeforeRestore = calendarTaskDao.getAllTasksAcrossSpaces().map { it.blockId }

        SyncCoordinator.mutex.withLock {
            appDatabase.replaceAllTablesWith(backup) { rebuildProjections(backup) }
            settingsManager.saveLastPushedTimestamp(0L)
            settingsManager.saveLastFetchedTimestamp(0L)
            settingsManager.forgetLanSyncDesktopId()
        }
        noteRepository.clearCaches()

        settingsText?.let { applyBackupSettings(it) }

        reminderBlockIdsBeforeRestore.forEach { reminderScheduler.cancel(it) }
        reminderRescheduler.rescheduleUpcomingReminders()

        spaceRepository.moveActiveSpaceIfItNoLongerExists()

        vectorDatabase.vectorStoreQueries.deleteAllBlocks()
        backgroundScope.launch { reindexAllNotesUseCase.execute().collect() }

        LocalMediaGcTrigger.requestCleanup()
        delay(100.milliseconds)
        SyncEventBus.emitSyncCompleted("import_complete")
        AutoSyncTrigger.requestSync()
    }

    private suspend fun rebuildProjections(backup: EmberrBackupData) {
        val blocksByNoteId = backup.blocks.groupBy { it.noteId }
        backup.notes.forEach { note ->
            val blocks = blocksByNoteId[note.noteId].orEmpty()
                .sortedBy { it.displayOrder }
                .mapNotNull { decodeBlockOrNull(it.blockDataJson) }
            noteRepository.refreshProjectionsForNote(note, blocks)
        }
    }

    private fun decodeBlockOrNull(blockDataJson: String): NoteBlock? =
        try {
            blockJson.decodeFromString(NoteBlockSerializer, blockDataJson)
        } catch (_: SerializationException) {
            null
        }

    private suspend fun applyBackupSettings(settingsText: String) {
        BackupFormat.applyPreferencesText(settingsManager, settingsText)
        if (settingsManager.autoBackupEnabledFlow.first()) {
            backupRescheduler.rescheduleNow(
                frequency = settingsManager.backupFrequencyFlow.first(),
                time = settingsManager.backupTimeFlow.first(),
                day = settingsManager.backupDayFlow.first()
            )
        }
    }
}

internal suspend fun AppDatabase.replaceAllTablesWith(
    backup: EmberrBackupData,
    afterTablesAreReplaced: suspend () -> Unit
) {
    val dao = backupRestoreDao()
    inOneTransaction {
        dao.deleteAllBlocks()
        dao.deleteAllCanvasStrokes()
        dao.deleteAllCanvasEdges()
        dao.deleteAllCanvasNodes()
        dao.deleteAllNotes()
        dao.deleteAllFolders()
        dao.deleteAllSpaces()
        dao.deleteAllCategories()
        dao.deleteAllPropertyTags()
        dao.deleteAllCustomProperties()
        dao.deleteAllChatSessions()
        dao.deleteAllCalendarEventExceptions()
        dao.deleteAllNoteTombstones()
        dao.deleteAllCalendarTasks()
        dao.deleteAllImageBlocks()
        dao.deleteAllDocumentBlocks()
        dao.deleteAllBookmarkBlocks()
        dao.deleteAllMediaReferences()

        dao.insertSpaces(backup.spaces)
        dao.insertNotes(backup.notes.map { it.copy(selfHostSyncedAt = 0L) })
        dao.insertFolders(backup.folders)
        dao.insertBlocks(backup.blocks)
        dao.insertCategories(backup.categories)
        dao.insertPropertyTags(backup.propertyTags)
        dao.insertCustomProperties(backup.customProperties)
        dao.insertChatSessions(backup.chatSessions)
        dao.insertCalendarEventExceptions(backup.calendarEventExceptions)
        dao.insertNoteTombstones(backup.noteTombstones)
        dao.insertCanvasNodes(backup.canvasNodes)
        dao.insertCanvasEdges(backup.canvasEdges)
        dao.insertCanvasStrokes(backup.canvasStrokes)

        afterTablesAreReplaced()
    }
}
