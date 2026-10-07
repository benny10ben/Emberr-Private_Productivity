package com.emberr.domain.selfhost.crypto

import com.emberr.core.security.AesGcmEncryptionManager
import com.emberr.core.security.SyncEncryptionManager

class DesktopVaultKeyLockTest : VaultKeyLockContract() {
    override fun createKeyDerivationManager(): KeyDerivationManager = Pbkdf2KeyDerivationManager()
    override fun createSyncEncryptionManager(): SyncEncryptionManager = AesGcmEncryptionManager()
}
