// The single place the rest of the desktop app reads and writes secrets through.
package com.emberr.core.security.secrets

import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DesktopSecretStore(
    private val backendSelector: SecretBackendSelector,
    private val plaintextBackend: PlaintextSecretBackend
) {

    private val mutableStorageState = MutableStateFlow<SecretStorageState>(SecretStorageState.Starting)
    val storageState: StateFlow<SecretStorageState> = mutableStorageState.asStateFlow()

    private val mutablePlainTextWarningPending = MutableStateFlow(false)
    val plainTextWarningPending: StateFlow<Boolean> = mutablePlainTextWarningPending.asStateFlow()

    private val backendIsReady = CountDownLatch(1)
    private val startupLock = Any()

    @Volatile
    private var activeBackend: SecretBackend? = null

    @Volatile
    private var startupHasBegun = false

    fun initialise() {
        synchronized(startupLock) {
            if (startupHasBegun) return
            startupHasBegun = true
        }
        try {
            applySelection(backendSelector.selectUsableBackend())
        } catch (cause: Exception) {
            applySelection(
                SecretBackendSelection(
                    backend = plaintextBackend,
                    displayName = "Plain text file",
                    unavailableReason = "Choosing a credential manager failed: ${cause.message.orEmpty()}"
                )
            )
        } finally {
            backendIsReady.countDown()
        }
    }

    suspend fun awaitReadyState(): SecretStorageState {
        withContext(Dispatchers.IO) { awaitActiveBackend() }
        return mutableStorageState.value
    }

    fun readSecret(namespace: SecretNamespace, account: String): String? {
        val backend = awaitActiveBackend()
        val serviceName = namespace.keyringServiceName
        val storedValue = runCatching { backend.readSecret(serviceName, account) }.getOrNull()
        if (storedValue != null) return storedValue
        if (backend === plaintextBackend) return null
        return plaintextBackend.readSecret(serviceName, account)
    }

    fun writeSecret(namespace: SecretNamespace, account: String, secret: String) {
        val backend = awaitActiveBackend()
        val serviceName = namespace.keyringServiceName
        try {
            backend.writeSecret(serviceName, account, secret)
            if (backend !== plaintextBackend && plaintextBackend.readSecret(serviceName, account) != null) {
                runCatching { plaintextBackend.removeSecret(serviceName, account) }
            }
        } catch (cause: Exception) {
            plaintextBackend.writeSecret(serviceName, account, secret)
            reportCredentialManagerStoppedAcceptingWrites(cause)
        }
        refreshPlainTextWarningState()
    }

    fun removeSecret(namespace: SecretNamespace, account: String) {
        val backend = awaitActiveBackend()
        runCatching { backend.removeSecret(namespace.keyringServiceName, account) }
        if (backend !== plaintextBackend) {
            runCatching { plaintextBackend.removeSecret(namespace.keyringServiceName, account) }
        }
    }

    fun rememberPlainTextWarningWasShown() {
        plaintextBackend.rememberPlainTextWarningWasShown()
        refreshPlainTextWarningState()
    }

    private fun refreshPlainTextWarningState() {
        mutablePlainTextWarningPending.value =
            mutableStorageState.value is SecretStorageState.StoredInPlainText &&
                plaintextBackend.hasStoredAnySecret() &&
                !plaintextBackend.wasPlainTextWarningAlreadyShown()
    }

    private fun reportCredentialManagerStoppedAcceptingWrites(cause: Exception) {
        activeBackend = plaintextBackend
        mutableStorageState.value = SecretStorageState.StoredInPlainText(
            reason = "The credential manager stopped accepting writes: ${cause.message.orEmpty()}",
            remedy = CredentialManagerAdvice.remedyForCurrentDesktop()
        )
    }

    private fun applySelection(selection: SecretBackendSelection) {
        if (selection.unavailableReason == null) {
            PlainTextSecretMover.moveIntoCredentialManager(selection.backend, plaintextBackend)
        }
        activeBackend = selection.backend
        mutableStorageState.value = if (selection.unavailableReason == null) {
            SecretStorageState.ProtectedByCredentialManager(selection.displayName)
        } else {
            SecretStorageState.StoredInPlainText(
                reason = selection.unavailableReason,
                remedy = CredentialManagerAdvice.remedyForCurrentDesktop()
            )
        }
        refreshPlainTextWarningState()
    }

    private fun awaitActiveBackend(): SecretBackend {
        if (!startupHasBegun) startInBackground()
        backendIsReady.await()
        return activeBackend ?: plaintextBackend
    }

    private fun startInBackground() {
        Thread({ initialise() }, STARTUP_THREAD_NAME).apply { isDaemon = true }.start()
    }

    private companion object {
        const val STARTUP_THREAD_NAME = "emberr-secret-store-startup"
    }
}
