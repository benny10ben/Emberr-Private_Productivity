package com.emberr.domain.selfhost.crypto

import com.emberr.core.security.SyncEncryptionManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
private data class VaultFile(val salt: String, val lockedVaultKey: String)

class NewVault(val vaultFileBytes: ByteArray, val vaultKey: ByteArray)

@OptIn(ExperimentalEncodingApi::class)
class VaultKeyLock(
    private val keyDerivationManager: KeyDerivationManager,
    private val syncEncryptionManager: SyncEncryptionManager
) {

    fun createVault(passphrase: CharArray): NewVault {
        val salt = keyDerivationManager.generateSalt()
        val vaultKey = keyDerivationManager.generateVaultKey()
        val passphraseKey = keyDerivationManager.deriveAesKey(passphrase, salt)
        val lockedVaultKey = syncEncryptionManager.encryptBytes(vaultKey, Base64.encode(passphraseKey))

        val vaultFile = VaultFile(salt = Base64.encode(salt), lockedVaultKey = Base64.encode(lockedVaultKey))
        val vaultFileBytes = Json.encodeToString(VaultFile.serializer(), vaultFile).encodeToByteArray()
        return NewVault(vaultFileBytes = vaultFileBytes, vaultKey = vaultKey)
    }

    fun unlockVault(vaultFileBytes: ByteArray, passphrase: CharArray): ByteArray {
        val (salt, lockedVaultKey) = try {
            val vaultFile = Json.decodeFromString(VaultFile.serializer(), vaultFileBytes.decodeToString())
            Base64.decode(vaultFile.salt) to Base64.decode(vaultFile.lockedVaultKey)
        } catch (cause: IllegalArgumentException) {
            throw DamagedVaultFileException(cause)
        }

        val passphraseKey = keyDerivationManager.deriveAesKey(passphrase, salt)
        return try {
            syncEncryptionManager.decryptBytes(lockedVaultKey, Base64.encode(passphraseKey))
        } catch (cause: Exception) {
            throw IncorrectPassphraseException(cause)
        }
    }
}
