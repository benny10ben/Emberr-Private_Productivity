package com.emberr.domain.selfhost.sync

enum class SelfHostSyncNetwork {
    WIFI_AND_MOBILE_DATA,
    WIFI_ONLY,
    MOBILE_DATA_ONLY;

    fun allowsSync(isOnMeteredNetwork: Boolean): Boolean = when (this) {
        WIFI_AND_MOBILE_DATA -> true
        WIFI_ONLY -> !isOnMeteredNetwork
        MOBILE_DATA_ONLY -> isOnMeteredNetwork
    }

    companion object {
        fun fromStoredName(storedName: String): SelfHostSyncNetwork =
            entries.firstOrNull { it.name == storedName } ?: WIFI_AND_MOBILE_DATA
    }
}

interface MeteredNetworkChecker {
    fun isOnMeteredNetwork(): Boolean
}
