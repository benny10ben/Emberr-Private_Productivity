package com.emberr.presentation.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emberr.data.local.room.SpaceEntity
import com.emberr.domain.space.ActiveSpaceStore
import com.emberr.domain.space.SpaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpaceViewModel(
    private val spaceRepository: SpaceRepository,
    private val activeSpaceStore: ActiveSpaceStore
) : ViewModel() {

    val spaces: StateFlow<List<SpaceEntity>> = spaceRepository.observeSpaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSpaceId: StateFlow<String> = activeSpaceStore.activeSpaceId
        .stateIn(viewModelScope, SharingStarted.Eagerly, activeSpaceStore.currentActiveSpaceId())

    fun createSpaceAndOpenIt(displayName: String) {
        val cleanedName = displayName.trim()
        if (cleanedName.isEmpty()) return

        viewModelScope.launch {
            val newSpaceId = spaceRepository.createSpace(cleanedName)
            activeSpaceStore.setActiveSpace(newSpaceId)
        }
    }

    fun openSpace(spaceId: String) {
        if (activeSpaceStore.isActiveSpace(spaceId)) return
        activeSpaceStore.setActiveSpace(spaceId)
    }

    fun renameSpace(spaceId: String, displayName: String) {
        val cleanedName = displayName.trim()
        if (cleanedName.isEmpty()) return

        viewModelScope.launch { spaceRepository.renameSpace(spaceId, cleanedName) }
    }

    fun reorderSpaces(orderedSpaceIds: List<String>) {
        viewModelScope.launch { spaceRepository.reorderSpaces(orderedSpaceIds) }
    }

    fun deleteSpace(spaceId: String) {
        if (spaces.value.size <= 1) return

        viewModelScope.launch { spaceRepository.deleteSpace(spaceId) }
    }
}
