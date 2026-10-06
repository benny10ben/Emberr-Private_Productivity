package com.emberr.domain.backup.manual

import android.content.Context
import android.net.Uri
import com.emberr.data.local.room.AppDatabase
import com.emberr.data.local.room.getDatabaseBuilder
import com.emberr.data.local.room.getRoomDatabase
import com.emberr.domain.backup.BackupFormat
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

class AndroidManualBackupImporter(
    private val context: Context,
    private val backupExporter: AndroidManualBackupExporter,
    private val backupRestorer: BackupRestorer
) {

    suspend fun importFromZip(uri: Uri) {
        val mediaDir = File(context.filesDir, "media")
        val restoredMediaDir = File(context.filesDir, "restore-media-temp")
        val tempDbFile = File(context.cacheDir, "emberr_manual_import_temp_${UUID.randomUUID()}.db")
        if (tempDbFile.exists()) tempDbFile.delete()
        var settingsText: String? = null
        var tempDatabase: AppDatabase? = null

        replaceSafetyCopy(File(context.filesDir, "backups")) { newCopy -> backupExporter.exportToZip(Uri.fromFile(newCopy)) }

        try {
            restoredMediaDir.deleteRecursively()
            restoredMediaDir.mkdirs()

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
                                safeMediaFileName(entry.name, BackupFormat.MEDIA_ENTRY_PREFIX)?.let { fileName ->
                                    File(restoredMediaDir, fileName).outputStream().use { output -> zipIn.copyTo(output) }
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            check(tempDbFile.exists() && tempDbFile.length() > 0L) { "Backup file has no ${BackupFormat.DATABASE_ENTRY_NAME}" }
            checkBackupDatabaseCanBeRestored(tempDbFile)

            tempDatabase = getRoomDatabase(getDatabaseBuilder(context, tempDbFile.absolutePath))
            val backupData = BackupRepositoryImpl(
                noteDao = tempDatabase.noteDao(),
                folderDao = tempDatabase.folderDao(),
                blockDao = tempDatabase.blockDao(),
                calendarTaskDao = tempDatabase.calendarTaskDao(),
                categoryDao = tempDatabase.categoryDao(),
                propertyTagDao = tempDatabase.propertyTagDao(),
                customPropertyDao = tempDatabase.customPropertyDao(),
                imageBlockDao = tempDatabase.imageBlockDao(),
                documentBlockDao = tempDatabase.documentBlockDao(),
                bookmarkBlockDao = tempDatabase.bookmarkBlockDao(),
                spaceDao = tempDatabase.spaceDao(),
                chatSessionDao = tempDatabase.chatSessionDao(),
                calendarEventExceptionDao = tempDatabase.calendarEventExceptionDao(),
                selfHostDeletedNoteDao = tempDatabase.selfHostDeletedNoteDao(),
                canvasDao = tempDatabase.canvasDao()
            ).createBackupData()

            moveRestoredMediaInto(mediaDir, restoredMediaDir)
            backupRestorer.replaceAllDataWith(backupData, settingsText)
        } finally {
            tempDatabase?.close()
            tempDbFile.delete()
            restoredMediaDir.deleteRecursively()
        }
    }
}
