package com.emberr.domain.backup.manual

import androidx.room.execSQL
import androidx.room.useWriterConnection
import com.emberr.core.desktop.DesktopAppStorage
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.room.AppDatabase
import com.emberr.domain.backup.BackupFormat
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopManualBackupExporter(
    private val appDatabase: AppDatabase,
    private val settingsManager: SettingsManager
) {

    suspend fun exportToZip(destinationFile: File) {
        val plainDbFile = File(System.getProperty("java.io.tmpdir"), "emberr_manual_export_temp_${System.nanoTime()}.db")
        if (plainDbFile.exists()) plainDbFile.delete()

        try {
            appDatabase.useWriterConnection { connection ->
                connection.execSQL("VACUUM INTO '${plainDbFile.absolutePath}'")
            }
            check(plainDbFile.length() > 0L) { "VACUUM INTO produced an empty file" }
            val preferencesText = BackupFormat.buildPreferencesText(settingsManager)
            writeZip(destinationFile, plainDbFile, preferencesText)
        } finally {
            plainDbFile.delete()
        }
    }

    private fun writeZip(destinationFile: File, plainDbFile: File, preferencesText: String) {
        val mediaDir = DesktopAppStorage.mediaDirectory

        ZipOutputStream(FileOutputStream(destinationFile)).use { zipOut ->
            zipOut.putNextEntry(ZipEntry(BackupFormat.DATABASE_ENTRY_NAME))
            plainDbFile.inputStream().use { input -> input.copyTo(zipOut) }
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry(BackupFormat.SETTINGS_ENTRY_NAME))
            zipOut.write(preferencesText.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

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
