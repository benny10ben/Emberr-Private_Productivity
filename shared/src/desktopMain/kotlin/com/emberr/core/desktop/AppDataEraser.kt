package com.emberr.core.desktop

import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

private const val ERASE_REQUEST_FILE_NAME = "erase-all-data-requested"
private val FILES_THE_RUNNING_APP_HOLDS_OPEN = setOf("instance.lock", "instance.socket")

fun requestAppDataEraseOnNextLaunch(emberrDirectory: File): Boolean = runCatching {
    emberrDirectory.mkdirs()
    File(emberrDirectory, ERASE_REQUEST_FILE_NAME).writeText("")
}.isSuccess

@OptIn(ExperimentalPathApi::class)
fun eraseAppDataIfRequested(emberrDirectory: File) {
    val eraseRequestFile = File(emberrDirectory, ERASE_REQUEST_FILE_NAME)
    if (!eraseRequestFile.isFile) return

    val everythingWasDeleted = emberrDirectory.listFiles().orEmpty()
        .filter { entry -> entry.name != ERASE_REQUEST_FILE_NAME && entry.name !in FILES_THE_RUNNING_APP_HOLDS_OPEN }
        .map { entry -> runCatching { entry.toPath().deleteRecursively() }.isSuccess }
        .all { wasDeleted -> wasDeleted }

    if (everythingWasDeleted) eraseRequestFile.delete()
}
