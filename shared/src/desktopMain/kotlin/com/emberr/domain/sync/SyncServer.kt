package com.emberr.domain.sync

import com.emberr.core.security.SyncEncryptionManager
import com.emberr.core.security.SyncHmacSigner
import com.emberr.data.local.prefs.SettingsManager
import com.emberr.data.local.prefs.SyncConstants
import com.emberr.domain.media.LocalMediaGcTrigger
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.ContentType
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.json.Json
import java.security.MessageDigest

// Tracks active file uploads so GET requests return HTTP 425 (Too Early)
// when a requested file is currently being uploaded by another device.
private object InFlightUploadsTracker {
    private val uploading = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun markStarted(fileName: String) {
        uploading.add(fileName)
    }
    fun markFinished(fileName: String) {
        uploading.remove(fileName)
    }
    fun isUploading(fileName: String): Boolean = fileName in uploading
}

// Parses a standard open-ended "bytes=<start>-" Range header into its start offset. Returns null
// for a missing/malformed header, which callers treat as "no resume requested, send from byte 0."
private fun parseRangeStartOffset(rangeHeader: String?): Long? {
    if (rangeHeader == null) return null
    val match = Regex("""bytes=(\d+)-""").find(rangeHeader) ?: return null
    return match.groupValues[1].toLongOrNull()
}

// Verifies request headers against an HMAC signature to reject expired or tampered requests.
private fun ApplicationCall.hasValidSyncSignature(settingsManager: SettingsManager, hmacSigner: SyncHmacSigner): Boolean {
    val timestampMillis = request.headers[SyncConstants.HEADER_SYNC_TIMESTAMP]?.toLongOrNull() ?: return false
    val signature = request.headers[SyncConstants.HEADER_SYNC_SIGNATURE] ?: return false

    val age = System.currentTimeMillis() - timestampMillis
    if (age > SyncConstants.MAX_REQUEST_AGE_MS || age < -SyncConstants.MAX_REQUEST_AGE_MS) return false

    val secretKey = settingsManager.getSyncEncryptionKey()
    if (secretKey.isBlank()) return false

    val expectedSignature = hmacSigner.sign(
        path = request.path(),
        timestampMillis = timestampMillis,
        secretKey = secretKey
    )
    // Uses constant-time comparison to prevent timing attacks.
    return MessageDigest.isEqual(expectedSignature.toByteArray(), signature.toByteArray())
}

private suspend fun ApplicationCall.rejectedUnacceptableRequest(
    settingsManager: SettingsManager,
    hmacSigner: SyncHmacSigner
): Boolean {
    if (!hasValidSyncSignature(settingsManager, hmacSigner)) {
        respond(io.ktor.http.HttpStatusCode.Unauthorized, "Invalid or expired sync signature")
        return true
    }

    val peerSchemaVersion = request.headers[SyncConstants.HEADER_SYNC_SCHEMA_VERSION]?.toIntOrNull()
    if (peerSchemaVersion == null || !isSupportedLanSyncSchemaVersion(peerSchemaVersion)) {
        respond(
            io.ktor.http.HttpStatusCode.UpgradeRequired,
            "This device speaks sync format $LAN_SYNC_SCHEMA_VERSION and cannot sync with format " +
                    (peerSchemaVersion?.toString() ?: "unknown")
        )
        return true
    }

    return false
}

private const val SERVER_STOP_TIMEOUT_MS = 500L

class RunningSyncServer internal constructor(private val stopServer: () -> Unit) {
    fun stop() {
        runCatching { stopServer() }
    }
}

