package com.emberr.domain.backup.manual

import com.emberr.data.local.prefs.SettingsManager
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResetLanSyncProgressIfRestoreUnfinishedTest {

    private val savedTimestamps = mutableMapOf<String, Any?>()

    private fun settings(restoreUnfinished: Boolean): SettingsManager =
        Proxy.newProxyInstance(
            SettingsManager::class.java.classLoader,
            arrayOf(SettingsManager::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "isRestoreUnfinished" -> restoreUnfinished
                "saveLastPushedTimestamp", "saveLastFetchedTimestamp" -> {
                    savedTimestamps[method.name] = arguments?.firstOrNull()
                    null
                }
                else -> error("This test does not expect SettingsManager.${method.name} to be called")
            }
        } as SettingsManager

    @Test
    fun afterAnUnfinishedRestoreTheNextLanSyncStartsFromTheBeginning() {
        resetLanSyncProgressIfRestoreUnfinished(settings(restoreUnfinished = true))

        assertEquals(0L, savedTimestamps["saveLastPushedTimestamp"])
        assertEquals(0L, savedTimestamps["saveLastFetchedTimestamp"])
    }

    @Test
    fun withoutAnUnfinishedRestoreTheLanSyncProgressIsKept() {
        resetLanSyncProgressIfRestoreUnfinished(settings(restoreUnfinished = false))

        assertTrue(savedTimestamps.isEmpty())
    }
}
