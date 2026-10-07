package com.emberr.domain.backup.manual

import android.content.Context
import android.net.Uri
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.AppDatabase
import com.emberr.domain.backup.BackupFormat
import com.emberr.domain.backup.exportPlainSqliteCopy
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidManualBackupExporter(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val settingsManager: SettingsManager
) {

    suspend fun exportToZip(uri: Uri) {
        val plainDbFile = File(context.cacheDir, "emberr_manual_export_temp.db")
        if (plainDbFile.exists()) plainDbFile.delete()

        try {
            exportPlainSqliteCopy(appDatabase, plainDbFile)
            check(plainDbFile.length() > 0L) { "sqlcipher_export produced an empty file" }
            val preferencesText = BackupFormat.buildPreferencesText(settingsManager)
            writeZip(uri, plainDbFile, preferencesText)
        } finally {
            plainDbFile.delete()
        }
    }

    private fun writeZip(uri: Uri, plainDbFile: File, preferencesText: String) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                zipOut.putNextEntry(ZipEntry(BackupFormat.DATABASE_ENTRY_NAME))
                plainDbFile.inputStream().use { input -> input.copyTo(zipOut) }
                zipOut.closeEntry()

                zipOut.putNextEntry(ZipEntry(BackupFormat.SETTINGS_ENTRY_NAME))
                zipOut.write(preferencesText.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                val mediaDir = File(context.filesDir, "media")
                if (mediaDir.exists() && mediaDir.isDirectory) {
                    mediaDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            zipOut.putNextEntry(ZipEntry("${BackupFormat.MEDIA_ENTRY_PREFIX}${file.name}"))
                            file.inputStream().use { input -> input.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }
            }
        }
    }
}
