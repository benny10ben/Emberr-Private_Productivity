package com.emberr.domain.selfhost.crypto

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Pbkdf2KeyDerivationManager : KeyDerivationManager {

    private val algorithm = "PBKDF2WithHmacSHA256"
    private val iterationCount = 600_000
    private val keyLengthBits = 256
    private val saltLengthBytes = 16
    private val vaultKeyLengthBytes = 32
    private val passphraseLength = 16
    private val passphraseAlphabet = (
        "23456789" +
            "ABCDEFGHJKLMNPQRSTUVWXYZ" +
            "abcdefghijkmnpqrstuvwxyz"
        ).toCharArray()

    override fun deriveAesKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        require(passphrase.size == passphraseLength) {
            "Passphrase must be exactly $passphraseLength characters, was ${passphrase.size}"
        }
        require(salt.isNotEmpty()) { "Salt must not be empty" }

        val keySpec = PBEKeySpec(passphrase, salt, iterationCount, keyLengthBits)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(keySpec).encoded
        } finally {
            keySpec.clearPassword()
        }
    }

    override fun generateSalt(): ByteArray {
        val salt = ByteArray(saltLengthBytes)
        SecureRandom().nextBytes(salt)
        return salt
    }

    override fun generateVaultKey(): ByteArray {
        val vaultKey = ByteArray(vaultKeyLengthBytes)
        SecureRandom().nextBytes(vaultKey)
        return vaultKey
    }

    override fun generatePassphrase(): String {
        val random = SecureRandom()
        return buildString(passphraseLength) {
            repeat(passphraseLength) {
                append(passphraseAlphabet[random.nextInt(passphraseAlphabet.size)])
            }
        }
    }
}