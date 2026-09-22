package com.emberr.domain.sync

interface LanSyncServerController {
    fun startIfPaired()
    fun startForPairing()
    fun stopIfNotPaired()
    fun stopNow()
}
