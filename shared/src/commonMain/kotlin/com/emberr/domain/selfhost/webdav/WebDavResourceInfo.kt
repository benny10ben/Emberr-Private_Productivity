package com.emberr.domain.selfhost.webdav

data class WebDavResourceInfo(
    val href: String,
    val etag: String?,
    val isCollection: Boolean,
    val contentLength: Long?,
    val lastModifiedMs: Long? = null
)