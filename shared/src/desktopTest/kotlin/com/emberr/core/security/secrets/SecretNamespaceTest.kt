package com.emberr.core.security.secrets

import kotlin.test.Test
import kotlin.test.assertEquals

class SecretNamespaceTest {

    @Test
    fun releaseBuildKeepsTheOriginalKeyringNames() {
        assertEquals("EmberrAiKeyVault", SecretNamespace.AiProviders.keyringServiceNameFor(isDebugBuild = false))
        assertEquals("EmberrSelfHostSyncVault", SecretNamespace.SelfHostSync.keyringServiceNameFor(isDebugBuild = false))
        assertEquals("EmberrAppVault", SecretNamespace.AppSettings.keyringServiceNameFor(isDebugBuild = false))
    }

    @Test
    fun debugBuildUsesSeparateKeyringNames() {
        assertEquals("EmberrAiKeyVaultDebug", SecretNamespace.AiProviders.keyringServiceNameFor(isDebugBuild = true))
        assertEquals("EmberrSelfHostSyncVaultDebug", SecretNamespace.SelfHostSync.keyringServiceNameFor(isDebugBuild = true))
        assertEquals("EmberrAppVaultDebug", SecretNamespace.AppSettings.keyringServiceNameFor(isDebugBuild = true))
    }

    @Test
    fun appUsesTheReleaseKeyringNamesWhenTheDebugFlagIsMissing() {
        assertEquals("EmberrAiKeyVault", SecretNamespace.AiProviders.keyringServiceName)
    }
}
