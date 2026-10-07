package com.emberr.domain.selfhost.crypto

import com.emberr.core.security.SyncEncryptionManager
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

abstract class VaultKeyLockContract {

    abstract fun createKeyDerivationManager(): KeyDerivationManager

    abstract fun createSyncEncryptionManager(): SyncEncryptionManager

    private val passphrase = "AbCdEfGhJkLmNpQr"
    private val wrongPassphrase = "AbCdEfGhJkLmNpQs"

    private fun createVaultKeyLock() = VaultKeyLock(createKeyDerivationManager(), createSyncEncryptionManager())

    @Test
    fun theRightPassphraseUnlocksTheSameVaultKeyThatWasCreated() {
        val vaultKeyLock = createVaultKeyLock()
        val newVault = vaultKeyLock.createVault(passphrase.toCharArray())

        val unlockedVaultKey = vaultKeyLock.unlockVault(newVault.vaultFileBytes, passphrase.toCharArray())

        assertContentEquals(newVault.vaultKey, unlockedVaultKey)
    }

    @Test
    fun aWrongPassphraseIsRejected() {
        val vaultKeyLock = createVaultKeyLock()
        val newVault = vaultKeyLock.createVault(passphrase.toCharArray())

        assertFailsWith<IncorrectPassphraseException> {
            vaultKeyLock.unlockVault(newVault.vaultFileBytes, wrongPassphrase.toCharArray())
        }
    }

    @Test
    fun aDamagedVaultFileIsReportedAsDamagedNotAsAWrongPassphrase() {
        val vaultKeyLock = createVaultKeyLock()

        assertFailsWith<DamagedVaultFileException> {
            vaultKeyLock.unlockVault("this is not a vault file".encodeToByteArray(), passphrase.toCharArray())
        }
    }

    @Test
    fun twoVaultsMadeWithTheSamePassphraseGetDifferentVaultKeys() {
        val vaultKeyLock = createVaultKeyLock()

        val firstVault = vaultKeyLock.createVault(passphrase.toCharArray())
        val secondVault = vaultKeyLock.createVault(passphrase.toCharArray())

        assertFalse(firstVault.vaultKey.contentEquals(secondVault.vaultKey))
    }

    @Test
    fun theVaultFileNeverContainsTheVaultKeyItself() {
        val newVault = createVaultKeyLock().createVault(passphrase.toCharArray())

        val vaultFileText = newVault.vaultFileBytes.decodeToString()

        assertFalse(vaultFileText.contains(Base64.getEncoder().encodeToString(newVault.vaultKey)))
    }
}
