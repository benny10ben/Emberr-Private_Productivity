package com.emberr.domain.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppUpdateManifest(
    val version: String,
    val appImage: AppUpdateDownload? = null,
    val tarball: AppUpdateDownload? = null,
    val windowsInstaller: AppUpdateDownload? = null
)

@Serializable
data class AppUpdateDownload(
    val url: String,
    val sha256: String,
    val sizeBytes: Long
)

private val manifestJson = Json { ignoreUnknownKeys = true }

fun parseAppUpdateManifest(manifestBytes: ByteArray): AppUpdateManifest =
    manifestJson.decodeFromString(manifestBytes.decodeToString())
