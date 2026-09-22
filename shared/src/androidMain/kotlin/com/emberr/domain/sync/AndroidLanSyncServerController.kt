package com.emberr.domain.sync

class AndroidLanSyncServerController : LanSyncServerController {
    override fun startIfPaired() = Unit
    override fun startForPairing() = Unit
    override fun stopIfNotPaired() = Unit
    override fun stopNow() = Unit
}
