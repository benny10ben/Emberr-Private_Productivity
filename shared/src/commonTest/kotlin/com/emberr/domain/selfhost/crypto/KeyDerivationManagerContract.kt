package com.emberr.domain.selfhost.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class KeyDerivationManagerContract {

    abstract fun createKeyDerivationManager(): KeyDerivationManager

    private val passphrase = "AbCdEfGhJkLmNpQr".toCharArray()
    private val salt = ByteArray(16) { it.toByte() }

    @Test
    fun theSamePassphraseAndSaltAlwaysProduceTheSameKeySoPairedDevicesAgree() {
        val manager = createKeyDerivationManager()

        val firstKey = manager.deriveAesKey(passphrase.copyOf(), salt)
        val secondKey = manager.deriveAesKey(passphrase.copyOf(), salt)

        assertContentEquals(firstKey, secondKey)
    }

    @Test
    fun aDerivedKeyIsAlwaysThirtyTwoBytesSoItFitsAesTwoFiftySix() {
        val manager = createKeyDerivationManager()

        assertEquals(32, manager.deriveAesKey(passphrase.copyOf(), salt).size)
    }

    @Test
    fun aDifferentSaltProducesADifferentKey() {
        val manager = createKeyDerivationManager()

        val keyFromOneSalt = manager.deriveAesKey(passphrase.copyOf(), salt)
        val keyFromAnotherSalt = manager.deriveAesKey(passphrase.copyOf(), ByteArray(16) { (it + 1).toByte() })

        assertFalse(keyFromOneSalt.contentEquals(keyFromAnotherSalt))
    }

    @Test
    fun aPassphraseOfTheWrongLengthIsRefusedBeforeAnyWorkIsDone() {
        val manager = createKeyDerivationManager()

        assertFailsWith<IllegalArgumentException> {
            manager.deriveAesKey("tooShort".toCharArray(), salt)
        }
        assertFailsWith<IllegalArgumentException> {
            manager.deriveAesKey("thisPassphraseIsFarTooLong".toCharArray(), salt)
        }
        assertFailsWith<IllegalArgumentException> {
            manager.deriveAesKey(CharArray(0), salt)
        }
    }

    @Test
    fun anEmptySaltIsRefused() {
        val manager = createKeyDerivationManager()

        assertFailsWith<IllegalArgumentException> {
            manager.deriveAesKey(passphrase.copyOf(), ByteArray(0))
        }
    }

    @Test
    fun aGeneratedSaltIsSixteenRandomBytes() {
        val manager = createKeyDerivationManager()

        val firstSalt = manager.generateSalt()
        val secondSalt = manager.generateSalt()

        assertEquals(16, firstSalt.size)
        assertEquals(16, secondSalt.size)
        assertFalse(firstSalt.contentEquals(secondSalt))
    }

    @Test
    fun aGeneratedVaultKeyIsThirtyTwoRandomBytes() {
        val manager = createKeyDerivationManager()

        val firstVaultKey = manager.generateVaultKey()
        val secondVaultKey = manager.generateVaultKey()

        assertEquals(32, firstVaultKey.size)
        assertEquals(32, secondVaultKey.size)
        assertFalse(firstVaultKey.contentEquals(secondVaultKey))
    }

    @Test
    fun aGeneratedPassphraseIsSixteenCharactersLongToMatchWhatDerivationExpects() {
        val manager = createKeyDerivationManager()

        assertEquals(16, manager.generatePassphrase().length)
    }

    @Test
    fun aGeneratedPassphraseLeavesOutCharactersPeopleMisreadWhenTyping() {
        val manager = createKeyDerivationManager()
        val charactersThatLookAlike = setOf('0', 'O', 'o', '1', 'l', 'I')

        repeat(20) {
            val generated = manager.generatePassphrase()

            assertTrue(
                generated.none { it in charactersThatLookAlike },
                "'$generated' contains an easily misread character"
            )
        }
    }

    @Test
    fun twoGeneratedPassphrasesAreNeverTheSame() {
        val manager = createKeyDerivationManager()

        val generated = List(20) { manager.generatePassphrase() }

        assertEquals(generated.size, generated.toSet().size)
    }

    @Test
    fun aGeneratedPassphraseIsAcceptedByDerivationWithoutAnyEditing() {
        val manager = createKeyDerivationManager()

        val generated = manager.generatePassphrase()

        assertEquals(32, manager.deriveAesKey(generated.toCharArray(), salt).size)
    }
}
