package com.emberr.domain.model

import kotlinx.serialization.Serializable

const val FAVORITE_NOTE_ORDER_ENTITY_ID = "favorite_note_order"

@Serializable
data class FavoriteNoteOrder(
    val noteIds: List<String> = emptyList(),
    val updatedAt: Long = 0L
)

@Serializable
data class FavoriteNoteOrderBySpace(
    val ordersBySpaceId: Map<String, FavoriteNoteOrder> = emptyMap()
)
