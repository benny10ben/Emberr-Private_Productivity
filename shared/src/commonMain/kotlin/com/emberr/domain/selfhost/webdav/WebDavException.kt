package com.emberr.domain.selfhost.webdav

open class WebDavException(message: String, val statusCode: Int? = null) : Exception(message)

class WebDavConflictException(message: String) : WebDavException(message, statusCode = 412)

class WebDavWeakETagException : WebDavException(
    "This server only sends weak ETags, so Emberr can't save edits without risking overwriting " +
        "changes from your other devices. Use a WebDAV server that sends strong ETags."
)

class WebDavConfigurationException(message: String) : Exception(message)

class WebDavDecryptionException(message: String, cause: Throwable) : Exception(message, cause)
