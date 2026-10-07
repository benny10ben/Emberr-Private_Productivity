package com.emberr.domain.selfhost.crypto

interface KeyDerivationManager {
    fun deriveAesKey(passphrase: CharArray, salt: ByteArray): ByteArray
    fun generateSalt(): ByteArray
    fun generateVaultKey(): ByteArray
    fun generatePassphrase(): String
}