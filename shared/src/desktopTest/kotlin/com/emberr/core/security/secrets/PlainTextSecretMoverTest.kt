package com.emberr.core.security.secrets

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlainTextSecretMoverTest {

    private val storageDirectory = Files.createTempDirectory("emberr-plain-text-secret-mover").toFile()

    @AfterTest
    fun deleteStorageDirectory() {
        storageDirectory.deleteRecursively()
    }

    @Test
    fun everySecretMovesIntoTheCredentialManagerAndLeavesTheFile() {
        val plaintextBackend = PlaintextSecretBackend(storageDirectory)
        plaintextBackend.writeSecret("EmberrAiKeyVault", "openai_api_key", "sk-test")
        plaintextBackend.writeSecret("EmberrSelfHostSyncVault", "vault_key", "vault-secret")
        val credentialManager = InMemorySecretBackend()

        PlainTextSecretMover.moveIntoCredentialManager(credentialManager, plaintextBackend)

        assertEquals("sk-test", credentialManager.readSecret("EmberrAiKeyVault", "openai_api_key"))
        assertEquals("vault-secret", credentialManager.readSecret("EmberrSelfHostSyncVault", "vault_key"))
        assertTrue(PlaintextSecretBackend(storageDirectory).storedSecrets().isEmpty())
    }

    @Test
    fun theWarningShownFlagStaysInTheFileAndIsNotTreatedAsASecret() {
        val plaintextBackend = PlaintextSecretBackend(storageDirectory)
        plaintextBackend.rememberPlainTextWarningWasShown()
        val credentialManager = InMemorySecretBackend()

        PlainTextSecretMover.moveIntoCredentialManager(credentialManager, plaintextBackend)

        assertTrue(credentialManager.storedValues.isEmpty())
        assertTrue(PlaintextSecretBackend(storageDirectory).wasPlainTextWarningAlreadyShown())
    }

    @Test
    fun aSecretStaysInTheFileWhenTheCredentialManagerRejectsTheWrite() {
        val plaintextBackend = PlaintextSecretBackend(storageDirectory)
        plaintextBackend.writeSecret("EmberrAiKeyVault", "openai_api_key", "sk-test")

        PlainTextSecretMover.moveIntoCredentialManager(RejectingSecretBackend(), plaintextBackend)

        assertEquals("sk-test", PlaintextSecretBackend(storageDirectory).readSecret("EmberrAiKeyVault", "openai_api_key"))
    }

    @Test
    fun aSecretStaysInTheFileWhenTheCredentialManagerReadsBackSomethingElse() {
        val plaintextBackend = PlaintextSecretBackend(storageDirectory)
        plaintextBackend.writeSecret("EmberrAiKeyVault", "openai_api_key", "sk-test")

        PlainTextSecretMover.moveIntoCredentialManager(ForgetfulSecretBackend(), plaintextBackend)

        assertEquals("sk-test", PlaintextSecretBackend(storageDirectory).readSecret("EmberrAiKeyVault", "openai_api_key"))
    }

    @Test
    fun anAccountContainingTheSeparatorKeepsItsFullName() {
        val plaintextBackend = PlaintextSecretBackend(storageDirectory)
        plaintextBackend.writeSecret("EmberrAppVault", "server/https://example.com", "password")
        val credentialManager = InMemorySecretBackend()

        PlainTextSecretMover.moveIntoCredentialManager(credentialManager, plaintextBackend)

        assertEquals("password", credentialManager.readSecret("EmberrAppVault", "server/https://example.com"))
        assertNull(PlaintextSecretBackend(storageDirectory).readSecret("EmberrAppVault", "server/https://example.com"))
    }

    private class InMemorySecretBackend : SecretBackend {
        val storedValues = mutableMapOf<Pair<String, String>, String>()

        override fun readSecret(service: String, account: String): String? = storedValues[service to account]

        override fun writeSecret(service: String, account: String, secret: String) {
            storedValues[service to account] = secret
        }

        override fun removeSecret(service: String, account: String) {
            storedValues.remove(service to account)
        }
    }

    private class RejectingSecretBackend : SecretBackend {
        override fun readSecret(service: String, account: String): String? = null

        override fun writeSecret(service: String, account: String, secret: String) {
            throw IllegalStateException("The credential manager is locked.")
        }

        override fun removeSecret(service: String, account: String) {}
    }

    private class ForgetfulSecretBackend : SecretBackend {
        override fun readSecret(service: String, account: String): String? = null

        override fun writeSecret(service: String, account: String, secret: String) {}

        override fun removeSecret(service: String, account: String) {}
    }
}
