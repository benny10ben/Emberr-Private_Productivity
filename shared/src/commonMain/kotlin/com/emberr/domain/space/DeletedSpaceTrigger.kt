package com.emberr.domain.space

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DeletedSpaceTrigger {
    private val _deletedSpaceIds = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val deletedSpaceIds = _deletedSpaceIds.asSharedFlow()

    fun spaceWasDeleted(spaceId: String) {
        _deletedSpaceIds.tryEmit(spaceId)
    }
}
