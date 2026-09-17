package com.emberr.domain.space

import com.emberr.data.local.prefs.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class ActiveSpaceStore(private val settingsManager: SettingsManager) {

    val activeSpaceId: Flow<String> = settingsManager.activeSpaceIdFlow.distinctUntilChanged()

    fun currentActiveSpaceId(): String = settingsManager.getActiveSpaceId()

    fun isActiveSpace(spaceId: String?): Boolean = spaceId != null && spaceId == currentActiveSpaceId()

    fun setActiveSpace(spaceId: String) {
        require(spaceId.isNotBlank()) { "A space id cannot be blank." }
        settingsManager.saveActiveSpaceId(spaceId)
    }
}
