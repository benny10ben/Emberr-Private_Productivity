package com.emberr.domain.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AppUpdateException(message: String) : Exception(message)

class AppImageUpdater {

    private val httpClient by lazy {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
        }
    }

    suspend fun findNewerRelease(installedVersion: String): AppUpdateManifest? = withContext(Dispatchers.IO) {
        val manifestBytes = downloadBytes(LATEST_MANIFEST_URL)
        val signatureBytes = downloadBytes(LATEST_MANIFEST_SIGNATURE_URL)

        if (!isValidManifestSignature(manifestBytes, signatureBytes)) {
            throw AppUpdateException("The update could not be verified as coming from Emberr, so it was ignored.")
        }

        parseAppUpdateManifest(manifestBytes).takeIf { isNewerVersion(it.version, installedVersion) }
    }

    suspend fun downloadAndReplace(
        release: AppUpdateManifest,
        appImageFile: File,
        onProgressPercent: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val download = release.appImage
        val downloadedFile = File(appImageFile.parentFile, ".${appImageFile.name}.download")

        try {
            httpClient.prepareGet(download.url).execute { response ->
                if (!response.status.isSuccess()) {
                    throw IOException("Server responded with HTTP ${response.status.value}")
                }
                writeWithProgress(response.bodyAsChannel(), downloadedFile, download.sizeBytes, onProgressPercent)
            }
            replaceAppImageWithVerifiedDownload(downloadedFile, appImageFile, download.sha256)
        } finally {
            downloadedFile.delete()
        }
    }

    private suspend fun downloadBytes(url: String): ByteArray {
        val response = httpClient.get(url)
        if (!response.status.isSuccess()) {
            throw IOException("Server responded with HTTP ${response.status.value} for $url")
        }
        return response.body()
    }

    private suspend fun writeWithProgress(
        channel: ByteReadChannel,
        destinationFile: File,
        expectedSizeBytes: Long,
        onProgressPercent: (Int) -> Unit
    ) {
        val buffer = ByteArray(64 * 1024)
        var bytesWritten = 0L
        var lastReportedPercent = -1

        FileOutputStream(destinationFile).use { output ->
            while (true) {
                val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                if (bytesRead == -1) break

                bytesWritten += bytesRead
                if (bytesWritten > expectedSizeBytes) {
                    throw AppUpdateException("The download is larger than expected, so it was not installed.")
                }
                output.write(buffer, 0, bytesRead)

                val percent = (bytesWritten * 100 / expectedSizeBytes).toInt()
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    onProgressPercent(percent)
                }
            }
        }
    }

    companion object {
        const val RELEASES_PAGE_URL = "https://github.com/benny10ben/Emberr-Private_Productivity/releases/latest"
        private const val LATEST_MANIFEST_URL = "$RELEASES_PAGE_URL/download/latest.json"
        private const val LATEST_MANIFEST_SIGNATURE_URL = "$RELEASES_PAGE_URL/download/latest.json.sig"
        private const val CONNECT_TIMEOUT_MS = 30_000L
        private const val SOCKET_TIMEOUT_MS = 60_000L
    }
}
