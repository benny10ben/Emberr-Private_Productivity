// Describes where secrets are currently kept, and names the three groups of secrets.
package com.emberr.core.security.secrets

import com.emberr.core.desktop.DesktopAppStorage

sealed interface SecretStorageState {

    data object Starting : SecretStorageState

    data class ProtectedByCredentialManager(val backendDisplayName: String) : SecretStorageState

    data class StoredInPlainText(val reason: String, val remedy: String) : SecretStorageState
}

enum class SecretNamespace(private val releaseKeyringServiceName: String) {
    AiProviders("EmberrAiKeyVault"),
    SelfHostSync("EmberrSelfHostSyncVault"),
    AppSettings("EmberrAppVault");

    val keyringServiceName: String
        get() = keyringServiceNameFor(DesktopAppStorage.isDebugBuild)

    fun keyringServiceNameFor(isDebugBuild: Boolean): String =
        if (isDebugBuild) "${releaseKeyringServiceName}Debug" else releaseKeyringServiceName
}
