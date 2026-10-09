// Tries every credential manager this operating system offers and picks the first that passes the probe.
package com.emberr.core.security.secrets

import com.github.javakeyring.KeyringStorageType

class SecretBackendSelector(
    private val probe: SecretBackendProbe,
    private val plaintextBackend: PlaintextSecretBackend
) {

    fun selectUsableBackend(): SecretBackendSelection {
        var atLeastOneManagerAnswered = false

        candidatesForCurrentOperatingSystem().forEach { candidate ->
            val backend = probe.withTimeout(null) { candidate.openBackend() }
            if (backend != null) {
                atLeastOneManagerAnswered = true
                if (probe.isBackendUsable(backend)) {
                    return SecretBackendSelection(
                        backend = backend,
                        displayName = resolveDisplayNameFor(candidate),
                        unavailableReason = null
                    )
                }
            }
        }

        return SecretBackendSelection(
            backend = plaintextBackend,
            displayName = PLAIN_TEXT_DISPLAY_NAME,
            unavailableReason = if (atLeastOneManagerAnswered) {
                "A credential manager answered but rejected a test write."
            } else {
                "No credential manager is running on this system."
            }
        )
    }

    private fun resolveDisplayNameFor(candidate: KeyringCandidate): String =
        probe.withTimeout(null) { candidate.detectDisplayName() } ?: candidate.fallbackDisplayName

    private fun candidatesForCurrentOperatingSystem(): List<KeyringCandidate> {
        val operatingSystemName = System.getProperty("os.name").orEmpty().lowercase()
        return when {
            operatingSystemName.contains("mac") || operatingSystemName.contains("darwin") -> listOf(
                KeyringCandidate("macOS Keychain") { KeyringSecretBackend.openOrNull(KeyringStorageType.OSX_KEYCHAIN) },
                KeyringCandidate("macOS Keychain") { KeyringSecretBackend.openOrNull(KeyringStorageType.LEGACY_OSX_KEYCHAIN) }
            )

            operatingSystemName.contains("windows") -> listOf(
                KeyringCandidate("Windows Credential Manager") {
                    KeyringSecretBackend.openOrNull(KeyringStorageType.WINDOWS_CREDENTIAL_STORE)
                }
            )

            else -> listOf(
                KeyringCandidate(
                    fallbackDisplayName = SecretServiceProviderName.UNRECOGNISED_PROVIDER_NAME,
                    detectDisplayName = { SecretServiceProviderName.detectOrNull() },
                    openBackend = { LibsecretSecretBackend.openOrNull() }
                )
            )
        }
    }

    private class KeyringCandidate(
        val fallbackDisplayName: String,
        val detectDisplayName: () -> String? = { null },
        val openBackend: () -> SecretBackend?
    )

    private companion object {
        const val PLAIN_TEXT_DISPLAY_NAME = "Plain text file"
    }
}

class SecretBackendSelection(
    val backend: SecretBackend,
    val displayName: String,
    val unavailableReason: String?
)
