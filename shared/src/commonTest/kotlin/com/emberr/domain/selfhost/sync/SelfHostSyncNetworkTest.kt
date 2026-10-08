package com.emberr.domain.selfhost.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfHostSyncNetworkTest {

    @Test
    fun bothAllowsSyncOnWifiAndOnMobileData() {
        assertTrue(SelfHostSyncNetwork.WIFI_AND_MOBILE_DATA.allowsSync(isOnMeteredNetwork = false))
        assertTrue(SelfHostSyncNetwork.WIFI_AND_MOBILE_DATA.allowsSync(isOnMeteredNetwork = true))
    }

    @Test
    fun wifiOnlyAllowsSyncOnlyOnWifi() {
        assertTrue(SelfHostSyncNetwork.WIFI_ONLY.allowsSync(isOnMeteredNetwork = false))
        assertFalse(SelfHostSyncNetwork.WIFI_ONLY.allowsSync(isOnMeteredNetwork = true))
    }

    @Test
    fun mobileDataOnlyAllowsSyncOnlyOnMobileData() {
        assertFalse(SelfHostSyncNetwork.MOBILE_DATA_ONLY.allowsSync(isOnMeteredNetwork = false))
        assertTrue(SelfHostSyncNetwork.MOBILE_DATA_ONLY.allowsSync(isOnMeteredNetwork = true))
    }

    @Test
    fun aSavedChoiceIsReadBack() {
        assertEquals(SelfHostSyncNetwork.WIFI_ONLY, SelfHostSyncNetwork.fromStoredName("WIFI_ONLY"))
        assertEquals(SelfHostSyncNetwork.MOBILE_DATA_ONLY, SelfHostSyncNetwork.fromStoredName("MOBILE_DATA_ONLY"))
    }

    @Test
    fun nothingSavedOrAnUnknownValueMeansBoth() {
        assertEquals(SelfHostSyncNetwork.WIFI_AND_MOBILE_DATA, SelfHostSyncNetwork.fromStoredName(""))
        assertEquals(SelfHostSyncNetwork.WIFI_AND_MOBILE_DATA, SelfHostSyncNetwork.fromStoredName("SOMETHING_ELSE"))
    }
}
