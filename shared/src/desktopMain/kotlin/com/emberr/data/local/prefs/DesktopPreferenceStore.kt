package com.emberr.data.local.prefs

import com.emberr.core.security.OwnerOnlyFilePermissions
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

class DesktopPreferenceStore(storageDirectory: File) {

    private val settingsFile = File(storageDirectory, "settings.properties")
    private val temporaryFile = File(storageDirectory, "settings.properties.tmp")
    private val values = Properties()
    private val writeLock = Any()

    init {
        runCatching {
            storageDirectory.mkdirs()
            OwnerOnlyFilePermissions.restrictDirectoryToOwner(storageDirectory.toPath())
        }

        if (settingsFile.exists()) loadFromDisk()
    }

    fun get(key: String, defaultValue: String): String = values.getProperty(key) ?: defaultValue

    fun getOrNull(key: String): String? = values.getProperty(key)

    fun keys(): Set<String> = values.stringPropertyNames()

    fun put(key: String, value: String) {
        values.setProperty(key, value)
        persist()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        values.getProperty(key)?.toBooleanStrictOrNull() ?: defaultValue

    fun putBoolean(key: String, value: Boolean) = put(key, value.toString())

    fun getInt(key: String, defaultValue: Int): Int =
        values.getProperty(key)?.toIntOrNull() ?: defaultValue

    fun putInt(key: String, value: Int) = put(key, value.toString())

    fun getLong(key: String, defaultValue: Long): Long =
        values.getProperty(key)?.toLongOrNull() ?: defaultValue

    fun putLong(key: String, value: Long) = put(key, value.toString())

    fun getFloat(key: String, defaultValue: Float): Float =
        values.getProperty(key)?.toFloatOrNull() ?: defaultValue

    fun putFloat(key: String, value: Float) = put(key, value.toString())

    fun remove(key: String) {
        values.remove(key)
        persist()
    }

    private fun loadFromDisk() {
        runCatching {
            settingsFile.inputStream().use { stream -> values.load(stream) }
        }
    }

    private fun persist() {
        synchronized(writeLock) {
            runCatching {
                temporaryFile.outputStream().use { stream ->
                    values.store(stream, "Emberr desktop settings")
                }
                Files.move(
                    temporaryFile.toPath(),
                    settingsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                OwnerOnlyFilePermissions.restrictFileToOwner(settingsFile.toPath())
            }
        }
    }
}
