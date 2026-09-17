package com.emberr.domain.selfhost.webdav

import com.emberr.core.security.SyncEncryptionManager
import com.emberr.domain.selfhost.crypto.SecureSyncKeyStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalEncodingApi::class)
class WebDavSyncClient(
    private val secureSyncKeyStorage: SecureSyncKeyStorage,
    private val syncEncryptionManager: SyncEncryptionManager
) {

    // Every sync operation holds SyncCoordinator.mutex for its full duration, so a server that
    // accepts a connection but never responds would otherwise hang this call - and the shared
    // mutex, and therefore the editor's own saves - forever instead of just failing this cycle.
    private val httpClient = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
    }

    private companion object {
        const val WEBDAV_LOCKED_STATUS = 423
        const val MAX_LOCK_RETRIES = 3
        const val LOCK_RETRY_BASE_DELAY_MS = 400L
        const val MAX_NETWORK_RETRIES = 2
        const val NETWORK_RETRY_BASE_DELAY_MS = 300L
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val SOCKET_TIMEOUT_MS = 30_000L
    }

    private fun requireCredentials(): SelfHostServerCredentials {
        val credentials = secureSyncKeyStorage.getServerCredentials()
            ?: throw WebDavConfigurationException("No self-hosted WebDAV server has been configured yet")
        WebDavServerUrlValidator.validate(credentials.serverUrl)
        return credentials
    }

    private fun requireEncryptionKeyBase64(): String {
        val key = secureSyncKeyStorage.getEncryptionKey()
            ?: throw WebDavConfigurationException("No sync encryption key has been derived yet")
        return Base64.encode(key)
    }

    private fun joinUrl(baseUrl: String, path: String): String {
        val strippedBase = baseUrl.trimEnd('/')
        val strippedPath = path.trimStart('/')
        return "$strippedBase/$strippedPath"
    }

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    private fun resolveUrl(credentials: SelfHostServerCredentials, remotePath: String): String =
        joinUrl(credentials.serverUrl, remotePath)

    private fun basicAuthHeaderValue(credentials: SelfHostServerCredentials): String {
        val raw = "${credentials.username}:${credentials.password}".encodeToByteArray()
        return "Basic ${Base64.encode(raw)}"
    }

    suspend fun ensureRemoteLayoutExists() {
        createDirectory(WebDavSyncPaths.ROOT)
        createDirectory(WebDavSyncPaths.NOTES_DIR)
        createDirectory(WebDavSyncPaths.DAILY_DIR)
        createDirectory(WebDavSyncPaths.MEDIA_DIR)
        createDirectory(WebDavSyncPaths.CHAT_SESSIONS_DIR)
    }

    suspend fun createDirectory(remotePath: String): Boolean {
        val credentials = requireCredentials()
        val url = ensureTrailingSlash(joinUrl(credentials.serverUrl, remotePath))

        // Same transient lock contention as putFile - ensureRemoteLayoutExists runs this at the start
        // of every sync cycle, so two devices syncing around the same moment can race on the same
        // MKCOL just as easily as on manifest.json.
        var attempt = 0
        // Same transient-network-drop retry as getFile/putFile - this runs first thing in every
        // sync cycle, so an uncaught exception here (unlike the 423 status handled below) would
        // abort the entire cycle for a blip that a retry would have absorbed.
        var networkAttempt = 0
        while (true) {
            val response = try {
                httpClient.request(url) {
                    method = HttpMethod("MKCOL")
                    header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
                }
            } catch (cause: Exception) {
                if (networkAttempt >= MAX_NETWORK_RETRIES) throw cause
                delay((NETWORK_RETRY_BASE_DELAY_MS * (networkAttempt + 1)).milliseconds)
                networkAttempt++
                continue
            }
            when (response.status.value) {
                in 200..299 -> return true
                HttpStatusCode.MethodNotAllowed.value -> return true
                WEBDAV_LOCKED_STATUS -> {
                    if (attempt >= MAX_LOCK_RETRIES) throw statusException("MKCOL", remotePath, response)
                    delay((LOCK_RETRY_BASE_DELAY_MS * (attempt + 1)).milliseconds)
                    attempt++
                }
                else -> throw statusException("MKCOL", remotePath, response)
            }
        }
    }

    suspend fun listDirectory(remotePath: String): List<WebDavResourceInfo> {
        val response = propfind(remotePath, depth = "1", credentials = requireCredentials())
        if (response.status.value !in 200..299) {
            throw statusException("PROPFIND", remotePath, response)
        }
        return parseMultistatusXml(response.bodyAsText(), remotePath, excludeSelf = true)
    }

    suspend fun getResourceInfo(remotePath: String): WebDavResourceInfo? {
        val response = propfind(remotePath, depth = "0", credentials = requireCredentials())
        if (response.status.value == HttpStatusCode.NotFound.value) return null
        if (response.status.value !in 200..299) {
            throw statusException("PROPFIND", remotePath, response)
        }
        return parseMultistatusXml(response.bodyAsText(), remotePath, excludeSelf = false).firstOrNull()
    }

    suspend fun checkETagSupport(): Boolean =
        getResourceInfo(WebDavSyncPaths.MANIFEST_FILE)?.etag != null

    suspend fun testConnection(credentials: SelfHostServerCredentials): WebDavConnectionTestResult {
        return try {
            WebDavServerUrlValidator.validate(credentials.serverUrl)
            val response = propfind(WebDavSyncPaths.ROOT, depth = "0", credentials = credentials)
            when (response.status.value) {
                in 200..299 -> WebDavConnectionTestResult.Success
                HttpStatusCode.NotFound.value -> WebDavConnectionTestResult.Success
                HttpStatusCode.Unauthorized.value -> WebDavConnectionTestResult.InvalidCredentials
                HttpStatusCode.Forbidden.value -> WebDavConnectionTestResult.InvalidCredentials
                else -> WebDavConnectionTestResult.ServerError(response.status.value)
            }
        } catch (cause: WebDavConfigurationException) {
            WebDavConnectionTestResult.InvalidConfiguration(cause.message ?: "This server address isn't valid")
        } catch (cause: Exception) {
            WebDavConnectionTestResult.NetworkFailure(cause)
        }
    }

    suspend fun checkVaultExists(credentials: SelfHostServerCredentials): Boolean {
        WebDavServerUrlValidator.validate(credentials.serverUrl)
        val response = httpClient.request(resolveUrl(credentials, WebDavSyncPaths.SALT_FILE)) {
            method = HttpMethod.Head
            header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
        }
        return when (response.status.value) {
            in 200..299 -> true
            HttpStatusCode.NotFound.value -> false
            else -> throw statusException("HEAD", WebDavSyncPaths.SALT_FILE, response)
        }
    }

    private suspend fun propfind(remotePath: String, depth: String, credentials: SelfHostServerCredentials): HttpResponse {
        val requestBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:resourcetype/>
                <D:getetag/>
                <D:getcontentlength/>
              </D:prop>
            </D:propfind>
        """.trimIndent()

        return httpClient.request(resolveUrl(credentials, remotePath)) {
            method = HttpMethod("PROPFIND")
            header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
            header("Depth", depth)
            contentType(ContentType.Text.Xml)
            setBody(requestBody)
        }
    }

    suspend fun uploadSaltFile(salt: ByteArray, failIfExists: Boolean = false) {
        uploadPlainBytes(WebDavSyncPaths.SALT_FILE, salt, failIfExists = failIfExists)
    }

    suspend fun downloadSaltFile(): ByteArray? {
        return downloadPlainBytes(WebDavSyncPaths.SALT_FILE)
    }

    suspend fun uploadPlainBytes(remotePath: String, bytes: ByteArray, failIfExists: Boolean = false) {
        val credentials = requireCredentials()
        val response = httpClient.put(resolveUrl(credentials, remotePath)) {
            header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
            if (failIfExists) {
                header(HttpHeaders.IfNoneMatch, "*")
            }
            contentType(ContentType.Text.Plain)
            setBody(Base64.encode(bytes))
        }
        when (response.status.value) {
            in 200..299 -> Unit
            HttpStatusCode.PreconditionFailed.value -> throw WebDavConflictException(
                "A file already exists at $remotePath"
            )
            else -> throw statusException("PUT", remotePath, response)
        }
    }

    suspend fun deleteFile(remotePath: String): Boolean {
        val credentials = requireCredentials()
        var networkAttempt = 0
        while (true) {
            val response = try {
                httpClient.request(resolveUrl(credentials, remotePath)) {
                    method = HttpMethod.Delete
                    header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
                }
            } catch (cause: Exception) {
                if (networkAttempt >= MAX_NETWORK_RETRIES) throw cause
                delay((NETWORK_RETRY_BASE_DELAY_MS * (networkAttempt + 1)).milliseconds)
                networkAttempt++
                continue
            }
            return when (response.status.value) {
                in 200..299 -> true
                // Already gone is the same outcome as "we just deleted it" from the caller's
                // perspective - a retried cleanup cycle for an already-deleted file must not throw.
                HttpStatusCode.NotFound.value -> true
                else -> throw statusException("DELETE", remotePath, response)
            }
        }
    }

    suspend fun downloadPlainBytes(remotePath: String): ByteArray? {
        val credentials = requireCredentials()
        val response = httpClient.get(resolveUrl(credentials, remotePath)) {
            header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
        }
        if (response.status.value == HttpStatusCode.NotFound.value) return null
        if (response.status.value !in 200..299) {
            throw statusException("GET", remotePath, response)
        }
        return Base64.decode(response.bodyAsText().trim())
    }

    suspend fun uploadNote(noteId: String, jsonPayload: String, ifMatchEtag: String? = null): String? =
        uploadEncryptedJson(WebDavSyncPaths.notePath(noteId), jsonPayload, ifMatchEtag)

    suspend fun downloadNote(noteId: String): String? =
        downloadAndDecryptJson(WebDavSyncPaths.notePath(noteId))

    suspend fun downloadNoteWithEtag(noteId: String): Pair<String, String?>? =
        downloadAndDecryptJsonWithEtag(WebDavSyncPaths.notePath(noteId))

    suspend fun uploadDaily(
        spaceId: String,
        dateString: String,
        jsonPayload: String,
        ifMatchEtag: String? = null
    ): String? = uploadEncryptedJson(WebDavSyncPaths.dailyPath(spaceId, dateString), jsonPayload, ifMatchEtag)

    suspend fun downloadDaily(spaceId: String, dateString: String): String? =
        downloadAndDecryptJson(WebDavSyncPaths.dailyPath(spaceId, dateString))

    suspend fun downloadDailyWithEtag(spaceId: String, dateString: String): Pair<String, String?>? =
        downloadAndDecryptJsonWithEtag(WebDavSyncPaths.dailyPath(spaceId, dateString))

    // Media files are streamed rather than passed as ByteArray - a note's attached video/document can be
    // orders of magnitude larger than a note's own JSON, so encryptBytes/decryptBytes's whole-array approach
    // (used below for notes/daily JSON, which are always small) would hold multiple copies of a huge file in
    // memory at once. uploadMedia/downloadMediaToFile instead reuse the chunked, memory-bounded
    // encryptStream/decryptStream and read/write the local file directly.
    suspend fun uploadMedia(mediaId: String, sourceFile: File): String? =
        uploadEncryptedFile(WebDavSyncPaths.mediaPath(mediaId), sourceFile)

    suspend fun downloadMediaToFile(mediaId: String, destinationFile: File): Boolean =
        downloadAndDecryptToFile(WebDavSyncPaths.mediaPath(mediaId), destinationFile)

    suspend fun uploadEncryptedJson(remotePath: String, jsonPayload: String, ifMatchEtag: String? = null): String? {
        val encryptedBase64 = syncEncryptionManager.encryptPayload(jsonPayload, requireEncryptionKeyBase64())
        return putFile(remotePath, Base64.decode(encryptedBase64), ifMatchEtag)
    }

    suspend fun downloadAndDecryptJson(remotePath: String): String? {
        val encryptedBytes = getFile(remotePath) ?: return null
        val encryptedBase64 = Base64.encode(encryptedBytes)
        return syncEncryptionManager.decryptPayload(encryptedBase64, requireEncryptionKeyBase64())
    }

    suspend fun downloadAndDecryptJsonWithEtag(remotePath: String): Pair<String, String?>? {
        val (encryptedBytes, etag) = getFileWithEtag(remotePath) ?: return null
        val encryptedBase64 = Base64.encode(encryptedBytes)
        val decrypted = syncEncryptionManager.decryptPayload(encryptedBase64, requireEncryptionKeyBase64())
        return decrypted to etag
    }

    suspend fun uploadEncryptedBytes(remotePath: String, rawBytes: ByteArray, ifMatchEtag: String? = null): String? {
        val encryptedBytes = syncEncryptionManager.encryptBytes(rawBytes, requireEncryptionKeyBase64())
        return putFile(remotePath, encryptedBytes, ifMatchEtag)
    }

    suspend fun downloadAndDecryptBytes(remotePath: String): ByteArray? {
        val encryptedBytes = getFile(remotePath) ?: return null
        return syncEncryptionManager.decryptBytes(encryptedBytes, requireEncryptionKeyBase64())
    }

    suspend fun uploadEncryptedFile(remotePath: String, sourceFile: File): String? {
        // Suffixed with a UUID rather than just sourceFile.name - the media engine's own mutex and
        // sequential per-file loop mean this can't currently collide with itself, but a shared,
        // non-unique temp path here is exactly the pattern that caused real corruption on the LAN
        // sync path once its transfers were decoupled to run concurrently - keep this safe by
        // construction rather than relying on the caller never becoming concurrent.
        val tempEncryptedFile = File(sourceFile.parentFile, "${sourceFile.name}.${java.util.UUID.randomUUID()}.enc.tmp")
        return try {
            sourceFile.inputStream().use { plainInput ->
                tempEncryptedFile.outputStream().use { encryptedOutput ->
                    syncEncryptionManager.encryptStream(plainInput, encryptedOutput, requireEncryptionKeyBase64())
                }
            }
            putFileStreaming(remotePath, tempEncryptedFile)
        } finally {
            tempEncryptedFile.delete()
        }
    }

    suspend fun downloadAndDecryptToFile(remotePath: String, destinationFile: File): Boolean {
        val credentials = requireCredentials()
        // Suffixed with a UUID for the same reason as uploadEncryptedFile's temp file above - not
        // currently reachable given this engine's own mutex + sequential per-file loop, but a shared
        // temp path is exactly what corrupted transfers once the LAN sync path became concurrent.
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.${java.util.UUID.randomUUID()}.tmp")

        // Same network-drop retry as getFileWithEtag, but the decrypt+move happens per attempt too, since a
        // connection dropped mid-body surfaces as an exception out of decryptStream, not a bad status code.
        var attempt = 0
        while (true) {
            try {
                val response = httpClient.get(resolveUrl(credentials, remotePath)) {
                    header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
                }
                if (response.status.value == HttpStatusCode.NotFound.value) return false
                if (response.status.value !in 200..299) {
                    throw statusException("GET", remotePath, response)
                }

                // Decrypts into a temp file first and only moves it over the real destination once the whole
                // transfer succeeds, so a dropped connection can never leave a truncated file at the path
                // syncMediaLocked's file.exists() check would otherwise treat as "already synced".
                response.bodyAsChannel().toInputStream().use { encryptedInput ->
                    tempFile.outputStream().use { plainOutput ->
                        syncEncryptionManager.decryptStream(encryptedInput, plainOutput, requireEncryptionKeyBase64())
                    }
                }
                withContext(Dispatchers.IO) {
                    Files.move(
                        tempFile.toPath(),
                        destinationFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                }
                return true
            } catch (cause: WebDavException) {
                throw cause
            } catch (cause: Exception) {
                if (attempt >= MAX_NETWORK_RETRIES) throw cause
                delay((NETWORK_RETRY_BASE_DELAY_MS * (attempt + 1)).milliseconds)
                attempt++
            } finally {
                tempFile.delete()
            }
        }
    }

    private suspend fun putFileStreaming(remotePath: String, sourceFile: File): String? {
        val credentials = requireCredentials()

        // Same lock-contention and network-drop retries as putFile below; media doesn't use ifMatchEtag since
        // syncMediaLocked never conditions media uploads on one.
        var attempt = 0
        var networkAttempt = 0
        while (true) {
            val response = try {
                httpClient.put(resolveUrl(credentials, remotePath)) {
                    header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
                    contentType(ContentType.Application.OctetStream)
                    setBody(object : OutgoingContent.ReadChannelContent() {
                        override val contentType = ContentType.Application.OctetStream
                        override val contentLength = sourceFile.length()
                        override fun readFrom(): ByteReadChannel = sourceFile.inputStream().toByteReadChannel()
                    })
                }
            } catch (cause: Exception) {
                if (networkAttempt >= MAX_NETWORK_RETRIES) throw cause
                delay((NETWORK_RETRY_BASE_DELAY_MS * (networkAttempt + 1)).milliseconds)
                networkAttempt++
                continue
            }

            when (response.status.value) {
                in 200..299 -> return response.headers[HttpHeaders.ETag]?.trim('"')
                WEBDAV_LOCKED_STATUS -> {
                    if (attempt >= MAX_LOCK_RETRIES) throw statusException("PUT", remotePath, response)
                    delay((LOCK_RETRY_BASE_DELAY_MS * (attempt + 1)).milliseconds)
                    attempt++
                }
                else -> throw statusException("PUT", remotePath, response)
            }
        }
    }

    private suspend fun putFile(remotePath: String, bytes: ByteArray, ifMatchEtag: String?): String? {
        val credentials = requireCredentials()

        // 423 means another client's PUT to this same resource (almost always manifest.json, the one
        // file every device writes every sync cycle) holds the server's write lock right now - a
        // transient, expected side effect of concurrent devices syncing on overlapping schedules, not
        // a real conflict like 412. Retry a few times with backoff instead of failing the whole cycle.
        var attempt = 0
        // Separate counter for a dropped connection mid-request/response (same class of transient
        // network hiccup handled in getFile) - distinct from the lock-contention retries above.
        var networkAttempt = 0
        while (true) {
            val response = try {
                httpClient.put(resolveUrl(credentials, remotePath)) {
                    header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
                    contentType(ContentType.Application.OctetStream)
                    if (ifMatchEtag != null) {
                        header(HttpHeaders.IfMatch, "\"$ifMatchEtag\"")
                    }
                    setBody(bytes)
                }
            } catch (cause: Exception) {
                if (networkAttempt >= MAX_NETWORK_RETRIES) throw cause
                delay((NETWORK_RETRY_BASE_DELAY_MS * (networkAttempt + 1)).milliseconds)
                networkAttempt++
                continue
            }

            when (response.status.value) {
                in 200..299 -> return response.headers[HttpHeaders.ETag]?.trim('"')
                HttpStatusCode.PreconditionFailed.value -> throw WebDavConflictException(
                    "Remote file $remotePath was modified by another client since it was last synced"
                )
                WEBDAV_LOCKED_STATUS -> {
                    if (attempt >= MAX_LOCK_RETRIES) throw statusException("PUT", remotePath, response)
                    delay((LOCK_RETRY_BASE_DELAY_MS * (attempt + 1)).milliseconds)
                    attempt++
                }
                else -> throw statusException("PUT", remotePath, response)
            }
        }
    }

    private suspend fun getFile(remotePath: String): ByteArray? = getFileWithEtag(remotePath)?.first

    // Returns the ETag from the SAME response the content came from, rather than the caller doing a
    // separate PROPFIND later - if the caller then conditions a later PUT on a freshly-read ETag
    // instead of this one, a write from another device landing in between is invisible to that check:
    // the fresh ETag matches by the time of the PUT, so the write silently succeeds and overwrites the
    // other device's change instead of being rejected as a conflict. Callers that push back what they
    // read here (WebDAV note/daily reconcile) must thread this exact ETag through to that PUT.
    private suspend fun getFileWithEtag(remotePath: String): Pair<ByteArray, String?>? {
        val credentials = requireCredentials()

        // A dropped connection mid-response surfaces here as an exception from response.body()
        // (e.g. "Content-Length mismatch") rather than a bad status code, since the status line
        // already arrived before the connection died. Retry a couple of times before giving up -
        // otherwise one transient network hiccup fails the whole sync cycle and stalls until the
        // next scheduled trigger (debounce/poller), which can add tens of seconds to a minute.
        var attempt = 0
        while (true) {
            try {
                val response = httpClient.get(resolveUrl(credentials, remotePath)) {
                    header(HttpHeaders.Authorization, basicAuthHeaderValue(credentials))
                }
                if (response.status.value == HttpStatusCode.NotFound.value) return null
                if (response.status.value !in 200..299) {
                    throw statusException("GET", remotePath, response)
                }
                val bytes: ByteArray = response.body()
                val etag = response.headers[HttpHeaders.ETag]?.trim('"')
                return bytes.takeIf { it.isNotEmpty() }?.let { it to etag }
            } catch (cause: WebDavException) {
                throw cause
            } catch (cause: Exception) {
                if (attempt >= MAX_NETWORK_RETRIES) throw cause
                delay((NETWORK_RETRY_BASE_DELAY_MS * (attempt + 1)).milliseconds)
                attempt++
            }
        }
    }

    private fun statusException(verb: String, remotePath: String, response: HttpResponse): WebDavException {
        return WebDavException(
            "$verb failed for $remotePath with status ${response.status.value}",
            statusCode = response.status.value
        )
    }

    private fun parseMultistatusXml(xml: String, requestPath: String, excludeSelf: Boolean): List<WebDavResourceInfo> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

        val normalizedRequestPath = requestPath.trimEnd('/')
        val responseNodes = document.getElementsByTagNameNS("DAV:", "response")
        val results = mutableListOf<WebDavResourceInfo>()

        for (index in 0 until responseNodes.length) {
            val responseElement = responseNodes.item(index) as? Element ?: continue
            val rawHref = responseElement.getElementsByTagNameNS("DAV:", "href")
                .item(0)?.textContent?.trim() ?: continue

            val href = if (rawHref.startsWith("http://") || rawHref.startsWith("https://")) {
                Url(rawHref).encodedPath
            } else {
                rawHref
            }

            if (excludeSelf && href.trimEnd('/') == normalizedRequestPath) continue

            var etag: String? = null
            var isCollection = false
            var contentLength: Long? = null

            val propstatNodes = responseElement.getElementsByTagNameNS("DAV:", "propstat")
            for (propIndex in 0 until propstatNodes.length) {
                val propstatElement = propstatNodes.item(propIndex) as? Element ?: continue
                val status = propstatElement.getElementsByTagNameNS("DAV:", "status")
                    .item(0)?.textContent.orEmpty()
                if (!status.contains("200")) continue

                val propElement = propstatElement.getElementsByTagNameNS("DAV:", "prop")
                    .item(0) as? Element ?: continue

                etag = propElement.getElementsByTagNameNS("DAV:", "getetag")
                    .item(0)?.textContent?.trim()?.trim('"')

                isCollection = (propElement.getElementsByTagNameNS("DAV:", "resourcetype")
                    .item(0) as? Element)
                    ?.getElementsByTagNameNS("DAV:", "collection")
                    ?.length?.let { it > 0 } == true

                contentLength = propElement.getElementsByTagNameNS("DAV:", "getcontentlength")
                    .item(0)?.textContent?.trim()?.toLongOrNull()
            }

            results += WebDavResourceInfo(
                href = href,
                etag = etag,
                isCollection = isCollection,
                contentLength = contentLength
            )
        }

        return results
    }
}