// Last resort store that keeps secrets as readable text in ~/.emberr/secrets.properties.
package com.emberr.core.security.secrets

import com.emberr.core.security.OwnerOnlyFilePermissions
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

class PlaintextSecretBackend(storageDirectory: File) : SecretBackend {

    private val secretsFile = File(storageDirectory, "secrets.properties")
    private val temporaryFile = File(storageDirectory, "secrets.properties.tmp")
    private val storedValues = Properties()
    private val fileLock = Any()

    init {
        runCatching {
            storageDirectory.mkdirs()
            OwnerOnlyFilePermissions.restrictDirectoryToOwner(storageDirectory.toPath())
        }
        if (secretsFile.exists()) {
            runCatching { secretsFile.inputStream().use { stream -> storedValues.load(stream) } }
            OwnerOnlyFilePermissions.restrictFileToOwner(secretsFile.toPath())
        }
    }

    override fun readSecret(service: String, account: String): String? =
        synchronized(fileLock) { storedValues.getProperty(entryKeyFor(service, account)) }

    override fun writeSecret(service: String, account: String, secret: String) {
        synchronized(fileLock) {
            storedValues.setProperty(entryKeyFor(service, account), secret)
            persistToDisk()
        }
    }

    override fun removeSecret(service: String, account: String) {
        synchronized(fileLock) {
            storedValues.remove(entryKeyFor(service, account))
            persistToDisk()
        }
    }

    fun hasStoredAnySecret(): Boolean = synchronized(fileLock) {
        storedValues.stringPropertyNames().any { key -> key.contains(ENTRY_KEY_SEPARATOR) }
    }

    fun storedSecrets(): List<PlainTextSecret> = synchronized(fileLock) {
        storedValues.stringPropertyNames()
            .filter { key -> key.contains(ENTRY_KEY_SEPARATOR) }
            .map { key ->
                PlainTextSecret(
                    service = key.substringBefore(ENTRY_KEY_SEPARATOR),
                    account = key.substringAfter(ENTRY_KEY_SEPARATOR),
                    secret = storedValues.getProperty(key)
                )
            }
    }

    fun wasPlainTextWarningAlreadyShown(): Boolean =
        synchronized(fileLock) { storedValues.getProperty(PLAIN_TEXT_WARNING_SHOWN_KEY).toBoolean() }

    fun rememberPlainTextWarningWasShown() {
        synchronized(fileLock) {
            storedValues.setProperty(PLAIN_TEXT_WARNING_SHOWN_KEY, true.toString())
            persistToDisk()
        }
    }

    private fun entryKeyFor(service: String, account: String) = "$service$ENTRY_KEY_SEPARATOR$account"

    private fun persistToDisk() {
        val temporaryPath = temporaryFile.toPath()
        Files.deleteIfExists(temporaryPath)
        OwnerOnlyFilePermissions.createFileReadableOnlyByOwner(temporaryPath)
        temporaryFile.outputStream().use { stream -> storedValues.store(stream, null) }
        moveIntoPlace()
        OwnerOnlyFilePermissions.restrictFileToOwner(secretsFile.toPath())
    }

    private fun moveIntoPlace() {
        val temporaryPath = temporaryFile.toPath()
        val destinationPath = secretsFile.toPath()
        try {
            Files.move(
                temporaryPath,
                destinationPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (cause: AtomicMoveNotSupportedException) {
            Files.move(temporaryPath, destinationPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val PLAIN_TEXT_WARNING_SHOWN_KEY = "plain_text_warning_shown"
        const val ENTRY_KEY_SEPARATOR = "/"
    }
}

class PlainTextSecret(val service: String, val account: String, val secret: String)
