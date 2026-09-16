// Console logging for everything the vault mirror does. Errors are also kept in a file, since a
// packaged desktop app has no terminal to read them from.

package com.emberr.domain.vault

import java.io.File
import java.io.IOException
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val LOG_TAG = "EmberrVault"
private const val MAX_LOG_FILE_BYTES = 512L * 1024L
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

object VaultLog {

    private val fileLock = Any()
    private var logFile: File? = null

    fun keepErrorsIn(file: File) {
        synchronized(fileLock) { logFile = file }
    }

    fun d(message: String) {
        println("$LOG_TAG: $message")
    }

    fun e(message: String) {
        System.err.println("$LOG_TAG: $message")
        appendToLogFile(message)
    }

    private fun appendToLogFile(message: String) {
        synchronized(fileLock) {
            val file = logFile ?: return
            try {
                if (file.length() > MAX_LOG_FILE_BYTES) file.writeText("")
                file.appendText("${LocalTime.now().format(timeFormatter)} $message\n")
            } catch (_: IOException) {
            }
        }
    }
}
