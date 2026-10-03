package com.emberr.domain.sync

import com.emberr.core.security.SyncEncryptionManager
import com.emberr.core.security.SyncHmacSigner
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import com.emberr.domain.util.network.LocalNetworkHostValidator
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// PEER_NOT_READY indicates a 425 Too Early response (another device is currently uploading the file).
// FAILED indicates a standard transfer failure (404, timeout, or connection error).
enum class MediaTransferOutcome { SUCCESS, PEER_NOT_READY, FAILED }

class SyncClient(
    private val settingsManager: SettingsManager,
    private val hmacSigner: SyncHmacSigner,
    private val syncEncryptionManager: SyncEncryptionManager
) {
    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        // Extended timeouts to accommodate large file uploads/downloads over slow Wi-Fi.
        const val REQUEST_TIMEOUT_MS = 10 * 60_000L
        const val SOCKET_TIMEOUT_MS = 10 * 60_000L
    }

    private val client = HttpClient {
        expectSuccess = true
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        // Signs outgoing requests using HMAC-SHA256 based on the request path and current timestamp.
        install(createClientPlugin("HmacAuthPlugin") {
            onRequest { request, _ ->
                val timestampMillis = Clock.System.now().toEpochMilliseconds()
                val signature = hmacSigner.sign(
                    path = request.url.encodedPath,
                    timestampMillis = timestampMillis,
                    secretKey = settingsManager.getSyncEncryptionKey()
                )
                request.headers.append(SyncConstants.HEADER_SYNC_TIMESTAMP, timestampMillis.toString())
                request.headers.append(SyncConstants.HEADER_SYNC_SIGNATURE, signature)
                request.headers.append(
                    SyncConstants.HEADER_SYNC_SCHEMA_VERSION,
                    LAN_SYNC_SCHEMA_VERSION.toString()
                )
            }
        })
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                val rejection = (cause as? ResponseException)?.response ?: return@handleResponseExceptionWithRequest
                if (rejection.status == HttpStatusCode.UpgradeRequired) {
                    throw LanSyncSchemaMismatchException(
                        "The paired device does not support sync format $LAN_SYNC_SCHEMA_VERSION. " +
                                "Update Emberr on both devices so they match."
                    )
                }
            }
        }
    }

    // Closes the underlying HTTP client and releases its connection pool and thread resources.
    fun close() {
        client.close()
    }

    private val serverUrl: String
        get() {
            val ip = settingsManager.getSyncIpAddress()
            if (!LocalNetworkHostValidator.isLocalNetworkHost(ip)) {
                throw LanSyncConfigurationException(
                    "Sync target '$ip' is not a local network address - refusing to send " +
                            "unencrypted-transport traffic outside the LAN"
                )
            }
            val port = settingsManager.getSyncPort()
            return "http://$ip:$port"
        }

    suspend fun pushChanges(changes: List<SyncEnvelope>) {
        if (changes.isEmpty()) return

        client.post("$serverUrl${SyncConstants.ROUTE_PUSH}") {
            contentType(ContentType.Application.Json)
            setBody(SyncPayload(changes))
        }
    }

    suspend fun fetchChanges(since: Long): List<SyncEnvelope> {
        val response = client.get("$serverUrl${SyncConstants.ROUTE_FETCH}") {
            parameter("since", since)
        }
        val payload: SyncPayload = response.body()
        return payload.changes
    }

    suspend fun requestUnpair(): Boolean {
        return try {
            val response = client.post("$serverUrl${SyncConstants.ROUTE_UNPAIR}")
            response.status.value in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // MEDIA ROUTES - streamed through AES/GCM so a large file is never fully buffered in memory

    suspend fun downloadMedia(fileName: String, destinationFile: File): MediaTransferOutcome {
        val tempFile = File(destinationFile.parentFile, "$fileName.tmp")
        val resumeOffset = if (tempFile.exists()) tempFile.length() else 0L
        val url = "$serverUrl/sync/media/$fileName"
        val startedAt = Clock.System.now().toEpochMilliseconds()
        return try {
            val downloaded = client.prepareGet(url) {
                if (resumeOffset > 0) {
                    header(HttpHeaders.Range, "bytes=$resumeOffset-")
                }
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    LanSyncLog.e("downloadMedia: $fileName request failed with status ${response.status.value}")
                    return@execute false
                }
                response.bodyAsChannel().toInputStream().use { encryptedInput ->
                    FileOutputStream(tempFile, resumeOffset > 0).use { plainOutput ->
                        syncEncryptionManager.decryptStream(encryptedInput, plainOutput, settingsManager.getSyncEncryptionKey())
                    }
                }
                true
            }
            // Atomically moves the temporary file to its final destination after a successful download.
            if (downloaded) {
                Files.move(tempFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                MediaTransferOutcome.SUCCESS
            } else {
                MediaTransferOutcome.FAILED
            }
        } catch (e: ClientRequestException) {
            val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
            when (e.response.status.value) {
                425 -> MediaTransferOutcome.PEER_NOT_READY
                // if the peer no longer recognizes our resume offset (e.g. the source file changed
                // size since our last attempt) - discard the stale partial file so the next attempt
                // starts clean instead of repeating a 416 forever.
                416 -> {
                    LanSyncLog.e("downloadMedia: $fileName resume offset $resumeOffset rejected by peer (416), discarding partial file")
                    tempFile.delete()
                    MediaTransferOutcome.FAILED
                }
                else -> {
                    LanSyncLog.e("downloadMedia: $fileName failed after ${elapsedMs}ms with ${e::class.simpleName}: ${e.message}", e)
                    MediaTransferOutcome.FAILED
                }
            }
        } catch (e: Exception) {
            val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
            LanSyncLog.e("downloadMedia: $fileName failed after ${elapsedMs}ms with ${e::class.simpleName}: ${e.message}", e)
            MediaTransferOutcome.FAILED
        }
    }

    suspend fun listRemoteMedia(): List<com.emberr.domain.sync.RemoteMediaEntry> {
        return try {
            client.get("$serverUrl/sync/media/list").body<com.emberr.domain.sync.RemoteMediaList>().entries
        } catch (e: Exception) {
            LanSyncLog.e("listRemoteMedia: failed with ${e::class.simpleName}: ${e.message}", e)
            emptyList()
        }
    }

    // Asks the peer how many bytes of a previous, interrupted upload attempt it still has
    // buffered in its receiving temp file, so this attempt knows where to resume from. Returns 0
    // both when there's genuinely no partial upload and when the check itself fails
    suspend fun getUploadStatus(fileName: String): Long {
        return try {
            client.get("$serverUrl/sync/media/$fileName/upload-status").body<MediaUploadStatus>().receivedBytes
        } catch (e: Exception) {
            LanSyncLog.e("getUploadStatus: $fileName failed with ${e::class.simpleName}: ${e.message}", e)
            0L
        }
    }

    suspend fun uploadMedia(fileName: String, file: File): MediaTransferOutcome {
        // This scratch file only ever holds THIS attempt's encrypted bytes - unlike the download
        // side's temp file, it doesn't need to persist across attempts, since the peer (not this
        // device) is the one remembering how much has been received; every attempt re-derives
        // fresh from the always-intact local plaintext source file.
        val tempEncryptedFile = File(file.parentFile, "$fileName.enc.tmp")
        val startedAt = Clock.System.now().toEpochMilliseconds()
        return try {
            val resumeOffset = getUploadStatus(fileName).coerceIn(0L, file.length())

            withContext(Dispatchers.IO) {
                file.inputStream().use { plainInput ->
                    if (resumeOffset > 0) plainInput.channel.position(resumeOffset)
                    tempEncryptedFile.outputStream().use { encryptedOutput ->
                        syncEncryptionManager.encryptStream(plainInput, encryptedOutput, settingsManager.getSyncEncryptionKey())
                    }
                }
            }

            val response = client.post("$serverUrl/sync/media/$fileName") {
                if (resumeOffset > 0) {
                    header(SyncConstants.HEADER_RESUME_OFFSET, resumeOffset.toString())
                }
                contentType(ContentType.Application.OctetStream)
                setBody(object : OutgoingContent.ReadChannelContent() {
                    override val contentType = ContentType.Application.OctetStream
                    override val contentLength = tempEncryptedFile.length()
                    override fun readFrom(): ByteReadChannel = tempEncryptedFile.inputStream().toByteReadChannel()
                })
            }
            val succeeded = response.status.value in 200..299
            if (!succeeded) {
                val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
                LanSyncLog.e("uploadMedia: $fileName rejected with status ${response.status.value} after ${elapsedMs}ms")
            }
            if (succeeded) MediaTransferOutcome.SUCCESS else MediaTransferOutcome.FAILED
        } catch (e: Exception) {
            val elapsedMs = Clock.System.now().toEpochMilliseconds() - startedAt
            LanSyncLog.e("uploadMedia: $fileName failed after ${elapsedMs}ms with ${e::class.simpleName}: ${e.message}", e)
            MediaTransferOutcome.FAILED
        } finally {
            tempEncryptedFile.delete()
        }
    }
}