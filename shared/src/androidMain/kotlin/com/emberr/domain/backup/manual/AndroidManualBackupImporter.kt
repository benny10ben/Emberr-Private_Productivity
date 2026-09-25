package com.emberr.domain.backup.manual

import android.content.Context
import android.net.Uri
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.AppDatabase
import com.emberr.data.local.room.getDatabaseBuilder
import com.emberr.data.local.room.getRoomDatabase
import com.emberr.domain.backup.BackupFormat
import com.emberr.domain.backup.automatic.BackupRescheduler
import com.emberr.domain.repository.NoteRepository
import com.emberr.domain.space.SpaceRepository
import com.emberr.domain.util.sync.SyncEventBus
import com.emberr.domain.vault.VaultMirrorTrigger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.milliseconds

class AndroidManualBackupImporter(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val backupRepository: BackupRepository,
    private val noteRepository: NoteRepository,
    private val spaceRepository: SpaceRepository,
    private val backupRescheduler: BackupRescheduler
) {

    suspend fun importFromZip(uri: Uri) {
        val tempDbFile = File(context.cacheDir, "emberr_manual_import_temp_${UUID.randomUUID()}.db")
        if (tempDbFile.exists()) tempDbFile.delete()
        var settingsText: String? = null
        var tempDatabase: AppDatabase? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == BackupFormat.DATABASE_ENTRY_NAME -> {
                                tempDbFile.outputStream().use { output -> zipIn.copyTo(output) }
                            }
                            entry.name == BackupFormat.SETTINGS_ENTRY_NAME -> {
                                settingsText = zipIn.readBytes().decodeToString()
                            }
                            entry.name.startsWith(BackupFormat.MEDIA_ENTRY_PREFIX) -> {
                                val mediaFile = File(context.filesDir, entry.name)
                                mediaFile.parentFile?.mkdirs()
                                mediaFile.outputStream().use { output -> zipIn.copyTo(output) }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            check(tempDbFile.exists() && tempDbFile.length() > 0L) { "Backup file has no ${BackupFormat.DATABASE_ENTRY_NAME}" }

            val builder = getDatabaseBuilder(context, tempDbFile.absolutePath)
            tempDatabase = getRoomDatabase(builder)

            val importedRepository = BackupRepositoryImpl(
                noteDao = tempDatabase.noteDao(),
                folderDao = tempDatabase.folderDao(),
                tagDao = tempDatabase.tagDao(),
                blockDao = tempDatabase.blockDao(),
                calendarTaskDao = tempDatabase.calendarTaskDao(),
                categoryDao = tempDatabase.categoryDao(),
                imageBlockDao = tempDatabase.imageBlockDao(),
                documentBlockDao = tempDatabase.documentBlockDao(),
                bookmarkBlockDao = tempDatabase.bookmarkBlockDao(),
                mediaReferenceDao = tempDatabase.mediaReferenceDao(),
                spaceDao = tempDatabase.spaceDao(),
                chatSessionDao = tempDatabase.chatSessionDao(),
                databaseTemplateDao = tempDatabase.databaseTemplateDao(),
                calendarEventExceptionDao = tempDatabase.calendarEventExceptionDao(),
                selfHostDeletedNoteDao = tempDatabase.selfHostDeletedNoteDao(),
                canvasDao = tempDatabase.canvasDao(),
                settingsManager = settingsManager
            )
            val backupData = importedRepository.createBackupData()
            backupRepository.restoreBackup(backupData)

            settingsText?.let { text ->
                BackupFormat.applyPreferencesText(settingsManager, text)
                if (settingsManager.autoBackupEnabledFlow.first()) {
                    backupRescheduler.rescheduleNow(
                        frequency = settingsManager.backupFrequencyFlow.first(),
                        time = settingsManager.backupTimeFlow.first(),
                        day = settingsManager.backupDayFlow.first()
                    )
                }
            }

            spaceRepository.moveActiveSpaceIfItNoLongerExists()
            noteRepository.clearCaches()
            VaultMirrorTrigger.requestFullRefresh()
            delay(100.milliseconds)
            SyncEventBus.emitSyncCompleted("import_complete")
        } finally {
            tempDatabase?.close()
            tempDbFile.delete()
        }
    }
}
