package com.emberr.domain.selfhost.sync

class DesktopMeteredNetworkChecker : MeteredNetworkChecker {

    override fun isOnMeteredNetwork(): Boolean = false
}