fun startSyncServer(
    settingsManager: SettingsManager,
    syncRepository: SyncRepository,
    hmacSigner: SyncHmacSigner,
    syncEncryptionManager: SyncEncryptionManager,
    pairingState: SyncPairingState,
    serverAvailability: SyncServerAvailability
): RunningSyncServer? {
    val port = settingsManager.getSyncPort().let { if (it <= 0) SyncConstants.DEFAULT_PORT else it }

    val server = embeddedServer(Netty, host = "0.0.0.0", port = port) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }

        routing {
            get(SyncConstants.ROUTE_FETCH) {
                if (call.rejectedUnacceptableRequest(settingsManager, hmacSigner)) return@get

                pairingState.markPaired()

                // Fetches changes since the client's provided timestamp (idempotent snapshot).
                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                val changes = syncRepository.collectLocalChanges(since)
                call.respond(SyncPayload(changes))
            }

            post(SyncConstants.ROUTE_PUSH) {
                if (call.rejectedUnacceptableRequest(settingsManager, hmacSigner)) return@post
                pairingState.markPaired()

                try {
                    val payload = call.receive<SyncPayload>()
                    // Applies incoming changes per envelope. If any envelope fails or is skipped due to a lock,
                    // returns a non-2xx status so the client knows to retry the push.
                    val appliedCleanly = syncRepository.applyRemoteChanges(payload.changes)
                    if (appliedCleanly) {
                        call.respond(io.ktor.http.HttpStatusCode.OK)
                    } else {
                        call.respond(io.ktor.http.HttpStatusCode.Conflict, "Some changes could not be applied, retry")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest, e.message ?: "Sync push failed")
                }
            }

            post(SyncConstants.ROUTE_UNPAIR) {
                if (call.rejectedUnacceptableRequest(settingsManager, hmacSigner)) return@post

                pairingState.unpairLocally()
                call.respond(io.ktor.http.HttpStatusCode.OK)
            }

            get("/sync/media/{fileName}") {
                if (call.rejectedUnacceptableRequest(settingsManager, hmacSigner)) return@get

                val fileName = call.parameters["fileName"]
                if (fileName == null) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    return@get
                }

                val mediaDir = java.io.File(System.getProperty("user.home"), ".emberr/media")
                val file = java.io.File(mediaDir, fileName)

                if (!file.exists()) {
                    if (InFlightUploadsTracker.isUploading(fileName)) {
                        call.respond(io.ktor.http.HttpStatusCode(425, "Too Early"))
                    } else {
                        LanSyncLog.e("GET /sync/media: $fileName not found at ${file.absolutePath}, responding 404")
                        call.respond(io.ktor.http.HttpStatusCode.NotFound)
                    }
                    return@get
                }

                // A resuming client asks for the file starting partway through, at however many
                // plaintext bytes it already decrypted and saved from an earlier, interrupted
                // attempt - so it never has to re-download bytes it already has.
                val resumeOffset = parseRangeStartOffset(call.request.headers[io.ktor.http.HttpHeaders.Range])
                if (resumeOffset != null && (resumeOffset < 0 || resumeOffset > file.length())) {
                    call.respond(io.ktor.http.HttpStatusCode.RequestedRangeNotSatisfiable)
                    return@get
                }
                val skipBytes = resumeOffset ?: 0L

                val startedAt = System.currentTimeMillis()
                try {
                    call.respondOutputStream(
                        ContentType.Application.OctetStream,
                        status = if (skipBytes > 0) io.ktor.http.HttpStatusCode(206, "Partial Content") else io.ktor.http.HttpStatusCode.OK
                    ) {
                        this.use { responseOutput ->
                            file.inputStream().use { plainInput ->
                                if (skipBytes > 0) plainInput.channel.position(skipBytes)
                                syncEncryptionManager.encryptStream(plainInput, responseOutput, settingsManager.getSyncEncryptionKey())
                            }
                        }
                    }
                } catch (e: Exception) {
                    LanSyncLog.e(
                        "GET /sync/media: streaming $fileName failed after ${System.currentTimeMillis() - startedAt}ms with ${e::class.simpleName}: ${e.message}",
                        e
                    )
                    throw e
                }
            }

            post("/sync/media/{fileName}") {
                if (call.rejectedUnacceptableRequest(settingsManager, hmacSigner)) return@post

                val fileName = call.parameters["fileName"]
                if (fileName == null) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    return@post
                }

                val mediaDir = java.io.File(System.getProperty("user.home"), ".emberr/media").apply { mkdirs() }
                val file = java.io.File(mediaDir, fileName)
                // Stable (not per-attempt-random) temp file path, so an interrupted upload's bytes
                // are still here for a later attempt to resume - moved atomically to its final name
                // only once fully received, to avoid ever exposing an incomplete file.
                val tempFile = java.io.File(mediaDir, "$fileName.upload.tmp")

                // A resuming client tells us how many plaintext bytes of a previous attempt we
                // already confirmed receiving (via the /upload-status check below) and sends only
                // the remainder. If that no longer matches what's actually on disk - e.g. this is
                // the first attempt, or our temp file was reclaimed by GC in the meantime - reject
                // so the client re-checks status and restarts cleanly instead of corrupting the file.
                val resumeOffset = call.request.headers[SyncConstants.HEADER_RESUME_OFFSET]?.toLongOrNull() ?: 0L
                if (resumeOffset > 0) {
                    if (!tempFile.exists() || tempFile.length() != resumeOffset) {
                        call.respond(io.ktor.http.HttpStatusCode.Conflict, "Resume offset does not match server state, restart upload")
                        return@post
                    }
                } else {
                    tempFile.delete()
                }

                val startedAt = System.currentTimeMillis()
                InFlightUploadsTracker.markStarted(fileName)
                try {
                    call.receiveChannel().toInputStream().use { encryptedInput ->
                        java.io.FileOutputStream(tempFile, resumeOffset > 0).use { plainOutput ->
                            syncEncryptionManager.decryptStream(encryptedInput, plainOutput, settingsManager.getSyncEncryptionKey())
                        }
                    }
                    java.nio.file.Files.move(
                        tempFile.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                    call.respond(io.ktor.http.HttpStatusCode.OK)
                } catch (e: Exception) {
                    LanSyncLog.e(
                        "POST /sync/media: $fileName failed after ${System.currentTimeMillis() - startedAt}ms with ${e::class.simpleName}: ${e.message}",
                        e
                    )
                    // The temp file is intentionally kept (not deleted) - whatever whole chunks it
                    // already holds let the next attempt resume instead of starting over, and it'll
                    // only be reclaimed by LocalMediaGarbageCollector if it's truly abandoned.
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest, e.message ?: "Media upload failed")
                } finally {
                    InFlightUploadsTracker.markFinished(fileName)
                    LocalMediaGcTrigger.requestCleanup()
                }
            }

            get("/sync/media/{fileName}/upload-status") {
                if (call.rejectedUnacceptableRequest(settingsManager, hmacSigner)) return@get

                val fileName = call.parameters["fileName"]
                if (fileName == null) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    return@get
                }

                val mediaDir = java.io.File(System.getProperty("user.home"), ".emberr/media")
                val tempFile = java.io.File(mediaDir, "$fileName.upload.tmp")
                val receivedBytes = if (tempFile.exists()) tempFile.length() else 0L
                call.respond(MediaUploadStatus(receivedBytes))
            }

            get("/sync/media/list") {
                if (call.rejectedUnacceptableRequest(settingsManager, hmacSigner)) return@get

                val mediaDir = java.io.File(System.getProperty("user.home"), ".emberr/media")
                val entries = (mediaDir.listFiles() ?: emptyArray())
                    .filter { it.isFile }
                    .map { RemoteMediaEntry(fileName = it.name, lastModified = it.lastModified()) }
                call.respond(RemoteMediaList(entries))
            }

            delete("/sync/media/{fileName}") {
                if (call.rejectedUnacceptableRequest(settingsManager, hmacSigner)) return@delete

                val fileName = call.parameters["fileName"]
                if (fileName == null) {
                    call.respond(io.ktor.http.HttpStatusCode.BadRequest)
                    return@delete
                }

                val mediaDir = java.io.File(System.getProperty("user.home"), ".emberr/media")
                val file = java.io.File(mediaDir, fileName)
                if (file.exists()) file.delete()
                call.respond(io.ktor.http.HttpStatusCode.OK)
            }
        }
    }

    return runCatching { server.start(wait = false) }.fold(
        onSuccess = {
            serverAvailability.markRunning(port)
            RunningSyncServer { server.stop(gracePeriodMillis = 0, timeoutMillis = SERVER_STOP_TIMEOUT_MS) }
        },
        onFailure = { startFailure ->
            runCatching { server.stop(gracePeriodMillis = 0, timeoutMillis = 0) }
            serverAvailability.markUnavailable(port, startFailure)
            null
        }
    )
}