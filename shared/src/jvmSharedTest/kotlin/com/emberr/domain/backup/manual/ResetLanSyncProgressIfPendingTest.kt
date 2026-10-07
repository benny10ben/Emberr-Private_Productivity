package com.emberr.domain.backup.manual

import com.emberr.data.local.prefs.SettingsManager
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResetLanSyncProgressIfPendingTest {

    private val savedValues = mutableMapOf<String, Any?>()

    private fun settings(resetPending: Boolean): SettingsManager =
        Proxy.newProxyInstance(
            SettingsManager::class.java.classLoader,
            arrayOf(SettingsManager::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "isLanSyncResetPending" -> resetPending
                "saveLastPushedTimestamp", "saveLastFetchedTimestamp", "saveLanSyncResetPending" -> {
                    savedValues[method.name] = arguments?.firstOrNull()
                    null
                }
                else -> error("This test does not expect SettingsManager.${method.name} to be called")
            }
        } as SettingsManager

    @Test
    fun afterARestoreTheNextLanSyncStartsFromTheBeginning() {
        resetLanSyncProgressIfPending(settings(resetPending = true))

        assertEquals(0L, savedValues["saveLastPushedTimestamp"])
        assertEquals(0L, savedValues["saveLastFetchedTimestamp"])
    }

    @Test
    fun theResetHappensOnlyOncePerRestore() {
        resetLanSyncProgressIfPending(settings(resetPending = true))

        assertEquals(false, savedValues["saveLanSyncResetPending"])
    }

    @Test
    fun withoutAPendingResetTheLanSyncProgressIsKept() {
        resetLanSyncProgressIfPending(settings(resetPending = false))

        assertTrue(savedValues.isEmpty())
    }
}
