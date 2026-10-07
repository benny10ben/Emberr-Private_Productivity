package com.emberr.domain.selfhost.webdav

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

internal fun HttpClientConfig<*>.askServerNotToCompressResponses() {
    defaultRequest {
        header(HttpHeaders.AcceptEncoding, "identity")
    }
}

internal fun isWeakETag(etag: String): Boolean = etag.startsWith("W/")
