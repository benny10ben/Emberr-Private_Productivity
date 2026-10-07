package com.emberr.domain.selfhost.webdav

open class WebDavException(message: String, val statusCode: Int? = null) : Exception(message)

class WebDavConflictException(message: String) : WebDavException(message, statusCode = 412)

class WebDavConfigurationException(message: String) : Exception(message)

class WebDavDecryptionException(message: String, cause: Throwable) : Exception(message, cause)
