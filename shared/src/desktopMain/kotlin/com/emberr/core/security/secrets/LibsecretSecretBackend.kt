package com.emberr.core.security.secrets

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

class LibsecretSecretBackend private constructor(
    private val libsecret: LibsecretLibrary,
    private val glib: GlibLibrary
) : SecretBackend {

    private val schema = EmberrSecretSchema().apply { write() }

    override fun readSecret(service: String, account: String): String? {
        val error = PointerByReference()
        val secretPointer = libsecret.secret_password_lookup_sync(
            schema.pointer,
            null,
            error,
            SERVICE_ATTRIBUTE, service,
            ACCOUNT_ATTRIBUTE, account,
            null
        )
        throwIfFailed(error)
        if (secretPointer == null) return null
        return try {
            secretPointer.getString(0, Charsets.UTF_8.name())
        } finally {
            libsecret.secret_password_free(secretPointer)
        }
    }

    override fun writeSecret(service: String, account: String, secret: String) {
        val error = PointerByReference()
        val wasStored = libsecret.secret_password_store_sync(
            schema.pointer,
            null,
            "$service: $account",
            secret,
            null,
            error,
            SERVICE_ATTRIBUTE, service,
            ACCOUNT_ATTRIBUTE, account,
            null
        )
        throwIfFailed(error)
        check(wasStored) { "The credential manager did not store the secret." }
    }

    override fun removeSecret(service: String, account: String) {
        val error = PointerByReference()
        libsecret.secret_password_clear_sync(
            schema.pointer,
            null,
            error,
            SERVICE_ATTRIBUTE, service,
            ACCOUNT_ATTRIBUTE, account,
            null
        )
        throwIfFailed(error)
    }

    private fun confirmSecretServiceAnswers(gobject: GobjectLibrary) {
        val error = PointerByReference()
        val secretService = libsecret.secret_service_get_sync(OPEN_SESSION_FLAG, null, error)
        throwIfFailed(error)
        checkNotNull(secretService) { "The Secret Service did not answer." }
        gobject.g_object_unref(secretService)
    }

    private fun throwIfFailed(error: PointerByReference) {
        val errorPointer = error.value ?: return
        val message = GlibError(errorPointer).message.orEmpty()
        glib.g_error_free(errorPointer)
        throw IllegalStateException(message)
    }

    companion object {
        private const val LIBSECRET_FILE_NAME = "libsecret-1.so.0"
        private const val GLIB_FILE_NAME = "libglib-2.0.so.0"
        private const val GOBJECT_FILE_NAME = "libgobject-2.0.so.0"
        private const val OPEN_SESSION_FLAG = 1 shl 1
        private const val SERVICE_ATTRIBUTE = "service"
        private const val ACCOUNT_ATTRIBUTE = "account"

        fun openOrNull(): LibsecretSecretBackend? = runCatching {
            val backend = LibsecretSecretBackend(
                libsecret = Native.load(LIBSECRET_FILE_NAME, LibsecretLibrary::class.java),
                glib = Native.load(GLIB_FILE_NAME, GlibLibrary::class.java)
            )
            backend.confirmSecretServiceAnswers(Native.load(GOBJECT_FILE_NAME, GobjectLibrary::class.java))
            backend
        }.getOrNull()
    }
}

private interface LibsecretLibrary : Library {

    fun secret_service_get_sync(flags: Int, cancellable: Pointer?, error: PointerByReference): Pointer?

    fun secret_password_store_sync(
        schema: Pointer,
        collection: String?,
        label: String,
        password: String,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributeNamesAndValues: String?
    ): Boolean

    fun secret_password_lookup_sync(
        schema: Pointer,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributeNamesAndValues: String?
    ): Pointer?

    fun secret_password_clear_sync(
        schema: Pointer,
        cancellable: Pointer?,
        error: PointerByReference,
        vararg attributeNamesAndValues: String?
    ): Boolean

    fun secret_password_free(password: Pointer)
}

private interface GlibLibrary : Library {
    fun g_error_free(error: Pointer)
}

private interface GobjectLibrary : Library {
    fun g_object_unref(instance: Pointer)
}

@Structure.FieldOrder(
    "name",
    "flags",
    "attributes",
    "reserved",
    "reserved1",
    "reserved2",
    "reserved3",
    "reserved4",
    "reserved5",
    "reserved6",
    "reserved7"
)
class EmberrSecretSchema : Structure() {
    @JvmField var name: String? = "com.emberr.Secret"
    @JvmField var flags: Int = DO_NOT_MATCH_SCHEMA_NAME_FLAG
    @JvmField var attributes: Array<SecretSchemaAttribute> = Array(MAXIMUM_ATTRIBUTE_COUNT) { index ->
        SecretSchemaAttribute().apply { name = attributeNames.getOrNull(index) }
    }
    @JvmField var reserved: Int = 0
    @JvmField var reserved1: Pointer? = null
    @JvmField var reserved2: Pointer? = null
    @JvmField var reserved3: Pointer? = null
    @JvmField var reserved4: Pointer? = null
    @JvmField var reserved5: Pointer? = null
    @JvmField var reserved6: Pointer? = null
    @JvmField var reserved7: Pointer? = null

    private companion object {
        const val DO_NOT_MATCH_SCHEMA_NAME_FLAG = 1 shl 1
        const val MAXIMUM_ATTRIBUTE_COUNT = 32
        val attributeNames = listOf("service", "account")
    }
}

@Structure.FieldOrder("name", "type")
class SecretSchemaAttribute : Structure() {
    @JvmField var name: String? = null
    @JvmField var type: Int = STRING_ATTRIBUTE_TYPE

    private companion object {
        const val STRING_ATTRIBUTE_TYPE = 0
    }
}

@Structure.FieldOrder("domain", "code", "message")
class GlibError(pointer: Pointer) : Structure(pointer) {
    @JvmField var domain: Int = 0
    @JvmField var code: Int = 0
    @JvmField var message: String? = null

    init {
        read()
    }
}
