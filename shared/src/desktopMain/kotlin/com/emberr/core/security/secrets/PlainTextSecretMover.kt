package com.emberr.core.security.secrets

object PlainTextSecretMover {

    fun moveIntoCredentialManager(credentialManager: SecretBackend, plaintextBackend: PlaintextSecretBackend) {
        plaintextBackend.storedSecrets().forEach { storedSecret ->
            runCatching {
                credentialManager.writeSecret(storedSecret.service, storedSecret.account, storedSecret.secret)
                val valueReadBack = credentialManager.readSecret(storedSecret.service, storedSecret.account)
                if (valueReadBack == storedSecret.secret) {
                    plaintextBackend.removeSecret(storedSecret.service, storedSecret.account)
                }
            }
        }
    }
}
