package com.emberr.domain.selfhost.webdav

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebDavETagsTest {

    @Test
    fun aTagStartingWithTheWeakMarkerIsWeak() {
        assertTrue(isWeakETag("W/\"5f2a-1c\""))
    }

    @Test
    fun aQuotedTagIsStrong() {
        assertFalse(isWeakETag("\"5f2a-1c\""))
    }

    @Test
    fun aStrongTagWhoseValueStartsWithTheWeakMarkerIsStillStrong() {
        assertFalse(isWeakETag("\"W/5f2a-1c\""))
    }

    @Test
    fun everyRequestAsksTheServerNotToCompressTheResponse() = runTest {
        var acceptEncodingSent: String? = null
        val client = HttpClient(
            MockEngine { request ->
                acceptEncodingSent = request.headers[HttpHeaders.AcceptEncoding]
                respondOk()
            }
        ) {
            askServerNotToCompressResponses()
        }

        client.get("https://example.com/emberr/manifest.json")

        assertEquals("identity", acceptEncodingSent)
    }
}
